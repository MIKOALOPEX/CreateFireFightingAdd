package com.mikoalopex.createfirefightingadd.client.notice;

import java.util.List;
import java.util.Optional;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Decides whether the one-time notice applies to this client.
 *
 * <p>The notice covers an original combined Firefighting Add release
 * (0.2.9-beta or earlier) that is installed together with a split release
 * (0.3.0-release or later) of either split project. It is an extra save
 * compatibility warning, not a NeoForge loader version check.</p>
 *
 * <p>The original Firefighting Add mod id is {@code createfirefightingadd},
 * which is also the current split firefighting mod id. NeoForge rejects two
 * jars with the same mod id before mod construction, so a real ModList can
 * contain the original firefighting jar only when the current split
 * firefighting jar is absent. The exact rule is still expressed over an
 * installed mod list so it is testable; the runtime adapter passes the real
 * ModList and therefore can observe the original firefighting jar only next to
 * the current powertrain jar.</p>
 */
public final class LegacyNoticePolicy {
    /** Original combined and current split firefighting mod id. */
    static final String FIREFIGHTING_MOD_ID = "createfirefightingadd";
    /** Current split powertrain mod id. */
    static final String POWERTRAIN_MOD_ID = "createpowertrain";
    /** Inclusive upper bound of the original combined firefighting generation. */
    static final String ORIGINAL_FIRE_LAST_VERSION = "0.2.9-beta";
    /** Inclusive lower bound of the split generation. */
    static final String SPLIT_FIRST_VERSION = "0.3.0-release";
    /**
     * Shared client-side marker. Both projects use the same file so the same
     * generation warning is stored at most once per client.
     */
    static final String STATE_FILE_NAME = "create-split-legacy-notice.json";

    /** One installed mod record, used by the pure policy and its tests. */
    public record ModRecord(String modId, String version) {
    }

    /** The original firefighting release that triggered the notice. */
    public record Detected(String modId, String version) {
    }

    /**
     * Applies the exact notice rule to an installed mod list.
     *
     * <p>The rule is: an original firefighting release at or below
     * {@link #ORIGINAL_FIRE_LAST_VERSION}, and a current split firefighting or
     * powertrain release at or above {@link #SPLIT_FIRST_VERSION}. An older
     * powertrain release is not a risk source and never satisfies the rule on
     * its own.</p>
     */
    public static Optional<Detected> detect(List<ModRecord> installed) {
        Optional<ModRecord> originalFire = installed.stream()
            .filter(mod -> FIREFIGHTING_MOD_ID.equals(mod.modId()))
            .filter(mod -> atMost(mod.version(), ORIGINAL_FIRE_LAST_VERSION))
            .findFirst();
        if (originalFire.isEmpty()) {
            return Optional.empty();
        }

        boolean currentFire = installed.stream()
            .anyMatch(mod -> FIREFIGHTING_MOD_ID.equals(mod.modId())
                && atLeast(mod.version(), SPLIT_FIRST_VERSION));
        boolean currentPowertrain = installed.stream()
            .anyMatch(mod -> POWERTRAIN_MOD_ID.equals(mod.modId())
                && atLeast(mod.version(), SPLIT_FIRST_VERSION));
        if (!currentFire && !currentPowertrain) {
            return Optional.empty();
        }

        ModRecord detected = originalFire.get();
        return Optional.of(new Detected(detected.modId(), detected.version()));
    }

    /** Returns true when the version is at or below the inclusive maximum. */
    static boolean atMost(String version, String maximum) {
        if (version == null || version.isBlank()) {
            return false;
        }
        try {
            return new ComparableVersion(version).compareTo(new ComparableVersion(maximum)) <= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Returns true when the version is at or above the inclusive minimum. */
    static boolean atLeast(String version, String minimum) {
        if (version == null || version.isBlank()) {
            return false;
        }
        try {
            return new ComparableVersion(version).compareTo(new ComparableVersion(minimum)) >= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Pure gate for the screen opening hook.
     *
     * @param plainTitleScreen true only for a plain vanilla title screen, so a
     *                         page already opened by another mod is never replaced
     * @param acknowledged     true after the shared client marker was stored
     * @param shownThisSession true after this client already replaced a title
     *                         screen during the current session
     * @param detected         the original firefighting release, if any
     */
    public static boolean shouldOpen(boolean plainTitleScreen, boolean acknowledged, boolean shownThisSession,
                                     Optional<Detected> detected) {
        return plainTitleScreen && !acknowledged && !shownThisSession && detected.isPresent();
    }

    private LegacyNoticePolicy() {
    }
}
