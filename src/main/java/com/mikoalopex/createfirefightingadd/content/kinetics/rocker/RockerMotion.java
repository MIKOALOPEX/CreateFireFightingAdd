package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import net.minecraft.nbt.CompoundTag;

/** Tracks a complete stroke, including the unpowered return in ratchet mode. */
public final class RockerMotion {
    public static final int ROTARY = 0, RECIPROCATING = 1, RATCHET = 2;
    public double travel;
    public boolean running, reverse;
    public int mode, half = 1, step;

    public void write(CompoundTag tag) {
        tag.putInt("RockerMode", mode);
        tag.putInt("RockerHalf", half);
        tag.putInt("RockerStep", step);
        tag.putBoolean("Stroke", running);
        tag.putBoolean("StrokeReverse", reverse);
        tag.putDouble("Travel", travel);
    }

    public void read(CompoundTag tag) {
        mode = Math.clamp(tag.getInt("RockerMode"), ROTARY, RATCHET);
        half = tag.getInt("RockerHalf") < 0 ? -1 : 1;
        step = Math.clamp(tag.getInt("RockerStep"), 0, 6);
        reverse = tag.getBoolean("StrokeReverse");
        double savedTravel = tag.getDouble("Travel");
        running = mode != ROTARY && tag.getBoolean("Stroke") && Double.isFinite(savedTravel)
            && savedTravel >= 0 && savedTravel < 180;
        travel = running ? savedTravel : 0;
    }

    public double multiplier() { return 1 + step * 0.5; }
    public double outputSpeed(double baseSpeed) { return baseSpeed / multiplier(); }
    public double capacityCoefficient(double baseCapacity) { return baseCapacity * multiplier() * multiplier(); }
    public double angle() {
        double excursion = travel <= 90 ? travel : 180 - travel;
        return half * 45 + (reverse ? -1 : 1) * excursion;
    }
    public double speed() {
        if (!running) return 0;
        if (mode == RATCHET && travel >= 90) return 0;
        double remaining = (travel < 90 ? 90 : 180) - travel;
        return Math.min(outputSpeed(32), remaining / 0.3) * (reverse ? -1 : 1) * (travel >= 90 ? -1 : 1);
    }
    public void start(boolean reverse) { this.reverse = reverse; travel = 0; running = true; }
    public void tick() {
        if (!running) return;
        double end = travel < 90 ? 90 : 180;
        travel = Math.min(end, travel + outputSpeed(32) * 0.3);
        if (travel >= 180) { travel = 0; running = false; }
    }
    public void select(int side) {
        if (side == half) mode = (mode + 1) % 3;
        else half = side;
    }
}
