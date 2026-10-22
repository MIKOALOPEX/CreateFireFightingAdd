package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.AllItems;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FluidCouplingClient {
    private static final Object[] EDGES = {new Object(), new Object(), new Object(), new Object()};
    private FluidCouplingClient() {}
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit)
                || !mc.player.getMainHandItem().is(AllItems.WRENCH.get())
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof FluidCouplingBlockEntity be)
                || be.settings().type() != FluidCouplingInterfaces.FREE) {
            for (Object key : EDGES) Outliner.getInstance().remove(key);
            return;
        }
        Vec3 center = be.localAnchor();
        Vec3 x = FluidCouplingGeometry.orient(new Vec3(0.25, 0, 0), be.facing());
        Vec3 z = FluidCouplingGeometry.orient(new Vec3(0, 0, 0.25), be.facing());
        Vec3[] corners = {center.add(x).add(z), center.subtract(x).add(z), center.subtract(x).subtract(z), center.add(x).subtract(z)};
        for (int i = 0; i < 4; i++) Outliner.getInstance().showLine(EDGES[i],
            SableStructureCompat.transformPositionToWorld(be, corners[i]),
            SableStructureCompat.transformPositionToWorld(be, corners[(i + 1) % 4]))
            .colored(0x70FF33).lineWidth(1 / 16f).disableLineNormals();
    }
}
