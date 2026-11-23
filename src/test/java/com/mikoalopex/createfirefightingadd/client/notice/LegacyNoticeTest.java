package com.mikoalopex.createfirefightingadd.client.notice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Standalone checks for the original Firefighting Add compatibility notice. No
 * Minecraft client is started; the checks cover the version boundaries, the
 * exact installed-mod rule, the client marker, and the same-process shared
 * marker.
 */
public final class LegacyNoticeTest {
    private static final String FIRE = "createfirefightingadd";
    private static final String POWER = "createpowertrain";
    private static final String OLD_FIRE = "0.2.9-beta";
    private static final String OLD_FIRE_208 = "0.2.8-beta";
    private static final String OLD_POWER = "0.2.8-beta";
    private static final String NEW_FIRE = "0.3.0-release";
    private static final String NEW_POWER = "0.3.0-release";

    public static void main(String[] args) throws IOException {
        boundaries();
        rule();
        installedListShape();
        gate();
        state();
        sharedMarker();
        System.out.println("Legacy notice boundary, rule, persistence and shared-marker checks passed");
    }

    private static void boundaries() {
        check(LegacyNoticePolicy.atMost(OLD_FIRE, OLD_FIRE), "0.2.9-beta is the inclusive old boundary");
        check(LegacyNoticePolicy.atMost(OLD_FIRE_208, OLD_FIRE), "0.2.8-beta is below the old boundary");
        check(LegacyNoticePolicy.atMost("0.2.1-beta", OLD_FIRE), "0.2.1-beta is below the old boundary");
        check(LegacyNoticePolicy.atMost("0.2", OLD_FIRE), "0.2 is below the old boundary");
        check(!LegacyNoticePolicy.atMost("0.2.9", OLD_FIRE), "0.2.9 official is above 0.2.9-beta");
        check(!LegacyNoticePolicy.atMost("0.2.10-beta", OLD_FIRE), "0.2.10-beta is above 0.2.9-beta");
        check(!LegacyNoticePolicy.atMost(null, OLD_FIRE), "a missing old version is not old");
        check(!LegacyNoticePolicy.atMost("", OLD_FIRE), "an empty old version is not old");

        check(LegacyNoticePolicy.atLeast(NEW_FIRE, NEW_FIRE), "0.3.0-release is the inclusive new boundary");
        check(LegacyNoticePolicy.atLeast("0.3.0", NEW_FIRE), "0.3.0 is at the new boundary");
        check(LegacyNoticePolicy.atLeast("0.3.1", NEW_FIRE), "0.3.1 is above the new boundary");
        check(LegacyNoticePolicy.atLeast("1.0.0", NEW_FIRE), "1.0.0 is above the new boundary");
        check(!LegacyNoticePolicy.atLeast("0.3.0-beta", NEW_FIRE), "0.3.0-beta is below 0.3.0-release");
        check(!LegacyNoticePolicy.atLeast("0.2.9", NEW_FIRE), "0.2.9 is below the split generation");
        check(!LegacyNoticePolicy.atLeast(null, NEW_FIRE), "a missing new version is not current");
        check(!LegacyNoticePolicy.atLeast("", NEW_FIRE), "an empty new version is not current");
    }

