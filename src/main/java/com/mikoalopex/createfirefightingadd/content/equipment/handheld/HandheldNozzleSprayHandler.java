package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.HandheldNozzleSprayEffects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HandheldNozzleSprayHandler {
	private static final double MAX_BINDING_DISTANCE_SQR = 32.0 * 32.0;
	private static final Set<UUID> SPRAYING = new HashSet<>();

	private HandheldNozzleSprayHandler() {
	}

	public static void setSpraying(ServerPlayer player, boolean spraying) {
		boolean changed = spraying ? SPRAYING.add(player.getUUID()) : SPRAYING.remove(player.getUUID());
		if (changed)
			syncSpraying(player, spraying);
	}

	public static void serverTick(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer))
			return;
		clearInactiveBindings(serverPlayer);
		ItemStack stack = activeController(serverPlayer);
		if (!stack.isEmpty() && isBindingTooFar(serverPlayer, stack)) {
			HandheldNozzleControllerItem.clearBinding(serverPlayer.level(), stack, serverPlayer);
			setSpraying(serverPlayer, false);
			return;
		}
		if (!SPRAYING.contains(serverPlayer.getUUID()))
			return;
		if (stack.isEmpty()) {
			setSpraying(serverPlayer, false);
			return;
		}
		if (!HandheldNozzleSprayEffects.spray(serverPlayer, stack)) {
			setSpraying(serverPlayer, false);
			return;
		}
		if (serverPlayer.tickCount % 20 == 0)
			syncSpraying(serverPlayer, true);
	}

	public static void clearPlayer(Player player) {
		if (player instanceof ServerPlayer serverPlayer)
			setSpraying(serverPlayer, false);
		else
			SPRAYING.remove(player.getUUID());
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof HandheldNozzleControllerItem && HandheldNozzleControllerItem.isBound(stack))
				HandheldNozzleControllerItem.clearBinding(player.level(), stack, player);
		}
	}

	private static void syncSpraying(ServerPlayer player, boolean spraying) {
		HandheldNozzlePosePacket packet = new HandheldNozzlePosePacket(player.getId(), spraying);
		PacketDistributor.sendToPlayersTrackingEntity(player, packet);
		PacketDistributor.sendToPlayer(player, packet);
	}

	public static void cancelHandheldBreak(PlayerInteractEvent.LeftClickBlock event) {
		if (event.getEntity().level().isClientSide)
			return;
		if (activeController(event.getEntity()).isEmpty())
			return;
		if (SPRAYING.contains(event.getEntity().getUUID()))
			event.setCanceled(true);
	}

	private static ItemStack activeController(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof HandheldNozzleControllerItem && HandheldNozzleControllerItem.isBound(main))
			return main;
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		if (off.getItem() instanceof HandheldNozzleControllerItem && HandheldNozzleControllerItem.isBound(off))
			return off;
		return ItemStack.EMPTY;
	}

	private static boolean isBindingTooFar(ServerPlayer player, ItemStack stack) {
		return HandheldNozzleControllerItem.readBinding(stack)
			.map(binding -> {
				if (!player.level().dimension().equals(binding.dimension()))
					return true;
				return player.distanceToSqr(
					binding.pos().getX() + 0.5,
					binding.pos().getY() + 0.5,
					binding.pos().getZ() + 0.5) > MAX_BINDING_DISTANCE_SQR;
			})
			.orElse(false);
	}

	private static void clearInactiveBindings(ServerPlayer player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack == main || stack == off)
				continue;
			if (stack.getItem() instanceof HandheldNozzleControllerItem && HandheldNozzleControllerItem.isBound(stack))
				HandheldNozzleControllerItem.clearBinding(player.level(), stack, player);
		}
	}
}
