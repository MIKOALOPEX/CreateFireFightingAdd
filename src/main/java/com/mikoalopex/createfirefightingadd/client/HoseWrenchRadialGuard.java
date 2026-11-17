package com.mikoalopex.createfirefightingadd.client;

import com.mikoalopex.createfirefightingadd.content.items.hose_wrench.HoseWrenchTargets;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.equipment.wrench.WrenchItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Suppresses Create's radial wrench menu when the rotate-menu key is pressed
 * while targeting a hose port supported by this mod's toolbar.
 */
public final class HoseWrenchRadialGuard {
    private HoseWrenchRadialGuard() {
    }

    /**
     * @return {@code true} when Create's radial wrench menu must not open for
     *         this key event, so the hose wrench ALT wheel keeps the input
     */
    public static boolean shouldSuppressNativeRotationMenu(int key, boolean pressed) {
        if (!pressed) {
            return false;
        }
        // Only the gesture that would open the native wheel; create's handler
        // re-checks this after us, but matching it here keeps the guard narrow.
        if (!AllKeys.ROTATE_MENU.doesModifierAndCodeMatch(key)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null) {
            return false;
        }
        if (minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return false;
        }

        // RadialWrenchHandler only opens for the Create wrench in the main hand.
        if (!(player.getMainHandItem().getItem() instanceof WrenchItem)) {
            return false;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        // Same ownership predicate the hose wrench GUI uses: this mod's
        // namespace and the size-cycling port interface.
        return HoseWrenchTargets.isHosePort(level.getBlockState(hit.getBlockPos()));
    }
}
