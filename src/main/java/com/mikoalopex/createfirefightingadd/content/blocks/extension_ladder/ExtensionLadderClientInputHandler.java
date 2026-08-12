package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ExtensionLadderClientInputHandler {
	private static int lastForwardInput;
	private static boolean wasJumpDown;
	private static BlockPos lastAdjustPos;
	private static double lastAdjustX;
	private static double lastAdjustZ;
	private static long lastAdjustSentAt;

	private ExtensionLadderClientInputHandler() {
	}

	public static void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) {
			wasJumpDown = false;
			sendIfChanged(0, false);
			return;
		}

		int input = 0;
		if (mc.options.keyUp.isDown())
			input++;
		if (mc.options.keyDown.isDown())
			input--;
		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPulse = jumpDown && !wasJumpDown;
		wasJumpDown = jumpDown;
		ExtensionLadderClimbingController.clientTick(mc.player, input, jumpPulse);
		sendIfChanged(input, jumpPulse);
		sendAdjustIfNeeded(mc);
	}

	private static void sendIfChanged(int input, boolean jumpPulse) {
		if (input == lastForwardInput && !jumpPulse)
			return;
		lastForwardInput = input;
		PacketDistributor.sendToServer(new ExtensionLadderInputPacket(input, jumpPulse));
	}

	private static void sendAdjustIfNeeded(Minecraft mc) {
		if (mc.level == null || mc.player == null || !mc.options.keyUse.isDown()
			|| !mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty()
			|| !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
			|| !mc.level.getBlockState(hit.getBlockPos()).is(CreateFireFightingAdd.EXTENSION_LADDER.get())) {
			lastAdjustPos = null;
			lastAdjustSentAt = 0;
			return;
		}

		double x = mc.player.getViewVector(1.0f).x;
		double z = mc.player.getViewVector(1.0f).z;
		BlockPos pos = hit.getBlockPos();
		long now = mc.level.getGameTime();
		if (pos.equals(lastAdjustPos) && Math.abs(x - lastAdjustX) < 0.002 && Math.abs(z - lastAdjustZ) < 0.002
			&& now - lastAdjustSentAt < 2)
			return;

		lastAdjustPos = pos;
		lastAdjustX = x;
		lastAdjustZ = z;
		lastAdjustSentAt = now;
		PacketDistributor.sendToServer(new ExtensionLadderAdjustPacket(pos, x, z));
	}
}
