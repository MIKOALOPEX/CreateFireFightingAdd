package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Model-space docking points, separate from the fixed pivots used by physical joints. */
public final class FluidCouplingGeometry {
    // The -Z rim limits tilt to 45 degrees; the +Z opening permits 67.5 degrees.
    public static final double MIN_PITCH = -45;
    public static final double MAX_PITCH = 67.5;
    public static final double INSERTION_PIXELS = 7;
    public static final double NOZZLE_TIP_Z = 23.475;
    public static final Vec3 PITCH_PIVOT = new Vec3(8, 11, 8);
    public static final Vec3 RECEPTACLE_MOUTH = new Vec3(8, 20, 8);
    public static final Vec3 BASE_OUTLET = new Vec3(8, 6, 8);
    public static final Vec3 RING_CENTER = new Vec3(8, 10, 8);
    public static final double RING_HALF_DEPTH = 1.0 / 16;
    public static final double HOSE_HALF_WIDTH = 4.0 / 16;

    private FluidCouplingGeometry() {}

    public static double clampPitch(double degrees) {
        return Double.isFinite(degrees) ? Math.clamp(degrees, MIN_PITCH, MAX_PITCH) : 0;
    }

    /** Blockbench's positive X rotation sends the positive Y axis toward positive Z. */
    public static Vec3 pitch(Vec3 point, double degrees) {
        double radians = Math.toRadians(clampPitch(degrees));
        Vec3 p = point.subtract(PITCH_PIVOT);
        double c = Math.cos(radians), s = Math.sin(radians);
        return PITCH_PIVOT.add(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
    }

    public static Vec3 receptacleContact(double degrees) {
        return pitch(RECEPTACLE_MOUTH.add(0, -INSERTION_PIXELS, 0), degrees);
    }

    public static Vec3 receptacleAxis(double degrees) {
        return pitch(PITCH_PIVOT.add(0, 1, 0), degrees).subtract(PITCH_PIVOT);
    }

    /** Projects the nozzle axis into the receptacle frame for visual tracking only. */
    public static double trackingPitch(Vec3 nozzleForward) {
        return pitchToward(nozzleForward.scale(-1));
    }

    /** Projects the target onto the pitch plane; unreachable angles stop at the mechanical limits. */
    public static double pitchToward(Vec3 target) {
        if (!Double.isFinite(target.y) || !Double.isFinite(target.z)
                || target.y * target.y + target.z * target.z < 1e-12) return 0;
        return clampPitch(Math.toDegrees(Math.atan2(target.z, target.y)));
    }

    public static Vec3 freeAnchor(int height) {
        if (height < 0 || height > 4) throw new IllegalArgumentException("Coupling height must be 0..4");
        return new Vec3(8, 8 + height * 4, 8);
    }

    public static Vec3 nozzleTip() { return new Vec3(8, 8, NOZZLE_TIP_Z); }

    /** The hinge stays at the receptacle pivot, not at the moving insertion point. */
    public static Vec3 nozzleJointPivot() {
        double contactRadius = RECEPTACLE_MOUTH.y - INSERTION_PIXELS - PITCH_PIVOT.y;
        return nozzleTip().add(0, 0, contactRadius);
    }

    /** Converts the nozzle's +Z longitudinal axis to the common +Y mounting convention. */
    public static Vec3 nozzleToMount(Vec3 point) {
        return new Vec3(point.x, point.z, 16 - point.y);
    }

    public static Vec3 modelPoint(Vec3 pixels, Direction facing) {
        return orient(pixels.add(-8, -8, -8).scale(1.0 / 16), facing).add(0.5, 0.5, 0.5);
    }

    public static Vec3 orient(Vec3 p, Direction facing) {
        return switch (facing) {
            case UP -> p;
            case DOWN -> new Vec3(p.x, -p.y, -p.z);
            case NORTH -> new Vec3(p.x, p.z, -p.y);
            case SOUTH -> new Vec3(-p.x, p.z, p.y);
            case WEST -> new Vec3(-p.y, p.z, -p.x);
            case EAST -> new Vec3(p.y, p.z, p.x);
        };
    }

    public static Vec3 unorient(Vec3 p, Direction facing) {
        return new Vec3(p.dot(orient(new Vec3(1, 0, 0), facing)),
            p.dot(orient(new Vec3(0, 1, 0), facing)), p.dot(orient(new Vec3(0, 0, 1), facing)));
    }

    public record Ring(Vec3 center, Vec3 axis, Vec3 firstFace, Vec3 secondFace) {}

    /** Called once in the controller's frame; both hose halves use these same two faces. */
    public static Ring ring(Vec3 firstAnchor, Vec3 secondAnchor, Vec3 firstNormal, Vec3 secondNormal) {
        Vec3 axis = firstNormal.subtract(secondNormal);
        if (axis.lengthSqr() < 1e-10) axis = firstNormal;
        if (axis.lengthSqr() < 1e-10) axis = new Vec3(0, 1, 0);
        axis = axis.normalize();
        Vec3 center = firstAnchor.add(secondAnchor).scale(0.5);
        return new Ring(center, axis, center.subtract(axis.scale(RING_HALF_DEPTH)),
            center.add(axis.scale(RING_HALF_DEPTH)));
    }
}
