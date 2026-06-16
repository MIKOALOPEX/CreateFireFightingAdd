package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ConeSprayShape implements SprayShape {

	private final int range;
	private final double halfAngleRad;

	public ConeSprayShape(int range, double halfAngleDeg) {
		this.range = range;
		this.halfAngleRad = Math.toRadians(halfAngleDeg);
	}

	@Override
	public Vec3 randomSprayDirection(Vec3 baseDirection, RandomSource random) {
		Vec3[] perps = perpendiculars(baseDirection);
		double r = random.nextDouble() * Math.tan(halfAngleRad);
		double theta = random.nextDouble() * 2.0 * Math.PI;
		Vec3 offset = perps[0].scale(Math.cos(theta) * r).add(perps[1].scale(Math.sin(theta) * r));
		return baseDirection.add(offset).normalize();
	}

	@Override
	public Vec3 randomSprayDirection(Vec3 baseDirection, Random random) {
		Vec3[] perps = perpendiculars(baseDirection);
		double r = random.nextDouble() * Math.tan(halfAngleRad);
		double theta = random.nextDouble() * 2.0 * Math.PI;
		Vec3 offset = perps[0].scale(Math.cos(theta) * r).add(perps[1].scale(Math.sin(theta) * r));
		return baseDirection.add(offset).normalize();
	}

	@Override
	public void forEachPosition(Vec3 origin, Vec3 direction, PositionAction action) {
		Vec3[] perps = perpendiculars(direction);
		Vec3 perp1 = perps[0];
		Vec3 perp2 = perps[1];

		for (int dist = 1; dist <= range; dist++) {
			double radius = dist * Math.tan(halfAngleRad);
			int radiusInt = (int) Math.ceil(radius);
			Vec3 center = origin.add(direction.scale(dist));

			for (int da = -radiusInt; da <= radiusInt; da++) {
				for (int db = -radiusInt; db <= radiusInt; db++) {

					Vec3 checkCenter = center.add(perp1.scale(da)).add(perp2.scale(db));

					Vec3 toCenter = checkCenter.subtract(origin);
					double axialDist = toCenter.dot(direction);
					if (axialDist < 0)
						continue;

					double radialDistSq = toCenter.lengthSqr() - axialDist * axialDist;
					double maxRadial = axialDist * Math.tan(halfAngleRad);
					double tolerance = 0.5 + maxRadial * 0.3;
					if (radialDistSq > (maxRadial + tolerance) * (maxRadial + tolerance))
						continue;

					BlockPos pos = BlockPos.containing(checkCenter);
					action.accept(pos, dist, da, db);
				}
			}
		}
	}

	@Override
	public int getRange() {
		return range;
	}

	static Vec3[] perpendiculars(Vec3 facing) {
		Vec3 ref = Math.abs(facing.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 perp1 = facing.cross(ref).normalize();
		Vec3 perp2 = facing.cross(perp1).normalize();
		return new Vec3[] { perp1, perp2 };
	}

	@Override
	public List<Vec3> stratifiedDirections(Vec3 baseDirection, int count, long tick, Random random) {
		List<Vec3> dirs = new ArrayList<>(count);
		Vec3[] perps = perpendiculars(baseDirection);
		double tanHalf = Math.tan(halfAngleRad);
		double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

		// With only 1 projectile, alternate radius each tick to fill cone over time
		if (count == 1) {
			int phase = (int) (tick % 4);
			double r = switch (phase) {
				case 0 -> 0.0;                 // center
				case 1 -> 0.5 * tanHalf;        // inner ring
				case 2 -> 0.75 * tanHalf;       // mid ring
				default -> 1.0 * tanHalf;       // outer ring
			};
			if (r == 0.0) {
				dirs.add(baseDirection);
			} else {
				double angle = tick * goldenAngle;
				Vec3 offset = perps[0].scale(Math.cos(angle) * r).add(perps[1].scale(Math.sin(angle) * r));
				dirs.add(baseDirection.add(offset).normalize());
			}
			return dirs;
		}

		// Multiple projectiles: always include center line, then stratified by area
		dirs.add(baseDirection);
		for (int i = 1; i < count; i++) {
			double area = (double) i / count;
			double r = Math.sqrt(area) * tanHalf;
			double theta = tick * goldenAngle + (2.0 * Math.PI * i) / (count - 1);
			Vec3 offset = perps[0].scale(Math.cos(theta) * r).add(perps[1].scale(Math.sin(theta) * r));
			dirs.add(baseDirection.add(offset).normalize());
		}
		return dirs;
	}
}
