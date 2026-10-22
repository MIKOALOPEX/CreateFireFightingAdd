package com.mikoalopex.createfirefightingadd.compatibility;

/** Capability result with a reason for missing or incompatible dependencies. */
public record CapabilityStatus(State state, String reason) {
    public enum State { AVAILABLE, MISSING, UNSUPPORTED, API_UNAVAILABLE }
    public boolean available() { return state == State.AVAILABLE; }
    public static CapabilityStatus ready() { return new CapabilityStatus(State.AVAILABLE, "ready"); }
}
