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
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseEndpointModel;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat.FireHoseRenderTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders static hose links and delegates moving-endpoint links to
 * {@link FireHoseDynamicRenderer}.
 */
public class FireHoseRenderer extends SmartBlockEntityRenderer<FireHoseBlockEntity> {

    private static final float TUBE_WIDTH = 8.0f;
    private static final float TEXTURE_WIDTH = 16.0f;
    private static final Vector3d ZERO = new Vector3d();
    private static final Vector3d UP = new Vector3d(0, 1, 0);
    private static final Vector3d DOWN = new Vector3d(0, -1, 0);
    private static final Vector3d NORTH = new Vector3d(0, 0, -1);
    private static final Vector3d SOUTH = new Vector3d(0, 0, 1);
    private static final Vector3d EAST = new Vector3d(1, 0, 0);
    private static final Vector3d WEST = new Vector3d(-1, 0, 0);

    private static final Map<ResourceLocation, RenderType> HOSE_RENDER_TYPES = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> ENDPOINT_RENDER_TYPES = new HashMap<>();
    private static final RenderType DEFAULT_HOSE_RENDER_TYPE = createHoseRenderType(
            FireHoseAppearances.get(FireHoseAppearances.DEFAULT).hoseTexture());
    private static final RenderType BLACK_HOSE_RENDER_TYPE = createHoseRenderType(
            FireHoseAppearances.get(FireHoseAppearances.BLACK).hoseTexture());

    private static RenderType createHoseRenderType(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    private static RenderType hoseRenderType(FireHoseAppearances.Entry appearance) {
        ResourceLocation texture = appearance.hoseTexture();
        if (texture == null)
            return null;
        if (FireHoseAppearances.DEFAULT.equals(appearance.id()))
            return DEFAULT_HOSE_RENDER_TYPE;
        if (FireHoseAppearances.BLACK.equals(appearance.id()))
            return BLACK_HOSE_RENDER_TYPE;
        return HOSE_RENDER_TYPES.computeIfAbsent(texture, FireHoseRenderer::createHoseRenderType);
    }

    private static RenderType endpointRenderType(ResourceLocation texture) {
        return ENDPOINT_RENDER_TYPES.computeIfAbsent(texture,
            key -> RenderType.entityCutoutNoCull(key));
    }

    private void renderCustomEndpoint(FireHoseBlockEntity be, PoseStack poseStack,
            MultiBufferSource bufferSource, int light, FireHoseAppearances.Entry appearance) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(FireHoseBlock.APPEARANCE)
            || state.getValue(FireHoseBlock.APPEARANCE) != FireHoseEndpointModel.CUSTOM)
            return;

        // Built-in endpoint models are baked from blockstates; external textures use this fallback cuboid.
        VertexConsumer buffer = bufferSource.getBuffer(endpointRenderType(appearance.endpointTexture()));
        poseStack.pushPose();
        rotateEndpointModel(poseStack, state.getValue(FireHoseBlock.FACING));
        renderEndpointCuboid(poseStack, buffer, light);
        poseStack.popPose();
    }

    private static void rotateEndpointModel(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
            case EAST -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            case SOUTH -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case WEST -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
            }
            case UP -> {
            }
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);
    }

    private void renderEndpointCuboid(PoseStack poseStack, VertexConsumer buffer, int light) {
        double x0 = 3.0 / 16.0;
        double y0 = 0.0;
        double z0 = 3.0 / 16.0;
        double x1 = 13.0 / 16.0;
        double y1 = 4.0 / 16.0;
        double z1 = 13.0 / 16.0;

        float u0 = 0.0f;
        float v0 = 0.0f;
        float uSide = 5.0f / 16.0f;
        float vSide = 2.0f / 16.0f;
        float uFace0 = 5.0f / 16.0f;
        float uFace1 = 10.0f / 16.0f;
        float vDown0 = 0.0f;
        float vDown1 = 5.0f / 16.0f;
        float vUp0 = 5.0f / 16.0f;
        float vUp1 = 10.0f / 16.0f;

        endpointQuad(poseStack, buffer, light, NORTH,
            new Vector3d(x1, y0, z0), new Vector3d(x1, y1, z0),
            new Vector3d(x0, y1, z0), new Vector3d(x0, y0, z0), u0, v0, uSide, vSide);
        endpointQuad(poseStack, buffer, light, SOUTH,
            new Vector3d(x0, y0, z1), new Vector3d(x0, y1, z1),
            new Vector3d(x1, y1, z1), new Vector3d(x1, y0, z1), u0, v0, uSide, vSide);
        endpointQuad(poseStack, buffer, light, EAST,
            new Vector3d(x1, y0, z1), new Vector3d(x1, y1, z1),
            new Vector3d(x1, y1, z0), new Vector3d(x1, y0, z0), u0, v0, uSide, vSide);
        endpointQuad(poseStack, buffer, light, WEST,
            new Vector3d(x0, y0, z0), new Vector3d(x0, y1, z0),
            new Vector3d(x0, y1, z1), new Vector3d(x0, y0, z1), u0, v0, uSide, vSide);
        endpointQuad(poseStack, buffer, light, UP,
            new Vector3d(x0, y1, z1), new Vector3d(x0, y1, z0),
            new Vector3d(x1, y1, z0), new Vector3d(x1, y1, z1), uFace0, vUp0, uFace1, vUp1);
        endpointQuad(poseStack, buffer, light, DOWN,
            new Vector3d(x0, y0, z0), new Vector3d(x0, y0, z1),
            new Vector3d(x1, y0, z1), new Vector3d(x1, y0, z0), uFace0, vDown0, uFace1, vDown1);
    }

    private void endpointQuad(PoseStack poseStack, VertexConsumer buffer, int light, Vector3dc normal,
            Vector3dc a, Vector3dc b, Vector3dc c, Vector3dc d, float u0, float v0, float u1, float v1) {
        vert(poseStack, buffer, a, 0xFFFFFFFF, u0, v0, normal, light);
        vert(poseStack, buffer, b, 0xFFFFFFFF, u0, v1, normal, light);
        vert(poseStack, buffer, c, 0xFFFFFFFF, u1, v1, normal, light);
        vert(poseStack, buffer, d, 0xFFFFFFFF, u1, v0, normal, light);
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
        FireHoseAppearances.Entry appearance = be.getHoseAppearance();
        renderCustomEndpoint(be, ps, bufferSource, light, appearance);

        if (be.route != null && be.route.nodes.size() >= 2)
            return;

        if (!be.isController())
            return;

        FireHoseBlockEntity other = be.getPairedHose();
        if (other == null)
            return;

        RenderType renderType = hoseRenderType(appearance);
        if (renderType == null)
            return;

        VertexConsumer buffer = bufferSource.getBuffer(renderType);

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
