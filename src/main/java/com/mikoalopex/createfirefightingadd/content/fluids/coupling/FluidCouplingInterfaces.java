package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Built-in pairing policies; activation roles are independent of fluid direction. */
public enum FluidCouplingInterfaces implements FluidCouplingInterface {
    FREE("free", Activation.FREE),
    RECEPTACLE("boom_receptacle", Activation.ACTIVE),
    BOOM_NOZZLE("boom_nozzle", Activation.PASSIVE);

    private final ResourceLocation id;
    private final Activation activation;

    FluidCouplingInterfaces(String path, Activation activation) {
        id = ResourceLocation.fromNamespaceAndPath("createfirefightingadd", path);
        this.activation = activation;
    }

    @Override public ResourceLocation id() { return id; }
    @Override public Activation activation() { return activation; }
    @Override public boolean locksConstraint() { return this != FREE; }

    @Override public boolean accepts(FluidCouplingInterface other) {
        return other != null && switch (this) {
            case FREE -> other.id().equals(FREE.id);
            case RECEPTACLE -> other.id().equals(BOOM_NOZZLE.id);
            case BOOM_NOZZLE -> other.id().equals(RECEPTACLE.id);
        };
    }

    @Override public Vec3 contact(int height, double pitch) {
        return switch (this) {
            case FREE -> FluidCouplingGeometry.freeAnchor(height);
            case RECEPTACLE -> FluidCouplingGeometry.receptacleContact(pitch);
            case BOOM_NOZZLE -> FluidCouplingGeometry.nozzleToMount(FluidCouplingGeometry.nozzleTip());
        };
    }

    @Override public Vec3 jointPivot(int height) {
        return switch (this) {
            case FREE -> FluidCouplingGeometry.freeAnchor(height);
            case RECEPTACLE -> FluidCouplingGeometry.PITCH_PIVOT;
            case BOOM_NOZZLE -> FluidCouplingGeometry.nozzleToMount(FluidCouplingGeometry.nozzleJointPivot());
        };
    }
}
