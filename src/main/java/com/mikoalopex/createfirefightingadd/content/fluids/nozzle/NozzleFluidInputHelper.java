package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllFluids;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public final class NozzleFluidInputHelper {
	private NozzleFluidInputHelper() {
	}

	public static Optional<NozzleSprayRule> ruleFromContainer(@Nullable Level level, ItemStack stack) {
		Optional<FluidStack> fluid = fluidFromContainer(stack);
		if (fluid.isEmpty())
			return Optional.empty();
		NozzleSprayRule rule = ruleFromFluid(level, fluid.get());
		if (isPotionItem(stack))
			rule = rule.asLocked();
		return Optional.of(rule);
	}

	public static NozzleSprayRule ruleFromFluid(@Nullable Level level, FluidStack stack) {
		if (level != null) {
			Optional<NozzleSprayRule> locked = NozzleGlobalSprayRules.lockedDisplayRule(level, stack);
			if (locked.isPresent())
				return locked.get();
		}
		NozzleSprayRule rule = NozzleSprayRule.of(stack);
		if (isPotionFluid(stack))
			rule = rule.asLocked();
		return rule;
	}

	public static Optional<FluidStack> fluidFromContainer(ItemStack stack) {
		if (stack.isEmpty())
			return Optional.empty();
		Optional<FluidStack> potion = potionFluid(stack);
		if (potion.isPresent())
			return potion;

		ItemStack probe = stack.copyWithCount(1);
		IFluidHandlerItem handler = probe.getCapability(Capabilities.FluidHandler.ITEM);
		if (handler == null)
			return Optional.empty();
		for (int tank = 0; tank < handler.getTanks(); tank++) {
			FluidStack stored = handler.getFluidInTank(tank);
			if (stored.isEmpty())
				continue;
			FluidStack simulated = handler.drain(stored.copyWithAmount(Math.max(1, stored.getAmount())),
				IFluidHandler.FluidAction.SIMULATE);
			if (!simulated.isEmpty())
				return Optional.of(simulated.copyWithAmount(1));
		}
		return Optional.empty();
	}

	private static Optional<FluidStack> potionFluid(ItemStack stack) {
		if (!isPotionItem(stack) || AllFluids.POTION == null)
			return Optional.empty();
		PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		FluidStack fluid = new FluidStack(AllFluids.POTION.get(), 1);
		fluid.set(DataComponents.POTION_CONTENTS, contents);
		return Optional.of(fluid);
	}

	private static boolean isPotionItem(ItemStack stack) {
		return stack.is(Items.POTION)
			|| stack.is(Items.SPLASH_POTION)
			|| stack.is(Items.LINGERING_POTION);
	}

	private static boolean isPotionFluid(FluidStack stack) {
		return !stack.isEmpty()
			&& AllFluids.POTION != null
			&& stack.getFluid() == AllFluids.POTION.get();
	}
}
