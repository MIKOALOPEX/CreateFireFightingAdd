package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Per-interface geometry and pairing policy; connection state is owned by the endpoint. */
public interface FluidCouplingInterface {
    ResourceLocation id();
    boolean accepts(FluidCouplingInterface other);
    Activation activation();
    boolean locksConstraint();
    Vec3 contact(int height, double pitch);
    Vec3 jointPivot(int height);

    enum Activation { FREE, ACTIVE, PASSIVE }
}
