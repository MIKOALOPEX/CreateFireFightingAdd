package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import net.minecraft.nbt.CompoundTag;

/** Free-mode settings remain stored while a hard-boom interface overrides the physical joint. */
public record FluidCouplingSettings(FluidCouplingInterfaces type, int height, int freeMode,
        int lower, int upper, boolean flipRange) {
    public static final FluidCouplingSettings DEFAULT = new FluidCouplingSettings(
        FluidCouplingInterfaces.FREE, 0, 0, -45, 45, false);

    public FluidCouplingSettings {
        if (type == null || height < 0 || height > 4 || freeMode < 0 || freeMode > 2
                || lower < -180 || upper > 180 || lower > upper)
            throw new IllegalArgumentException("Invalid fluid coupling settings");
    }

    public record Constraint(int mode, double lower, double upper, boolean flipRange, boolean modelZero) {}

    public Constraint constraint() {
        return type.locksConstraint()
            ? new Constraint(1, FluidCouplingGeometry.MIN_PITCH, FluidCouplingGeometry.MAX_PITCH, false, true)
            : new Constraint(freeMode, lower, upper, flipRange, false);
    }

    public FluidCouplingSettings withInterface(FluidCouplingInterfaces next) {
        return new FluidCouplingSettings(next, height, freeMode, lower, upper, flipRange);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Interface", type.id().toString());
        tag.putInt("Height", height);
        tag.putInt("FreeMode", freeMode);
        tag.putInt("Lower", lower);
        tag.putInt("Upper", upper);
        tag.putBoolean("FlipRange", flipRange);
        return tag;
    }

    public static FluidCouplingSettings load(CompoundTag tag) {
        if (tag.isEmpty()) return DEFAULT;
        FluidCouplingInterfaces type = FluidCouplingInterfaces.FREE;
        for (var candidate : FluidCouplingInterfaces.values())
            if (candidate.id().toString().equals(tag.getString("Interface"))) type = candidate;
        int lower = Math.clamp(tag.getInt("Lower"), -180, 180);
        int upper = Math.clamp(tag.getInt("Upper"), lower, 180);
        return new FluidCouplingSettings(type, Math.clamp(tag.getInt("Height"), 0, 4),
            Math.clamp(tag.getInt("FreeMode"), 0, 2), lower, upper, tag.getBoolean("FlipRange"));
    }
}
