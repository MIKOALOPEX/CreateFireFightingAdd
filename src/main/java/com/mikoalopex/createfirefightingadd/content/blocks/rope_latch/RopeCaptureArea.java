package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

/** A world AABB selects index buckets; the oriented volume performs the narrow phase. */
record RopeCaptureArea(AABB bounds, Vec3 center, Vec3 right, Vec3 up, Vec3 forward) {
    static RopeCaptureArea sphere(Vec3 point) {
        return new RopeCaptureArea(new AABB(point, point).inflate(RopeLatchIndex.RADIUS), point, null, null, null);
    }

    static RopeCaptureArea rod(Vec3 center, Vec3 right, Vec3 up, Vec3 forward) {
        double x = Math.abs(right.x) * .75 + Math.abs(up.x) * .75 + Math.abs(forward.x) * 1.75;
        double y = Math.abs(right.y) * .75 + Math.abs(up.y) * .75 + Math.abs(forward.y) * 1.75;
        double z = Math.abs(right.z) * .75 + Math.abs(up.z) * .75 + Math.abs(forward.z) * 1.75;
        return new RopeCaptureArea(new AABB(center, center).inflate(x, y, z), center, right, up, forward);
    }

    boolean contains(Vector3dc point) {
        double x = point.x() - center.x, y = point.y() - center.y, z = point.z() - center.z;
        if (forward == null) return x * x + y * y + z * z <= RopeLatchIndex.RADIUS * RopeLatchIndex.RADIUS;
        return Math.abs(x * right.x + y * right.y + z * right.z) <= .75
            && Math.abs(x * up.x + y * up.y + z * up.z) <= .75
            && Math.abs(x * forward.x + y * forward.y + z * forward.z) <= 1.75;
    }
}
