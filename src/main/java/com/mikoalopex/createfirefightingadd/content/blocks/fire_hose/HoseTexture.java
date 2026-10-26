package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

/** One texture repeat occupies one block of drawn hose length. */
public final class HoseTexture {
    @FunctionalInterface
    public interface Slice { void accept(double from, double to, float v0, float v1); }

    public static void split(double start, double end, Slice output) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || end - start < 1e-9) return;
        double cursor = start;
        while (cursor < end - 1e-9) {
            double tile = Math.floor(cursor + 1e-9);
            double next = Math.min(end, tile + 1);
            output.accept((cursor - start) / (end - start), (next - start) / (end - start),
                (float) Math.max(0, cursor - tile), (float) (next - tile));
            cursor = next;
        }
    }

    public static float u0(float width) { return (8 - width) / 32f; }
    public static float u1(float width) { return (8 + width) / 32f; }
    private HoseTexture() {}
}
