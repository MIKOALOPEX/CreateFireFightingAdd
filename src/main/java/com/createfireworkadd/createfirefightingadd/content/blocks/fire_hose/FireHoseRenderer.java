/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Portions of this software use code from Simulated (dev.simulated_team.simulated),
 * licensed under the MIT License.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */

package com.createfireworkadd.createfirefightingadd.content.blocks.fire_hose;

import com.createfireworkadd.createfirefightingadd.Config;
import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

public class FireHoseRenderer extends SmartBlockEntityRenderer<FireHoseBlockEntity> {

    private static final float TUBE_WIDTH = 8.0f;
    private static final float TEXTURE_WIDTH = 16.0f;

    private static final RenderType WHITE_HOSE_RENDER_TYPE = createHoseRenderType("fire_hose_tube",
            Createfirefightingadd.path("textures/block/fire_hose.png"));
    private static final RenderType BLACK_HOSE_RENDER_TYPE = createHoseRenderType("fire_hose_tube_black",
            Createfirefightingadd.path("textures/block/fire_hose_black.png"));

    private static RenderType createHoseRenderType(String name, ResourceLocation texture) {
        return RenderType.create(
            Createfirefightingadd.MODID + ":" + name,
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
        );
    }

    private final Vector3d controlPointA = new Vector3d();
    private final Vector3d controlPointB = new Vector3d();
    private final Vector3d segmentALerp = new Vector3d();
    private final Vector3d segmentBLerp = new Vector3d();
    private final Vector3d segmentCLerp = new Vector3d();
    private final Vector3d startUp = new Vector3d();
    private final Vector3d endUp = new Vector3d();
    private final Vector3d startLeft = new Vector3d();
    private final Vector3d endLeft = new Vector3d();
    private final Vector3d vertex = new Vector3d();

