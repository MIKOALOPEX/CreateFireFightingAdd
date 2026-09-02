package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.FireExtinguisherSprayEffects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FireExtinguisherSprayHandler {
	private static final Set<UUID> SPRAYING = new HashSet<>();

	private FireExtinguisherSprayHandler() {
	}

	public static void setSpraying(ServerPlayer player, boolean spraying) {
		if (!spraying)
			FireExtinguisherSprayEffects.stopSound(player);
		boolean changed = spraying ? SPRAYING.add(player.getUUID()) : SPRAYING.remove(player.getUUID());
		if (changed)
			syncSpraying(player, spraying);
	}

	public static void serverTick(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer))
			return;
		if (!SPRAYING.contains(serverPlayer.getUUID()))
			return;

		ActiveExtinguisher active = activeExtinguisher(serverPlayer);
		if (!active.present()) {
			setSpraying(serverPlayer, false);
			return;
		}
		if (!FireExtinguisherSprayEffects.spray(serverPlayer, active.stack(), active.hand())) {
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
	}

	public static void cancelBreak(PlayerInteractEvent.LeftClickBlock event) {
		if (event.getEntity().level().isClientSide)
			return;
		if (activeExtinguisher(event.getEntity()).present()
			&& SPRAYING.contains(event.getEntity().getUUID()))
			event.setCanceled(true);
	}

	private static void syncSpraying(ServerPlayer player, boolean spraying) {
		FireExtinguisherPosePacket packet = new FireExtinguisherPosePacket(player.getId(), spraying);
		PacketDistributor.sendToPlayersTrackingEntity(player, packet);
		PacketDistributor.sendToPlayer(player, packet);
	}

	private static ActiveExtinguisher activeExtinguisher(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
			return new ActiveExtinguisher(main, InteractionHand.MAIN_HAND);
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		if (off.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
			return new ActiveExtinguisher(off, InteractionHand.OFF_HAND);
		return ActiveExtinguisher.EMPTY;
	}

	private record ActiveExtinguisher(ItemStack stack, InteractionHand hand) {
		private static final ActiveExtinguisher EMPTY =
			new ActiveExtinguisher(ItemStack.EMPTY, InteractionHand.MAIN_HAND);

		boolean present() {
			return !stack.isEmpty();
		}
	}
}
