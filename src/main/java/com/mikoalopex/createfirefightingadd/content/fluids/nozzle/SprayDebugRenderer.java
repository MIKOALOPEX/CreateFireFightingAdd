package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/** Debug wireframe rendering for nozzle spray tubes. Call from BER render(). */
public class SprayDebugRenderer {

	private static final int RING_SEGMENTS = 16;
	private static final int RING_INTERVAL = 3;

	private static final double CONE_HALF_ANGLE_DEG = 8.0;
	private static final double FLAT_HALF_ANGLE_H_DEG = 60.0;
	private static final double FLAT_HALF_ANGLE_V_DEG = 4.0;

	/** Render the debug wireframe tube for a spraying nozzle. No-op when F3+B is off. */
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

		// Centerline  - yellow
		for (int i = 0; i < centerline.size() - 1; i++) {
			Vec3 a = centerline.get(i).position().subtract(basePos);
			Vec3 b = centerline.get(i + 1).position().subtract(basePos);
			renderLine(matrix, vc, a, b, 1f, 1f, 0f);
		}

		// Cross-section rings
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

		// Rings  - cyan
		for (List<Vec3> ring : allRings) {
			for (int i = 0; i < ring.size(); i++) {
				Vec3 a = ring.get(i);
				Vec3 b = ring.get((i + 1) % ring.size());
				renderLine(matrix, vc, a, b, 0.2f, 0.8f, 1f);
			}
		}

		// Longitudinal lines  - light blue
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

	/** Cone nozzle: single-centerline scaled rings (adequate for small 8掳 half-angle). */
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

	/** Flat nozzle: trace multiple independent trajectories to capture the true envelope.
	 *  Wide-angle projectiles have less forward velocity, so the fan contracts at the edges
	 *  rather than widening linearly. */
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

		// Trace one trajectory per ring segment
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
}
