package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class FanSprayShape implements SprayShape {

	private final int range;
	private final double halfAngleHRad;
	private final double halfAngleVRad;

	public FanSprayShape(int range, double halfAngleHDeg, double halfAngleVDeg) {
		this.range = range;
		this.halfAngleHRad = Math.toRadians(halfAngleHDeg);
		this.halfAngleVRad = Math.toRadians(halfAngleVDeg);
	}

	@Override
	public Vec3 randomSprayDirection(Vec3 baseDirection, RandomSource random) {
		Vec3[] axes = fanAxes(baseDirection);
		double r = random.nextDouble();
		double theta = random.nextDouble() * 2.0 * Math.PI;
		double h = Math.cos(theta) * r * Math.tan(halfAngleHRad);
		double v = Math.sin(theta) * r * Math.tan(halfAngleVRad);
		return baseDirection.add(axes[0].scale(h)).add(axes[1].scale(v)).normalize();
	}

	@Override
	public Vec3 randomSprayDirection(Vec3 baseDirection, Random random) {
		Vec3[] axes = fanAxes(baseDirection);
		double r = random.nextDouble();
		double theta = random.nextDouble() * 2.0 * Math.PI;
		double h = Math.cos(theta) * r * Math.tan(halfAngleHRad);
		double v = Math.sin(theta) * r * Math.tan(halfAngleVRad);
		return baseDirection.add(axes[0].scale(h)).add(axes[1].scale(v)).normalize();
	}

	@Override
	public void forEachPosition(Vec3 origin, Vec3 direction, PositionAction action) {
		Vec3[] axes = fanAxes(direction);
		Vec3 axisH = axes[0];
		Vec3 axisV = axes[1];

		for (int dist = 1; dist <= range; dist++) {
			double maxH = dist * Math.tan(halfAngleHRad);
			double maxV = dist * Math.tan(halfAngleVRad);
			double tolH = 0.5 + maxH * 0.3;
			double tolV = 0.5 + maxV * 0.3;
			double effectiveH = maxH + tolH;
			double effectiveV = maxV + tolV;
			int hInt = (int) Math.ceil(effectiveH);
			int vInt = (int) Math.ceil(effectiveV);
			Vec3 center = origin.add(direction.scale(dist));

			for (int dh = -hInt; dh <= hInt; dh++) {
				for (int dv = -vInt; dv <= vInt; dv++) {
					Vec3 checkCenter = center.add(axisH.scale(dh)).add(axisV.scale(dv));

					Vec3 toCenter = checkCenter.subtract(origin);
					double axialDist = toCenter.dot(direction);
					if (axialDist <= 0)
						continue;

					Vec3 radialVec = toCenter.subtract(direction.scale(axialDist));
					double hDist = Math.abs(radialVec.dot(axisH));
					double vDist = Math.abs(radialVec.dot(axisV));

					double hNorm = hDist / effectiveH;
					double vNorm = vDist / effectiveV;
					if (hNorm * hNorm + vNorm * vNorm > 1.0)
						continue;

					BlockPos pos = BlockPos.containing(checkCenter);
					action.accept(pos, dist, dh, dv);
				}
			}
		}
	}

	@Override
	public int getRange() {
		return range;
	}

	static Vec3[] fanAxes(Vec3 facing) {
		Vec3 ref = Math.abs(facing.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 axisH = facing.cross(ref).normalize();
		Vec3 axisV = facing.cross(axisH).normalize();
		return new Vec3[] { axisH, axisV };
	}
}
