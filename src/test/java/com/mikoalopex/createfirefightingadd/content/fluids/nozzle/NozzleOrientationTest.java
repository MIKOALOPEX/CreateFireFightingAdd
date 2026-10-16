package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Random;
import java.util.function.UnaryOperator;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Checks fan orientation and rotated sampling without launching a world. */
public final class NozzleOrientationTest {
    public static void main(String[] args) {
        UnaryOperator<Vec3> rotation = v -> v.xRot(0.7f).yRot(1.1f).zRot(0.4f);
        for (Direction facing : Direction.values()) {
            Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 width = FanSprayShape.nozzleWidth(facing);
            check(width.dot(forward) == 0, "Model width must be perpendicular to facing");
            check(width.equals(facing.getAxis() == Direction.Axis.X
                ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0)), "Width must match blockstate model rotation");
            FanSprayShape shape = new FanSprayShape(32, 60, 4, width);
            Vec3 center = forward.scale(5);
            check(shape.containsPoint(center.add(width.scale(3)), center, forward, 5), "Wide fan side");
            check(!shape.containsPoint(center.add(forward.cross(width)), center, forward, 5), "Narrow fan side");
            SprayShape rotated = FanSprayShape.transformWidth(shape, rotation);
            Random localRandom = new Random(42);
            Random worldRandom = new Random(42);
            for (int i = 0; i < 100; i++) {
                Vec3 expected = rotation.apply(shape.randomSprayDirection(forward, localRandom));
                Vec3 actual = rotated.randomSprayDirection(rotation.apply(forward), worldRandom);
                check(expected.distanceTo(actual) < 1e-6, "Rotated structure must retain fan roll and sampling");
            }
        }
        System.out.println("NozzleOrientation: six facings and rotated fan sampling passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
