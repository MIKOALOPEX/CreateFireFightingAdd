package com.mikoalopex.createfirefightingadd.client.notice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Standalone checks for the legacy coexistence notice. No Minecraft client is
 * started; the checks cover the version condition, the client marker file, and
 * the title-screen coordination rules.
 */
public final class LegacyNoticeTest {
    private static final String FIRE = "createfirefightingadd";
    private static final String POWER = "createpowertrain";
    private static final String CURRENT = "0.3.0-release";
    private static final String OLD_FIRE = "0.2.9-beta";
    private static final String OLD_POWER = "0.2.8-beta";

    public static void main(String[] args) throws IOException {
        policy();
        state();
        coexistence();
        System.out.println("Legacy notice policy, persistence and screen coexistence checks passed");
    }

    private static void policy() {
        check(LegacyNoticePolicy.isLegacy(OLD_FIRE), "0.2.9-beta must be legacy");
        check(LegacyNoticePolicy.isLegacy(OLD_POWER), "0.2.8-beta must be legacy");
        check(LegacyNoticePolicy.isLegacy("0.2.1-beta"), "0.2.1-beta must be legacy");
        check(LegacyNoticePolicy.isLegacy("0.2"), "0.2 must be legacy");
        check(!LegacyNoticePolicy.isLegacy(CURRENT), "0.3.0-release must not be legacy");
        check(!LegacyNoticePolicy.isLegacy("0.3.0"), "0.3.0 must not be legacy");
        check(!LegacyNoticePolicy.isLegacy("0.3.1"), "0.3.1 must not be legacy");
        check(!LegacyNoticePolicy.isLegacy("0.10.0"), "0.10.0 must not be legacy");
        check(!LegacyNoticePolicy.isLegacy(null), "missing version must not be legacy");
        check(!LegacyNoticePolicy.isLegacy(""), "empty version must not be legacy");

        Optional<LegacyNoticePolicy.Detected> detected =
            LegacyNoticePolicy.detect(id -> id.equals(POWER) ? OLD_POWER : CURRENT);
        check(detected.isPresent(), "old powertrain must be detected");
        check(detected.get().modId().equals(POWER), "detected id must be createpowertrain");
        check(detected.get().version().equals(OLD_POWER), "detected version must be preserved");
        check(LegacyNoticePolicy.detect(id -> null).isEmpty(), "absent other mod must not be detected");
        check(LegacyNoticePolicy.detect(id -> CURRENT).isEmpty(), "current other version must not be detected");

        check(LegacyNoticePolicy.shouldOpen(true, false, false, detected), "plain title with fresh client");
        check(!LegacyNoticePolicy.shouldOpen(false, false, false, detected),
            "another mod screen must not be replaced");
        check(!LegacyNoticePolicy.shouldOpen(true, true, false, detected), "acknowledged client must be suppressed");
        check(!LegacyNoticePolicy.shouldOpen(true, false, true, detected),
            "a second title opening in one session must be suppressed");
        check(!LegacyNoticePolicy.shouldOpen(true, false, false, Optional.empty()),
            "no legacy mod means no notice");
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

    private static void coexistence() throws IOException {
        // Model both split projects on one client. A loadable installation must
        // never open two notices, and every loadable old/new mix must open one.
        List<Mod> candidates = List.of(
            new Mod(FIRE, OLD_FIRE),
            new Mod(FIRE, CURRENT),
            new Mod(POWER, OLD_POWER),
            new Mod(POWER, CURRENT));
        int loadableCount = 0;
        for (int mask = 0; mask < (1 << candidates.size()); mask++) {
            List<Mod> installed = new ArrayList<>();
            for (int bit = 0; bit < candidates.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    installed.add(candidates.get(bit));
                }
            }
            boolean loadable = isLoadable(installed);
            boolean expectedFire = hasCurrent(installed, FIRE) && hasOld(installed, POWER);
            boolean expectedPower = hasCurrent(installed, POWER) && hasOld(installed, FIRE);
            check(fireNotice(installed) == expectedFire, "fire trigger must follow the old powertrain rule");
            check(powerNotice(installed) == expectedPower, "power trigger must follow the old fire rule");
            if (loadable) {
                loadableCount++;
                int triggers = (expectedFire ? 1 : 0) + (expectedPower ? 1 : 0);
                check(triggers <= 1, "a loadable installation must not open two notices: " + describe(installed));
            }
        }
        check(loadableCount == 9, "duplicate mod ids leave nine loadable subsets");

        List<Mod> newFireOldPower = List.of(new Mod(FIRE, CURRENT), new Mod(POWER, OLD_POWER));
        check(isLoadable(newFireOldPower) && fireNotice(newFireOldPower) && !powerNotice(newFireOldPower),
            "new fire with old powertrain must open only the fire notice");

        List<Mod> oldFireNewPower = List.of(new Mod(FIRE, OLD_FIRE), new Mod(POWER, CURRENT));
        check(isLoadable(oldFireNewPower) && powerNotice(oldFireNewPower) && !fireNotice(oldFireNewPower),
            "old fire with new powertrain must open only the power notice");

        List<Mod> currentOnly = List.of(new Mod(FIRE, CURRENT), new Mod(POWER, CURRENT));
        check(isLoadable(currentOnly) && !fireNotice(currentOnly) && !powerNotice(currentOnly),
            "the current pair must not open a notice");

        List<Mod> oldOnly = List.of(new Mod(FIRE, OLD_FIRE), new Mod(POWER, OLD_POWER));
        check(isLoadable(oldOnly) && !fireNotice(oldOnly) && !powerNotice(oldOnly),
            "an old-only installation runs no split notice");

        check(!isLoadable(List.of(new Mod(FIRE, OLD_FIRE), new Mod(FIRE, CURRENT))),
            "two firefighting jars are rejected by the loader");
        check(!isLoadable(List.of(new Mod(POWER, OLD_POWER), new Mod(POWER, CURRENT))),
            "two powertrain jars are rejected by the loader");
        check(!isLoadable(List.of(new Mod(FIRE, OLD_FIRE), new Mod(FIRE, CURRENT),
            new Mod(POWER, CURRENT))), "a duplicate firefighting jar is rejected even with the current pair");
        check(!isLoadable(List.of(new Mod(FIRE, CURRENT), new Mod(POWER, OLD_POWER),
            new Mod(POWER, CURRENT))), "a duplicate powertrain jar is rejected even with the current pair");

        // Both projects register the same opening hook. Only the first can
        // replace one plain title screen; the second must see a replaced screen.
        Optional<LegacyNoticePolicy.Detected> thisProject =
            LegacyNoticePolicy.detect(id -> id.equals(POWER) ? OLD_POWER : CURRENT);
        LegacyNoticePolicy.Detected otherProject =
            new LegacyNoticePolicy.Detected(FIRE, OLD_FIRE);
        check(thisProject.isPresent(), "the fire project must detect the old powertrain");

        boolean plainTitle = true;
        int opened = 0;
        if (LegacyNoticePolicy.shouldOpen(plainTitle, false, false, thisProject)) {
            opened++;
            plainTitle = false;
        }
        if (LegacyNoticePolicy.shouldOpen(plainTitle, false, false, Optional.of(otherProject))) {
            opened++;
        }
        check(opened == 1, "only one notice may replace a single title screen");
        check(!plainTitle, "the second project must see the replaced screen");

        // The shared marker is written by whichever project showed the notice,
        // so the other project stays quiet on later launches.
        Path directory = Files.createTempDirectory("cffa-legacy-shared-");
        try {
            LegacyNoticeState shared = new LegacyNoticeState(
                directory.resolve(LegacyNoticePolicy.STATE_FILE_NAME));
            check(LegacyNoticePolicy.shouldOpen(true, shared.isAcknowledged(), false, thisProject),
                "the fresh shared marker must allow the first notice");
            check(shared.acknowledge(), "the shared marker must be written");
            check(!LegacyNoticePolicy.shouldOpen(true, shared.isAcknowledged(), false, Optional.of(otherProject)),
                "the shared marker must suppress the other project's notice");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static boolean fireNotice(List<Mod> mods) {
        return hasCurrent(mods, FIRE) && hasOld(mods, POWER);
    }

    private static boolean powerNotice(List<Mod> mods) {
        return hasCurrent(mods, POWER) && hasOld(mods, FIRE);
    }

    private static boolean hasCurrent(List<Mod> mods, String id) {
        String version = versionOf(mods, id);
        return version != null && !LegacyNoticePolicy.isLegacy(version);
    }

    private static boolean hasOld(List<Mod> mods, String id) {
        return LegacyNoticePolicy.isLegacy(versionOf(mods, id));
    }

    private static String versionOf(List<Mod> mods, String id) {
        for (Mod mod : mods) {
            if (mod.id().equals(id)) {
                return mod.version();
            }
        }
        return null;
    }

    private static boolean isLoadable(List<Mod> mods) {
        Set<String> ids = new HashSet<>();
        for (Mod mod : mods) {
            if (!ids.add(mod.id())) {
                return false;
            }
        }
        return true;
    }

    private static String describe(List<Mod> mods) {
        return mods.stream().map(mod -> mod.id() + "@" + mod.version()).toList().toString();
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

    private record Mod(String id, String version) {
    }

    private LegacyNoticeTest() {
    }
}
