package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Verifies six-face geometry without loading a world or an optional physics backend. */
public final class FluidCouplingGeometryTest {
    private static int checks;

    public static void main(String[] args) {
        check(FluidCouplingGeometry.pitchToward(new Vec3(0, 1, 1)) == 45, "Aim toward target position");
        check(FluidCouplingGeometry.pitchToward(new Vec3(0, 0, 1)) == 67.5, "Unreachable open-side target clamps");
        check(FluidCouplingGeometry.pitchToward(new Vec3(0, 0, -1)) == -45, "Unreachable rim-side target clamps");
        check(FluidCouplingGeometry.pitchToward(new Vec3(9, 1, 1)) == 45, "Off-plane target still tracks pitch");
        check(FluidCouplingGeometry.pitchToward(Vec3.ZERO) == 0, "Coincident target stays finite");
        check(FluidCouplingIndex.inSearchRange(FluidCouplingInterfaces.RECEPTACLE, Vec3.ZERO, new Vec3(3, 0, 0)), "Three block pitch search");
        check(!FluidCouplingIndex.inSearchRange(FluidCouplingInterfaces.RECEPTACLE, Vec3.ZERO, new Vec3(3.01, 0, 0)), "Pitch search boundary");
        check(!FluidCouplingIndex.inSearchRange(FluidCouplingInterfaces.RECEPTACLE, Vec3.ZERO, new Vec3(3, 3, 0)), "Spherical rather than cubic search");
        check(!FluidCouplingIndex.inSearchRange(FluidCouplingInterfaces.FREE, Vec3.ZERO, new Vec3(2, 0, 0)), "Free search unchanged");
        check(FluidCouplingIndex.inside(Vec3.ZERO, new Vec3(0.75, 0.75, 0.75), 0.75), "Forced box boundary");
        check(!FluidCouplingIndex.inside(Vec3.ZERO, new Vec3(0.751, 0, 0), 0.75), "Forced box unchanged");
        for (Direction facing : Direction.values()) {
            Vec3 normal = FluidCouplingGeometry.orient(new Vec3(0, 1, 0), facing);
            close(normal, Vec3.atLowerCornerOf(facing.getNormal()), "Mount normal");
            Vec3 point = new Vec3(0.3, -0.7, 2.1);
            close(FluidCouplingGeometry.unorient(FluidCouplingGeometry.orient(point, facing), facing), point, "Inverse frame");
            for (int i = 0; i < 5; i++) {
                close(FluidCouplingGeometry.modelPoint(FluidCouplingGeometry.freeAnchor(i), facing),
                    new Vec3(0.5, 0.5, 0.5).add(normal.scale(i / 4.0)), "Free height");
            }
            for (int step = -90; step <= 135; step++) {
                double pitch = step / 2.0;
                Vec3 axis = FluidCouplingGeometry.receptacleAxis(pitch);
                Vec3 mouth = FluidCouplingGeometry.pitch(FluidCouplingGeometry.RECEPTACLE_MOUTH, pitch);
                Vec3 contact = FluidCouplingGeometry.receptacleContact(pitch);
                close(mouth.subtract(contact), axis.scale(7), "Seven pixel insertion");
                Vec3 pivot = FluidCouplingGeometry.PITCH_PIVOT;
                close(contact.subtract(pivot), axis.scale(2), "Contact follows fixed physical pivot");
                Vec3 orientedContact = FluidCouplingGeometry.modelPoint(contact, facing);
                Vec3 orientedMouth = FluidCouplingGeometry.modelPoint(mouth, facing);
                close(orientedMouth.subtract(orientedContact),
                    FluidCouplingGeometry.orient(axis, facing).scale(7.0 / 16), "Rotated insertion");
                check(Math.abs(FluidCouplingGeometry.trackingPitch(axis.scale(-1)) - pitch) < 1e-8, "Tracking sign");
            }
        }
        check(FluidCouplingGeometry.clampPitch(90) == 67.5, "Upper limit");
        check(FluidCouplingGeometry.clampPitch(-90) == -45, "Rim-side lower limit");
        check(Math.abs(Math.toDegrees(Math.asin(FluidCouplingGeometry.receptacleAxis(-90).y)) - 45) < 1e-8,
            "Rim-side minimum angle above base plane");
        check(Math.abs(Math.toDegrees(Math.asin(FluidCouplingGeometry.receptacleAxis(67.5).y)) - 22.5) < 1e-8,
            "Minimum angle above base plane");
        check(FluidCouplingGeometry.clampPitch(Double.NaN) == 0, "Finite animation");
        check(FluidCouplingGeometry.trackingPitch(new Vec3(1, 0, 0)) == 0, "Out-of-plane target");
        close(FluidCouplingGeometry.nozzleJointPivot().subtract(FluidCouplingGeometry.nozzleTip()),
            new Vec3(0, 0, 2), "Nozzle pivot offset");
        for (var a : FluidCouplingInterfaces.values()) for (var b : FluidCouplingInterfaces.values()) {
            check(a.accepts(b) == b.accepts(a), "Symmetric compatibility");
            check(a.accepts(b) == (a == FluidCouplingInterfaces.FREE && b == a
                || a.hardBoom() && b.hardBoom() && a != b
                || a.hose() && b.hose() && a != b), "Pair policy excludes cross-family and same-role pairs");
        }
        var settings = new FluidCouplingSettings(FluidCouplingInterfaces.FREE, 4, 2, -88, 103, true);
        var receptacle = settings.withInterface(FluidCouplingInterfaces.RECEPTACLE);
        var nozzle = settings.withInterface(FluidCouplingInterfaces.BOOM_NOZZLE);
        for (Direction facing : Direction.values()) {
            var a = FluidCouplingJoint.endpoint(Level.OVERWORLD, null, BlockPos.ZERO, facing, receptacle, v -> v);
            var b = FluidCouplingJoint.endpoint(Level.OVERWORLD, java.util.UUID.randomUUID(), BlockPos.ZERO,
                facing.getOpposite(), nozzle, v -> v);
            var request = FluidCouplingJoint.request(java.util.UUID.randomUUID(), receptacle, nozzle, a, b);
            check(request.alignHingeTangents() && request.mode() == 1
                && request.lowerDegrees() == -45 && request.upperDegrees() == 67.5, "Fixed hard-boom request");
            check(Math.abs(a.normal().dot(a.tangent())) < 1e-8, "Orthogonal physical frame");
            Vec3 expected = FluidCouplingGeometry.modelPoint(FluidCouplingGeometry.PITCH_PIVOT, facing);
            close(new Vec3(a.anchor().x(), a.anchor().y(), a.anchor().z()), expected, "Fixed constraint pivot");
        }
        for (var type : FluidCouplingInterfaces.values()) {
            var changed = settings.withInterface(type);
            check(FluidCouplingSettings.load(changed.save()).equals(changed), "Settings round trip");
            check(changed.withInterface(FluidCouplingInterfaces.FREE).equals(settings), "Retain free settings");
            if (type.hardBoom()) {
                var rule = changed.constraint();
                check(rule.mode() == 1 && rule.lower() == -45 && rule.upper() == 67.5
                    && !rule.flipRange() && rule.modelZero(), "Hard-boom constraint cannot be overridden");
            }
            if (type.hose()) {
                var rule = changed.constraint();
                check(rule.mode() == 2 && rule.lower() == 0 && rule.upper() == 0
                    && !rule.flipRange() && !rule.modelZero(), "Hose always uses fixed joint, ignoring free hinge settings");
            }
        }
        var drogue = settings.withInterface(FluidCouplingInterfaces.HOSE_DROGUE);
        var probe = settings.withInterface(FluidCouplingInterfaces.HOSE_PROBE);
        check(drogue.type().activation() == FluidCouplingInterface.Activation.ACTIVE, "Drogue owns pairing");
        check(probe.type().activation() == FluidCouplingInterface.Activation.PASSIVE, "Probe is passive");
        for (Direction facing : Direction.values()) {
            var a = FluidCouplingJoint.endpoint(Level.OVERWORLD, null, BlockPos.ZERO, facing, drogue, v -> v);
            var b = FluidCouplingJoint.endpoint(Level.OVERWORLD, java.util.UUID.randomUUID(), BlockPos.ZERO,
                facing.getOpposite(), probe, v -> v);
            var request = FluidCouplingJoint.request(java.util.UUID.randomUUID(), drogue, probe, a, b);
            check(request.mode() == 2 && !request.alignHingeTangents(), "Fixed hose joint request");
            check(a.normal().dot(b.normal()) < -0.999999, "Hose mates along facing, not hard-boom pitch axis");
            close(new Vec3(a.anchor().x(), a.anchor().y(), a.anchor().z()),
                FluidCouplingGeometry.modelPoint(FluidCouplingGeometry.hoseToMount(new Vec3(7, 8, 8)), facing), "Drogue socket pivot");
            close(new Vec3(b.anchor().x(), b.anchor().y(), b.anchor().z()),
                FluidCouplingGeometry.modelPoint(FluidCouplingGeometry.hoseToMount(new Vec3(-6, 8, 8)), facing.getOpposite()), "Probe insertion pivot");
        }
        float deployed = 0;
        for (int tick = 0; tick < HoseDeployment.DURATION_TICKS; tick++) {
            check(!HoseDeployment.ready(deployed), "No pairing during partial deployment");
            deployed = HoseDeployment.step(deployed, true);
        }
        check(HoseDeployment.ready(deployed), "Pairing allowed after deployment");
        for (int tick = 0; tick < HoseDeployment.DURATION_TICKS; tick++) deployed = HoseDeployment.step(deployed, false);
        check(deployed == 0, "Fully stowed after redstone removed");
        float halfway = 0;
        for (int tick = 0; tick < 10; tick++) halfway = HoseDeployment.step(halfway, true);
        float reversed = HoseDeployment.step(halfway, false);
        check(reversed > 0 && reversed < halfway && Math.abs(halfway - reversed - 0.05f) < 1e-6,
            "Mid-animation reversal is continuous");
        check(HoseDeployment.clamp(Float.NaN) == 0 && HoseDeployment.clamp(7) == 1, "Persisted deployment sanitization");
        check(HoseDrogueParts.PARTS.size() == 28, "All moving source elements represented");
        check(HoseDrogueParts.PARTS.stream().filter(p -> p.angle() != 0).count() == 12, "Twelve net panels rotate");
        check(HoseDrogueParts.PARTS.stream().filter(p -> p.angle() == 0).count() == 16, "Sixteen ring pieces translate only");
        var ring = FluidCouplingGeometry.ring(Vec3.ZERO, new Vec3(0, 2, 0),
            new Vec3(0, 1, 0), new Vec3(0, -1, 0));
        close(ring.firstFace().add(ring.secondFace()).scale(0.5), ring.center(), "Shared ring center");
        check(Math.abs(ring.firstFace().distanceTo(ring.secondFace()) - 2.0 / 16) < 1e-10, "Ring thickness");
        var degenerate = FluidCouplingGeometry.ring(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        check(Double.isFinite(degenerate.axis().length()) && degenerate.axis().lengthSqr() > 0.99, "Finite ring frame");
        System.out.println("FluidCouplingGeometry: " + checks + " geometry and policy checks passed");
    }

    private static void close(Vec3 actual, Vec3 expected, String message) {
        check(actual.distanceTo(expected) < 1e-8, message);
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
