package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

public class FireExtinguisherItemHandler extends FluidHandlerItemStack {
	public FireExtinguisherItemHandler(Supplier<DataComponentType<SimpleFluidContent>> componentType,
			ItemStack container) {
		super(componentType, container, FireExtinguisherItem.CAPACITY);
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return FluidStack.EMPTY;
	}

	public FluidStack drainForSpray(int amount, FluidAction action) {
		return super.drain(amount, action);
	}

	public void setStoredFluid(FluidStack fluid) {
		setFluid(fluid);
	}
}
