package com.mikoalopex.createfirefightingadd.content.equipment.backtank;

import java.util.List;
import java.util.function.Supplier;

import com.mikoalopex.createfirefightingadd.api.backtank.MultipurposeBacktankFluidApi;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

public class MultipurposeBacktankItem extends BacktankItem {
	private static final int FALLBACK_MAX_AIR = 900;

	public MultipurposeBacktankItem(Holder<ArmorMaterial> material, Properties properties,
			ResourceLocation armorTexture, Supplier<BacktankBlockItem> placeable) {
		super(material, properties, armorTexture, placeable);
	}

	public static ItemStack withFullAir(ItemStack stack) {
		stack.set(AllDataComponents.BACKTANK_AIR, safeMaxAirWithoutEnchants());
		return stack;
	}

	@Override
	public ItemStack getDefaultInstance() {
		return withFullAir(super.getDefaultInstance());
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		int maxAir = Math.max(1, safeMaxAir(stack));
		int air = stack.getOrDefault(AllDataComponents.BACKTANK_AIR, 0);
		return Math.round(13.0F * Mth.clamp(air / (float) maxAir, 0, 1));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xEFEFEF;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		FluidStack fluid = MultipurposeBacktankFluidApi.getFluid(stack, context.registries());
		if (!fluid.isEmpty()) {
			tooltip.add(Component.translatable("createfirefightingadd.multipurpose_backtank.fluid",
				fluid.getHoverName(), fluid.getAmount())
				.withStyle(ChatFormatting.GRAY));
		}
	}

	private static int safeMaxAirWithoutEnchants() {
		try {
			return BacktankUtil.maxAirWithoutEnchants();
		} catch (IllegalStateException ignored) {
			return FALLBACK_MAX_AIR;
		}
	}

	private static int safeMaxAir(ItemStack stack) {
		try {
			return BacktankUtil.maxAir(stack);
		} catch (IllegalStateException ignored) {
			return Math.max(FALLBACK_MAX_AIR, stack.getOrDefault(AllDataComponents.BACKTANK_AIR, 0));
		}
	}
}
