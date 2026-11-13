package com.mikoalopex.createfirefightingadd.transition;

public final class CleanupRequestTest {
    public static void main(String[] args) {
        var timeout = new CleanupRequest(0);
        timeout.exitStarted(CleanupRequest.WINDOW_NANOS);
        timeout.storageClosed();
        require(!timeout.consume(), "late exit must never authorize IO");
        var slowSave = new CleanupRequest(0);
        slowSave.exitStarted(CleanupRequest.WINDOW_NANOS - 1);
        require(!slowSave.consume(), "exit begun is not storage closed");
        require(!slowSave.expire(CleanupRequest.WINDOW_NANOS * 10), "normal save may take longer");
        slowSave.storageClosed();
        require(slowSave.consume(), "timely exit and closed storage authorize one job");
        require(!slowSave.consume(), "no duplicate job");
        var noExit = new CleanupRequest(0);
        noExit.storageClosed();
        require(!noExit.consume(), "no stopping event means no cleanup");
        var wrap = new CleanupRequest(Long.MAX_VALUE - 5);
        require(!wrap.expire(Long.MIN_VALUE + 5), "monotonic clock wrap");
        System.out.println("Cleanup request deadline checks passed");
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
