package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ExtensionLadderGeometry {
	public static final float PIXEL = 1 / 16f;
	public static final int BASE_LENGTH_PIXELS = 43;
	public static final int MAX_MOVE_OFFSET_PIXELS = 39;
	public static final float BASE_LENGTH = BASE_LENGTH_PIXELS * PIXEL;
	public static final float MAX_MOVE_OFFSET = MAX_MOVE_OFFSET_PIXELS * PIXEL;
	public static final float MAX_LENGTH = BASE_LENGTH + MAX_MOVE_OFFSET;
	public static final float WIDTH = 16 * PIXEL;
	public static final float HALF_WIDTH = WIDTH * 0.5f;
	public static final float PROBE_RADIUS = 1.5f * PIXEL;
	public static final float MAX_PITCH = (float) Math.toRadians(90);
	public static final double CLIMB_NORMAL_MIN = 0;
	public static final double CLIMB_NORMAL_MAX = 0.5;
	public static final double CLIMB_WIDTH_HALF_EXTENT = 0.4;
	public static final double CLIMB_LENGTH_PADDING = 0;

	public static final Vec3 MODEL_PIVOT = pixels(8, -13, 8);
	public static final Vec3 PULLEY_POINT_1 = pixels(8, 24, 7);
	public static final Vec3 PULLEY_POINT_2 = pixels(8, 24, 10);
	public static final Vec3 TIP_SOUTH_FACE_CENTER = rotatedTipSouthFaceCenter();

	private ExtensionLadderGeometry() {
	}

	public static Vec3 pixels(double x, double y, double z) {
		return new Vec3(x * PIXEL, y * PIXEL, z * PIXEL);
	}

	private static Vec3 rotatedTipSouthFaceCenter() {
		double angle = Math.toRadians(22.5);
		Vec3 point = pixels(8, -10, 6.5);
		Vec3 origin = pixels(14, -11, 5);
		Vec3 delta = point.subtract(origin);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return origin.add(delta.x, delta.y * cos - delta.z * sin, delta.y * sin + delta.z * cos);
	}

	public static Vec3 normalizeHorizontal(Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0, vector.z);
		if (horizontal.lengthSqr() < 1.0E-6)
			return Vec3.ZERO;
		return horizontal.normalize();
	}

	public static LocalFrame localFrame(Vec3 anchor, Vec3 fallDirection, float pitch, boolean modelCalibrated) {
		Vec3 forward = normalizeHorizontal(fallDirection);
		if (forward == Vec3.ZERO)
			forward = new Vec3(0, 0, 1);

		Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
		Vec3 longAxis = forward.scale(Mth.sin(pitch)).add(new Vec3(0, Mth.cos(pitch), 0)).normalize();
		Vec3 normal = right.cross(longAxis).normalize();

		if (modelCalibrated) {
			right = right.scale(-1);
			normal = normal.scale(-1);
		}

		return new LocalFrame(anchor, right, longAxis, normal);
	}

	public record LocalFrame(Vec3 anchor, Vec3 right, Vec3 longAxis, Vec3 normal) {
		public Vec3 point(double along, double width, double normalOffset) {
			return anchor
				.add(longAxis.scale(along))
				.add(right.scale(width))
				.add(normal.scale(normalOffset));
		}

	}

	public record WorldFrame(Vec3 anchor, Vec3 right, Vec3 longAxis, Vec3 normal) {
		public Projection project(Vec3 worldPosition) {
			Vec3 delta = worldPosition.subtract(anchor);
			return new Projection(delta.dot(longAxis), delta.dot(right), delta.dot(normal));
		}

		public Vec3 point(double along, double width, double normalOffset) {
			return anchor
				.add(longAxis.scale(along))
				.add(right.scale(width))
				.add(normal.scale(normalOffset));
		}

		public Vec3[] climbBoxCorners(double moveOffsetPixels) {
			Vec3[] corners = new Vec3[8];
			int index = 0;
			double[] alongs = { -CLIMB_LENGTH_PADDING, climbLength(moveOffsetPixels) + CLIMB_LENGTH_PADDING };
			double[] widths = { -CLIMB_WIDTH_HALF_EXTENT, CLIMB_WIDTH_HALF_EXTENT };
			double[] normals = { CLIMB_NORMAL_MIN, CLIMB_NORMAL_MAX };
			for (double along : alongs)
				for (double width : widths)
					for (double normal : normals)
						corners[index++] = point(along, width, normal);
			return corners;
		}
	}

	public static double climbLength(double moveOffsetPixels) {
		return BASE_LENGTH + moveOffsetPixels * PIXEL;
	}

	public record Projection(double along, double width, double normal) {
	}
}
