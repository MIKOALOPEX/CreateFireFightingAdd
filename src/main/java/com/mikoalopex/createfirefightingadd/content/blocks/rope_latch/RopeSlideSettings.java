package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

/** Immutable server settings shared by the configurator, rope sliding and tailhook tracking. */
public record RopeSlideSettings(double friction, double geometricFriction, double responseGain,
        double maxSpeed, double gravityAcceleration, double gravityRetention, double tautRatio,
        int scanIntervalTicks, int searchDurationSeconds, double swingDamping, double upperSwingDamping) {
    public static final RopeSlideSettings DEFAULT = new RopeSlideSettings(0.01, 0.2, 100, 5, 7, 0.98, 0.97, 10, 30, 0.9, 0.5);
}