    public FireHoseRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    private static int getStressColor(FireHoseBlockEntity be, float partialTicks,
                                       Vector3d otherCenter, Vector3dc center) {
        double distance = otherCenter.distance(center);
        double snapDistance = Config.hoseMaxLength * Config.hoseSnapMultiplier;
        double renderLen = be.getRenderLength(partialTicks);
        double flashingStart = snapDistance * 0.85;

        if (distance > flashingStart) {
            double renderTime = Minecraft.getInstance().player.tickCount + partialTicks;
            float alpha = Mth.clamp((float) ((distance - flashingStart) / (snapDistance - flashingStart)), 0.0f, 1.0f) * 0.3f;
            alpha = alpha * Mth.lerp(0.25f, (float) java.lang.Math.sin(renderTime / 3.0f) * 0.5f + 0.5f, 1.0f);

            int r = 255;
            int g = Mth.clamp((int) (255 * (1.0f - alpha * 2)), 0, 255);
            int b = Mth.clamp((int) (255 * (1.0f - alpha * 2)), 0, 255);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return 0xFFFFFFFF;
    }

    @Override
    protected void renderSafe(FireHoseBlockEntity be, float partialTicks, PoseStack ps,
                               MultiBufferSource bufferSource, int light, int overlay) {
        super.renderSafe(be, partialTicks, ps, bufferSource, light, overlay);
        if (!be.isController()) {
            return;
        }

        FireHoseBlockEntity other = be.getPairedHose();
        if (other == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        VertexConsumer buffer = bufferSource.getBuffer(be.isBlackHose() ? BLACK_HOSE_RENDER_TYPE : WHITE_HOSE_RENDER_TYPE);

        ps.pushPose();

        ClientSubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        assert container != null;

        UUID otherSubLevelID = be.getPartnerSubLevelID();
        ClientSubLevel otherSubLevel = otherSubLevelID != null
                ? (ClientSubLevel) container.getSubLevel(otherSubLevelID) : null;
        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(be);

        BlockPos blockPos = be.getBlockPos();
        Vector3dc center = be.getCenter();
        Vector3d otherCenter = other.getCenter();

        Direction facing = be.getBlockState().getValue(FireHoseBlock.FACING);
        Direction otherFacing = other.getBlockState().getValue(FireHoseBlock.FACING);
        Vector3dc normalA = JOMLConversion.atLowerCornerOf(facing.getNormal());
        Vector3d normalB = JOMLConversion.atLowerCornerOf(otherFacing.getNormal());

        ps.translate(center.x() - blockPos.getX(), center.y() - blockPos.getY(),
                center.z() - blockPos.getZ());

        double PI2 = java.lang.Math.PI / 2.0;
        double PI4 = PI2 / 2.0;
        Pose3dc renderPose = subLevel != null ? subLevel.renderPose() : null;
        Pose3dc otherRenderPose = otherSubLevel != null ? otherSubLevel.renderPose() : null;

        if (otherRenderPose != null) {
            otherRenderPose.transformNormal(normalB);
            otherRenderPose.transformPosition(otherCenter);
        }
        if (renderPose != null) {
            renderPose.transformNormalInverse(normalB);
            renderPose.transformPositionInverse(otherCenter);
        }

        int color = getStressColor(be, partialTicks, otherCenter, center);

        double splineDistance = center.distance(otherCenter);
        List<SplinePoint> splinePoints = generateSpline(
                JOMLConversion.ZERO,
                otherCenter.sub(center, new Vector3d()),
                normalA, normalB,
                splineDistance / 5.0 + 0.25);

        int totalPoints = splinePoints.size();
        Vector3d pointNormal = new Vector3d();
        Vector3d startUpDir = JOMLConversion.toJOML(getUpDirection(be,
                otherCenter.sub(center, new Vector3d())));

        pointNormal.set(splinePoints.getFirst().normal);

        Matrix3d matrix = new Matrix3d(
                startUpDir, pointNormal,
                startUpDir.cross(pointNormal, new Vector3d()));

        double totalSpringLength = 0.0;
        for (int i = 0; i < totalPoints - 1; i++) {
            SplinePoint point = splinePoints.get(i);
            SplinePoint nextPoint = splinePoints.get(i + 1);
            totalSpringLength += point.point.distance(nextPoint.point);
            matrix.rotateLocal(
                    SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal));
        }

        Quaterniond orientation = new Quaterniond();
        Quaterniondc orientation1 = renderPose != null
                ? renderPose.orientation() : JOMLConversion.QUAT_IDENTITY;
        Quaterniondc orientation2 = otherRenderPose != null
                ? otherRenderPose.orientation() : JOMLConversion.QUAT_IDENTITY;

        Quaterniond blockOrientation1 = new Quaterniond(facing.getRotation());
        Quaterniond blockOrientation2 = new Quaterniond(otherFacing.getRotation());
        blockOrientation2.premul(orientation2).premul(orientation1.conjugate(new Quaterniond()));

        Quaterniond relativeBlockOrientation = new Quaterniond(blockOrientation1)
                .div(blockOrientation2);

        orientation.mul(new Quaterniond(relativeBlockOrientation));
        orientation.mul(matrix.getNormalizedRotation(new Quaterniond()));

        if (java.lang.Math.abs(OrientedBoundingBox3d.UP.dot(
                new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1e-5) {
            orientation.rotateLocalX(java.lang.Math.PI);
        }

        double d = OrientedBoundingBox3d.UP.dot(
                new Vector3d(orientation.x(), orientation.y(), orientation.z()));
        double deg = 2.0 * java.lang.Math.atan2(-d, orientation.w());
        double twist = java.lang.Math.floor((deg + PI4) / PI2) * PI2 - deg;

        float uvScale = (float) ((be.getRenderLength(partialTicks) - 0.75) / totalSpringLength);
        double runningSpringLength = 0.0;

        matrix.set(startUpDir, pointNormal,
                startUpDir.cross(pointNormal, new Vector3d()));

        for (int i = 0; i < totalPoints - 1; i++) {
            SplinePoint point = splinePoints.get(i);
            SplinePoint nextPoint = splinePoints.get(i + 1);

            Vector3dc upDir = matrix.getColumn(0, new Vector3d());

            matrix.rotateLocal(
                    SimMathUtils.getQuaternionfFromVectorRotation(point.normal, nextPoint.normal));
            matrix.rotateY(-twist / (totalPoints - 1));

            Vector3dc nextUpDir = matrix.getColumn(0, new Vector3d());
            double length = point.point.distance(nextPoint.point);

            renderSegment(ps, point.normal, nextPoint.normal, upDir, nextUpDir,
                    point.point, nextPoint.point, false,
                    (float) runningSpringLength * uvScale,
                    (float) (runningSpringLength + length) * uvScale,
                    light, color, buffer, TUBE_WIDTH, TEXTURE_WIDTH);

            renderSegment(ps, point.normal.negate(new Vector3d()),
                    nextPoint.normal.negate(new Vector3d()),
                    upDir.negate(new Vector3d()), nextUpDir.negate(new Vector3d()),
                    point.point, nextPoint.point, true,
                    0.0f - (float) runningSpringLength * uvScale,
                    0.0f - (float) (runningSpringLength + length) * uvScale,
                    light, color, buffer, TUBE_WIDTH, TEXTURE_WIDTH);

            runningSpringLength += length;
        }

        ps.popPose();
    }

    private Vec3 getUpDirection(FireHoseBlockEntity be, Vector3dc directionToSpring) {
        Direction facing = be.getBlockState().getValue(FireHoseBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        double dot = directionToSpring.dot(normal.x, normal.y, normal.z);
        Vector3d dir = directionToSpring.sub(normal.x * dot, normal.y * dot, normal.z * dot,
                new Vector3d());

        if (dir.lengthSquared() < 1e-6)
            return facing.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);

        return Vec3.atLowerCornerOf(
                Direction.getNearest(dir.x, dir.y, dir.z).getOpposite().getNormal());
    }

    private List<SplinePoint> generateSpline(Vector3dc pointA, Vector3dc pointB,
                                              Vector3dc normalA, Vector3dc normalB,
                                              double controlPointLength) {
        List<SplinePoint> list = new ObjectArrayList<>();
        double influence = controlPointLength;
        pointA.fma(influence, normalA, controlPointA);
        pointB.fma(influence, normalB, controlPointB);

        double len = pointA.distance(pointB);
        int initialPointCount = Mth.clamp(Mth.ceil(len), 5, 8);
        for (int i = 0; i <= initialPointCount; i++) {
            double t = (double) i / initialPointCount;
            pointA.lerp(controlPointA, t, segmentALerp);
            controlPointA.lerp(controlPointB, t, segmentBLerp);
            controlPointB.lerp(pointB, t, segmentCLerp);

            Vector3d point = new Vector3d(segmentALerp
                    .lerp(segmentBLerp, t)
                    .lerp(segmentBLerp.lerp(segmentCLerp, t), t));
            Vector3d normal = new Vector3d();

            if (list.isEmpty()) {
                normal.set(normalA);
            } else if (list.size() == initialPointCount) {
                normal.set(normalB).negate();
            } else {
                point.sub(list.get(list.size() - 1).point, normal).normalize();
            }

            list.add(new SplinePoint(point, normal));
        }
        return list;
    }

    private void renderSegment(PoseStack ms, Vector3dc startDirection, Vector3dc endDirection,
                                Vector3dc inputStartUp, Vector3dc inputEndUp,
                                Vector3dc startPos, Vector3dc endPos, boolean second,
                                float uvStart, float uvEnd, int light, int color,
                                VertexConsumer a, float width, float textureWidth) {
        inputStartUp.cross(startDirection, startLeft).normalize();
        inputEndUp.cross(endDirection, endLeft).normalize();

        float texW = width / textureWidth;
        double scale = width / 16.0 / 2.0;

        startLeft.mul(scale);
        inputStartUp.mul(scale, startUp);
        endLeft.mul(scale);
        inputEndUp.mul(scale, endUp);

        float uvScale = 16.0f / textureWidth;
        float uvXOffset = second ? width / textureWidth : 0.0f;

        // Bottom face
        vert(ms, a, startPos.add(startLeft, vertex).sub(startUp),
                color, 0.0f + uvXOffset, uvStart * uvScale, light);
        vert(ms, a, endPos.add(endLeft, vertex).sub(endUp),
                color, 0.0f + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, endPos.sub(endLeft, vertex).sub(endUp),
                color, texW + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, startPos.sub(startLeft, vertex).sub(startUp),
                color, texW + uvXOffset, uvStart * uvScale, light);

        // Top face
        vert(ms, a, startPos.sub(startLeft, vertex).add(startUp),
                color, 0.0f + uvXOffset, uvStart * uvScale, light);
        vert(ms, a, endPos.sub(endLeft, vertex).add(endUp),
                color, 0.0f + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, endPos.add(endLeft, vertex).add(endUp),
                color, texW + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, startPos.add(startLeft, vertex).add(startUp),
                color, texW + uvXOffset, uvStart * uvScale, light);

        // Right side face
        vert(ms, a, startPos.sub(startLeft, vertex).sub(startUp),
                color, 0.0f + uvXOffset, uvStart * uvScale, light);
        vert(ms, a, endPos.sub(endLeft, vertex).sub(endUp),
                color, 0.0f + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, endPos.sub(endLeft, vertex).add(endUp),
                color, texW + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, startPos.sub(startLeft, vertex).add(startUp),
                color, texW + uvXOffset, uvStart * uvScale, light);

        // Left side face
        vert(ms, a, startPos.add(startLeft, vertex).add(startUp),
                color, 0.0f + uvXOffset, uvStart * uvScale, light);
        vert(ms, a, endPos.add(endLeft, vertex).add(endUp),
                color, 0.0f + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, endPos.add(endLeft, vertex).sub(endUp),
                color, texW + uvXOffset, uvEnd * uvScale, light);
        vert(ms, a, startPos.add(startLeft, vertex).sub(startUp),
                color, texW + uvXOffset, uvStart * uvScale, light);
    }

    private void vert(PoseStack ms, VertexConsumer a, Vector3dc pos, int color,
                       float u, float v, int light) {
        float wu = u % 1.0f;
        float wv = v % 1.0f;
        if (wu < 0) wu += 1.0f;
        if (wv < 0) wv += 1.0f;
        a.addVertex(ms.last().pose(), (float) pos.x(), (float) pos.y(), (float) pos.z())
                .setColor(color)
                .setUv(wu, wv)
                .setUv2(light & 0xFFFF, light >> 16);
    }

    @Override
    public boolean shouldRender(FireHoseBlockEntity be, Vec3 cameraPos) {
        return true;
    }

    @Override
    public boolean shouldRenderOffScreen(FireHoseBlockEntity be) {
        return super.shouldRenderOffScreen(be);
    }

    record SplinePoint(Vector3dc point, Vector3dc normal) {}
}
