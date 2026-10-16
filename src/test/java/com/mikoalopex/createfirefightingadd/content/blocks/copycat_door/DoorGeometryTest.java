package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class DoorGeometryTest {
    private static int checks;

    public static void main(String[] args) {
        float pivot = DoorGeometry.THICKNESS / 2;
        for (Direction normal : Direction.values()) {
            for (Direction side : Direction.values()) {
                if (side.getAxis() == normal.getAxis()) continue;
                Vec3 offset = new Vec3(side.getStepX() * .45, side.getStepY() * .45, side.getStepZ() * .45);
                check(DoorGeometry.placementHinge(normal, offset, Direction.SOUTH) == side, "placement edge");
            }
            for (int edge = 0; edge < 4; edge++) {
                var frame = DoorGeometry.frame(normal, edge, false);
                var outward = frame.transformDirection(new Vector3f(0, 0, -1));
                check(Direction.getNearest(outward.x, outward.y, outward.z) == normal, "panel normal");
                Vec3 a = DoorGeometry.point(frame, pivot, 0, pivot);
                Vec3 b = DoorGeometry.point(frame, pivot, 1, pivot);
                check(Math.abs(a.distanceTo(b) - 1) < 1e-5, "16px axis");
                for (int step = 0; step <= 20; step++) {
                    var part = DoorGeometry.parts(DoorGeometry.frame(normal, edge, false), step / 20f).getFirst();
                    check(DoorGeometry.point(part.transform(), pivot, 0, pivot).distanceTo(a) < 1e-5, "fixed hinge");
                }
            }
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean mirror : new boolean[] {false, true}) {
                for (int step = 0; step <= 20; step++) {
                    var parts = DoorGeometry.slidingParts(facing, mirror, true, step / 20f);
                    Vec3 a = DoorGeometry.point(parts.getFirst().transform(), .5, .5, 0);
                    Vec3 b = DoorGeometry.point(parts.getLast().transform(), .5, .5, 0);
                    check(a.distanceTo(b) < 1e-5, "fold joint continuity");
                    Vec3 hinge = DoorGeometry.point(parts.getFirst().transform(), mirror ? 1 : 0, 0, 0);
                    Vec3 corner = DoorGeometry.point(DoorGeometry.slidingFrame(facing), 0, 0, mirror ? 1 : 0)
                        .add(facing.getStepX() * Math.min(1, step / 2f) / 32, -1 / 512.0,
                            facing.getStepZ() * Math.min(1, step / 2f) / 32);
                    check(hinge.distanceTo(corner) < 1e-5, "Create corner hinge");
                    for (var part : parts) {
                        Vec3 low = DoorGeometry.point(part.transform(), .5, 0, pivot);
                        Vec3 high = DoorGeometry.point(part.transform(), .5, 1, pivot);
                        check(Math.abs(high.y - low.y - 1) < 1e-5, "one block height");
                    }
                }
                var closed = DoorGeometry.slidingParts(facing, mirror, false, 0).getFirst();
                var open = DoorGeometry.slidingParts(facing, mirror, false, 1).getFirst();
                double travel = DoorGeometry.point(closed.transform(), 0, 0, 0)
                    .distanceTo(DoorGeometry.point(open.transform(), 0, 0, 0));
                check(Math.abs(travel - Math.hypot(13 / 16.0, 1 / 32.0)) < 1e-5, "Create slide travel");
            }
        }
        System.out.println("DoorGeometry: " + checks + " orientation, hinge and folding checks passed");
    }

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
}
