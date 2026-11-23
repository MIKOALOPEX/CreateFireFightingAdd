package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.fluids.FluidStack;
import plus.dragons.createdragonsplus.common.fluids.dye.DyeFluidType;
import plus.dragons.createdragonsplus.common.fluids.dye.DyeVariantRegistry;
import plus.dragons.createdragonsplus.common.registry.CDPDataMaps;

/** Dragons Plus 1.11+ uses namespaced dye variants, including non-vanilla colors. */
final class DragonsPlusColoring {
	private DragonsPlusColoring() {
	}

	static @Nullable ResourceLocation color(FluidStack fluid) {
		if (fluid.getFluidType() instanceof DyeFluidType dye)
			return dye.getVariant().id();
		return fluid.getFluidHolder().getData(CDPDataMaps.FLUID_FAN_COLORING_CATALYSTS);
	}

	static int tint(FluidStack fluid) {
		if (fluid.getFluidType() instanceof DyeFluidType dye)
			return dye.getVariant().color();
		ResourceLocation color = color(fluid);
		return color == null ? 0xFFFFFF : DyeVariantRegistry.get(color).map(variant -> variant.color()).orElse(0xFFFFFF);
	}

	static void rebuild(MinecraftServer server) {
		NozzleSpecialColoring.rebuild();
		NozzleColoringIndex.rebuild(server.getRecipeManager(), server.registryAccess());
	}
}
