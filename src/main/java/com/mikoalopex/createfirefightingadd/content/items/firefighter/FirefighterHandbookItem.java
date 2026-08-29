package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.AbstractSprayDeviceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FirefighterHandbookItem extends Item {
	public FirefighterHandbookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
			open(serverPlayer, hand, stack);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (!(player instanceof ServerPlayer serverPlayer))
			return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
		if (!isBindableSource(context.getLevel(), context.getClickedPos())) {
			if (hasDefaultBlockInteraction(context.getLevel(), context.getClickedPos()))
				return InteractionResult.PASS;
			open(serverPlayer, context.getHand(), context.getItemInHand());
			return InteractionResult.SUCCESS;
		}
		if (!Config.firefighterExtinguishRecordsEnabled) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.records_disabled"), true);
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		var owner = FirefighterHandbookData.owner(stack);
		if (owner.isEmpty()) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.open_to_register"), true);
			return InteractionResult.SUCCESS;
		}
		FirefighterRecordStore.bindSource(context.getLevel(), context.getClickedPos(), owner.get());
		player.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.source_bound"), true);
		return InteractionResult.SUCCESS;
	}

	public ItemInteractionResult tryBindToSpraySource(ItemStack stack, Level level, Player player, BlockPos pos) {
		if (!(player instanceof ServerPlayer) || level.isClientSide)
			return ItemInteractionResult.SUCCESS;
		if (!isBindableSource(level, pos))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!Config.firefighterExtinguishRecordsEnabled) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.records_disabled"), true);
			return ItemInteractionResult.SUCCESS;
		}
		UUID owner = FirefighterHandbookData.owner(stack).orElse(null);
		if (owner == null) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.open_to_register"), true);
			return ItemInteractionResult.SUCCESS;
		}
		FirefighterRecordStore.bindSource(level, pos, owner);
		player.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.source_bound"), true);
		return ItemInteractionResult.SUCCESS;
	}

	private static boolean isBindableSource(Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof AbstractSprayDeviceBlockEntity
			|| level.getBlockEntity(pos) instanceof FireHydrantCabinetBlockEntity;
	}

	private static boolean hasDefaultBlockInteraction(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		// Preserve vanilla block use before falling back to the handbook screen.
		if (state.getMenuProvider(level, pos) != null)
			return true;
		return state.getBlock() instanceof BedBlock
			|| state.getBlock() instanceof BellBlock
			|| state.getBlock() instanceof ButtonBlock
			|| state.getBlock() instanceof DoorBlock
			|| state.getBlock() instanceof FenceGateBlock
			|| state.getBlock() instanceof LeverBlock
			|| state.getBlock() instanceof NoteBlock
			|| state.getBlock() instanceof TrapDoorBlock;
	}

	static void open(ServerPlayer player, InteractionHand hand, ItemStack stack) {
		boolean enabled = Config.firefighterExtinguishRecordsEnabled;
		UUID owner = FirefighterHandbookData.owner(stack).orElse(null);
		FirefighterHandbookSnapshot snapshot;
		if (owner != null) {
			if (enabled)
				FirefighterRecordStore.record(player.server, owner).ifPresent(record ->
					FirefighterHandbookData.syncFromServer(stack, record));
			snapshot = FirefighterRecordStore.snapshot(player, owner, FirefighterHandbookData.count(stack), enabled);
		} else {
			snapshot = FirefighterHandbookData.snapshotFromStack(stack, enabled);
		}
		FirefighterHandbookSnapshot finalSnapshot = snapshot;
		player.openMenu(new SimpleMenuProvider(
			(id, inventory, ignored) -> new FirefighterHandbookMenu(id, inventory, hand, finalSnapshot),
			Component.translatable("item.createfirefightingadd.fire_handbook")),
			buf -> {
				buf.writeVarInt(hand.ordinal());
				finalSnapshot.write(buf);
			});
	}
}
