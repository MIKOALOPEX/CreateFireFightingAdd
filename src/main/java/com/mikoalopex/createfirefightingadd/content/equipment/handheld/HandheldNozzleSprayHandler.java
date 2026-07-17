package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.api.handheld.HandheldNozzleBindingApi;
import com.mikoalopex.createfirefightingadd.api.handheld.HandheldNozzleBindingApi.BindingLocation;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.HandheldNozzleSprayEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HandheldNozzleSprayHandler {
	private static final Set<UUID> SPRAYING = new HashSet<>();
	private static final Map<UUID, Set<BindingKey>> CARRIED_BINDINGS = new HashMap<>();
	private static final Map<UUID, ControllerSyncState> LAST_CONTROLLER_STATES = new HashMap<>();

	private HandheldNozzleSprayHandler() {
	}

	public static void setSpraying(ServerPlayer player, boolean spraying) {
		if (!spraying)
			HandheldNozzleSprayEffects.stopSound(player);
		boolean changed = spraying ? SPRAYING.add(player.getUUID()) : SPRAYING.remove(player.getUUID());
		if (changed)
			syncSpraying(player, spraying);
	}

	public static void serverTick(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer))
			return;
		syncCarriedBindings(serverPlayer);
		ItemStack stack = activeController(serverPlayer);
		if (!stack.isEmpty() && isBindingTooFar(serverPlayer, stack)) {
			HandheldNozzleBindingApi.clearBinding(serverPlayer.level(), stack, serverPlayer);
			setSpraying(serverPlayer, false);
			stack = ItemStack.EMPTY;
		}
		syncControllerState(serverPlayer);
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
		CARRIED_BINDINGS.remove(player.getUUID());
		LAST_CONTROLLER_STATES.remove(player.getUUID());
		if (player instanceof ServerPlayer serverPlayer)
			sendControllerState(serverPlayer, ControllerSyncState.EMPTY);
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof HandheldNozzleControllerItem && HandheldNozzleControllerItem.isBound(stack))
				HandheldNozzleBindingApi.clearBinding(player.level(), stack, player);
		}
	}

	public static void forceClearCabinetBinding(ServerLevel level, BlockPos pos, UUID hydrantId) {
		BindingKey key = new BindingKey(pos.immutable(), level.dimension(), hydrantId);
		HandheldNozzleControllerEntity.forceClearBinding(level, pos, hydrantId);
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			boolean changed = false;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (!HandheldNozzleBindingApi.isBoundController(stack))
					continue;
				if (!HandheldNozzleControllerItem.readBinding(stack).map(binding -> key.equals(BindingKey.from(binding)))
					.orElse(false))
					continue;

				HandheldNozzleBindingApi.clearBinding(level, stack, player);
				changed = true;
			}

			if (!changed)
				continue;
			setSpraying(player, false);
			removeCarriedBinding(player, key);
			player.getInventory().setChanged();
			syncControllerState(player);
		}
	}

	private static void syncSpraying(ServerPlayer player, boolean spraying) {
		HandheldNozzlePosePacket packet = new HandheldNozzlePosePacket(player.getId(), spraying);
		PacketDistributor.sendToPlayersTrackingEntity(player, packet);
		PacketDistributor.sendToPlayer(player, packet);
	}

	private static void syncControllerState(ServerPlayer player) {
		ControllerSyncState current = controllerSyncState(player);
		ControllerSyncState previous = LAST_CONTROLLER_STATES.get(player.getUUID());
		if (current.equals(previous) && player.tickCount % 40 != 0)
			return;
		if (current.present())
			LAST_CONTROLLER_STATES.put(player.getUUID(), current);
		else
			LAST_CONTROLLER_STATES.remove(player.getUUID());
		sendControllerState(player, current);
	}

	private static void sendControllerState(ServerPlayer player, ControllerSyncState state) {
		HandheldNozzleControllerStatePacket packet = state.toPacket(player.getId());
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

	private static ControllerSyncState controllerSyncState(ServerPlayer player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof HandheldNozzleControllerItem)
			return ControllerSyncState.from(main, false, player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);

		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		if (off.getItem() instanceof HandheldNozzleControllerItem)
			return ControllerSyncState.from(off, false, player.getMainArm() != net.minecraft.world.entity.HumanoidArm.LEFT);

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack == main || stack == off)
				continue;
			if (stack.getItem() instanceof HandheldNozzleControllerItem)
				return ControllerSyncState.from(stack, true, false);
		}
		return ControllerSyncState.EMPTY;
	}

	private static boolean isBindingTooFar(ServerPlayer player, ItemStack stack) {
		return HandheldNozzleControllerItem.readBinding(stack)
			.map(binding -> {
				if (!player.level().dimension().equals(binding.dimension()))
					return true;
				return player.distanceToSqr(
					binding.pos().getX() + 0.5,
					binding.pos().getY() + 0.5,
					binding.pos().getZ() + 0.5) > HandheldNozzleBindingApi.MAX_BINDING_DISTANCE_SQR;
			})
			.orElse(false);
	}

	private static void syncCarriedBindings(ServerPlayer player) {
		Set<BindingKey> current = new HashSet<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			trackCarriedBinding(player, player.getInventory().getItem(i), current);
		}
		trackCarriedBinding(player, player.containerMenu.getCarried(), current);

		Set<BindingKey> previous = CARRIED_BINDINGS.get(player.getUUID());
		if (previous != null) {
			for (BindingKey key : previous) {
				if (current.contains(key))
					continue;
				if (HandheldNozzleBindingApi.shouldClearBinding(player, BindingLocation.OUTSIDE_PLAYER_INVENTORY)) {
					if (HandheldNozzleControllerEntity.hasActiveBinding(key.dimension(), key.pos(), key.hydrantId()))
						continue;
					clearCabinetBinding(player, key);
				}
			}
		}

		if (current.isEmpty())
			CARRIED_BINDINGS.remove(player.getUUID());
		else
			CARRIED_BINDINGS.put(player.getUUID(), current);
	}

	private static void trackCarriedBinding(ServerPlayer player, ItemStack stack, Set<BindingKey> current) {
		if (!HandheldNozzleBindingApi.isBoundController(stack))
			return;
		HandheldNozzleControllerItem.readBinding(stack).ifPresent(binding -> {
			if (shouldClearCarriedBinding(player, binding)) {
				HandheldNozzleBindingApi.clearBinding(player.level(), stack, player);
				return;
			}
			BindingKey key = BindingKey.from(binding);
			current.add(key);
		});
	}

	private static boolean shouldClearCarriedBinding(ServerPlayer player, HandheldNozzleControllerItem.Binding binding) {
		if (!player.level().dimension().equals(binding.dimension()))
			return true;
		if (player.distanceToSqr(
			binding.pos().getX() + 0.5,
			binding.pos().getY() + 0.5,
			binding.pos().getZ() + 0.5) > HandheldNozzleBindingApi.MAX_BINDING_DISTANCE_SQR)
			return true;
		ServerLevel boundLevel = player.getServer().getLevel(binding.dimension());
		if (boundLevel == null)
			return true;
		return !(boundLevel.getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
			|| !cabinet.getHydrantId().equals(binding.hydrantId())
			|| !cabinet.isBoundTo(player.getUUID());
	}

	private static void clearCabinetBinding(ServerPlayer player, BindingKey key) {
		ServerLevel level = player.getServer().getLevel(key.dimension());
		if (level == null)
			return;
		if (level.getBlockEntity(key.pos()) instanceof FireHydrantCabinetBlockEntity cabinet
			&& cabinet.getHydrantId().equals(key.hydrantId())) {
			cabinet.clearBinding(player.getUUID());
		}
	}

	private static void removeCarriedBinding(ServerPlayer player, BindingKey key) {
		Set<BindingKey> carried = CARRIED_BINDINGS.get(player.getUUID());
		if (carried != null) {
			carried.remove(key);
			if (carried.isEmpty())
				CARRIED_BINDINGS.remove(player.getUUID());
		}
	}

	private record BindingKey(BlockPos pos, ResourceKey<Level> dimension, UUID hydrantId) {
		static BindingKey from(HandheldNozzleControllerItem.Binding binding) {
			return new BindingKey(binding.pos(), binding.dimension(), binding.hydrantId());
		}
	}

	private record ControllerSyncState(boolean present, boolean stowed, boolean leftHand,
			HandheldNozzleControllerItem.Binding binding) {
		private static final ControllerSyncState EMPTY = new ControllerSyncState(false, false, false, null);

		static ControllerSyncState from(ItemStack stack, boolean stowed, boolean leftHand) {
			return new ControllerSyncState(true, stowed, leftHand,
				HandheldNozzleControllerItem.readBinding(stack).orElse(null));
		}

		HandheldNozzleControllerStatePacket toPacket(int entityId) {
			if (!present)
				return HandheldNozzleControllerStatePacket.clear(entityId);
			if (binding == null)
				return new HandheldNozzleControllerStatePacket(entityId, true, stowed, leftHand, false,
					BlockPos.ZERO, "", new UUID(0L, 0L), HandheldNozzleType.NONE.ordinal());
			return new HandheldNozzleControllerStatePacket(entityId, true, stowed, leftHand, true,
				binding.pos(), binding.dimension().location().toString(), binding.hydrantId(),
				binding.nozzleType().ordinal());
		}
	}
}
