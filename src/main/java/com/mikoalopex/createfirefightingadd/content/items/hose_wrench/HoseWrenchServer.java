package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.content.equipment.wrench.WrenchItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server authority for the hose-port toolbar.
 *
 * <p>Rotation is deliberately absent here. It stays on the ordinary Create
 * wrench path, so {@code onWrenched} runs exactly once and keeps Create's
 * copycat-removal ordering. The thickness action is taken from the native
 * {@link PlayerInteractEvent.RightClickBlock}: the event's own hit vector is
 * passed to {@code cycleSize}, which preserves Sable sub-level coordinates and
 * avoids a second, custom raycast.</p>
 */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class HoseWrenchServer {
    private HoseWrenchServer() {
    }

    public static void onMode(ServerPlayer player, int modeId) {
        if (!HoseWrenchMode.isValid(modeId)) {
            sync(player);
            return;
        }
        HoseWrenchState.set(player, HoseWrenchMode.byId(modeId));
        sync(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.isCanceled() || player.isSpectator() || player.isShiftKeyDown() || !player.mayBuild()) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof WrenchItem)) {
            return;
        }
        if (HoseWrenchState.get(player) != HoseWrenchMode.SIZE) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!HoseWrenchTargets.isHosePort(state)) {
            return;
        }
        if (!(state.getBlock() instanceof HoseWrenchSizeCyclable cyclable)) {
            return;
        }

        // event.getHitVec() already contains the server-side hit for this
        // connection, including Sable's sub-level projection.
        UseOnContext context = new UseOnContext(player, event.getHand(), event.getHitVec());
        cyclable.cycleSize(state, context);

        // SIZE owns the click: never let the same click fall through to the
        // default wrench rotation.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HoseWrenchState.set(player, HoseWrenchState.get(event.getOriginal()));
        }
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new HoseWrenchModeS2CPacket(HoseWrenchState.get(player).ordinal()));
    }
}
