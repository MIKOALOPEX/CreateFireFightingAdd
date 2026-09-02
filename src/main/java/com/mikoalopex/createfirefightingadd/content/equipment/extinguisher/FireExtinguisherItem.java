package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRuleSet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

public class FireExtinguisherItem extends BlockItem {
	public static final int CAPACITY = 1000;

	public FireExtinguisherItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static FluidStack getFluid(ItemStack stack) {
		return stack.getOrDefault(CreateFireFightingAdd.FIRE_EXTINGUISHER_FLUID.get(),
			net.neoforged.neoforge.fluids.SimpleFluidContent.EMPTY).copy();
	}

	public static NozzleSprayRuleSet getSprayRules(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
		return NozzleSprayRuleSet.fromStack(stack, registries);
	}

	public static void setSprayRules(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries,
			NozzleSprayRuleSet rules) {
		NozzleSprayRuleSet.setOnStack(stack, registries, rules);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null)
			return InteractionResult.PASS;
		if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FireExtinguisherBlockEntity be)
			return be.addBottle(context.getPlayer(), context.getItemInHand());
		if (!context.getPlayer().isShiftKeyDown())
			return InteractionResult.PASS;
		return super.useOn(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
			TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		FluidStack fluid = getFluid(stack);
		if (fluid.isEmpty()) {
			tooltip.add(Component.translatable("createfirefightingadd.fire_extinguisher.empty")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.add(Component.translatable("createfirefightingadd.fire_extinguisher.fluid",
			fluid.getHoverName(), fluid.getAmount(), CAPACITY).withStyle(ChatFormatting.GRAY));
	}
}
