package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class FireHoseDynamicRenderer {
	private static final float TUBE_WIDTH = 8.0f;
	private static final float TEXTURE_WIDTH = 16.0f;
	private static final Vector3d UP = new Vector3d(0, 1, 0);
	private static final RenderType WHITE_HOSE_RENDER_TYPE = createHoseRenderType("moving_fire_hose_tube",
		CreateFireFightingAdd.path("textures/block/fire_hose.png"));
	private static final RenderType BLACK_HOSE_RENDER_TYPE = createHoseRenderType("moving_fire_hose_tube_black",
		CreateFireFightingAdd.path("textures/block/fire_hose_black.png"));
	private static final Map<Integer, DynamicHose> DYNAMIC_HOSES = new HashMap<>();

	private FireHoseDynamicRenderer() {
	}

	public static void submit(int key, Vec3 start, Vec3 end, Direction startFacing, Direction endFacing,
			boolean black, long expiresAt) {
		DYNAMIC_HOSES.put(key, new DynamicHose(start, end, startFacing, endFacing, black, expiresAt));
	}

	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || DYNAMIC_HOSES.isEmpty())
			return;

		long gameTime = minecraft.level.getGameTime();
		DYNAMIC_HOSES.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAt());
		if (DYNAMIC_HOSES.isEmpty())
			return;

		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		PoseStack poseStack = event.getPoseStack();
		Camera camera = event.getCamera();
		Vec3 cameraPos = camera.getPosition();

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		for (DynamicHose hose : DYNAMIC_HOSES.values()) {
			VertexConsumer buffer = bufferSource.getBuffer(hose.black() ? BLACK_HOSE_RENDER_TYPE : WHITE_HOSE_RENDER_TYPE);
			renderHose(poseStack, buffer, hose, sampleLight(minecraft.level, hose));
		}
		poseStack.popPose();
		bufferSource.endBatch(WHITE_HOSE_RENDER_TYPE);
		bufferSource.endBatch(BLACK_HOSE_RENDER_TYPE);
	}

	private static RenderType createHoseRenderType(String name, ResourceLocation texture) {
		return RenderType.entityCutoutNoCull(texture);
	}

	private static int sampleLight(Level level, DynamicHose hose) {
		Vec3 center = hose.start().add(hose.end()).scale(0.5);
		return LevelRenderer.getLightColor(level, BlockPos.containing(center));
	}

	private static void renderHose(PoseStack poseStack, VertexConsumer buffer, DynamicHose hose, int light) {
		renderSplineHose(poseStack, buffer, hose.start(), hose.end(), hose.startFacing(), hose.endFacing(), light, true);
	}

	public static void renderSplineHose(PoseStack poseStack, VertexConsumer buffer, Vec3 startPos, Vec3 endPos,
			Direction startFacing, Direction endFacing, int light, boolean applyStressColor) {
		renderSplineHose(poseStack, buffer, startPos, endPos, startFacing, endFacing, light,
			applyStressColor, TUBE_WIDTH);
	}

	public static void renderSplineHose(PoseStack poseStack, VertexConsumer buffer, Vec3 startPos, Vec3 endPos,
			Direction startFacing, Direction endFacing, int light, boolean applyStressColor, float tubeWidth) {
		renderSplineHose(poseStack, buffer, startPos, endPos,
			Vec3.atLowerCornerOf(startFacing.getNormal()),
			Vec3.atLowerCornerOf(endFacing.getNormal()),
			light, applyStressColor, tubeWidth);
	}

	public static void renderSplineHose(PoseStack poseStack, VertexConsumer buffer, Vec3 startPos, Vec3 endPos,
			Vec3 startNormal, Vec3 endNormal, int light, boolean applyStressColor, float tubeWidth) {
		renderSplineHose(poseStack, buffer, startPos, endPos, startNormal, endNormal, light,
			applyStressColor, tubeWidth, tubeWidth);
	}

	public static void renderSplineHose(PoseStack poseStack, VertexConsumer buffer, Vec3 startPos, Vec3 endPos,
			Vec3 startNormal, Vec3 endNormal, int light, boolean applyStressColor, float tubeWidth, float uvTubeWidth) {
		renderSplineHose(poseStack, buffer, startPos, endPos, startNormal, endNormal, null, light,
			applyStressColor, tubeWidth, uvTubeWidth);
	}

	public static void renderSplineHose(PoseStack poseStack, VertexConsumer buffer, Vec3 startPos, Vec3 endPos,
			Vec3 startNormal, Vec3 endNormal, Vec3 endUp, int light, boolean applyStressColor,
			float tubeWidth, float uvTubeWidth) {
		Vector3d start = vector(startPos);
		Vector3d end = vector(endPos);
		Vector3d normalA = vector(startNormal).normalize();
		Vector3d normalB = vector(endNormal).normalize();
		double distance = start.distance(end);
		if (distance < 0.01)
			return;

		List<SplinePoint> splinePoints = generateSpline(start, end, normalA, normalB, distance / 5.0 + 0.25);
		if (splinePoints.size() < 2)
			return;

		int color = applyStressColor ? stressColor(distance) : 0xFFFFFFFF;
		Matrix3d matrix = initialFrame(normalA, end.sub(start, new Vector3d()), splinePoints.get(0).normal());
		double totalLength = 0.0;
		for (int i = 0; i < splinePoints.size() - 1; i++) {
			SplinePoint point = splinePoints.get(i);
			SplinePoint next = splinePoints.get(i + 1);
			totalLength += point.point().distance(next.point());
			matrix.rotateLocal(rotationBetween(point.normal(), next.normal()));
		}

		if (totalLength <= 0)
			return;

		double twist = endUp == null
			? snappedTwist(matrix)
			: -signedAngleAround(matrix.getColumn(0, new Vector3d()), vector(endUp), normalB);
		float uvScale = (float) ((distance - 0.75) / totalLength);
		double runningLength = 0.0;

		matrix = initialFrame(normalA, end.sub(start, new Vector3d()), splinePoints.get(0).normal());
		for (int i = 0; i < splinePoints.size() - 1; i++) {
			SplinePoint point = splinePoints.get(i);
			SplinePoint next = splinePoints.get(i + 1);
			Vector3dc up = matrix.getColumn(0, new Vector3d());
			matrix.rotateLocal(rotationBetween(point.normal(), next.normal()));
			matrix.rotateY(-twist / (splinePoints.size() - 1));
			Vector3dc nextUp = matrix.getColumn(0, new Vector3d());
			double length = point.point().distance(next.point());

			renderSegment(poseStack, point.normal(), next.normal(), up, nextUp,
				point.point(), next.point(), false,
				(float) runningLength * uvScale, (float) (runningLength + length) * uvScale,
				light, color, buffer, tubeWidth, uvTubeWidth);
			renderSegment(poseStack, point.normal().negate(new Vector3d()), next.normal().negate(new Vector3d()),
				up.negate(new Vector3d()), nextUp.negate(new Vector3d()),
				point.point(), next.point(), true,
				0.0f - (float) runningLength * uvScale,
				0.0f - (float) (runningLength + length) * uvScale,
				light, color, buffer, tubeWidth, uvTubeWidth);

			runningLength += length;
		}
	}

	private static double snappedTwist(Matrix3d matrix) {
		Quaterniond orientation = matrix.getNormalizedRotation(new Quaterniond());
		if (Math.abs(UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1e-5)
			orientation.rotateLocalX(Math.PI);

		double halfPi = Math.PI / 2.0;
		double quarterPi = halfPi / 2.0;
		double d = UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()));
		double deg = 2.0 * Math.atan2(-d, orientation.w());
		return Math.floor((deg + quarterPi) / halfPi) * halfPi - deg;
	}

	private static double signedAngleAround(Vector3dc from, Vector3dc to, Vector3dc axis) {
		Vector3d normalizedAxis = new Vector3d(axis).normalize();
		Vector3d projectedFrom = projectOntoPlane(from, normalizedAxis);
		Vector3d projectedTo = projectOntoPlane(to, normalizedAxis);
		if (projectedFrom.lengthSquared() < 1e-8 || projectedTo.lengthSquared() < 1e-8)
			return 0.0;

		projectedFrom.normalize();
		projectedTo.normalize();
		double sin = normalizedAxis.dot(projectedFrom.cross(projectedTo, new Vector3d()));
		double cos = projectedFrom.dot(projectedTo);
		return Math.atan2(sin, cos);
	}

	private static Vector3d projectOntoPlane(Vector3dc vector, Vector3dc normal) {
		return new Vector3d(vector).sub(new Vector3d(normal).mul(vector.dot(normal)));
	}

	private static Matrix3d initialFrame(Vector3dc facingNormal, Vector3dc directionToHose, Vector3dc firstNormal) {
		Vector3d up = vector(getUpDirection(facingNormal, directionToHose));
		return new Matrix3d(up, firstNormal, up.cross(firstNormal, new Vector3d()));
	}

	private static int stressColor(double distance) {
		double snapDistance = Config.hoseMaxLength * Config.hoseSnapMultiplier;
		double flashingStart = snapDistance * 0.85;
		if (distance <= flashingStart)
			return 0xFFFFFFFF;

		Minecraft minecraft = Minecraft.getInstance();
		double renderTime = minecraft.player == null ? minecraft.level.getGameTime() : minecraft.player.tickCount;
		float alpha = Mth.clamp((float) ((distance - flashingStart) / (snapDistance - flashingStart)), 0.0f, 1.0f) * 0.3f;
		alpha = alpha * Mth.lerp(0.25f, (float) Math.sin(renderTime / 3.0f) * 0.5f + 0.5f, 1.0f);
		int r = 255;
		int g = Mth.clamp((int) (255 * (1.0f - alpha * 2)), 0, 255);
		int b = Mth.clamp((int) (255 * (1.0f - alpha * 2)), 0, 255);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
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

	private static Vec3 getUpDirection(Vector3dc facingNormal, Vector3dc directionToHose) {
		Vector3d normal = new Vector3d(facingNormal).normalize();
		double dot = directionToHose.dot(normal);
		Vector3d dir = directionToHose.sub(normal.mul(dot, new Vector3d()), new Vector3d());

		if (dir.lengthSquared() < 1e-6) {
			Vector3d fallback = Math.abs(normal.y()) > 0.9 ? new Vector3d(0, 0, -1) : new Vector3d(0, 1, 0);
			dir = fallback.sub(normal.mul(fallback.dot(normal), new Vector3d()), new Vector3d());
		}
		dir.normalize().negate();
		return new Vec3(dir.x, dir.y, dir.z);
	}

	private static List<SplinePoint> generateSpline(Vector3dc pointA, Vector3dc pointB,
			Vector3dc normalA, Vector3dc normalB, double controlPointLength) {
		List<SplinePoint> points = new ObjectArrayList<>();
		Vector3d controlA = pointA.fma(controlPointLength, normalA, new Vector3d());
		Vector3d controlB = pointB.fma(controlPointLength, normalB, new Vector3d());
		Vector3d segmentA = new Vector3d();
		Vector3d segmentB = new Vector3d();
		Vector3d segmentC = new Vector3d();
		double len = pointA.distance(pointB);
		int count = Mth.clamp(Mth.ceil(len), 5, 8);
		for (int i = 0; i <= count; i++) {
			double t = (double) i / count;
			pointA.lerp(controlA, t, segmentA);
			controlA.lerp(controlB, t, segmentB);
			controlB.lerp(pointB, t, segmentC);

			Vector3d point = new Vector3d(segmentA)
				.lerp(segmentB, t)
				.lerp(segmentB.lerp(segmentC, t, new Vector3d()), t);
			Vector3d normal = new Vector3d();
			if (points.isEmpty())
				normal.set(normalA);
			else if (points.size() == count)
				normal.set(normalB).negate();
			else
				point.sub(points.get(points.size() - 1).point(), normal).normalize();
			points.add(new SplinePoint(point, normal));
		}
		return points;
	}

	private static void renderSegment(PoseStack poseStack, Vector3dc startDirection, Vector3dc endDirection,
			Vector3dc inputStartUp, Vector3dc inputEndUp, Vector3dc startPos, Vector3dc endPos, boolean second,
			float uvStart, float uvEnd, int light, int color, VertexConsumer buffer, float tubeWidth, float uvTubeWidth) {
		Vector3d startLeft = inputStartUp.cross(startDirection, new Vector3d()).normalize();
		Vector3d endLeft = inputEndUp.cross(endDirection, new Vector3d()).normalize();
		double scale = tubeWidth / 16.0 / 2.0;
		Vector3d startUp = inputStartUp.mul(scale, new Vector3d());
		Vector3d endUp = inputEndUp.mul(scale, new Vector3d());
		startLeft.mul(scale);
		endLeft.mul(scale);

		float texW = uvTubeWidth / TEXTURE_WIDTH;
		float uvScale = 16.0f / TEXTURE_WIDTH;
		float uvXOffset = second ? uvTubeWidth / TEXTURE_WIDTH : 0.0f;
		Vector3d startDown = startUp.negate(new Vector3d());
		Vector3d endDown = endUp.negate(new Vector3d());
		Vector3d startRight = startLeft.negate(new Vector3d());
		Vector3d endRight = endLeft.negate(new Vector3d());

		quad(poseStack, buffer, color, startDown, endDown, light,
			startPos.add(startLeft, new Vector3d()).sub(startUp),
			endPos.add(endLeft, new Vector3d()).sub(endUp),
			endPos.sub(endLeft, new Vector3d()).sub(endUp),
			startPos.sub(startLeft, new Vector3d()).sub(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
		quad(poseStack, buffer, color, startUp, endUp, light,
			startPos.sub(startLeft, new Vector3d()).add(startUp),
			endPos.sub(endLeft, new Vector3d()).add(endUp),
			endPos.add(endLeft, new Vector3d()).add(endUp),
			startPos.add(startLeft, new Vector3d()).add(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
		quad(poseStack, buffer, color, startRight, endRight, light,
			startPos.sub(startLeft, new Vector3d()).sub(startUp),
			endPos.sub(endLeft, new Vector3d()).sub(endUp),
			endPos.sub(endLeft, new Vector3d()).add(endUp),
			startPos.sub(startLeft, new Vector3d()).add(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
		quad(poseStack, buffer, color, startLeft, endLeft, light,
			startPos.add(startLeft, new Vector3d()).add(startUp),
			endPos.add(endLeft, new Vector3d()).add(endUp),
			endPos.add(endLeft, new Vector3d()).sub(endUp),
			startPos.add(startLeft, new Vector3d()).sub(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
	}

	private static void quad(PoseStack poseStack, VertexConsumer buffer, int color,
			Vector3dc startNormal, Vector3dc endNormal, int light,
			Vector3dc a, Vector3dc b, Vector3dc c, Vector3dc d, float u0, float v0, float u1, float v1) {
		vert(poseStack, buffer, a, color, u0, v0, startNormal, light);
		vert(poseStack, buffer, b, color, u0, v1, endNormal, light);
		vert(poseStack, buffer, c, color, u1, v1, endNormal, light);
		vert(poseStack, buffer, d, color, u1, v0, startNormal, light);
	}

	private static void vert(PoseStack poseStack, VertexConsumer buffer, Vector3dc pos, int color,
			float u, float v, Vector3dc normal, int light) {
		float wrappedU = u % 1.0f;
		float wrappedV = v % 1.0f;
		if (wrappedU < 0)
			wrappedU += 1.0f;
		if (wrappedV < 0)
			wrappedV += 1.0f;
		Vector3d normalizedNormal = normal.normalize(new Vector3d());
		buffer.addVertex(poseStack.last().pose(), (float) pos.x(), (float) pos.y(), (float) pos.z())
			.setColor(color)
			.setUv(wrappedU, wrappedV)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(poseStack.last(), (float) normalizedNormal.x(), (float) normalizedNormal.y(), (float) normalizedNormal.z());
	}

	private record DynamicHose(Vec3 start, Vec3 end, Direction startFacing, Direction endFacing,
			boolean black, long expiresAt) {
	}

	private record SplinePoint(Vector3dc point, Vector3dc normal) {
	}
}
