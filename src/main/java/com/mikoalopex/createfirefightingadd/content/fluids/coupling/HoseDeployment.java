package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

/** Reversible one-second deployment, shared by server state and client interpolation. */
public final class HoseDeployment {
    public static final int DURATION_TICKS = 20;
    private HoseDeployment() {}
    public static float clamp(float progress) {
        return Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
    }
    public static float step(float progress, boolean deployed) {
        return clamp(clamp(progress) + (deployed ? 1f : -1f) / DURATION_TICKS);
    }
    public static boolean ready(float progress) { return clamp(progress) >= 1; }
}
