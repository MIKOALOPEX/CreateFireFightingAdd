package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.api.rope.RopeHookMode;

/** Rest-length ledger independent of the solver and its node discretization. */
final class RopeSlideState {
    static final double MIN_SIDE = 0.25;
    private RopeSlideSettings settings = RopeSlideSettings.DEFAULT;
    private double gravitySpeed;
    private double total;
    private double tail;
    private double speed;
    private RopeHookMode mode = RopeHookMode.SLIDING;

    void setMode(RopeHookMode next) {
        if (mode == next) return;
        mode = next;
        speed = 0;
        gravitySpeed = 0;
    }

    RopeSlideState(double total, double tail) {
        if (!Double.isFinite(tail)) throw new IllegalArgumentException("Invalid tail length");
        setTotal(total);
        this.tail = clamp(tail, MIN_SIDE, total - MIN_SIDE);
    }

    void setTotal(double length) {
        if (!Double.isFinite(length) || length < 2 * MIN_SIDE)
            throw new IllegalArgumentException("A sliding rope needs two non-empty sides");
        total = length;
        tail = clamp(tail, MIN_SIDE, total - MIN_SIDE);
    }

    double head() { return total - tail; }
    double tail() { return tail; }

    void configure(RopeSlideSettings settings) { this.settings = settings; }

    void step(double headPath, double tailPath) {
        step(headPath, tailPath, 0, 0);
    }

    void step(double headPath, double tailPath, double headSpan, double tailSpan) {
        step(headPath, tailPath, headSpan, tailSpan, 0);
    }

    void step(double headPath, double tailPath, double headSpan, double tailSpan, double gravitySlope) {
        if (!mode.transfersLength()) return;
        double a = Math.max(0, headPath / head() - 1);
        double b = Math.max(0, tailPath / tail - 1);
        double difference = a - b;
        if (!Double.isFinite(difference) || !Double.isFinite(headSpan) || !Double.isFinite(tailSpan)
                || !Double.isFinite(gravitySlope)) {
            speed = 0;
            gravitySpeed = 0;
            return;
        }
        double drive = deadZone(difference, settings.friction());
        // Solver projection can remove almost all stretch. Use local endpoint separation to detect
        // a taut side competing with available slack; two slack sides must not redistribute by length alone.
        double headTautness = headSpan / head();
        double tailTautness = tailSpan / tail;
        if (Math.max(headTautness, tailTautness) >= settings.tautRatio()) {
            double geometricDrive = deadZone(headTautness - tailTautness, settings.geometricFriction());
            if (Math.abs(geometricDrive) > Math.abs(drive)) drive = geometricDrive;
        }
        // Do not redistribute slack ropes under a synthetic load. Native physics handles their fall.
        if (Math.min(headTautness, tailTautness) >= settings.tautRatio() && gravitySlope != 0)
            gravitySpeed = clamp(gravitySpeed * settings.gravityRetention()
                + gravitySlope * settings.gravityAcceleration() / 20, -settings.maxSpeed(), settings.maxSpeed());
        else gravitySpeed = 0;
        if (drive == 0 && gravitySpeed == 0) {
            speed = 0;
            return;
        }
        // Rest-length transfer must follow demand immediately; velocity smoothing can retract
        // the newly loaded side for another tick after the sliding direction reverses.
        speed = clamp(drive * settings.responseGain() + gravitySpeed, -settings.maxSpeed(), settings.maxSpeed());
        double previous = tail;
        tail = clamp(tail - speed / 20, MIN_SIDE, total - MIN_SIDE);
        if (tail == previous) {
            speed = 0;
            gravitySpeed = 0;
        }
    }

    static int points(double length) { return (int) Math.ceil(length) + 1; }
    static double firstSegment(double length) { return length - (points(length) - 2); }

    private static double deadZone(double value, double threshold) {
        return Math.copySign(Math.max(0, Math.abs(value) - threshold), value);
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
