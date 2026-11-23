package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.List;
import java.util.Map;

import net.minecraft.world.phys.Vec3;

/** Geometry checks for real square ribs, clipped textures and sample-boundary continuity. */
public final class BellowsHoseTest {
	private static final Vec3 AXIS = new Vec3(0, 1, 0);
	private static final Vec3 UP = new Vec3(1, 0, 0);

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		net.minecraft.server.Bootstrap.bootStrap();
		for (int width : new int[] { 4, 6, 8 }) {
			var whole = BellowsHoseRenderer.mesh(key(0, 1, 0, width));
			check(whole.size() == 24, "Four six-face ribs per block");
			check(whole.stream().filter(BellowsHoseRenderer.Quad::cap).count() == 8, "Two caps per rib");
			for (var quad : whole) {
				close(quad.normal().length(), 1);
				for (var point : quad.points()) {
					close(Math.abs(point.x), (width + 2) / 32.0);
					close(Math.abs(point.z), (width + 2) / 32.0);
				}
				float[] uv = quad.uv();
				close(uv[4] - uv[0], (width + 2) / 16.0);
				close(uv[0], (8 - width) / 32.0);
				if (!quad.cap()) {
					close(uv[3] - uv[1], BellowsHoseRenderer.RIB_LENGTH);
					Vec3 center = quad.points()[0].add(quad.points()[2]).scale(.5);
					check(quad.normal().dot(new Vec3(center.x, 0, center.z)) > 0, "Side normals face outward");
				} else {
					double phase = quad.points()[0].y % BellowsHoseRenderer.PERIOD;
					close(quad.normal().y, phase < .1 ? -1 : 1);
				}
			}
			// Split through a rib, as a curve sample or bracket route may do.
			var first = BellowsHoseRenderer.mesh(key(0, .125, 0, width));
			var second = BellowsHoseRenderer.mesh(key(.125, 1, .125, width));
			check(first.stream().filter(BellowsHoseRenderer.Quad::cap).count()
				+ second.stream().filter(BellowsHoseRenderer.Quad::cap).count() == 8, "No internal split caps");
			for (int side = 0; side < 4; side++) {
				close(first.get(side).points()[1].distanceTo(second.get(side).points()[0]), 0);
				close(first.get(side).uv()[3], second.get(side).uv()[1]);
			}
			var shifted = BellowsHoseRenderer.mesh(key(0, 1, BellowsHoseRenderer.phase(12), width));
			check(shifted.size() == whole.size(), "Whole-block offsets retain repeat phase");
		}
		check(BellowsHoseRenderer.mesh(key(0, 0, 0, 8)).isEmpty(), "Zero-length links emit no geometry");
		System.out.println("Bellows: three widths, face counts, outward normals, cropped UVs and split continuity passed");
	}

	private static BellowsHoseRenderer.Key key(double start, double end, double phase, int width) {
		return new BellowsHoseRenderer.Key(new Vec3(0, start, 0), new Vec3(0, end, 0),
			AXIS, AXIS, UP, UP, phase, width);
	}

	private static void close(double actual, double expected) {
		check(Math.abs(actual - expected) < 1e-6, actual + " != " + expected);
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
