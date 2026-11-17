package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side toolbar state. The selected mode is mirrored through
 * {@link HoseWrenchState} so Create: Powertrain can share it without a
 * compile-time dependency.
 */
public final class HoseWrenchClientState {
    private static HoseWrenchMode mode = HoseWrenchMode.ROTATE;
    private static boolean visible;
    private static BlockHitResult hit;
    private static float openness;
    private static boolean synced;
    private static int lastSyncedMode = -1;
    private static HoseWrenchMode pendingServerMode;

    private HoseWrenchClientState() {
    }

    public static void show(BlockHitResult target) {
        hit = target;
        visible = true;
    }

    public static void hide() {
        visible = false;
    }

    public static void tickVisual() {
        float target = visible ? 1.0f : 0.0f;
        openness += (target - openness) * 0.35f;
        if (openness < 0.01f) {
            openness = 0.0f;
        }
    }

    /** Reads the shared NBT value so a mode chosen in the other mod is honored. */
    public static void refreshMode(LocalPlayer player) {
        if (pendingServerMode != null) {
            HoseWrenchState.set(player, pendingServerMode);
            pendingServerMode = null;
        }
        HoseWrenchMode stored = HoseWrenchState.get(player);
        if (stored != mode) {
            mode = stored;
        }
    }

    public static void ensureSynced() {
        if (!visible) {
            return;
        }
        if (synced && lastSyncedMode == mode.ordinal()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new HoseWrenchModeC2SPacket(mode.ordinal()));
        synced = true;
        lastSyncedMode = mode.ordinal();
    }

    /** Pushes the selected mode immediately, before a wrench use packet. */
    public static void syncNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new HoseWrenchModeC2SPacket(mode.ordinal()));
        synced = true;
        lastSyncedMode = mode.ordinal();
    }

    public static void cycle(int direction) {
        mode = direction > 0 ? mode.next() : mode.previous();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            HoseWrenchState.set(player, mode);
        }
        synced = false;
        lastSyncedMode = -1;
        ensureSynced();
    }

    public static void acceptServerMode(int modeId) {
        mode = HoseWrenchMode.byId(modeId);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            HoseWrenchState.set(player, mode);
            pendingServerMode = null;
        } else {
            pendingServerMode = mode;
        }
        synced = true;
        lastSyncedMode = mode.ordinal();
    }

    public static void reset() {
        mode = HoseWrenchMode.ROTATE;
        visible = false;
        hit = null;
        openness = 0.0f;
        synced = false;
        lastSyncedMode = -1;
        pendingServerMode = null;
    }

    public static HoseWrenchMode mode() {
        return mode;
    }

    public static boolean visible() {
        return visible;
    }

    public static BlockHitResult hit() {
        return hit;
    }

    public static float openness() {
        return openness;
    }
}
