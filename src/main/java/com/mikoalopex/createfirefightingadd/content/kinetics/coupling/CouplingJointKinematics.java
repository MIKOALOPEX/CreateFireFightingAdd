package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Solves the two perpendicular cross pins without changing either shaft's kinetic phase. */
public final class CouplingJointKinematics {
    private CouplingJointKinematics() {}

    public record Pins(Vector3d a, Vector3d b) {}

    public static Pins solve(Vector3dc axisA, Vector3dc axisB, Vector3dc reference, double phase) {
        Vector3d a = new Vector3d(axisA).normalize(), b = new Vector3d(axisB).normalize();
        Vector3d u = new Vector3d(reference).fma(-reference.dot(a), a);
        if (u.lengthSquared() < 1e-10) {
            u.set(Math.abs(a.y) > 0.85 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0));
            u.fma(-u.dot(a), a);
        }
        u.normalize().rotateAxis(phase, a.x, a.y, a.z);
        // At a 90-degree fold one phase can align a pin with the other shaft.
        // Blend toward the other transverse direction inside that narrow singular interval.
        double alignment = Math.abs(u.dot(b));
        if (alignment > 0.995) {
            double correction = (alignment - 0.995) / 0.005 * 0.15;
            u.rotateAxis(correction, a.x, a.y, a.z);
        }
        Vector3d v = new Vector3d(b).cross(u);
        // A fully folded joint has no unique rotating solution; choose a finite transverse frame.
        if (v.lengthSquared() < 1e-8) {
            u.set(a).cross(b).normalize();
            v.set(b).cross(u);
        }
        v.normalize();
        return new Pins(u, v);
    }

    public static Quaterniond frame(Vector3dc pin, Vector3dc shaft) {
        Vector3d y = new Vector3d(shaft).normalize();
        Vector3d x = new Vector3d(pin).fma(-pin.dot(y), y).normalize();
        Vector3d z = new Vector3d(x).cross(y).normalize();
        return new Quaterniond().setFromNormalized(new Matrix3d().setColumn(0, x).setColumn(1, y).setColumn(2, z));
    }
}
