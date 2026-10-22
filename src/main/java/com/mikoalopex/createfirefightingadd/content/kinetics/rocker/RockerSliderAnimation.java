package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

/** Local slider travel with short acceleration ramps and a constant-speed middle. */
public final class RockerSliderAnimation {
    private float start, target;
    private int elapsed, duration;
    private boolean initialized;

    public void tick(float next) {
        if (!initialized) {
            start = target = next;
            initialized = true;
            return;
        }
        if (next != target) {
            start = value(1);
            target = next;
            duration = Math.max(4, Math.round(3 + Math.abs(target - start) * 0.8f));
            elapsed = 1;
        } else if (elapsed <= duration) {
            elapsed++;
        }
    }

    public float value(float partial) {
        if (duration == 0) return target;
        float t = Math.clamp((elapsed - 1 + partial) / duration, 0, 1);
        return start + (target - start) * progress(t);
    }

    static float progress(float t) {
        float ramp = 0.2f;
        if (t < ramp) return t * t / (2 * ramp * (1 - ramp));
        if (t > 1 - ramp) return 1 - (1 - t) * (1 - t) / (2 * ramp * (1 - ramp));
        return (t - ramp / 2) / (1 - ramp);
    }
}
