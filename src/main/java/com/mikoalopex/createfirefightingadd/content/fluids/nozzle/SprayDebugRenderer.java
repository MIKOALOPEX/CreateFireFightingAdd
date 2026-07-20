package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

/** F3+B wireframe overlay for nozzle spray volumes. */
public class SprayDebugRenderer {

	private static final int RING_SEGMENTS = 16;
	private static final int RING_INTERVAL = 3;

	private static final double CONE_HALF_ANGLE_DEG = 8.0;
	private static final double FLAT_HALF_ANGLE_H_DEG = 60.0;
	private static final double FLAT_HALF_ANGLE_V_DEG = 4.0;
	private static final Map<Integer, DynamicSprayDebug> DYNAMIC_SPRAYS = new HashMap<>();

	public static void submitDynamicSpray(int key, Vec3 origin, Vec3 direction, boolean cone, int range,
			double speed, double gravity, double friction, int lifetime, long expiresAt) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes())
			return;
		DYNAMIC_SPRAYS.put(key, new DynamicSprayDebug(origin, direction.normalize(), cone, range, speed, gravity,
			friction, lifetime, expiresAt));
	}

	public static void renderDynamicSprays(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
			DYNAMIC_SPRAYS.clear();
			return;
		}
		if (minecraft.level == null || DYNAMIC_SPRAYS.isEmpty())
			return;

		long gameTime = minecraft.level.getGameTime();
		DYNAMIC_SPRAYS.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAt());
		if (DYNAMIC_SPRAYS.isEmpty())
			return;

		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		RenderType linesType = RenderType.lines();
		VertexConsumer vc = bufferSource.getBuffer(linesType);
		PoseStack poseStack = event.getPoseStack();
		Camera camera = event.getCamera();
		Vec3 cameraPos = camera.getPosition();

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Matrix4f matrix = poseStack.last().pose();

		for (DynamicSprayDebug debug : DYNAMIC_SPRAYS.values())
			renderDynamicSpray(matrix, vc, debug);

		poseStack.popPose();
		bufferSource.endBatch(linesType);
	}

	/** Renders the wireframe tube for a spraying nozzle. No-op when F3+B is off. */
	public static void renderDebug(AbstractSprayDeviceBlockEntity nozzle, PoseStack poseStack) {
		if (!Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes())
			return;
		if (nozzle.getEffectiveRange() <= 0 || !nozzle.useProjectileSpray())
			return;

		List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline;
		try {
			centerline = nozzle.computeLocalCenterline();
		} catch (Exception e) {
			return;
		}
		if (centerline.size() < 2)
			return;

		Vec3 basePos = Vec3.atLowerCornerOf(nozzle.getBlockPos());
		boolean isCone = nozzle instanceof ConeNozzleBlockEntity;

		MultiBufferSource.BufferSource bufferSource =
			Minecraft.getInstance().renderBuffers().bufferSource();
		RenderType linesType = RenderType.lines();
		VertexConsumer vc = bufferSource.getBuffer(linesType);
		Matrix4f matrix = poseStack.last().pose();

		// Yellow: centerline.
		for (int i = 0; i < centerline.size() - 1; i++) {
			Vec3 a = centerline.get(i).position().subtract(basePos);
			Vec3 b = centerline.get(i + 1).position().subtract(basePos);
			renderLine(matrix, vc, a, b, 1f, 1f, 0f);
		}

		// Cyan: cross-section rings.
		List<List<Vec3>> allRings;
		if (isCone) {
			allRings = buildConeRings(centerline, basePos);
		} else {
			allRings = buildFanRings(nozzle, basePos);
		}

		if (allRings.isEmpty()) {
			bufferSource.endBatch(linesType);
			return;
		}

		for (List<Vec3> ring : allRings) {
			for (int i = 0; i < ring.size(); i++) {
				Vec3 a = ring.get(i);
				Vec3 b = ring.get((i + 1) % ring.size());
				renderLine(matrix, vc, a, b, 0.2f, 0.8f, 1f);
			}
		}

		// Light blue: longitudinal lines.
		if (allRings.size() > 1) {
			for (int seg = 0; seg < RING_SEGMENTS; seg++) {
				for (int ri = 0; ri < allRings.size() - 1; ri++) {
					List<Vec3> ringA = allRings.get(ri);
					List<Vec3> ringB = allRings.get(ri + 1);
					if (seg < ringA.size() && seg < ringB.size()) {
						Vec3 a = ringA.get(seg);
						Vec3 b = ringB.get(seg);
						renderLine(matrix, vc, a, b, 0.3f, 0.7f, 1f);
					}
				}
			}
		}

		bufferSource.endBatch(linesType);
	}

	/** Cone nozzle overlay: single-centerline scaled rings. */
	private static List<List<Vec3>> buildConeRings(
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline, Vec3 basePos) {
		List<List<Vec3>> allRings = new ArrayList<>();
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> ringSamples = new ArrayList<>();

		for (int i = 0; i < centerline.size(); i += RING_INTERVAL) {
			AbstractSprayDeviceBlockEntity.CenterlineSample cs = centerline.get(i);
			if (cs.axialDist() <= 0)
				continue;
			allRings.add(computeConeRing(cs, basePos));
			ringSamples.add(cs);
		}

		if (!ringSamples.isEmpty() && centerline.size() > 1) {
			var last = centerline.get(centerline.size() - 1);
			if (last.axialDist() > 0 && ringSamples.get(ringSamples.size() - 1) != last)
				allRings.add(computeConeRing(last, basePos));
		}

		return allRings;
	}

	/**
	 * Flat nozzle overlay: trace independent trajectories to capture the true
	 * envelope. Wide-angle projectiles have less forward velocity, so the fan
	 * contracts at the edges instead of widening linearly.
	 */
	private static List<List<Vec3>> buildFanRings(
			AbstractSprayDeviceBlockEntity nozzle, Vec3 basePos) {
		Direction facing = nozzle.getFacing();
		Vec3 origin = Vec3.atCenterOf(nozzle.getBlockPos()).relative(facing, 0.6);
		Vec3 baseDir = Vec3.atLowerCornerOf(facing.getNormal());
		double speed = nozzle.getProjectileSpeed();
		double friction = nozzle.getProjectileFriction();
		Vec3 gravity = nozzle.getGravityVector();
		int lifetime = nozzle.getProjectileLifetime();
		double range = nozzle.getEffectiveRange();

		Vec3[] axes = FanSprayShape.fanAxes(baseDir);
		double tanH = Math.tan(Math.toRadians(FLAT_HALF_ANGLE_H_DEG));
		double tanV = Math.tan(Math.toRadians(FLAT_HALF_ANGLE_V_DEG));

		// Trace one trajectory per ring segment.
		List<List<Vec3>> trajectories = new ArrayList<>(RING_SEGMENTS);
		int maxTicks = 0;

		for (int seg = 0; seg < RING_SEGMENTS; seg++) {
			double angle = seg * 2.0 * Math.PI / RING_SEGMENTS;
			double h = Math.cos(angle) * tanH;
			double v = Math.sin(angle) * tanV;
			Vec3 dir = baseDir.add(axes[0].scale(h)).add(axes[1].scale(v)).normalize();

			List<Vec3> traj = new ArrayList<>();
			Vec3 pos = origin;
			Vec3 vel = dir.scale(speed);
			double traveled = 0;

			traj.add(pos.subtract(basePos));

			for (int tick = 0; tick < lifetime; tick++) {
				Vec3 prevPos = pos;
				vel = vel.add(gravity);
				vel = vel.scale(friction);
				pos = pos.add(vel);
				traveled += pos.distanceTo(prevPos);
				traj.add(pos.subtract(basePos));
				if (traveled > range || vel.lengthSqr() < 0.0001)
					break;
			}
			trajectories.add(traj);
			if (traj.size() > maxTicks)
				maxTicks = traj.size();
		}

		// Sample all trajectories at RING_INTERVAL tick intervals
		List<List<Vec3>> allRings = new ArrayList<>();

		for (int tick = RING_INTERVAL; tick < maxTicks; tick += RING_INTERVAL) {
			List<Vec3> ring = new ArrayList<>(RING_SEGMENTS);
			for (List<Vec3> traj : trajectories) {
				if (tick < traj.size())
					ring.add(traj.get(tick));
			}
			if (ring.size() >= 3)
				allRings.add(ring);
		}

		// Always include the last tick if it wasn't already on a RING_INTERVAL boundary
		int lastTick = maxTicks - 1;
		if (lastTick > 0 && lastTick % RING_INTERVAL != 0) {
			List<Vec3> ring = new ArrayList<>(RING_SEGMENTS);
			for (List<Vec3> traj : trajectories) {
				if (lastTick < traj.size())
					ring.add(traj.get(lastTick));
			}
			if (ring.size() >= 3)
				allRings.add(ring);
		}

		return allRings;
	}

	private static List<Vec3> computeConeRing(
			AbstractSprayDeviceBlockEntity.CenterlineSample cs, Vec3 basePos) {
		List<Vec3> ring = new ArrayList<>(RING_SEGMENTS);
		Vec3 pos = cs.position().subtract(basePos);
		Vec3[] perps = ConeSprayShape.perpendiculars(cs.direction());
		double radius = cs.axialDist() * Math.tan(Math.toRadians(CONE_HALF_ANGLE_DEG));
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double angle = i * 2.0 * Math.PI / RING_SEGMENTS;
			ring.add(pos.add(perps[0].scale(Math.cos(angle) * radius))
				.add(perps[1].scale(Math.sin(angle) * radius)));
		}
		return ring;
	}

	private static void renderLine(Matrix4f matrix, VertexConsumer vc,
			Vec3 from, Vec3 to, float r, float g, float b) {
		vc.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
				.setColor(r, g, b, 1f)
				.setNormal(0, 1, 0);
		vc.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
				.setColor(r, g, b, 1f)
				.setNormal(0, 1, 0);
	}

	private static void renderDynamicSpray(Matrix4f matrix, VertexConsumer vc, DynamicSprayDebug debug) {
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline = buildDynamicCenterline(debug);
		if (centerline.size() < 2)
			return;

		for (int i = 0; i < centerline.size() - 1; i++)
			renderLine(matrix, vc, centerline.get(i).position(), centerline.get(i + 1).position(), 1f, 1f, 0f);

		List<List<Vec3>> rings = debug.cone()
			? buildDynamicConeRings(centerline)
			: buildDynamicFanRings(debug);
		for (List<Vec3> ring : rings) {
			for (int i = 0; i < ring.size(); i++)
				renderLine(matrix, vc, ring.get(i), ring.get((i + 1) % ring.size()), 0.2f, 0.8f, 1f);
		}

		if (rings.size() > 1) {
			for (int seg = 0; seg < RING_SEGMENTS; seg++) {
				for (int ri = 0; ri < rings.size() - 1; ri++) {
					if (seg < rings.get(ri).size() && seg < rings.get(ri + 1).size())
						renderLine(matrix, vc, rings.get(ri).get(seg), rings.get(ri + 1).get(seg), 0.3f, 0.7f, 1f);
				}
			}
		}
	}

	private static List<AbstractSprayDeviceBlockEntity.CenterlineSample> buildDynamicCenterline(DynamicSprayDebug debug) {
		List<AbstractSprayDeviceBlockEntity.CenterlineSample> samples = new ArrayList<>();
		Vec3 position = debug.origin();
		Vec3 velocity = debug.direction().scale(debug.speed());
		Vec3 gravity = new Vec3(0, -debug.gravity(), 0);
		double traveled = 0;
		samples.add(new AbstractSprayDeviceBlockEntity.CenterlineSample(position, debug.direction(), 0));

		for (int tick = 0; tick < debug.lifetime(); tick++) {
			Vec3 previous = position;
			velocity = velocity.add(gravity).scale(debug.friction());
			position = position.add(velocity);
			traveled += position.distanceTo(previous);
			if (traveled > debug.range() || velocity.lengthSqr() < 0.0001)
				break;
			samples.add(new AbstractSprayDeviceBlockEntity.CenterlineSample(position, velocity.normalize(), traveled));
		}
		return samples;
	}

	private static List<List<Vec3>> buildDynamicConeRings(
			List<AbstractSprayDeviceBlockEntity.CenterlineSample> centerline) {
		List<List<Vec3>> rings = new ArrayList<>();
		for (int i = RING_INTERVAL; i < centerline.size(); i += RING_INTERVAL)
			rings.add(computeDynamicConeRing(centerline.get(i)));
		AbstractSprayDeviceBlockEntity.CenterlineSample last = centerline.get(centerline.size() - 1);
		if (last.axialDist() > 0 && (rings.isEmpty() || !rings.get(rings.size() - 1).contains(last.position())))
			rings.add(computeDynamicConeRing(last));
		return rings;
	}

	private static List<Vec3> computeDynamicConeRing(AbstractSprayDeviceBlockEntity.CenterlineSample sample) {
		List<Vec3> ring = new ArrayList<>(RING_SEGMENTS);
		Vec3[] perps = ConeSprayShape.perpendiculars(sample.direction());
		double radius = sample.axialDist() * Math.tan(Math.toRadians(CONE_HALF_ANGLE_DEG));
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double angle = i * 2.0 * Math.PI / RING_SEGMENTS;
			ring.add(sample.position()
				.add(perps[0].scale(Math.cos(angle) * radius))
				.add(perps[1].scale(Math.sin(angle) * radius)));
		}
		return ring;
	}

	private static List<List<Vec3>> buildDynamicFanRings(DynamicSprayDebug debug) {
		Vec3[] axes = FanSprayShape.fanAxes(debug.direction());
		double tanH = Math.tan(Math.toRadians(FLAT_HALF_ANGLE_H_DEG));
		double tanV = Math.tan(Math.toRadians(FLAT_HALF_ANGLE_V_DEG));
		List<List<Vec3>> trajectories = new ArrayList<>(RING_SEGMENTS);
		int maxTicks = 0;

		for (int seg = 0; seg < RING_SEGMENTS; seg++) {
			double angle = seg * 2.0 * Math.PI / RING_SEGMENTS;
			Vec3 dir = debug.direction()
				.add(axes[0].scale(Math.cos(angle) * tanH))
				.add(axes[1].scale(Math.sin(angle) * tanV))
				.normalize();
			List<Vec3> trajectory = traceDynamicTrajectory(debug, dir);
			trajectories.add(trajectory);
			maxTicks = Math.max(maxTicks, trajectory.size());
		}

		List<List<Vec3>> rings = new ArrayList<>();
		for (int tick = RING_INTERVAL; tick < maxTicks; tick += RING_INTERVAL)
			addDynamicFanRing(rings, trajectories, tick);
		int lastTick = maxTicks - 1;
		if (lastTick > 0 && lastTick % RING_INTERVAL != 0)
			addDynamicFanRing(rings, trajectories, lastTick);
		return rings;
	}

	private static List<Vec3> traceDynamicTrajectory(DynamicSprayDebug debug, Vec3 direction) {
		List<Vec3> trajectory = new ArrayList<>();
		Vec3 position = debug.origin();
		Vec3 velocity = direction.scale(debug.speed());
		Vec3 gravity = new Vec3(0, -debug.gravity(), 0);
		double traveled = 0;
		trajectory.add(position);
		for (int tick = 0; tick < debug.lifetime(); tick++) {
			Vec3 previous = position;
			velocity = velocity.add(gravity).scale(debug.friction());
			position = position.add(velocity);
			traveled += position.distanceTo(previous);
			trajectory.add(position);
			if (traveled > debug.range() || velocity.lengthSqr() < 0.0001)
				break;
		}
		return trajectory;
	}

	private static void addDynamicFanRing(List<List<Vec3>> rings, List<List<Vec3>> trajectories, int tick) {
		List<Vec3> ring = new ArrayList<>(RING_SEGMENTS);
		for (List<Vec3> trajectory : trajectories) {
			if (tick < trajectory.size())
				ring.add(trajectory.get(tick));
		}
		if (ring.size() >= 3)
			rings.add(ring);
	}

	private record DynamicSprayDebug(Vec3 origin, Vec3 direction, boolean cone, int range, double speed,
			double gravity, double friction, int lifetime, long expiresAt) {
	}
}
