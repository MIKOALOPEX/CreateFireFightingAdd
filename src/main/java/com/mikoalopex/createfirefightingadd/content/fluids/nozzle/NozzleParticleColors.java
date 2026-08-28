package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.simibubi.create.AllFluids;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NozzleParticleColors {
	private static final int DEFAULT_BLUE = 0x4D8CFF;

	private NozzleParticleColors() {
	}

	public static NozzleParticlePalette defaultPalette(FluidStack stack) {
		int base = baseColor(stack);
		int light = NozzleParticlePalette.mix(base, 0xFFFFFF, 0.35);
		int dark = NozzleParticlePalette.mix(base, 0x000000, 0.25);
		return new NozzleParticlePalette(
			new NozzleParticlePalette.Entry(base, 60),
			new NozzleParticlePalette.Entry(light, 25),
			new NozzleParticlePalette.Entry(dark, 15));
	}

	public static int baseColor(FluidStack stack) {
		if (stack == null || stack.isEmpty())
			return DEFAULT_BLUE;
		if (stack.getFluid().is(FluidTags.WATER))
			return DEFAULT_BLUE;
		if (stack.getFluid().is(FluidTags.LAVA))
			return 0xFF5A10;
		if (AllFluids.POTION != null && stack.getFluid().isSame(AllFluids.POTION.get()))
			return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();

		ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
		if (id == null)
			return DEFAULT_BLUE;
		int hash = id.toString().hashCode();
		int r = 96 + (hash >>> 16 & 0x7F);
		int g = 96 + (hash >>> 8 & 0x7F);
		int b = 96 + (hash & 0x7F);
		return (r << 16) | (g << 8) | b;
	}
}
