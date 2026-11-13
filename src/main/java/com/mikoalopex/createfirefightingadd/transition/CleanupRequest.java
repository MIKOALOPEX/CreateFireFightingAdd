package com.mikoalopex.createfirefightingadd.transition;

/** Memory-only request. No filesystem access is permitted before ready(). */
public final class CleanupRequest {
    public static final long WINDOW_NANOS = 30_000_000_000L;
    public enum State { WAITING, EXIT_STARTED, EXPIRED, READY, CONSUMED }
    private final long requestedAt;
    private State state = State.WAITING;

    public CleanupRequest(long now) { requestedAt = now; }
    public synchronized boolean expire(long now) {
        if (state == State.WAITING && now - requestedAt >= WINDOW_NANOS) {
            state = State.EXPIRED;
            return true;
        }
        return false;
    }
    public synchronized void exitStarted(long now) {
        expire(now);
        if (state == State.WAITING) state = State.EXIT_STARTED;
    }
    public synchronized void storageClosed() {
        if (state == State.EXIT_STARTED) state = State.READY;
    }
    public synchronized boolean consume() {
        if (state != State.READY) return false;
        state = State.CONSUMED;
        return true;
    }
    public synchronized State state() { return state; }
}
