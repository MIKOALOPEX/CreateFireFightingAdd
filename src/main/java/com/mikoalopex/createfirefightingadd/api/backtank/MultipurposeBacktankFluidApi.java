package com.mikoalopex.createfirefightingadd.api.backtank;

import java.util.function.Predicate;

import com.mikoalopex.createfirefightingadd.content.equipment.backtank.MultipurposeBacktankItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Small public entry point for tools that want to consume fluid from an equipped
 * multipurpose backtank without depending on its internal storage tags.
 */
public final class MultipurposeBacktankFluidApi {
	public static final int FLUID_CAPACITY = 2000;
	private static final String FLUID_TAG = "CreateFireFightingAddMultipurposeBacktankFluid";

	private MultipurposeBacktankFluidApi() {
	}

	public static FluidStack getFluid(ItemStack stack, HolderLookup.Provider registries) {
		if (!isBacktank(stack))
			return FluidStack.EMPTY;

		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = data.copyTag();
		if (!tag.contains(FLUID_TAG))
			return FluidStack.EMPTY;

		FluidStack fluid = FluidStack.parseOptional(registries, tag.getCompound(FLUID_TAG));
		return normalize(fluid);
	}

	public static void setFluid(ItemStack stack, HolderLookup.Provider registries, FluidStack fluid) {
		if (!isBacktank(stack))
			return;

		FluidStack normalized = normalize(fluid);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (normalized.isEmpty()) {
				tag.remove(FLUID_TAG);
				return;
			}
			tag.put(FLUID_TAG, normalized.copyWithAmount(Math.min(normalized.getAmount(), FLUID_CAPACITY))
				.saveOptional(registries));
		});
	}

	public static FluidStack drain(LivingEntity entity, int amount, Predicate<FluidStack> filter,
			FluidAction action) {
		if (entity == null || amount <= 0)
			return FluidStack.EMPTY;
		HolderLookup.Provider registries = entity.level().registryAccess();

		for (ItemStack stack : entity.getArmorSlots()) {
			if (!isBacktank(stack))
				continue;
			FluidStack stored = getFluid(stack, registries);
			if (stored.isEmpty() || !filter.test(stored))
				continue;

			FluidStack drained = stored.copyWithAmount(Math.min(amount, stored.getAmount()));
			if (action.execute()) {
				stored.shrink(drained.getAmount());
				setFluid(stack, registries, stored);
			}
			return drained;
		}

		return FluidStack.EMPTY;
	}

	public static boolean hasFluid(LivingEntity entity, Predicate<FluidStack> filter) {
		return !drain(entity, 1, filter, FluidAction.SIMULATE).isEmpty();
	}

	private static boolean isBacktank(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof MultipurposeBacktankItem;
	}

	private static FluidStack normalize(FluidStack stack) {
		if (stack == null || stack.isEmpty() || stack.getAmount() <= 0)
			return FluidStack.EMPTY;
		return stack.copyWithAmount(Math.min(stack.getAmount(), FLUID_CAPACITY));
	}
}
