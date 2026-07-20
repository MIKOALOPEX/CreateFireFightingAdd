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

package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat.FireHoseRenderTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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
    private static final Vector3d ZERO = new Vector3d();
    private static final Vector3d UP = new Vector3d(0, 1, 0);

    private static final RenderType WHITE_HOSE_RENDER_TYPE = createHoseRenderType("fire_hose_tube",
            CreateFireFightingAdd.path("textures/block/fire_hose.png"));
    private static final RenderType BLACK_HOSE_RENDER_TYPE = createHoseRenderType("fire_hose_tube_black",
            CreateFireFightingAdd.path("textures/block/fire_hose_black.png"));

    private static RenderType createHoseRenderType(String name, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
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
    private final Vector3d normalizedNormal = new Vector3d();
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

    private static Vector3d vector(Vec3i vec) {
        return new Vector3d(vec.getX(), vec.getY(), vec.getZ());
    }

    private static Vector3d vector(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    private static Quaterniond rotationBetween(Vector3dc from, Vector3dc to) {
        Vector3d normalizedFrom = new Vector3d(from);
        Vector3d normalizedTo = new Vector3d(to);
        if (normalizedFrom.lengthSquared() < 1e-8 || normalizedTo.lengthSquared() < 1e-8)
            return new Quaterniond();
        normalizedFrom.normalize();
        normalizedTo.normalize();
        return new Quaterniond().rotationTo(normalizedFrom, normalizedTo);
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

        UUID otherSubLevelID = be.getPartnerSubLevelID();

        BlockPos blockPos = be.getBlockPos();
        Vector3dc center = be.getCenter();
        Vector3d otherCenter = other.getCenter();

        Direction facing = be.getBlockState().getValue(FireHoseBlock.FACING);
        Direction otherFacing = other.getBlockState().getValue(FireHoseBlock.FACING);
        Vector3dc normalA = vector(facing.getNormal());
        Vector3d normalB = vector(otherFacing.getNormal());

        ps.translate(center.x() - blockPos.getX(), center.y() - blockPos.getY(),
                center.z() - blockPos.getZ());

        double PI2 = java.lang.Math.PI / 2.0;
        double PI4 = PI2 / 2.0;
        FireHoseRenderTransform transform = SableStructureClientCompat.transformFireHoseTarget(
                be, otherSubLevelID, otherCenter, normalB);
        otherCenter = transform.partnerCenter();
        normalB = transform.partnerNormal();

        int color = getStressColor(be, partialTicks, otherCenter, center);

        double splineDistance = center.distance(otherCenter);
        List<SplinePoint> splinePoints = generateSpline(
                ZERO,
                otherCenter.sub(center, new Vector3d()),
                normalA, normalB,
                splineDistance / 5.0 + 0.25);

        int totalPoints = splinePoints.size();
        Vector3d pointNormal = new Vector3d();
        Vector3d startUpDir = vector(getUpDirection(be, otherCenter.sub(center, new Vector3d())));

        pointNormal.set(splinePoints.getFirst().normal);

        Matrix3d matrix = new Matrix3d(
                startUpDir, pointNormal,
                startUpDir.cross(pointNormal, new Vector3d()));

        double totalSpringLength = 0.0;
        for (int i = 0; i < totalPoints - 1; i++) {
            SplinePoint point = splinePoints.get(i);
            SplinePoint nextPoint = splinePoints.get(i + 1);
            totalSpringLength += point.point.distance(nextPoint.point);
            matrix.rotateLocal(rotationBetween(point.normal, nextPoint.normal));
        }

        Quaterniond orientation = new Quaterniond();
        Quaterniondc orientation1 = transform.ownerOrientation();
        Quaterniondc orientation2 = transform.partnerOrientation();

        Quaterniond blockOrientation1 = new Quaterniond(facing.getRotation());
        Quaterniond blockOrientation2 = new Quaterniond(otherFacing.getRotation());
        blockOrientation2.premul(orientation2).premul(orientation1.conjugate(new Quaterniond()));

        Quaterniond relativeBlockOrientation = new Quaterniond(blockOrientation1)
                .div(blockOrientation2);

        orientation.mul(new Quaterniond(relativeBlockOrientation));
        orientation.mul(matrix.getNormalizedRotation(new Quaterniond()));

        if (java.lang.Math.abs(UP.dot(
                new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1e-5) {
            orientation.rotateLocalX(java.lang.Math.PI);
        }

        double d = UP.dot(
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

            matrix.rotateLocal(rotationBetween(point.normal, nextPoint.normal));
            matrix.rotateY(-twist / (totalPoints - 1));

            Vector3dc nextUpDir = matrix.getColumn(0, new Vector3d());
            double length = point.point.distance(nextPoint.point);

            renderSegment(ps, point.normal, nextPoint.normal, upDir, nextUpDir,
                    point.point, nextPoint.point,
                    (float) runningSpringLength * uvScale,
                    (float) (runningSpringLength + length) * uvScale,
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
                                Vector3dc startPos, Vector3dc endPos,
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
        Vector3d startDown = startUp.negate(new Vector3d());
        Vector3d endDown = endUp.negate(new Vector3d());
        Vector3d startRight = startLeft.negate(new Vector3d());
        Vector3d endRight = endLeft.negate(new Vector3d());

        // Bottom face
        vert(ms, a, startPos.add(startLeft, vertex).sub(startUp),
                color, 0.0f, uvStart * uvScale, startDown, light);
        vert(ms, a, endPos.add(endLeft, vertex).sub(endUp),
                color, 0.0f, uvEnd * uvScale, endDown, light);
        vert(ms, a, endPos.sub(endLeft, vertex).sub(endUp),
                color, texW, uvEnd * uvScale, endDown, light);
        vert(ms, a, startPos.sub(startLeft, vertex).sub(startUp),
                color, texW, uvStart * uvScale, startDown, light);

        // Top face
        vert(ms, a, startPos.sub(startLeft, vertex).add(startUp),
                color, 0.0f, uvStart * uvScale, startUp, light);
        vert(ms, a, endPos.sub(endLeft, vertex).add(endUp),
                color, 0.0f, uvEnd * uvScale, endUp, light);
        vert(ms, a, endPos.add(endLeft, vertex).add(endUp),
                color, texW, uvEnd * uvScale, endUp, light);
        vert(ms, a, startPos.add(startLeft, vertex).add(startUp),
                color, texW, uvStart * uvScale, startUp, light);

        // Right side face
        vert(ms, a, startPos.sub(startLeft, vertex).sub(startUp),
                color, 0.0f, uvStart * uvScale, startRight, light);
        vert(ms, a, endPos.sub(endLeft, vertex).sub(endUp),
                color, 0.0f, uvEnd * uvScale, endRight, light);
        vert(ms, a, endPos.sub(endLeft, vertex).add(endUp),
                color, texW, uvEnd * uvScale, endRight, light);
        vert(ms, a, startPos.sub(startLeft, vertex).add(startUp),
                color, texW, uvStart * uvScale, startRight, light);

        // Left side face
        vert(ms, a, startPos.add(startLeft, vertex).add(startUp),
                color, 0.0f, uvStart * uvScale, startLeft, light);
        vert(ms, a, endPos.add(endLeft, vertex).add(endUp),
                color, 0.0f, uvEnd * uvScale, endLeft, light);
        vert(ms, a, endPos.add(endLeft, vertex).sub(endUp),
                color, texW, uvEnd * uvScale, endLeft, light);
        vert(ms, a, startPos.add(startLeft, vertex).sub(startUp),
                color, texW, uvStart * uvScale, startLeft, light);
    }

    private void vert(PoseStack ms, VertexConsumer a, Vector3dc pos, int color,
                       float u, float v, Vector3dc normal, int light) {
        float wu = u % 1.0f;
        float wv = v % 1.0f;
        if (wu < 0) wu += 1.0f;
        if (wv < 0) wv += 1.0f;
        normal.normalize(normalizedNormal);
        a.addVertex(ms.last().pose(), (float) pos.x(), (float) pos.y(), (float) pos.z())
                .setColor(color)
                .setUv(wu, wv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(ms.last(), (float) normalizedNormal.x(), (float) normalizedNormal.y(), (float) normalizedNormal.z());
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
