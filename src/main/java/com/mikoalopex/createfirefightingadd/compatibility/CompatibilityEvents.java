package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class CompatibilityEvents {
    @SubscribeEvent public static void started(ServerStartedEvent event) { CompatibilityManager.logSummary(); }

    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new CompatibilitySyncPacket(CompatibilityManager.mask()));
    }

    private static long syncedRevision;
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        long revision = CompatibilityManager.revision();
        if (syncedRevision == revision) return;
        syncedRevision = revision;
        var packet = new CompatibilitySyncPacket(CompatibilityManager.mask());
        for (var player : event.getServer().getPlayerList().getPlayers())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        boolean heldDisabled = !ComponentPolicies.enabled(event.getItemStack());
        boolean blockDisabled = !ComponentPolicies.enabled(block);
        // Allow inventory recovery while rope operations are disabled.
        boolean recovery = BuiltInRegistries.BLOCK.getKey(block).equals(CreateFireFightingAdd.path("rope_connector_master"))
            && event.getItemStack().isEmpty();
        if (!heldDisabled && (!blockDisabled || recovery)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide())
            event.getEntity().displayClientMessage(Component.translatable("createfirefightingadd.compatibility.disabled"), true);
    }

    @SubscribeEvent public static void drops(BlockDropsEvent event) {
        event.getDrops().removeIf(item -> !ComponentPolicies.enabled(item.getItem()));
    }
    private CompatibilityEvents() {}
}
