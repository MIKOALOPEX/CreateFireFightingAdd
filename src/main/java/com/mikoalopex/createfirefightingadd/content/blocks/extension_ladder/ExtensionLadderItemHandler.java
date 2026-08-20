package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ExtensionLadderItemHandler {
	public static final ExtensionLadderItemHandler INSTANCE = new ExtensionLadderItemHandler();
	private static final Object FACE_MARKER = new Object();
	private static final Object CLEARANCE_MARKER = new Object();
	private static final int GREEN = 0x70FF33;
	private static final int RED = 0xFF5555;
	private static final int YELLOW = 0xFFD94A;
	private static final int FEEDBACK_TICKS = 24;
	private BlockPos feedbackSupport;
	private int feedbackColor = YELLOW;
	private long feedbackExpiresAt = Long.MIN_VALUE;

	private ExtensionLadderItemHandler() {
	}

	public boolean onUse(InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.screen != null || !isHolding(player, hand))
			return false;
		BlockHitResult hit = currentBlockHit(mc);
		if (hit == null)
			return false;
		if (hit.getDirection() != Direction.UP) {
			player.displayClientMessage(error("first_point_floor"), true);
			return true;
		}
		PacketDistributor.sendToServer(new PlaceExtensionLadderPacket(hand, hit.getBlockPos()));
		return true;
	}

	public void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			clear();
			return;
		}
		long gameTime = mc.level.getGameTime();
		if (renderFeedback(mc, gameTime))
			return;
		if (!isHolding(mc.player, null)) {
			clear();
			return;
		}
		BlockHitResult hit = currentBlockHit(mc);
		if (hit == null || hit.getDirection() != Direction.UP) {
			Outliner.getInstance().remove(FACE_MARKER);
			Outliner.getInstance().remove(CLEARANCE_MARKER);
			return;
		}
		renderPreview(mc, hit.getBlockPos(), hit.getDirection(), previewColor(hit.getBlockPos(), gameTime));
	}

	public void clear() {
		Outliner.getInstance().remove(FACE_MARKER);
		Outliner.getInstance().remove(CLEARANCE_MARKER);
		feedbackSupport = null;
		feedbackExpiresAt = Long.MIN_VALUE;
	}

	public void acceptPlacementFeedback(BlockPos support, boolean canPlace) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return;
		// The held-item preview stays client-only yellow; the server only flashes a verdict after a use attempt.
		feedbackSupport = support.immutable();
		feedbackColor = canPlace ? GREEN : RED;
		feedbackExpiresAt = mc.level.getGameTime() + FEEDBACK_TICKS;
	}

	private int previewColor(BlockPos support, long gameTime) {
		if (support.equals(feedbackSupport) && gameTime <= feedbackExpiresAt)
			return feedbackColor;
		return YELLOW;
	}

	private boolean renderFeedback(Minecraft mc, long gameTime) {
		if (feedbackSupport == null || gameTime > feedbackExpiresAt)
			return false;
		renderPreview(mc, feedbackSupport, Direction.UP, feedbackColor);
		return true;
	}

	private static void renderPreview(Minecraft mc, BlockPos support, Direction face, int color) {
		Outliner.getInstance().showAABB(FACE_MARKER, faceBox(support, face))
			.colored(color).lineWidth(1 / 16f).clearTextures().disableLineNormals();
		Outliner.getInstance().showAABB(CLEARANCE_MARKER,
			ExtensionLadderPlacement.anchorPreviewColumn(support, mc.player.getViewVector(1.0f)))
			.colored(color).lineWidth(1 / 32f).clearTextures().disableLineNormals();
	}

	private static boolean isHolding(LocalPlayer player, InteractionHand hand) {
		if (hand != null)
			return player.getItemInHand(hand).getItem() instanceof ExtensionLadderItem;
		return player.getMainHandItem().getItem() instanceof ExtensionLadderItem
			|| player.getOffhandItem().getItem() instanceof ExtensionLadderItem;
	}

	private static BlockHitResult currentBlockHit(Minecraft mc) {
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
			return hit;
		return null;
	}

	private static AABB faceBox(BlockPos pos, Direction face) {
		AABB block = new AABB(pos);
		return switch (face) {
			case DOWN -> new AABB(block.minX, block.minY - 0.01, block.minZ, block.maxX, block.minY, block.maxZ);
			case UP -> new AABB(block.minX, block.maxY, block.minZ, block.maxX, block.maxY + 0.01, block.maxZ);
			case NORTH -> new AABB(block.minX, block.minY, block.minZ - 0.01, block.maxX, block.maxY, block.minZ);
			case SOUTH -> new AABB(block.minX, block.minY, block.maxZ, block.maxX, block.maxY, block.maxZ + 0.01);
			case WEST -> new AABB(block.minX - 0.01, block.minY, block.minZ, block.minX, block.maxY, block.maxZ);
			case EAST -> new AABB(block.maxX, block.minY, block.minZ, block.maxX + 0.01, block.maxY, block.maxZ);
		};
	}

	private static Component error(String key) {
		return Component.translatable("createfirefightingadd.extension_ladder." + key)
			.withStyle(ChatFormatting.RED);
	}

}
