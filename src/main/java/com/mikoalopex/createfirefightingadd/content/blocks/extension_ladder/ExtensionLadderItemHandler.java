package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ExtensionLadderItemHandler {
	public static final ExtensionLadderItemHandler INSTANCE = new ExtensionLadderItemHandler();
	private static final Object MARKER = new Object();
	private static final int GREEN = 0x70FF33;
	private static final int RED = 0xFF5555;
	private static final double RANGE = 16.0;
	private BlockHitResult preview;

	private ExtensionLadderItemHandler() {
	}

	public boolean onUse(InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.screen != null || !isHolding(player, hand))
			return false;
		BlockHitResult hit = rayTrace(mc.level, player);
		if (hit == null)
			return false;
		if (hit.getDirection() != Direction.UP) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.extension_ladder.first_point_floor"), true);
			return true;
		}
		PacketDistributor.sendToServer(new PlaceExtensionLadderPacket(hand));
		return true;
	}

	public void clientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !isHolding(mc.player, null)) {
			clear();
			return;
		}
		BlockHitResult hit = rayTrace(mc.level, mc.player);
		if (hit == null || hit.getDirection() != Direction.UP) {
			Outliner.getInstance().remove(MARKER);
			preview = null;
			return;
		}
		preview = hit;
		Outliner.getInstance().showAABB(MARKER, faceBox(hit.getBlockPos(), hit.getDirection()))
			.colored(GREEN).lineWidth(1 / 16f).clearTextures().disableLineNormals();
	}

	public void clear() {
		preview = null;
		Outliner.getInstance().remove(MARKER);
	}

	private static boolean isHolding(LocalPlayer player, InteractionHand hand) {
		if (hand != null)
			return player.getItemInHand(hand).getItem() instanceof ExtensionLadderItem;
		return player.getMainHandItem().getItem() instanceof ExtensionLadderItem
			|| player.getOffhandItem().getItem() instanceof ExtensionLadderItem;
	}

	private static BlockHitResult rayTrace(Level level, LocalPlayer player) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0f).scale(RANGE));
		BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
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
}
