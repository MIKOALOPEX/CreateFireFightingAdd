package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.UUID;

public final class FireHoseSizesTest {
    public static void main(String[] args) {
        if (FireHoseSize.EIGHT.next() != FireHoseSize.SIX
            || FireHoseSize.SIX.next() != FireHoseSize.FOUR
            || FireHoseSize.FOUR.next() != FireHoseSize.EIGHT)
            throw new AssertionError("Size cycle order changed");
        for (int width : new int[] {4, 6, 8}) {
            FireHoseSize size = FireHoseSize.of(width);
            if (size.width() != width || !size.getSerializedName().equals(Integer.toString(width)))
                throw new AssertionError("Size mapping failed for " + width);
            float center = (HoseTexture.u0(width) + HoseTexture.u1(width)) / 2;
            if (Math.abs(center - 0.25f) > 1e-6)
                throw new AssertionError("Surface texture centre drifted for width " + width);
        }

        HoseRoute route = new HoseRoute(UUID.randomUUID());
        route.size = FireHoseSize.FOUR.width();
        HoseRoute restored = HoseRoute.read(route.write());
        if (restored == null || restored.size != 4)
            throw new AssertionError("Route did not keep the adjustable size");
        var legacy = route.write();
        legacy.remove("Size");
        if (HoseRoute.read(legacy).size != 8)
            throw new AssertionError("Legacy route did not default to the eight-pixel tube");

        // The regression task list is owned by another workstream; run the
        // water-flow check from this already-registered hose task.
        HoseWaterFlowProtectionTest.run();
        System.out.println("Fire hose size cycle, texture centring and route persistence passed");
    }
}
