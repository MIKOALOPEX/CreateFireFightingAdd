package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.equipment.wrench.WrenchItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client detection, ALT + wheel switching, and prediction suppression for the
 * thickness action.
 *
 * <p>The client cancels only the prediction of
 * {@link PlayerInteractEvent.RightClickBlock}. NeoForge still sends the native
 * {@code ServerboundUseItemOnPacket} after a canceled prediction, so the server
 * handler receives the real hit vector and performs the single
 * {@code cycleSize}. Rotation is never canceled here: it keeps the ordinary
 * Create wrench path.</p>
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID, value = Dist.CLIENT)
public final class HoseWrenchClient {
    private HoseWrenchClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null
            || player.isSpectator() || player.isShiftKeyDown()
            || wrenchHand(player) == null) {
            HoseWrenchClientState.hide();
            HoseWrenchClientState.tickVisual();
            return;
        }

        BlockHitResult hit = validHit(minecraft);
        if (hit == null) {
            HoseWrenchClientState.hide();
            HoseWrenchClientState.tickVisual();
            return;
        }

        HoseWrenchClientState.show(hit);
        HoseWrenchClientState.refreshMode(player);
        HoseWrenchClientState.ensureSynced();
        HoseWrenchClientState.tickVisual();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!HoseWrenchClientState.visible() || !Screen.hasAltDown()) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0d) {
            return;
        }
        HoseWrenchClientState.cycle(delta > 0.0d ? 1 : -1);
        event.setCanceled(true);
    }

    /**
     * Push the current mode before {@code startUseItem} sends the vanilla use
     * packet. This handler never cancels the use.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemModeSync(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.isCanceled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
            || player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) {
            return;
        }
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof WrenchItem)) {
            return;
        }
        if (validHit(minecraft) == null) {
            return;
        }
        HoseWrenchClientState.refreshMode(player);
        HoseWrenchClientState.syncNow();
    }

    /**
     * Cancel only the client prediction for SIZE. The vanilla use packet is
     * still sent by {@code MultiPlayerGameMode#startPrediction}, which is what
     * gives the server the authentic hit vector.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        if (player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof WrenchItem)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        BlockHitResult trackedHit = HoseWrenchClientState.hit();
        boolean trackedTarget = HoseWrenchClientState.visible() && trackedHit != null
            && trackedHit.getBlockPos().equals(event.getPos());
        if (!HoseWrenchTargets.isHosePort(state) && !trackedTarget) {
            return;
        }

        // The mode packet was already queued by onUseItemModeSync before this
        // prediction runs. ensureSynced is a no-op fallback in the normal case.
        HoseWrenchClientState.refreshMode(player);
        HoseWrenchClientState.ensureSynced();

        if (HoseWrenchClientState.mode() != HoseWrenchMode.SIZE) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        HoseWrenchClientState.reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HoseWrenchClientState.reset();
    }

    private static InteractionHand wrenchHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static BlockHitResult validHit(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK
            || minecraft.level == null) {
            return null;
        }
        if (!HoseWrenchTargets.isHosePort(minecraft.level.getBlockState(hit.getBlockPos()))) {
            return null;
        }
        return hit;
    }
}
