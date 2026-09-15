package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ExtensionLadderClientInputHandler {
	private static final float SCROLL_STEP_PIXELS = 2;
	private static final double DIRECTION_EPSILON = 0.002;
	private static final float MOVE_EPSILON = 0.01f;
	private static int lastForwardInput;
	private static int lastStrafeInput;
	private static long lastInputSentAt;
	private static boolean wasJumpDown;
	private static BlockPos capturedAdjustPos;
	private static float capturedMoveOffsetPixels = ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS;
	private static BlockPos lastAdjustPos;
	private static double lastAdjustX;
	private static double lastAdjustZ;
	private static float lastAdjustMoveOffsetPixels;
	private static long lastAdjustSentAt;

	private ExtensionLadderClientInputHandler() {
	}

	public static void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			wasJumpDown = false;
			sendIfChanged(0, 0, false);
			releaseAdjustCapture();
			return;
		}
		if (mc.screen != null) {
			wasJumpDown = false;
			ExtensionLadderClimbingController.clientTick(mc.player, 0, 0, false);
			sendIfChanged(0, 0, false);
			releaseAdjustCapture();
			return;
		}

		int input = 0;
		if (mc.options.keyUp.isDown())
			input++;
		if (mc.options.keyDown.isDown())
			input--;
		int strafe = 0;
		if (mc.options.keyRight.isDown())
			strafe++;
		if (mc.options.keyLeft.isDown())
			strafe--;
		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPulse = jumpDown && !wasJumpDown;
		wasJumpDown = jumpDown;
		ExtensionLadderClimbingController.clientTick(mc.player, input, strafe, jumpPulse);
		sendIfChanged(input, strafe, jumpPulse);
		sendAdjustIfNeeded(mc);
	}

	public static boolean onMouseScroll(double scrollDelta) {
		if (scrollDelta == 0)
			return false;
		Minecraft mc = Minecraft.getInstance();
		if (!ensureAdjustCapture(mc))
			return false;
		capturedMoveOffsetPixels = Mth.clamp(capturedMoveOffsetPixels + (float) scrollDelta * SCROLL_STEP_PIXELS,
			0, ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS);
		sendAdjustIfNeeded(mc, true);
		return true;
	}

	public static boolean isAdjusting() {
		return capturedAdjustPos != null;
	}

	private static void sendIfChanged(int input, int strafe, boolean jumpPulse) {
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level == null ? 0 : mc.level.getGameTime();
		boolean activeInput = input != 0 || strafe != 0;
		boolean periodicRefresh = activeInput && now - lastInputSentAt >= 2;
		if (input == lastForwardInput && strafe == lastStrafeInput && !jumpPulse && !periodicRefresh)
			return;
		lastForwardInput = input;
		lastStrafeInput = strafe;
		lastInputSentAt = now;
		PacketDistributor.sendToServer(new ExtensionLadderInputPacket(input, strafe, jumpPulse));
	}

	private static void sendAdjustIfNeeded(Minecraft mc) {
		sendAdjustIfNeeded(mc, false);
	}

	private static void sendAdjustIfNeeded(Minecraft mc, boolean force) {
		if (!ensureAdjustCapture(mc))
			return;

		Vec3 view = mc.player.getViewVector(1.0f);
		double x = view.x;
		double z = view.z;
		if (x * x + z * z < 1.0E-6)
			return;

		long now = mc.level.getGameTime();
		boolean changed = !capturedAdjustPos.equals(lastAdjustPos)
			|| Math.abs(x - lastAdjustX) >= DIRECTION_EPSILON
			|| Math.abs(z - lastAdjustZ) >= DIRECTION_EPSILON
			|| Math.abs(capturedMoveOffsetPixels - lastAdjustMoveOffsetPixels) >= MOVE_EPSILON;
		if (!force && !changed && now - lastAdjustSentAt < 2)
			return;

		lastAdjustPos = capturedAdjustPos;
		lastAdjustX = x;
		lastAdjustZ = z;
		lastAdjustMoveOffsetPixels = capturedMoveOffsetPixels;
		lastAdjustSentAt = now;
		PacketDistributor.sendToServer(new ExtensionLadderAdjustPacket(capturedAdjustPos, x, z,
			capturedMoveOffsetPixels));
	}

	private static boolean ensureAdjustCapture(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.screen != null || !mc.options.keyUse.isDown()
			|| !mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()) {
			releaseAdjustCapture();
			return false;
		}

		if (capturedAdjustPos != null) {
			if (mc.level.getBlockState(capturedAdjustPos).is(CreateFireFightingAdd.EXTENSION_LADDER.get()))
				return true;
			releaseAdjustCapture();
			return false;
		}

		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
			|| !mc.level.getBlockState(hit.getBlockPos()).is(CreateFireFightingAdd.EXTENSION_LADDER.get()))
			return false;

		capturedAdjustPos = hit.getBlockPos();
		capturedMoveOffsetPixels = currentMoveOffsetPixels(mc, capturedAdjustPos);
		lastAdjustPos = null;
		lastAdjustSentAt = 0;
		return true;
	}

	private static float currentMoveOffsetPixels(Minecraft mc, BlockPos pos) {
		if (mc.level != null && mc.level.getBlockEntity(pos) instanceof ExtensionLadderBlockEntity ladder)
			return ladder.getTargetMoveOffsetPixels();
		return ExtensionLadderGeometry.MAX_MOVE_OFFSET_PIXELS;
	}

	private static void releaseAdjustCapture() {
		capturedAdjustPos = null;
		lastAdjustPos = null;
		lastAdjustSentAt = 0;
	}
}
