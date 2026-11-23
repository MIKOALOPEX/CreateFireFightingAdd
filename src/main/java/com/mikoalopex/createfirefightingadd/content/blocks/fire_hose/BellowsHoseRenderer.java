package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3dc;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Short square ribs sharing the centerline and transported frames of the hose body. */
public final class BellowsHoseRenderer {
	static final double PERIOD = 4.0 / 16;
	static final double RIB_LENGTH = 2.0 / 16;
	static final double OFFSET = 1.0 / 16;
	static final RenderType SIDES = RenderType.entityCutoutNoCull(CreateFireFightingAdd.path(
		"textures/block/fire_hose/bellows/bellows_face.png"));
	static final RenderType CAPS = RenderType.entityCutoutNoCull(CreateFireFightingAdd.path(
		"textures/block/fire_hose/bellows/bellows_top.png"));
	private static final int CACHE_LIMIT = 512;
	private static final Map<Key, List<Quad>> CACHE = new LinkedHashMap<>(64, .75f, true);

	private BellowsHoseRenderer() {
	}

	public static Segment segment(Vector3dc start, Vector3dc end,
			Vector3dc startNormal, Vector3dc endNormal, Vector3dc startUp, Vector3dc endUp,
			double distance, float width, int light, int color) {
		return new Segment(new Key(vec(start), vec(end), vec(startNormal), vec(endNormal),
			vec(startUp), vec(endUp), phase(distance), width), light, color);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, List<Segment> segments) {
		if (segments.isEmpty())
			return;
		List<List<Quad>> meshes = new ArrayList<>(segments.size());
		for (Segment segment : segments) {
			List<Quad> quads = CACHE.get(segment.key);
			if (quads == null) {
				quads = mesh(segment.key);
				CACHE.put(segment.key, quads);
				if (CACHE.size() > CACHE_LIMIT)
					CACHE.remove(CACHE.keySet().iterator().next());
			}
			meshes.add(quads);
		}
		// Emit one material at a time; switching buffers invalidates non-fixed consumers.
		for (boolean caps : new boolean[] { false, true }) {
			VertexConsumer consumer = buffers.getBuffer(caps ? CAPS : SIDES);
			for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
				Segment segment = segments.get(segmentIndex);
				for (Quad quad : meshes.get(segmentIndex)) {
					if (quad.cap != caps)
						continue;
					for (int i = 0; i < 4; i++) {
						Vec3 point = quad.points[i];
						consumer.addVertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
							.setColor(segment.color).setUv(quad.uv[i * 2], quad.uv[i * 2 + 1])
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(segment.light)
							.setNormal(pose.last(), (float) quad.normal.x, (float) quad.normal.y, (float) quad.normal.z);
					}
				}
			}
		}
	}

	public record Segment(Key key, int light, int color) {
	}

	// Texture phase repeats every block, while the rib profile repeats four times per block.
	static double phase(double distance) {
		return distance - Math.floor(distance);
	}

	static List<Quad> mesh(Key key) {
		List<Quad> result = new ArrayList<>();
		double length = key.start.distanceTo(key.end);
		if (length < 1e-8)
			return List.of();
		double end = key.phase + length;
		float width = key.width + 2;
		float low = (10 - width) / 32f;
		float high = low + width / 16f;
		int first = (int) Math.floor((key.phase - OFFSET) / PERIOD);
		for (int index = first; ; index++) {
			double ribStart = index * PERIOD + OFFSET;
			double ribEnd = ribStart + RIB_LENGTH;
			if (ribStart >= end - 1e-9)
				break;
			double from = Math.max(key.phase, ribStart);
			double to = Math.min(end, ribEnd);
			if (to <= from + 1e-9)
				continue;
			Vec3[] a = corners(key, (from - key.phase) / length, width);
			Vec3[] b = corners(key, (to - key.phase) / length, width);
			// Each rib lies inside one texture repeat; its axial UV span is its drawn length.
			float v0 = (float) (from - Math.floor(ribStart));
			float v1 = (float) (to - Math.floor(ribStart));
			for (int side = 0; side < 4; side++) {
				int next = (side + 1) % 4;
				add(result, false, new Vec3[] { a[side], b[side], b[next], a[next] }, low, v0, high, v1);
			}
			// A curve sample boundary splits the sides but must not add an internal cap.
			if (Math.abs(from - ribStart) < 1e-9)
				add(result, true, a, low, low, high, high);
			if (Math.abs(to - ribEnd) < 1e-9)
				add(result, true, new Vec3[] { b[3], b[2], b[1], b[0] }, low, low, high, high);
		}
		return List.copyOf(result);
	}

	private static Vec3[] corners(Key key, double t, float width) {
		Vec3 center = key.start.lerp(key.end, t);
		Vec3 tangent = key.startNormal.lerp(key.endNormal, t).normalize();
		Vec3 up = key.startUp.lerp(key.endUp, t);
		up = up.subtract(tangent.scale(up.dot(tangent))).normalize();
		Vec3 left = up.cross(tangent).normalize().scale(width / 32.0);
		up = up.scale(width / 32.0);
		return new Vec3[] { center.add(left).subtract(up), center.subtract(left).subtract(up),
			center.subtract(left).add(up), center.add(left).add(up) };
	}

	private static void add(List<Quad> result, boolean cap, Vec3[] points, float u0, float v0, float u1, float v1) {
		Vec3 normal = points[1].subtract(points[0]).cross(points[2].subtract(points[0])).normalize();
		result.add(new Quad(cap, points, new float[] { u0, v0, u0, v1, u1, v1, u1, v0 }, normal));
	}

	private static Vec3 vec(Vector3dc vector) {
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	static record Key(Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal, Vec3 startUp, Vec3 endUp,
			double phase, float width) {
	}

	static record Quad(boolean cap, Vec3[] points, float[] uv, Vec3 normal) {
	}
}
