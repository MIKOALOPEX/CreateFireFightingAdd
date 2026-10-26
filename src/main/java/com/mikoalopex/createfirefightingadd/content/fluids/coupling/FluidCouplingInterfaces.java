package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Built-in pairing policies; activation roles are independent of fluid direction. */
public enum FluidCouplingInterfaces implements FluidCouplingInterface {
    FREE("free", Activation.FREE),
    RECEPTACLE("boom_receptacle", Activation.ACTIVE),
    BOOM_NOZZLE("boom_nozzle", Activation.PASSIVE),
    HOSE_DROGUE("hose_drogue", Activation.ACTIVE),
    HOSE_PROBE("hose_probe", Activation.PASSIVE);

    private final ResourceLocation id;
    private final Activation activation;

    FluidCouplingInterfaces(String path, Activation activation) {
        id = ResourceLocation.fromNamespaceAndPath("createfirefightingadd", path);
        this.activation = activation;
    }

    @Override public ResourceLocation id() { return id; }
    @Override public Activation activation() { return activation; }
    @Override public boolean locksConstraint() { return this != FREE; }
    public boolean hardBoom() { return this == RECEPTACLE || this == BOOM_NOZZLE; }
    public boolean hose() { return this == HOSE_DROGUE || this == HOSE_PROBE; }

    @Override public boolean accepts(FluidCouplingInterface other) {
        return other != null && switch (this) {
            case FREE -> other.id().equals(FREE.id);
            case RECEPTACLE -> other.id().equals(BOOM_NOZZLE.id);
            case BOOM_NOZZLE -> other.id().equals(RECEPTACLE.id);
            case HOSE_DROGUE -> other.id().equals(HOSE_PROBE.id);
            case HOSE_PROBE -> other.id().equals(HOSE_DROGUE.id);
        };
    }

    @Override public Vec3 contact(int height, double pitch) {
        return switch (this) {
            case FREE -> FluidCouplingGeometry.freeAnchor(height);
            case RECEPTACLE -> FluidCouplingGeometry.receptacleContact(pitch);
            case BOOM_NOZZLE -> FluidCouplingGeometry.nozzleToMount(FluidCouplingGeometry.nozzleTip());
            // Both endpoints retract 2px toward the mount (+X in the source models).
            case HOSE_DROGUE -> FluidCouplingGeometry.hoseToMount(new Vec3(7, 8, 8));
            case HOSE_PROBE -> FluidCouplingGeometry.hoseToMount(new Vec3(-6, 8, 8));
        };
    }

    @Override public Vec3 jointPivot(int height) {
        return switch (this) {
            case FREE -> FluidCouplingGeometry.freeAnchor(height);
            case RECEPTACLE -> FluidCouplingGeometry.PITCH_PIVOT;
            case BOOM_NOZZLE -> FluidCouplingGeometry.nozzleToMount(FluidCouplingGeometry.nozzleJointPivot());
            case HOSE_DROGUE, HOSE_PROBE -> contact(height, 0);
        };
    }
}
