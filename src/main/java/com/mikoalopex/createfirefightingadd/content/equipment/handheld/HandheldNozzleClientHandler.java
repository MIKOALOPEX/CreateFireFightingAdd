package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.HashMap;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HandheldNozzleClientHandler {
	private static final int REMOTE_SPRAYING_TTL = 40;
	private static final Map<Integer, Long> REMOTE_SPRAYING = new HashMap<>();
	private static boolean lastSentSpraying;
	private static boolean leftMouseHeld;
	private static boolean waitForFreshAttack;
	private static float previousSprayProgress;
	private static float sprayProgress;
	private static long lastMissingMessageAt;
	private static String lastBindingKey = "";

	private HandheldNozzleClientHandler() {
	}

	public static void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		cleanupRemoteSpraying(mc);
		previousSprayProgress = sprayProgress;
		if (mc.player == null || mc.screen != null) {
			leftMouseHeld = false;
			setSpraying(false);
			updateProgress(false);
			com.mikoalopex.createfirefightingadd.content.fluids.nozzle.HandheldNozzleClientSprayVisuals.tick(null, ItemStack.EMPTY, false);
			return;
		}

		ItemStack stack = activeController(mc.player.getMainHandItem(), mc.player.getOffhandItem());
		boolean holdingController = !stack.isEmpty();
		String bindingKey = bindingKey(stack);
		if (!bindingKey.equals(lastBindingKey)) {
			lastBindingKey = bindingKey;
			waitForFreshAttack = !bindingKey.isEmpty();
			setSpraying(false);
		}

		boolean attacking = leftMouseHeld;
		if (!attacking)
			waitForFreshAttack = false;
		boolean shouldSpray = holdingController && attacking && !waitForFreshAttack
			&& HandheldNozzleControllerItem.isBound(stack);

		if (holdingController)
			mc.options.keyAttack.setDown(false);
		if (holdingController && attacking) {
			if (!HandheldNozzleControllerItem.isBound(stack)
				&& mc.level != null && mc.level.getGameTime() - lastMissingMessageAt > 20) {
				lastMissingMessageAt = mc.level.getGameTime();
				mc.player.displayClientMessage(
					Component.translatable("createfirefightingadd.handheld_nozzle.missing_nozzle"), true);
			}
		}

		setSpraying(shouldSpray);
		updateProgress(shouldSpray);
		com.mikoalopex.createfirefightingadd.content.fluids.nozzle.HandheldNozzleClientSprayVisuals.tick(
			mc.player, stack, shouldSpray);
	}

	public static boolean isSpraying() {
		return lastSentSpraying;
	}

	public static boolean isSyncedSpraying(LivingEntity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player)
			return isSpraying();
		return REMOTE_SPRAYING.containsKey(entity.getId());
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

	public static void onMouseButton(int button, int action) {
		if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
			return;
		leftMouseHeld = action != org.lwjgl.glfw.GLFW.GLFW_RELEASE;
	}

	public static boolean shouldCancelAttackInput() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null)
			return false;
		return !activeController(mc.player.getMainHandItem(), mc.player.getOffhandItem()).isEmpty();
	}

	public static float getSprayProgress(float partialTick) {
		return Mth.lerp(partialTick, previousSprayProgress, sprayProgress);
	}

	private static void setSpraying(boolean spraying) {
		if (lastSentSpraying == spraying)
			return;
		lastSentSpraying = spraying;
		PacketDistributor.sendToServer(new HandheldNozzleSprayPacket(spraying));
	}

	private static void updateProgress(boolean spraying) {
		float target = spraying ? 1.0f : 0.0f;
		float speed = spraying ? 0.35f : 0.25f;
		sprayProgress += (target - sprayProgress) * speed;
		if (Math.abs(sprayProgress - target) < 0.01f)
			sprayProgress = target;
	}

	private static ItemStack activeController(ItemStack main, ItemStack offhand) {
		if (main.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get()))
			return main;
		if (offhand.is(CreateFireFightingAdd.HANDHELD_NOZZLE_CONTROLLER_ITEM.get()))
			return offhand;
		return ItemStack.EMPTY;
	}

	private static String bindingKey(ItemStack stack) {
		return HandheldNozzleControllerItem.readBinding(stack)
			.map(binding -> binding.dimension().location() + "|" + binding.pos().asLong()
				+ "|" + binding.hydrantId() + "|" + binding.nozzleType())
			.orElse("");
	}

	private static void cleanupRemoteSpraying(Minecraft mc) {
		if (mc.level == null) {
			REMOTE_SPRAYING.clear();
			return;
		}
		long now = mc.level.getGameTime();
		REMOTE_SPRAYING.entrySet().removeIf(entry -> now > entry.getValue());
	}
}
