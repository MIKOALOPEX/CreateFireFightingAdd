package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.util.Random;
import com.mikoalopex.createfirefightingadd.api.rope.RopeHookMode;

/** Standalone numerical checks; no Minecraft bootstrap or native physics library is required. */
public final class RopeSlideStateTest {
    private static int checks;

    public static void main(String[] args) {
        RopeSlideState tailhook = new RopeSlideState(20, 10);
        tailhook.setMode(RopeHookMode.ARRESTING);
        tailhook.step(11, 10);
        check(tailhook.tail() < 10, "Tailhook mode must transfer rope length like sliding mode");
        near(tailhook.head() + tailhook.tail(), 20);
        RopeSlideState switchable = new RopeSlideState(20, 10);
        switchable.step(10, 10, 10, 10, 0.5);
        double lockedTail = switchable.tail();
        switchable.setMode(RopeHookMode.LOCKED);
        for (int i = 0; i < 100; i++) switchable.step(30, 10, 20, 10, 1);
        near(switchable.tail(), lockedTail);
        switchable.setMode(RopeHookMode.SLIDING);
        switchable.step(switchable.head(), switchable.tail(), switchable.head(), switchable.tail(), 0);
        near(switchable.tail(), lockedTail);
        switchable.step(switchable.head() * 1.1, switchable.tail());
        check(switchable.tail() < lockedTail, "Unlock must resume transfer without replacing the length ledger");
        near(switchable.head() + switchable.tail(), 20);
        RopeSlideState reconfigured = new RopeSlideState(20, 10);
        reconfigured.configure(new RopeSlideSettings(0, 0, 100, 0, 7, 0.8, 0.97, 10, 30, 0.9, 0.5));
        reconfigured.step(11, 10, 10, 10, 0.5);
        near(reconfigured.tail(), 10);
        reconfigured.configure(RopeSlideSettings.DEFAULT);
        reconfigured.step(11, 10, 10, 10, 0.5);
        check(reconfigured.tail() < 10, "Reloaded settings must affect an existing sliding state");
        RopeSlideState gravity = new RopeSlideState(20, 10);
        gravity.step(10, 10, 10, 10, 0.5);
        check(gravity.tail() < 10, "Equal tautness must still allow downhill gravity travel");
        RopeSlideState reverseGravity = new RopeSlideState(20, 10);
        reverseGravity.step(10, 10, 10, 10, -0.5);
        check(reverseGravity.tail() > 10, "Gravity direction must follow the slope");
        RopeSlideState slackGravity = new RopeSlideState(20, 10);
        slackGravity.step(10, 10, 5, 5, 0.5);
        near(slackGravity.tail(), 10);
        RopeSlideState response = new RopeSlideState(10, 5);
        response.step(5.2, 5);
        near(response.tail(), 5 - (0.04 - 0.01) * 100 / 20);
        RopeSlideState capped = new RopeSlideState(10, 5);
        capped.step(10, 5);
        near(capped.tail(), 5 - 5.0 / 20);
        double beforeReversal = capped.tail();
        capped.step(capped.head(), capped.tail() * 1.02);
        near(capped.tail(), beforeReversal + (0.02 - 0.01) * 100 / 20);
        check(capped.tail() > beforeReversal, "Reversal must release the newly loaded side immediately");
        RopeSlideState smallPull = new RopeSlideState(10, 5);
        smallPull.step(5.1, 5);
        check(smallPull.tail() < 5, "Small stretch differences must overcome the reduced threshold");
        RopeSlideState smallGeometry = new RopeSlideState(10, 5);
        smallGeometry.step(5, 5, 5, 3.9);
        check(smallGeometry.tail() < 5, "Small geometric differences must overcome the reduced threshold");
        RopeSlideState projected = new RopeSlideState(10, 5);
        projected.step(5, 5, 5, 3);
        check(projected.tail() < 5, "Zero-stretch solver output must allow taut-side slack transfer");
        double afterTransfer = projected.tail();
        for (int i = 0; i < 40; i++)
            projected.step(projected.head(), projected.tail(), projected.head() * 0.6, projected.tail());
        check(projected.tail() > afterTransfer, "Geometric transfer must work in both directions");
        RopeSlideState slack = new RopeSlideState(10, 4);
        slack.step(6, 4, 3, 3);
        near(slack.tail(), 4);
        slack.step(6, 4, 6, 4);
        near(slack.tail(), 4);
        RopeSlideState state = new RopeSlideState(12.3, 5);
        state.step(6, 4);
        near(state.tail(), 5);
        state.step(state.head() * 1.0005, state.tail());
        near(state.tail(), 5);
        state.step(state.head() * 1.2, state.tail());
        check(state.tail() < 5, "Head tension must receive length from the tail");
        double afterHeadPull = state.tail();
        for (int i = 0; i < 40; i++) state.step(state.head(), state.tail() * 1.2);
        check(state.tail() > afterHeadPull, "Tail tension must reverse the transfer");

        Random random = new Random(74219);
        double total = 12.3;
        for (int i = 0; i < 20000; i++) {
            if (i % 7 == 0) {
                total = 0.5 + random.nextDouble() * 100;
                state.setTotal(total);
            }
            double before = state.tail();
            state.step(state.head() * (0.9 + random.nextDouble() * 0.3),
                state.tail() * (0.9 + random.nextDouble() * 0.3));
            near(state.head() + state.tail(), total);
            check(state.head() >= 0.25 - 1e-10 && state.tail() >= 0.25 - 1e-10, "Minimum side length");
            check(Math.abs(state.tail() - before) <= 0.25 + 1e-10, "Maximum transfer per tick");
            discretization(state.head());
            discretization(state.tail());
        }
        for (double length : new double[]{0.25, 0.99999999, 1, 1.00000001, 2, 2.00000001, 1024})
            discretization(length);
        state.setTotal(0.5);
        for (int i = 0; i < 100; i++) state.step(10, 0.25);
        near(state.head(), 0.25);
        near(state.tail(), 0.25);
        double tail = state.tail();
        state.step(Double.NaN, 0.25);
        near(state.tail(), tail);
        rejects(() -> new RopeSlideState(0.49, 0.25));
        rejects(() -> new RopeSlideState(Double.NaN, 1));
        rejects(() -> new RopeSlideState(4, Double.NaN));
        System.out.println("RopeSlideState: " + checks + " checks passed");
    }

    private static void discretization(double length) {
        int points = RopeSlideState.points(length);
        double fraction = RopeSlideState.firstSegment(length);
        check(points >= 2 && fraction > 0 && fraction <= 1, "Valid native first segment");
        near(points - 2 + fraction, length);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Invalid lengths must be rejected");
    }

    private static void near(double a, double b) { check(Math.abs(a - b) < 1e-9, a + " != " + b); }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