    private static void rule() {
        check(LegacyNoticePolicy.detect(List.of(mod(POWER, OLD_POWER), mod(FIRE, NEW_FIRE))).isEmpty(),
            "old powertrain with new firefighting must not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(POWER, OLD_POWER))).isEmpty(),
            "old powertrain alone must not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, NEW_FIRE), mod(POWER, NEW_POWER))).isEmpty(),
            "no old original firefighting must not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE))).isEmpty(),
            "only an old original firefighting must not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(POWER, OLD_POWER))).isEmpty(),
            "only old versions must not trigger");

        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(FIRE, NEW_FIRE))).isPresent(),
            "old fire with new fire must satisfy the pure rule");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(POWER, NEW_POWER))).isPresent(),
            "old fire with new powertrain must trigger");
        check(LegacyNoticePolicy.detect(List.of(
                mod(FIRE, OLD_FIRE), mod(FIRE, NEW_FIRE), mod(POWER, NEW_POWER))).isPresent(),
            "old fire with both current releases must satisfy the pure rule");
        check(LegacyNoticePolicy.detect(List.of(
                mod(FIRE, OLD_FIRE), mod(FIRE, NEW_FIRE), mod(POWER, OLD_POWER))).isPresent(),
            "old powertrain must not prevent a current fire from triggering");

        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, "0.2.9"), mod(POWER, NEW_POWER))).isEmpty(),
            "0.2.9 official is above the old inclusive boundary");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(FIRE, "0.3.0-beta"))).isEmpty(),
            "0.3.0-beta is below the new inclusive boundary");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(POWER, "0.3.0-beta"))).isEmpty(),
            "a new powertrain below the boundary must not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE_208), mod(POWER, NEW_POWER))).isPresent(),
            "0.2.8-beta is covered by the old boundary");

        Optional<LegacyNoticePolicy.Detected> detected =
            LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(POWER, NEW_POWER)));
        check(detected.isPresent(), "old fire with new powertrain must be detected");
        check(detected.get().modId().equals(FIRE), "detected id must be the original firefighting id");
        check(detected.get().version().equals(OLD_FIRE), "detected version must be the original firefighting version");
    }

    private static void installedListShape() {
        // The real ModList has at most one record per mod id. These are the
        // shapes the runtime adapter can pass to the policy.
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(POWER, NEW_POWER))).isPresent(),
            "real shape: old fire with new powertrain triggers");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, NEW_FIRE), mod(POWER, NEW_POWER))).isEmpty(),
            "real shape: the current pair does not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, NEW_FIRE), mod(POWER, OLD_POWER))).isEmpty(),
            "real shape: old powertrain with new fire does not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE))).isEmpty(),
            "real shape: old fire alone does not trigger");
        check(LegacyNoticePolicy.detect(List.of(mod(POWER, NEW_POWER))).isEmpty(),
            "real shape: new powertrain alone does not trigger");

        // The original and current firefighting jars share one mod id, so the
        // loader rejects that combination. The pure rule still covers it.
        List<LegacyNoticePolicy.ModRecord> sameId = List.of(mod(FIRE, OLD_FIRE), mod(FIRE, NEW_FIRE));
        check(!isLoadable(sameId), "two firefighting jars share one mod id and are rejected by the loader");
        check(LegacyNoticePolicy.detect(sameId).isPresent(), "the pure rule still covers old fire with new fire");
    }

    private static void gate() {
        Optional<LegacyNoticePolicy.Detected> detected =
            LegacyNoticePolicy.detect(List.of(mod(FIRE, OLD_FIRE), mod(POWER, NEW_POWER)));
        check(detected.isPresent(), "gate fixture must be detected");
        check(LegacyNoticePolicy.shouldOpen(true, false, false, detected), "plain title with a fresh client");
        check(!LegacyNoticePolicy.shouldOpen(false, false, false, detected),
            "another mod screen must not be replaced");
        check(!LegacyNoticePolicy.shouldOpen(true, true, false, detected), "acknowledged client must be suppressed");
        check(!LegacyNoticePolicy.shouldOpen(true, false, true, detected),
            "a second title opening in one session must be suppressed");
        check(!LegacyNoticePolicy.shouldOpen(true, false, false, Optional.empty()),
            "no old original firefighting means no notice");
    }

    private static void state() throws IOException {
        Path directory = Files.createTempDirectory("cffa-legacy-notice-");
        try {
            Path file = directory.resolve(LegacyNoticePolicy.STATE_FILE_NAME);
            LegacyNoticeState fresh = new LegacyNoticeState(file);
            check(fresh.load(), "a missing marker is a normal first run");
            check(!fresh.isAcknowledged(), "a missing marker is not acknowledged");

            check(fresh.acknowledge(), "acknowledge must write the marker");
            check(Files.isRegularFile(file), "the marker file must exist");
            LegacyNoticeState reloaded = new LegacyNoticeState(file);
            check(reloaded.load() && reloaded.isAcknowledged(), "the marker must survive a restart");

            Files.writeString(file, "{\"acknowledged\":false}");
            LegacyNoticeState notAcknowledged = new LegacyNoticeState(file);
            check(notAcknowledged.load() && !notAcknowledged.isAcknowledged(),
                "an explicit false marker must be honoured");

            Files.writeString(file, "{\"acknowledged\":\"yes\"}");
            LegacyNoticeState wrongType = new LegacyNoticeState(file);
            check(wrongType.load() && !wrongType.isAcknowledged(),
                "a non-boolean marker must not suppress the notice");

            Files.writeString(file, "{ not json");
            LegacyNoticeState malformed = new LegacyNoticeState(file);
            check(!malformed.load(), "a malformed marker must be reported");
            check(!malformed.isAcknowledged(), "a malformed marker must not suppress the notice");
            check(malformed.acknowledge(), "a malformed marker must be replaceable");
            LegacyNoticeState repaired = new LegacyNoticeState(file);
            check(repaired.load() && repaired.isAcknowledged(), "the repaired marker must reload");

            Path blocked = directory.resolve("blocked.json");
            Files.createDirectories(blocked);
            Files.writeString(blocked.resolve("keep"), "x");
            LegacyNoticeState failed = new LegacyNoticeState(blocked);
            check(!failed.acknowledge(), "a failed write must be reported");
            check(failed.isAcknowledged(), "the notice must stay closed for this session after a failed write");

            Path nested = directory.resolve("nested/a/b/notice.json");
            LegacyNoticeState nestedState = new LegacyNoticeState(nested);
            check(nestedState.acknowledge() && Files.isRegularFile(nested),
                "missing parent directories must be created");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void sharedMarker() throws IOException {
        Path directory = Files.createTempDirectory("cffa-legacy-shared-");
        try {
            Path file = directory.resolve(LegacyNoticePolicy.STATE_FILE_NAME);
            // Two independent state objects model the two mods' separate static
            // caches. The runtime adapter re-reads the shared file before every
            // gate decision, which this test reproduces explicitly.
            LegacyNoticeState projectA = new LegacyNoticeState(file);
            LegacyNoticeState projectB = new LegacyNoticeState(file);
            check(projectA.load() && projectB.load(), "both projects start from a fresh marker");
            check(!projectA.isAcknowledged() && !projectB.isAcknowledged(), "the fresh marker is unacknowledged");

            Optional<LegacyNoticePolicy.Detected> detected = LegacyNoticePolicy.detect(List.of(
                mod(FIRE, OLD_FIRE), mod(FIRE, NEW_FIRE), mod(POWER, NEW_POWER)));
            check(detected.isPresent(), "the shared-marker scenario must satisfy the pure rule");
            check(LegacyNoticePolicy.shouldOpen(true, projectA.isAcknowledged(), false, detected),
                "project A may open before acknowledgement");
            check(LegacyNoticePolicy.shouldOpen(true, projectB.isAcknowledged(), false, detected),
                "project B may open before acknowledgement");

            check(projectA.acknowledge(), "project A writes the shared marker");
            check(projectB.load(), "project B re-reads the shared marker");
            check(projectB.isAcknowledged(), "project B sees project A's acknowledgement in the same process");
            check(!LegacyNoticePolicy.shouldOpen(true, projectB.isAcknowledged(), false, detected),
                "project B must not open after project A acknowledged");

            LegacyNoticeState nextLaunch = new LegacyNoticeState(file);
            check(nextLaunch.load() && nextLaunch.isAcknowledged(), "the shared marker must survive a restart");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static LegacyNoticePolicy.ModRecord mod(String id, String version) {
        return new LegacyNoticePolicy.ModRecord(id, version);
    }

    private static boolean isLoadable(List<LegacyNoticePolicy.ModRecord> mods) {
        Set<String> ids = new HashSet<>();
        for (LegacyNoticePolicy.ModRecord mod : mods) {
            if (!ids.add(mod.modId())) {
                return false;
            }
        }
        return true;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private LegacyNoticeTest() {
    }
}
