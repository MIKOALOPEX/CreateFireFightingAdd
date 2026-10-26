package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Visual trims never modify the native rope points or their collision length. */
public final class PhysicalHoseGeometry {
    public record Segment(Vec3 start, Vec3 end, Vec3 normal, Vec3 up, double length, double middleLength) {
        public Vec3 center() { return start.add(end).scale(0.5); }
        public Vec3 midStart() { return center().subtract(normal.scale(middleLength / 2)); }
        public Vec3 midEnd() { return center().add(normal.scale(middleLength / 2)); }
        public boolean fullLength() { return Math.abs(length - 1) < 1e-4; }
    }

    public static List<Segment> segments(List<Vec3> points) {
        List<Segment> result = new ArrayList<>();
        Vec3 previousNormal = null, up = null;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i), b = points.get(i + 1), delta = b.subtract(a);
            double length = delta.length();
            if (length < 1e-6 || !Double.isFinite(length)) continue;
            Vec3 normal = delta.scale(1 / length);
            if (previousNormal == null) {
                Vec3 reference = Math.abs(normal.y) > 0.9 ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
                up = reference.subtract(normal.scale(reference.dot(normal))).normalize();
            } else {
                Vector3d transported = new Quaterniond().rotationTo(vector(previousNormal), vector(normal)).transform(vector(up));
                up = new Vec3(transported.x, transported.y, transported.z);
                up = up.subtract(normal.scale(up.dot(normal))).normalize();
            }
            // The fractional first segment is cropped around the texture centre, never stretched.
            result.add(new Segment(a, b, normal, up, length, Math.min(0.75, length * 0.75)));
            previousNormal = normal;
        }
        return result;
    }

    public static boolean straight(Segment a, Segment b) {
        return a.fullLength() && b.fullLength() && a.end.distanceToSqr(b.start) < 1e-8 && a.normal.dot(b.normal) > 1 - 1e-8;
    }

    /** Match heads to the rendered ends without changing the strand's point order. */
    public static boolean ownerAtFirst(Vec3 owner, Vec3 partner, List<Segment> segments) {
        Vec3 first = segments.getFirst().start(), last = segments.getLast().end();
        return owner.distanceToSqr(first) + partner.distanceToSqr(last)
            <= owner.distanceToSqr(last) + partner.distanceToSqr(first);
    }

    /** Quarter-turns preserve the square face while choosing the shortest corner connections. */
    public static Vec3 alignedUp(Vec3 normalA, Vec3 upA, Vec3 normalB, Vec3 upB) {
        Vec3 leftA = upA.cross(normalA), leftB = upB.cross(normalB);
        Vec3 best = upB;
        double score = -Double.MAX_VALUE;
        for (Vec3 candidate : new Vec3[] {upB, leftB, upB.scale(-1), leftB.scale(-1)}) {
            double alignment = upA.dot(candidate) + leftA.dot(candidate.cross(normalB));
            if (alignment > score) { score = alignment; best = candidate; }
        }
        return best;
    }

    public static Vector3d vector(Vec3 v) { return new Vector3d(v.x, v.y, v.z); }
    private PhysicalHoseGeometry() {}
}
