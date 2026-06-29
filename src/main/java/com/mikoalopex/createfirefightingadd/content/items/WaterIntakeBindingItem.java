package com.mikoalopex.createfirefightingadd.content.items;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.BucketControllerBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.water_intake.WaterIntakeBlockEntity;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class WaterIntakeBindingItem extends BlockItem {

	public WaterIntakeBindingItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = data.copyTag();
			if (tag.contains("BoundIntakePos")) {
				if (!level.isClientSide()) {
					tag.remove("BoundIntakePos");
					stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
					player.displayClientMessage(
						Component.translatable("message.createfirefightingadd.intake_unbound"), true);
				}
				return InteractionResultHolder.success(stack);
			}
		}
		return InteractionResultHolder.pass(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		BlockEntity be = level.getBlockEntity(pos);

		if (be instanceof WaterIntakeBlockEntity) {
			if (level.isClientSide())
				return InteractionResult.SUCCESS;

			CompoundTag tag = new CompoundTag();
			CustomData existing = ctx.getItemInHand().get(DataComponents.CUSTOM_DATA);
			if (existing != null)
				tag = existing.copyTag();
			tag.putLong("BoundIntakePos", pos.asLong());
			ctx.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

			if (ctx.getPlayer() != null) {
				ctx.getPlayer().displayClientMessage(
					Component.translatable("message.createfirefightingadd.intake_bound"), true);
			}
			return InteractionResult.SUCCESS;
		}

		return super.useOn(ctx);
	}

	@Override
	public InteractionResult place(BlockPlaceContext ctx) {
		CustomData data = ctx.getItemInHand().get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = data.copyTag();
			if (tag.contains("BoundIntakePos")) {
				BlockPos intakePos = BlockPos.of(tag.getLong("BoundIntakePos"));
				BlockPos placePos = ctx.getClickedPos();
				double distSqr = SableStructureCompat.distanceSquared(ctx.getLevel(), intakePos, placePos);
				if (distSqr > (double) Config.wirelessMaxBindDistance
						* Config.wirelessMaxBindDistance) {
					if (ctx.getPlayer() != null) {
						ctx.getPlayer().displayClientMessage(
							Component.translatable("message.createfirefightingadd.bind_too_far"),
							true);
					}
					return InteractionResult.FAIL;
				}
			}
		}

		InteractionResult result = super.place(ctx);
		if (result.consumesAction() && !ctx.getLevel().isClientSide()) {
			if (data != null) {
				CompoundTag tag = data.copyTag();
				if (tag.contains("BoundIntakePos")) {
					BlockPos intakePos = BlockPos.of(tag.getLong("BoundIntakePos"));
					BlockEntity be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
					if (be instanceof BucketControllerBlockEntity bucket) {
						bucket.setBoundIntake(intakePos);
						BlockEntity intakeBe = ctx.getLevel().getBlockEntity(intakePos);
						if (intakeBe instanceof WaterIntakeBlockEntity intake) {
							intake.setBoundBucket(ctx.getClickedPos());
						}
					}
				}
			}
		}
		return result;
	}
}
