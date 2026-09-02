package com.mikoalopex.createfirefightingadd.content.items.configurator;

import java.util.List;

import com.mikoalopex.createfirefightingadd.content.equipment.extinguisher.FireExtinguisherBlock;
import com.mikoalopex.createfirefightingadd.content.equipment.extinguisher.FireExtinguisherBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.AbstractSprayDeviceBlockEntity;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRuleSet;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public class MultifunctionConfiguratorItem extends Item {
	public MultifunctionConfiguratorItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.createfirefightingadd.multifunction_configurator.tooltip.dialogue1")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.createfirefightingadd.multifunction_configurator.tooltip.dialogue2")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.PASS;
		if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FireExtinguisherBlockEntity extinguisher)
			return applyToExtinguisher(context.getItemInHand(), context.getLevel(), player, extinguisher,
				FireExtinguisherBlock.bottleIndex(context.getLevel().getBlockState(context.getClickedPos()),
					new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
						context.getClickedPos(), context.isInside())));
		if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AbstractSprayDeviceBlockEntity nozzle)
			return applyToNozzle(context.getItemInHand(), context.getLevel(), player, nozzle);
		if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FireHydrantCabinetBlockEntity cabinet)
			return tryApplyToCabinet(context.getItemInHand(), context.getLevel(), player, cabinet) == ItemInteractionResult.SUCCESS
				? InteractionResult.SUCCESS : InteractionResult.PASS;
		return InteractionResult.PASS;
	}

	public ItemInteractionResult tryApplyToCabinet(ItemStack stack, Level level, Player player,
			FireHydrantCabinetBlockEntity cabinet) {
		if (level.isClientSide)
			return ItemInteractionResult.SUCCESS;

		NozzleSprayRuleSet rules = NozzleSprayRuleSet.fromStack(stack, player.registryAccess());
		if (rules.isEmpty()) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.nozzle.no_rules"), true);
			return ItemInteractionResult.SUCCESS;
		}
		cabinet.setCustomSprayRules(rules);
		player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.nozzle.applied"), true);
		return ItemInteractionResult.SUCCESS;
	}

	private InteractionResult applyToNozzle(ItemStack stack, Level level, Player player,
			AbstractSprayDeviceBlockEntity nozzle) {
		if (level.isClientSide)
			return InteractionResult.SUCCESS;

		NozzleSprayRuleSet rules = NozzleSprayRuleSet.fromStack(stack, player.registryAccess());
		if (rules.isEmpty()) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.nozzle.no_rules"), true);
			return InteractionResult.SUCCESS;
		}
		nozzle.setCustomSprayRules(rules);
		player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.nozzle.applied"), true);
		return InteractionResult.SUCCESS;
	}

	private InteractionResult applyToExtinguisher(ItemStack stack, Level level, Player player,
			FireExtinguisherBlockEntity extinguisher, int index) {
		if (level.isClientSide)
			return InteractionResult.SUCCESS;

		NozzleSprayRuleSet rules = NozzleSprayRuleSet.fromStack(stack, player.registryAccess());
		if (rules.isEmpty()) {
			player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.nozzle.no_rules"), true);
			return InteractionResult.SUCCESS;
		}
		if (!extinguisher.applyRules(index, rules, player.registryAccess()))
			return InteractionResult.PASS;
		player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.nozzle.applied"), true);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.getAbilities().instabuild) {
			if (!level.isClientSide)
				player.displayClientMessage(Component.translatable("createfirefightingadd.configurator.creative_only"), true);
			return InteractionResultHolder.consume(stack);
		}
		if (level.isClientSide)
			return InteractionResultHolder.success(stack);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider(
				(containerId, inventory, menuPlayer) -> new MultifunctionConfiguratorMenu(containerId, inventory, hand),
				Component.translatable("item.createfirefightingadd.multifunction_configurator")),
				buf -> buf.writeVarInt(hand.ordinal()));
		}
		return InteractionResultHolder.consume(stack);
	}
}
