package com.mikoalopex.createfirefightingadd.client.notice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Client-only adapter between the notice policy and the running game.
 *
 * <p>The adapter builds an installed-mod list from the real {@link ModList}.
 * The shared state file is re-read before every gate decision, so an
 * acknowledgement written by the other split project is visible in the same
 * process instead of remaining in an independent static cache.</p>
 */
final class LegacyNoticeClientState {
    private static LegacyNoticeState state;
    private static boolean reportedReadFailure;

    static synchronized LegacyNoticeState state() {
        if (state == null) {
            state = new LegacyNoticeState(
                FMLPaths.CONFIGDIR.get().resolve(LegacyNoticePolicy.STATE_FILE_NAME));
        }
        return state;
    }

    static synchronized boolean acknowledged() {
        LegacyNoticeState current = state();
        if (!current.load() && !reportedReadFailure) {
            reportedReadFailure = true;
            CreateFireFightingAdd.LOGGER.warn(
                "Could not read the legacy notice state from {}; the notice will be shown again.", current.file());
        }
        return current.isAcknowledged();
    }

    static synchronized void acknowledge() {
        LegacyNoticeState current = state();
        if (!current.acknowledge()) {
            CreateFireFightingAdd.LOGGER.warn(
                "Could not save the legacy notice state to {}; the notice will be shown again on the next launch.",
                current.file());
        }
    }

    static Optional<LegacyNoticePolicy.Detected> detect() {
        var modList = ModList.get();
        if (modList == null) {
            return Optional.empty();
        }
        List<LegacyNoticePolicy.ModRecord> installed = new ArrayList<>(2);
        addRecord(modList, installed, LegacyNoticePolicy.FIREFIGHTING_MOD_ID);
        addRecord(modList, installed, LegacyNoticePolicy.POWERTRAIN_MOD_ID);
        return LegacyNoticePolicy.detect(installed);
    }

    private static void addRecord(ModList modList, List<LegacyNoticePolicy.ModRecord> installed, String modId) {
        modList.getModContainerById(modId).ifPresent(container ->
            installed.add(new LegacyNoticePolicy.ModRecord(modId, container.getModInfo().getVersion().toString())));
    }

    private LegacyNoticeClientState() {
    }
}
