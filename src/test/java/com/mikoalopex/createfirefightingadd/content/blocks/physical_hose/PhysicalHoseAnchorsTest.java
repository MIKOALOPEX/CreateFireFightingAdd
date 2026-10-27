package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Length checks do not depend on the present sag or temporary solver stretch. */
public final class PhysicalHoseAnchorsTest {
    private static int checks;
    public static void main(String[] args) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) points.add(new Vec3(i * 0.5, -Math.sin(i * Math.PI / 11) * 2, 0));
        checkTargetFrames();
        var original = List.copyOf(points);
        var snapshot = new PhysicalHoseAnchors.Snapshot(UUID.randomUUID(), points, 1);
        var raised = PhysicalHoseAnchors.placement(snapshot, List.of(), 4, new Vec3(2, 2, 0), new Vec3(3, 2, 0));
        expect(raised.reachable(), "Raising a slack segment within both lengths must work");
        close(raised.leftLength(), 4); close(raised.rightLength(), 6);
        var leftOnly = PhysicalHoseAnchors.placement(snapshot, List.of(), 4, new Vec3(5.01, 0, 0), new Vec3(6.01, 0, 0));
        expect(!leftOnly.leftReachable() && leftOnly.rightReachable(), "Unused right length cannot be borrowed by the left");
        var rightOnly = PhysicalHoseAnchors.placement(snapshot, List.of(), 8, new Vec3(0, 0, 0), new Vec3(1, 0, 0));
        expect(rightOnly.leftReachable() && !rightOnly.rightReachable(), "Only the right side is out of reach");
        var both = PhysicalHoseAnchors.placement(snapshot, List.of(), 4, new Vec3(2, 12, 0), new Vec3(3, 12, 0));
        expect(!both.leftReachable() && !both.rightReachable(), "Both preview sides must turn red");
        var exact = PhysicalHoseAnchors.placement(snapshot, List.of(), 4, new Vec3(-4, 0, 0), new Vec3(11.5, 0, 0));
        expect(exact.reachable(), "Exact length is allowed; no arbitrary slack reserve");
        var tolerance = PhysicalHoseAnchors.placement(snapshot, List.of(), 4, new Vec3(-5, 0, 0), new Vec3(12.5, 0, 0));
        expect(tolerance.reachable(), "Both sides allow exactly one extra block");
        close(tolerance.leftLength(), 4); close(tolerance.rightLength(), 6);
        var outsideTolerance = PhysicalHoseAnchors.placement(snapshot, List.of(), 4, new Vec3(-5.01, 0, 0), new Vec3(12.51, 0, 0));
        expect(!outsideTolerance.leftReachable() && !outsideTolerance.rightReachable(), "Neither side allows more than one extra block");
        var fractional = new PhysicalHoseAnchors.Snapshot(snapshot.rope(), points, 0.25);
        close(fractional.length(0), 0.25); close(fractional.length(1), 1);
        var split = PhysicalHoseAnchors.placement(fractional, List.of(), 4, points.get(4), points.get(5));
        close(split.leftLength(), 3.25); close(split.rightLength(), 6);
        close(split.leftLength() + fractional.length(4) + split.rightLength(), 10.25);
        var first = PhysicalHoseAnchors.placement(fractional, List.of(), 0, points.getFirst(), points.get(1));
        close(first.leftLength(), 0);
        expect(first.leftReachable(), "A coincident endpoint remains reachable");
        var displaced = PhysicalHoseAnchors.placement(fractional, List.of(), 0, new Vec3(0, 0.1, 0), points.get(1));
        expect(displaced.leftReachable(), "The endpoint side uses the same placement tolerance");
        var bindings = List.of(new PhysicalHoseAnchors.Binding(2, BlockPos.ZERO, UUID.randomUUID()),
            new PhysicalHoseAnchors.Binding(8, BlockPos.ZERO, UUID.randomUUID()));
        var bounded = PhysicalHoseAnchors.placement(fractional, bindings, 5, points.get(5), points.get(6));
        close(bounded.leftLength(), 2); close(bounded.rightLength(), 2);
        expect(bounded.left().equals(points.get(3)) && bounded.right().equals(points.get(8)), "Nearest fixed faces bound each interval");
        expect(PhysicalHoseAnchors.fixed(bindings, 2) && !PhysicalHoseAnchors.fixed(bindings, 5), "Fixed segments cannot be selected again");
        // Stretching the pose does not add material length.
        var stretched = new PhysicalHoseAnchors.Snapshot(snapshot.rope(), points.stream().map(p -> p.scale(3)).toList(), 0.25);
        var stretchPlacement = PhysicalHoseAnchors.placement(stretched, bindings, 5, points.get(5), points.get(6));
        close(stretchPlacement.leftLength(), bounded.leftLength()); close(stretchPlacement.rightLength(), bounded.rightLength());
        for (Direction face : Direction.values()) for (float angle : new float[] {-90, -35, 0, 35, 90})
            for (boolean reversed : new boolean[] {false, true}) for (double length : new double[] {0.25, 1}) {
                Vec3 a = PhysicalHoseAnchors.port(BlockPos.ZERO, face, angle, reversed, length, false);
                Vec3 b = PhysicalHoseAnchors.port(BlockPos.ZERO, face, angle, reversed, length, true);
                close(a.distanceTo(b), length);
                close(a.add(b).scale(0.5).distanceTo(BlockPos.ZERO.getCenter()), 0);
                close(b.subtract(a).normalize().dot(Vec3.atLowerCornerOf(face.getNormal())), 0);
            }
        var zeroFirst = PhysicalHoseAnchors.indexedSegments(List.of(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1)));
        expect(zeroFirst.size() == 2 && zeroFirst.getFirst() == null && zeroFirst.get(1) != null,
            "A zero-length first segment must not renumber the selectable segments");
        for (int width : new int[] {4, 6, 8}) {
            var segment = PhysicalHoseGeometry.segments(List.of(new Vec3(0, -0.5, 0), new Vec3(0, 0.5, 0))).getFirst();
            double radius = width / 32.0;
            expect(Double.isFinite(PhysicalHoseAnchors.hit(new Vec3(radius * 0.9, 0, -2), new Vec3(radius * 0.9, 0, 2), segment, width)), "Rotated center cube should be selectable");
            expect(!Double.isFinite(PhysicalHoseAnchors.hit(new Vec3(radius * 1.1, 0, -2), new Vec3(radius * 1.1, 0, 2), segment, width)), "Selection follows the chosen width");
        }
        expect(points.equals(original), "Preview must not mutate physics points");
        System.out.println("Physical hose anchor geometry passed: " + checks + " checks");
    }
    private static void checkTargetFrames() {
        BlockPos pos = new BlockPos(25000000, 48, -25000000);
        Vec3 center = pos.getCenter();
        var localPoints = java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> center.add((i - 2.5) * 0.5, 0, 0)).toList();
        var local = new PhysicalHoseAnchors.Snapshot(UUID.randomUUID(), localPoints, 1);
        for (Direction face : Direction.values()) for (double yaw : new double[] {0, 0.7, 1.57}) {
            var rotation = new org.joml.Quaterniond().rotationYXZ(yaw, 0.35, -0.2);
            Vec3 worldCenter = new Vec3(12, 70, -8);
            java.util.function.UnaryOperator<Vec3> rotate = point -> {
                var value = rotation.transform(new org.joml.Vector3d(point.x, point.y, point.z));
                return new Vec3(value.x, value.y, value.z);
            };
            java.util.function.UnaryOperator<Vec3> toWorld = point -> rotate.apply(point.subtract(center)).add(worldCenter);
            Vec3 look = new Vec3(0.2, -0.4, 1).normalize();
            var expected = PhysicalHoseAnchors.target(local, 2, pos, face, look, point -> point);
            var world = new PhysicalHoseAnchors.Snapshot(local.rope(), localPoints.stream().map(toWorld).toList(), 1);
            var actual = PhysicalHoseAnchors.target(world, 2, pos, face, rotate.apply(look), toWorld);
            close(actual.start().distanceTo(toWorld.apply(expected.start())), 0);
            close(actual.end().distanceTo(toWorld.apply(expected.end())), 0);
            close(actual.start().add(actual.end()).scale(0.5).distanceTo(worldCenter), 0);
            close(actual.start().distanceTo(actual.end()), 1);
            var before = PhysicalHoseAnchors.placement(local, List.of(), 2, expected.start(), expected.end());
            var after = PhysicalHoseAnchors.placement(world, List.of(), 2, actual.start(), actual.end());
            expect(before.leftReachable() == after.leftReachable() && before.rightReachable() == after.rightReachable(),
                "Moving the whole scene must not change either side's reachability");
            var mixed = PhysicalHoseAnchors.placement(world, List.of(), 2, expected.start(), expected.end());
            expect(!mixed.leftReachable() && !mixed.rightReachable(), "Unconverted target ports reproduce the range regression");
            var worldTarget = PhysicalHoseAnchors.target(world, 2, new BlockPos(12, 70, -8), face, rotate.apply(look), point -> point);
            close(worldTarget.start().add(worldTarget.end()).scale(0.5).distanceTo(new BlockPos(12, 70, -8).getCenter()), 0);
        }
    }
    private static void close(double actual, double expected) { expect(Math.abs(actual - expected) < 1e-6, actual + " != " + expected); }
    private static void expect(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
