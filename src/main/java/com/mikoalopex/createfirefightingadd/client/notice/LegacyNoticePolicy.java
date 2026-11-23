package com.mikoalopex.createfirefightingadd.client.notice;

import java.util.Optional;
import java.util.function.Function;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Decides whether the one-time legacy coexistence notice applies to this client.
 *
 * <p>The notice is only about running a split release next to an older release
 * of the other project. Two jars that share one mod id are rejected by NeoForge
 * before mod construction, so this policy never inspects or works around the
 * loader. It also never reads worlds or save data.</p>
 */
public final class LegacyNoticePolicy {
    /** First split release. Older versions belong to the previous combined generation. */
    static final String SPLIT_BASELINE = "0.3.0";
    /** The mod id of the other project in the split. */
    static final String OTHER_PROJECT_MOD_ID = "createpowertrain";
    /**
     * Shared client-side marker. Both projects use the same file so the same
     * generation warning is stored at most once per client.
     */
    static final String STATE_FILE_NAME = "create-split-legacy-notice.json";

    /** The older mod that was detected next to this release. */
    public record Detected(String modId, String version) {
    }

    /**
     * Reads the other project's version without touching any world data.
     *
     * @param versionOf returns the installed version string for a mod id, or null
     */
    public static Optional<Detected> detect(Function<String, String> versionOf) {
        String version = versionOf.apply(OTHER_PROJECT_MOD_ID);
        if (!isLegacy(version)) {
            return Optional.empty();
        }
        return Optional.of(new Detected(OTHER_PROJECT_MOD_ID, version));
    }

    /** Returns true for versions older than the first split release. */
    public static boolean isLegacy(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        try {
            return new ComparableVersion(version).compareTo(new ComparableVersion(SPLIT_BASELINE)) < 0;
        } catch (RuntimeException e) {
            // Unknown version syntax should not open a notice or break the menu.
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
     * @param detected         the other project's older version, if any
     */
    public static boolean shouldOpen(boolean plainTitleScreen, boolean acknowledged, boolean shownThisSession,
                                     Optional<Detected> detected) {
        return plainTitleScreen && !acknowledged && !shownThisSession && detected.isPresent();
    }

    private LegacyNoticePolicy() {
    }
}
