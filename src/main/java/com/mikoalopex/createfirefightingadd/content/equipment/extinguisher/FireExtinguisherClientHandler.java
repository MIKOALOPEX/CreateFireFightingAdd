package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

import java.util.HashMap;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.FireExtinguisherClientSprayVisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class FireExtinguisherClientHandler {
	private static final int REMOTE_SPRAYING_TTL = 40;
	private static final Map<Integer, Long> REMOTE_SPRAYING = new HashMap<>();
	private static boolean lastSentSpraying;
	private static boolean attackHeld;

	private FireExtinguisherClientHandler() {
	}

	public static void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		cleanupRemoteSpraying(mc);
		if (mc.level == null || mc.player == null) {
			attackHeld = false;
			setSpraying(false);
			FireExtinguisherClientSprayVisuals.clearAll();
			return;
		}
		if (mc.screen != null) {
			attackHeld = false;
			setSpraying(false);
			FireExtinguisherClientSprayVisuals.tick(mc.player, ItemStack.EMPTY, InteractionHand.MAIN_HAND, false);
			tickRemoteSprayVisuals(mc);
			return;
		}

		ActiveExtinguisher active = activeExtinguisher(mc.player);
		ItemStack stack = active.stack();
		boolean holdingExtinguisher = active.present();
		boolean shouldSpray = holdingExtinguisher && (attackHeld || mc.options.keyAttack.isDown());
		if (holdingExtinguisher)
			mc.options.keyAttack.setDown(false);

		setSpraying(shouldSpray);
		FireExtinguisherClientSprayVisuals.tick(mc.player, stack, active.hand(), shouldSpray);
		tickRemoteSprayVisuals(mc);
	}

	public static void onMouseButton(int button, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.keyAttack.matchesMouse(button))
			attackHeld = action != GLFW.GLFW_RELEASE;
	}

	public static void onKey(int key, int scanCode, int action) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.keyAttack.matches(key, scanCode))
			attackHeld = action != GLFW.GLFW_RELEASE;
	}

	public static boolean shouldCancelAttackInput() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null)
			return false;
		return activeExtinguisher(mc.player).present();
	}

	public static void updateSyncedSpraying(int entityId, boolean spraying) {
		Minecraft mc = Minecraft.getInstance();
		if (!spraying) {
			REMOTE_SPRAYING.remove(entityId);
			return;
		}
		long now = mc.level == null ? 0 : mc.level.getGameTime();
		REMOTE_SPRAYING.put(entityId, now + REMOTE_SPRAYING_TTL);
	}

	private static void setSpraying(boolean spraying) {
		if (lastSentSpraying == spraying)
			return;
		lastSentSpraying = spraying;
		PacketDistributor.sendToServer(new FireExtinguisherSprayPacket(spraying));
	}

	private static void tickRemoteSprayVisuals(Minecraft mc) {
		if (mc.level == null || mc.player == null)
			return;
		for (Player player : mc.level.players()) {
			if (player == mc.player)
				continue;
			ActiveExtinguisher active = activeExtinguisher(player);
			if (!active.present()) {
				FireExtinguisherClientSprayVisuals.clear(player.getId());
				continue;
			}
			FireExtinguisherClientSprayVisuals.tick(player, active.stack(), active.hand(),
				REMOTE_SPRAYING.containsKey(player.getId()));
		}
	}

	private static void cleanupRemoteSpraying(Minecraft mc) {
		if (mc.level == null) {
			REMOTE_SPRAYING.clear();
			return;
		}
		long now = mc.level.getGameTime();
		REMOTE_SPRAYING.entrySet().removeIf(entry -> now > entry.getValue());
	}

	private static ActiveExtinguisher activeExtinguisher(Player player) {
		ItemStack main = player.getMainHandItem();
		if (main.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
			return new ActiveExtinguisher(main, InteractionHand.MAIN_HAND);
		ItemStack offhand = player.getOffhandItem();
		if (offhand.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
			return new ActiveExtinguisher(offhand, InteractionHand.OFF_HAND);
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
