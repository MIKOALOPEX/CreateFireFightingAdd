package com.mikoalopex.createfirefightingadd.client.notice;

import java.nio.file.Path;
import java.util.Optional;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Client-only adapter between the notice policy and the running game.
 *
 * <p>The state file is resolved from the client config directory once. No
 * server config, world data, or save is read.</p>
 */
final class LegacyNoticeClientState {
    private static LegacyNoticeState state;

    static synchronized LegacyNoticeState state() {
        if (state == null) {
            Path file = FMLPaths.CONFIGDIR.get().resolve(LegacyNoticePolicy.STATE_FILE_NAME);
            LegacyNoticeState loaded = new LegacyNoticeState(file);
            if (!loaded.load()) {
                CreateFireFightingAdd.LOGGER.warn(
                    "Could not read the legacy notice state from {}; the notice will be shown again.", file);
            }
            state = loaded;
        }
        return state;
    }

    static boolean acknowledged() {
        return state().isAcknowledged();
    }

    static void acknowledge() {
        LegacyNoticeState current = state();
        if (!current.acknowledge()) {
            CreateFireFightingAdd.LOGGER.warn(
                "Could not save the legacy notice state to {}; the notice will be shown again on the next launch.",
                current.file());
        }
    }

    static Optional<LegacyNoticePolicy.Detected> detect() {
        return LegacyNoticePolicy.detect(LegacyNoticeClientState::versionOf);
    }

    private static String versionOf(String modId) {
        var modList = ModList.get();
        if (modList == null) {
            return null;
        }
        return modList.getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse(null);
    }

    private LegacyNoticeClientState() {
    }
}
