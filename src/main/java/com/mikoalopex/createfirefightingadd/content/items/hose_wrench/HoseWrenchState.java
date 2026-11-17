package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player selected wrench mode.
 *
 * <p>The key is deliberately not namespaced. Both mods use the same key and
 * the same enum order, so installing both mods shares the selected mode
 * without either mod linking against the other. With only one mod installed
 * the key is simply private to that mod.</p>
 */
public final class HoseWrenchState {
    /** Shared by Create: Firefighting Additions and Create: Powertrain. */
    public static final String MODE_KEY = "CreateHoseWrenchMode";

    private HoseWrenchState() {
    }

    public static HoseWrenchMode get(Player player) {
        if (player == null) {
            return HoseWrenchMode.ROTATE;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(MODE_KEY)) {
            return HoseWrenchMode.ROTATE;
        }
        return HoseWrenchMode.byId(data.getInt(MODE_KEY));
    }

    public static void set(Player player, HoseWrenchMode mode) {
        if (player == null || mode == null) {
            return;
        }
        player.getPersistentData().putInt(MODE_KEY, mode.ordinal());
    }
}
