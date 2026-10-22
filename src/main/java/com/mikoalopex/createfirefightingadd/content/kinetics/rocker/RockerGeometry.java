package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplingGeometry;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public final class RockerGeometry {
    // Ray-test bounds only; the rotating rod has no world collision.
    public static final AABB INTERACTION_BOX = new AABB(-16, 5, 10, 32, 11, 13);

    public static Vec3 clip(Vec3 start, Vec3 end, Direction facing, double angle) {
        Vec3 a = inverse(start, facing, angle), b = inverse(end, facing, angle);
        return INTERACTION_BOX.clip(a, b).map(hit -> point(hit, facing, angle)).orElse(null);
    }
    private RockerGeometry() {}
    public static AABB coreHalf(Direction facing, int side, AABB bounds) {
        Vec3 a = inverse(new Vec3(bounds.minX, bounds.minY, bounds.minZ), facing, 0);
        Vec3 b = inverse(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ), facing, 0);
        AABB source = new AABB(a, b);
        double middle = (source.minY + source.maxY) / 2;
        return new AABB(source.minX, side > 0 ? middle : source.minY, source.minZ,
            source.maxX, side > 0 ? source.maxY : middle, source.maxZ);
    }
    public static int selectionSide(Vec3 local, Direction facing, AABB bounds) {
        if (!bounds.inflate(1e-5).contains(local)) return 0;
        Vec3 p = inverse(local, facing, 0);
        return p.y >= 8 ? 1 : -1;
    }
    public static Vec3 point(Vec3 source, Direction facing, double angle) {
        Vec3 p = rotate(source.subtract(26, 8, 8).scale(1 / 16.0), angle);
        return FluidCouplingGeometry.orient(new Vec3(p.x, -p.z, p.y), facing).add(0.5, 0.5, 0.5);
    }
    public static Vec3 inverse(Vec3 local, Direction facing, double angle) {
        Vec3 p = FluidCouplingGeometry.unorient(local.subtract(0.5, 0.5, 0.5), facing);
        return rotate(new Vec3(p.x, p.z, -p.y), -angle).scale(16).add(26, 8, 8);
    }
    private static Vec3 rotate(Vec3 p, double degrees) {
        double radians = Math.toRadians(degrees), c = Math.cos(radians), s = Math.sin(radians);
        return new Vec3(p.x*c+p.y*s, p.y*c-p.x*s, p.z);
    }
    public static AABB box(double x, double y, double z, double X, double Y, double Z, Direction facing, double angle) {
        Vec3 min = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 max = min.scale(-1);
        for (int i = 0; i < 8; i++) {
            Vec3 p = point(new Vec3((i&1)==0?x:X, (i&2)==0?y:Y, (i&4)==0?z:Z), facing, angle);
            min = new Vec3(Math.min(min.x,p.x), Math.min(min.y,p.y), Math.min(min.z,p.z));
            max = new Vec3(Math.max(max.x,p.x), Math.max(max.y,p.y), Math.max(max.z,p.z));
        }
        return new AABB(min, max);
    }
}
