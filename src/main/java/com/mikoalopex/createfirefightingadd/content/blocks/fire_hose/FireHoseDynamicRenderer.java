package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
			renderHose(poseStack, buffer, hose, 0xF000F0);
		}
		poseStack.popPose();
		bufferSource.endBatch(WHITE_HOSE_RENDER_TYPE);
		bufferSource.endBatch(BLACK_HOSE_RENDER_TYPE);
	}

	private static RenderType createHoseRenderType(String name, ResourceLocation texture) {
		return RenderType.create(
			CreateFireFightingAdd.MODID + ":" + name,
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

	private static void renderHose(PoseStack poseStack, VertexConsumer buffer, DynamicHose hose, int light) {
		Vector3d start = vector(hose.start());
		Vector3d end = vector(hose.end());
		Vector3d normalA = vector(hose.startFacing().getNormal());
		Vector3d normalB = vector(hose.endFacing().getNormal());
		double distance = start.distance(end);
		if (distance < 0.01)
			return;

		List<SplinePoint> splinePoints = generateSpline(start, end, normalA, normalB, distance / 5.0 + 0.25);
		if (splinePoints.size() < 2)
			return;

		int color = stressColor(distance);
		Matrix3d matrix = initialFrame(hose.startFacing(), end.sub(start, new Vector3d()), splinePoints.get(0).normal());
		double totalLength = 0.0;
		for (int i = 0; i < splinePoints.size() - 1; i++) {
			SplinePoint point = splinePoints.get(i);
			SplinePoint next = splinePoints.get(i + 1);
			totalLength += point.point().distance(next.point());
			matrix.rotateLocal(rotationBetween(point.normal(), next.normal()));
		}

		if (totalLength <= 0)
			return;

		Quaterniond orientation = matrix.getNormalizedRotation(new Quaterniond());
		if (Math.abs(UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()))) < 1e-5)
			orientation.rotateLocalX(Math.PI);

		double halfPi = Math.PI / 2.0;
		double quarterPi = halfPi / 2.0;
		double d = UP.dot(new Vector3d(orientation.x(), orientation.y(), orientation.z()));
		double deg = 2.0 * Math.atan2(-d, orientation.w());
		double twist = Math.floor((deg + quarterPi) / halfPi) * halfPi - deg;
		float uvScale = (float) ((distance - 0.75) / totalLength);
		double runningLength = 0.0;

		matrix = initialFrame(hose.startFacing(), end.sub(start, new Vector3d()), splinePoints.get(0).normal());
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
				light, color, buffer);
			renderSegment(poseStack, point.normal().negate(new Vector3d()), next.normal().negate(new Vector3d()),
				up.negate(new Vector3d()), nextUp.negate(new Vector3d()),
				point.point(), next.point(), true,
				0.0f - (float) runningLength * uvScale,
				0.0f - (float) (runningLength + length) * uvScale,
				light, color, buffer);

			runningLength += length;
		}
	}

	private static Matrix3d initialFrame(Direction facing, Vector3dc directionToHose, Vector3dc firstNormal) {
		Vector3d up = vector(getUpDirection(facing, directionToHose));
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

	private static Vec3 getUpDirection(Direction facing, Vector3dc directionToHose) {
		Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
		double dot = directionToHose.dot(normal.x, normal.y, normal.z);
		Vector3d dir = directionToHose.sub(normal.x * dot, normal.y * dot, normal.z * dot, new Vector3d());

		if (dir.lengthSquared() < 1e-6)
			return facing.getAxis().isHorizontal() ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
		return Vec3.atLowerCornerOf(Direction.getNearest(dir.x, dir.y, dir.z).getOpposite().getNormal());
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
			float uvStart, float uvEnd, int light, int color, VertexConsumer buffer) {
		Vector3d startLeft = inputStartUp.cross(startDirection, new Vector3d()).normalize();
		Vector3d endLeft = inputEndUp.cross(endDirection, new Vector3d()).normalize();
		double scale = TUBE_WIDTH / 16.0 / 2.0;
		Vector3d startUp = inputStartUp.mul(scale, new Vector3d());
		Vector3d endUp = inputEndUp.mul(scale, new Vector3d());
		startLeft.mul(scale);
		endLeft.mul(scale);

		float texW = TUBE_WIDTH / TEXTURE_WIDTH;
		float uvScale = 16.0f / TEXTURE_WIDTH;
		float uvXOffset = second ? TUBE_WIDTH / TEXTURE_WIDTH : 0.0f;

		quad(poseStack, buffer, color, light,
			startPos.add(startLeft, new Vector3d()).sub(startUp),
			endPos.add(endLeft, new Vector3d()).sub(endUp),
			endPos.sub(endLeft, new Vector3d()).sub(endUp),
			startPos.sub(startLeft, new Vector3d()).sub(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
		quad(poseStack, buffer, color, light,
			startPos.sub(startLeft, new Vector3d()).add(startUp),
			endPos.sub(endLeft, new Vector3d()).add(endUp),
			endPos.add(endLeft, new Vector3d()).add(endUp),
			startPos.add(startLeft, new Vector3d()).add(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
		quad(poseStack, buffer, color, light,
			startPos.sub(startLeft, new Vector3d()).sub(startUp),
			endPos.sub(endLeft, new Vector3d()).sub(endUp),
			endPos.sub(endLeft, new Vector3d()).add(endUp),
			startPos.sub(startLeft, new Vector3d()).add(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
		quad(poseStack, buffer, color, light,
			startPos.add(startLeft, new Vector3d()).add(startUp),
			endPos.add(endLeft, new Vector3d()).add(endUp),
			endPos.add(endLeft, new Vector3d()).sub(endUp),
			startPos.add(startLeft, new Vector3d()).sub(startUp),
			uvXOffset, uvStart * uvScale, texW + uvXOffset, uvEnd * uvScale);
	}

	private static void quad(PoseStack poseStack, VertexConsumer buffer, int color, int light,
			Vector3dc a, Vector3dc b, Vector3dc c, Vector3dc d, float u0, float v0, float u1, float v1) {
		vert(poseStack, buffer, a, color, u0, v0, light);
		vert(poseStack, buffer, b, color, u0, v1, light);
		vert(poseStack, buffer, c, color, u1, v1, light);
		vert(poseStack, buffer, d, color, u1, v0, light);
	}

	private static void vert(PoseStack poseStack, VertexConsumer buffer, Vector3dc pos, int color,
			float u, float v, int light) {
		float wrappedU = u % 1.0f;
		float wrappedV = v % 1.0f;
		if (wrappedU < 0)
			wrappedU += 1.0f;
		if (wrappedV < 0)
			wrappedV += 1.0f;
		buffer.addVertex(poseStack.last().pose(), (float) pos.x(), (float) pos.y(), (float) pos.z())
			.setColor(color)
			.setUv(wrappedU, wrappedV)
			.setUv2(light & 0xFFFF, light >> 16);
	}

	private record DynamicHose(Vec3 start, Vec3 end, Direction startFacing, Direction endFacing,
			boolean black, long expiresAt) {
	}

	private record SplinePoint(Vector3dc point, Vector3dc normal) {
	}
}
