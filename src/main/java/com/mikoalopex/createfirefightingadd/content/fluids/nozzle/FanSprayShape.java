package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.UnaryOperator;

public class FanSprayShape implements SprayShape {

	private final int range;
	private final double halfAngleHRad;
	private final double halfAngleVRad;
	private final Vec3 widthAxis;

	public FanSprayShape(int range, double halfAngleHDeg, double halfAngleVDeg) {
		this(range, halfAngleHDeg, halfAngleVDeg, null);
	}

	public FanSprayShape(int range, double halfAngleHDeg, double halfAngleVDeg, Vec3 widthAxis) {
		this.range = range;
		this.halfAngleHRad = Math.toRadians(halfAngleHDeg);
		this.halfAngleVRad = Math.toRadians(halfAngleVDeg);
		this.widthAxis = widthAxis;
	}

	@Override
	public Vec3 randomSprayDirection(Vec3 baseDirection, RandomSource random) {
		Vec3[] axes = fanAxes(baseDirection, widthAxis);
		double r = random.nextDouble();
		double theta = random.nextDouble() * 2.0 * Math.PI;
		double h = Math.cos(theta) * r * Math.tan(halfAngleHRad);
		double v = Math.sin(theta) * r * Math.tan(halfAngleVRad);
		return baseDirection.add(axes[0].scale(h)).add(axes[1].scale(v)).normalize();
	}

	@Override
	public Vec3 randomSprayDirection(Vec3 baseDirection, Random random) {
		Vec3[] axes = fanAxes(baseDirection, widthAxis);
		double r = random.nextDouble();
		double theta = random.nextDouble() * 2.0 * Math.PI;
		double h = Math.cos(theta) * r * Math.tan(halfAngleHRad);
		double v = Math.sin(theta) * r * Math.tan(halfAngleVRad);
		return baseDirection.add(axes[0].scale(h)).add(axes[1].scale(v)).normalize();
	}

	@Override
	public void forEachPosition(Vec3 origin, Vec3 direction, PositionAction action) {
		Vec3[] axes = fanAxes(direction, widthAxis);
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

	@Override
	public boolean containsPoint(Vec3 point, Vec3 streamPos, Vec3 streamDir, double axialDist) {
		Vec3 offset = point.subtract(streamPos);
		double along = offset.dot(streamDir);
		if (along < -1.0)
			return false;
		Vec3[] axes = fanAxes(streamDir, widthAxis);
		double hDist = offset.dot(axes[0]);
		double vDist = offset.dot(axes[1]);
		double maxH = axialDist * Math.tan(halfAngleHRad);
		double maxV = axialDist * Math.tan(halfAngleVRad);
		if (maxH <= 0.0 || maxV <= 0.0)
			return false;
		double hNorm = hDist / maxH;
		double vNorm = vDist / maxV;
		return hNorm * hNorm + vNorm * vNorm <= 1.0;
	}

	@Override
	public List<Vec3> stratifiedDirections(Vec3 baseDirection, int count, long tick, Random random) {
		List<Vec3> dirs = new ArrayList<>(count);
		Vec3[] axes = fanAxes(baseDirection, widthAxis);
		double tanH = Math.tan(halfAngleHRad);
		double tanV = Math.tan(halfAngleVRad);
		double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

		if (count <= 1) {
			dirs.add(baseDirection);
			return dirs;
		}

		dirs.add(baseDirection);
		for (int i = 1; i < count; i++) {
			double area = (double) i / count;
			double r = Math.sqrt(area);
			double theta = tick * goldenAngle + (2.0 * Math.PI * i) / (count - 1);
			double h = Math.cos(theta) * r * tanH;
			double v = Math.sin(theta) * r * tanV;
			dirs.add(baseDirection.add(axes[0].scale(h)).add(axes[1].scale(v)).normalize());
		}
		return dirs;
	}

	static Vec3[] fanAxes(Vec3 facing) {
		Vec3 ref = Math.abs(facing.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 axisH = facing.cross(ref).normalize();
		Vec3 axisV = facing.cross(axisH).normalize();
		return new Vec3[] { axisH, axisV };
	}

	static Vec3 nozzleWidth(Direction facing) {
		// The model opens along X; flat_nozzle.json rotates that axis onto Z for east/west.
		return facing.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
	}

	static Vec3[] fanAxes(Vec3 facing, Vec3 widthAxis) {
		if (widthAxis == null)
			return fanAxes(facing);
		Vec3 forward = facing.normalize();
		// Project the model's width onto the curved stream's cross-section, retaining its roll.
		Vec3 width = widthAxis.subtract(forward.scale(widthAxis.dot(forward)));
		if (width.lengthSqr() < 1e-8)
			return fanAxes(facing);
		width = width.normalize();
		return new Vec3[] { width, forward.cross(width).normalize() };
	}

	static SprayShape transformWidth(SprayShape shape, UnaryOperator<Vec3> transform) {
		if (!(shape instanceof FanSprayShape fan) || fan.widthAxis == null)
			return shape;
		return new FanSprayShape(fan.range, Math.toDegrees(fan.halfAngleHRad),
			Math.toDegrees(fan.halfAngleVRad), transform.apply(fan.widthAxis));
	}
}
