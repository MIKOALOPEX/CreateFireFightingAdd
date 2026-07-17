package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.HashMap;
import java.util.Map;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.HandheldNozzleClientSprayVisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HandheldNozzleClientHandler {
	private static final int REMOTE_SPRAYING_TTL = 40;
	private static final int USE_SWING_SUPPRESS_TICKS = 8;
	private static final Map<Integer, Long> REMOTE_SPRAYING = new HashMap<>();
	private static final Map<Integer, SyncedControllerState> REMOTE_CONTROLLERS = new HashMap<>();
	private static boolean lastSentSpraying;
	private static boolean leftMouseHeld;
	private static boolean waitForFreshAttack;
	private static int useSwingSuppressTicks;
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
		if (mc.level == null || mc.player == null) {
			leftMouseHeld = false;
			setSpraying(false);
			updateProgress(false);
			HandheldNozzleClientSprayVisuals.clearAll();
			return;
		}
		if (useSwingSuppressTicks > 0)
			useSwingSuppressTicks--;
		if (mc.screen != null) {
			leftMouseHeld = false;
			setSpraying(false);
			updateProgress(false);
			HandheldNozzleClientSprayVisuals.tick(mc.player, ItemStack.EMPTY, false);
			tickRemoteSprayVisuals(mc);
			return;
		}

		ItemStack stack = activeController(mc.player.getMainHandItem(), mc.player.getOffhandItem());
		boolean holdingController = !stack.isEmpty();
		String bindingKey = bindingKey(stack);
		if (!bindingKey.equals(lastBindingKey)) {
			lastBindingKey = bindingKey;
			waitForFreshAttack = !bindingKey.isEmpty();
			if (!bindingKey.isEmpty())
				useSwingSuppressTicks = Math.max(useSwingSuppressTicks, USE_SWING_SUPPRESS_TICKS);
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
		HandheldNozzleClientSprayVisuals.tick(mc.player, stack, shouldSpray);
		tickRemoteSprayVisuals(mc);
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

	public static void updateSyncedControllerState(int entityId, SyncedControllerState state) {
		if (!state.present()) {
			REMOTE_CONTROLLERS.remove(entityId);
			return;
		}
		REMOTE_CONTROLLERS.put(entityId, state);
	}

	public static SyncedControllerState getSyncedControllerState(LivingEntity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return null;
		SyncedControllerState state = REMOTE_CONTROLLERS.get(entity.getId());
		if (state == null)
			return null;
		if (mc.level.getGameTime() > state.expiresAt()) {
			REMOTE_CONTROLLERS.remove(entity.getId());
			return null;
		}
		return state;
	}

	public static void onMouseButton(int button, int action) {
		if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			leftMouseHeld = action != org.lwjgl.glfw.GLFW.GLFW_RELEASE;
			return;
		}
		if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT
			&& action == org.lwjgl.glfw.GLFW.GLFW_PRESS
			&& isBindingUseTarget())
			useSwingSuppressTicks = USE_SWING_SUPPRESS_TICKS;
	}

	public static boolean shouldCancelAttackInput() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null)
			return false;
		return !activeController(mc.player.getMainHandItem(), mc.player.getOffhandItem()).isEmpty();
	}

	public static boolean shouldSuppressUseSwing() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null)
			return false;
		return useSwingSuppressTicks > 0
			&& !activeController(mc.player.getMainHandItem(), mc.player.getOffhandItem()).isEmpty();
	}

	public static float getSprayProgress(float partialTick) {
		return Mth.lerp(partialTick, previousSprayProgress, sprayProgress);
	}

	private static boolean isBindingUseTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.hitResult == null)
			return false;
		if (!mc.player.isShiftKeyDown())
			return false;
		if (activeController(mc.player.getMainHandItem(), mc.player.getOffhandItem()).isEmpty())
			return false;
		if (mc.hitResult.getType() != HitResult.Type.BLOCK)
			return false;
		BlockHitResult hit = (BlockHitResult) mc.hitResult;
		return mc.level.getBlockEntity(hit.getBlockPos()) instanceof FireHydrantCabinetBlockEntity;
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

	private static void tickRemoteSprayVisuals(Minecraft mc) {
		if (mc.level == null || mc.player == null)
			return;
		for (Player player : mc.level.players()) {
			if (player == mc.player)
				continue;
			SyncedControllerState state = getSyncedControllerState(player);
			if (state == null || !state.present() || state.binding() == null) {
				HandheldNozzleClientSprayVisuals.clear(player.getId());
				continue;
			}
			HandheldNozzleClientSprayVisuals.tick(player, state.binding(), isSyncedSpraying(player));
		}
	}

	private static void cleanupRemoteSpraying(Minecraft mc) {
		if (mc.level == null) {
			REMOTE_SPRAYING.clear();
			REMOTE_CONTROLLERS.clear();
			return;
		}
		long now = mc.level.getGameTime();
		REMOTE_SPRAYING.entrySet().removeIf(entry -> now > entry.getValue());
		REMOTE_CONTROLLERS.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
	}

	public record SyncedControllerState(boolean present, boolean stowed, boolean leftHand,
			HandheldNozzleControllerItem.Binding binding, long expiresAt) {
	}
}
