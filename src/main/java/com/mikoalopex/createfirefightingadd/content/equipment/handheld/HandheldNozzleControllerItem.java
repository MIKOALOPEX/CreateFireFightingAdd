package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class HandheldNozzleControllerItem extends Item {
	private static final String POS_TAG = "HydrantPos";
	private static final String DIMENSION_TAG = "HydrantDimension";
	private static final String ID_TAG = "HydrantId";
	private static final String NOZZLE_TAG = "NozzleType";

	public HandheldNozzleControllerItem(Properties properties) {
		super(properties);
	}

	public ItemInteractionResult tryBindToCabinet(ItemStack stack, Level level, BlockPos pos, Player player) {
		if (!(level.getBlockEntity(pos) instanceof FireHydrantCabinetBlockEntity cabinet))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (level.isClientSide)
			return ItemInteractionResult.SUCCESS;

		if (!cabinet.hasHose()) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.handheld_nozzle.missing_hose"), true);
			return ItemInteractionResult.SUCCESS;
		}

		HandheldNozzleType nozzleType = cabinet.getNozzleType();
		if (!nozzleType.hasNozzle()) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.handheld_nozzle.missing_nozzle"), true);
			return ItemInteractionResult.SUCCESS;
		}

		cabinet.forceClearBinding();
		bind(stack, (ServerLevel) level, pos, cabinet.getHydrantId(), nozzleType);
		cabinet.bindTo(player);
		player.displayClientMessage(Component.translatable("createfirefightingadd.handheld_nozzle.bound"), true);
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()
			&& context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FireHydrantCabinetBlockEntity) {
			ItemInteractionResult result = tryBindToCabinet(context.getItemInHand(), context.getLevel(),
				context.getClickedPos(), player);
			return result == ItemInteractionResult.SUCCESS ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return super.useOn(context);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown())
			return InteractionResultHolder.pass(stack);
		if (level.isClientSide)
			return InteractionResultHolder.success(stack);
		if (isBound(stack)) {
			clearBinding(level, stack, player);
			player.displayClientMessage(Component.translatable("createfirefightingadd.handheld_nozzle.unbound"), true);
			return InteractionResultHolder.consume(stack);
		}
		return InteractionResultHolder.pass(stack);
	}

	public static boolean isBound(ItemStack stack) {
		return readBinding(stack).isPresent();
	}

	public static Optional<Binding> readBinding(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return Optional.empty();
		CompoundTag tag = data.copyTag();
		if (!tag.contains(POS_TAG) || !tag.contains(DIMENSION_TAG) || !tag.hasUUID(ID_TAG))
			return Optional.empty();
		ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(DIMENSION_TAG));
		if (dimension == null)
			return Optional.empty();
		HandheldNozzleType nozzleType;
		try {
			nozzleType = HandheldNozzleType.valueOf(tag.getString(NOZZLE_TAG));
		} catch (IllegalArgumentException e) {
			nozzleType = HandheldNozzleType.NONE;
		}
		return Optional.of(new Binding(
			BlockPos.of(tag.getLong(POS_TAG)),
			ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension),
			tag.getUUID(ID_TAG),
			nozzleType));
	}

	public static void bind(ItemStack stack, ServerLevel level, BlockPos pos, UUID hydrantId, HandheldNozzleType nozzleType) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putLong(POS_TAG, pos.asLong());
		tag.putString(DIMENSION_TAG, level.dimension().location().toString());
		tag.putUUID(ID_TAG, hydrantId);
		tag.putString(NOZZLE_TAG, nozzleType.name());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static void clearBinding(Level level, ItemStack stack, @Nullable Player player) {
		Optional<Binding> binding = readBinding(stack);
		if (binding.isPresent() && level instanceof ServerLevel serverLevel) {
			Binding data = binding.get();
			ServerLevel boundLevel = serverLevel.getServer().getLevel(data.dimension());
			if (boundLevel != null
				&& boundLevel.getBlockEntity(data.pos()) instanceof FireHydrantCabinetBlockEntity cabinet
				&& cabinet.getHydrantId().equals(data.hydrantId())) {
				cabinet.clearBinding(player == null ? null : player.getUUID());
			}
		}

		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return;
		CompoundTag tag = data.copyTag();
		tag.remove(POS_TAG);
		tag.remove(DIMENSION_TAG);
		tag.remove(ID_TAG);
		tag.remove(NOZZLE_TAG);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public record Binding(BlockPos pos, ResourceKey<Level> dimension, UUID hydrantId, HandheldNozzleType nozzleType) {
	}
}
