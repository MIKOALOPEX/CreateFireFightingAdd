package com.createfireworkadd.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class CylinderSprayShape implements SprayShape {

	private int range;
	private final int radius;

	public CylinderSprayShape(int range, int radius) {
		this.range = range;
		this.radius = radius;
	}

	public void setRange(int range) {
		this.range = range;
	}

	@Override
	public void forEachPosition(Vec3 origin, Vec3 direction, PositionAction action) {
		Vec3[] perps = perpendiculars(direction);

		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b <= radius; b++) {
				if (a * a + b * b > radius * radius)
					continue;

				for (int dist = 1; dist <= range; dist++) {
					Vec3 checkCenter = origin.add(direction.scale(dist))
						.add(perps[0].scale(a))
						.add(perps[1].scale(b));

					BlockPos pos = BlockPos.containing(checkCenter);
					action.accept(pos, dist, a, b);
				}
			}
		}
	}

	@Override
	public int getRange() {
		return range;
	}

	private static Vec3[] perpendiculars(Vec3 facing) {
		Vec3 ref = Math.abs(facing.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 perp1 = facing.cross(ref).normalize();
		Vec3 perp2 = facing.cross(perp1).normalize();
		return new Vec3[] { perp1, perp2 };
	}
}
