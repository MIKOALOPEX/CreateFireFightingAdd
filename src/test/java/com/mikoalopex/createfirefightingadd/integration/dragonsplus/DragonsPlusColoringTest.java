package com.mikoalopex.createfirefightingadd.integration.dragonsplus;

import java.util.List;
import java.util.Map;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.WaterFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import plus.dragons.createdragonsplus.common.fluids.dye.DyeFluidType;
import plus.dragons.createdragonsplus.common.fluids.dye.DyeVariant;

/** Exercises the current Dragons Plus variant API with a non-vanilla dye. */
public final class DragonsPlusColoringTest {
	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		net.minecraft.server.Bootstrap.bootStrap();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "copper");
		DyeVariant variant = new DyeVariant(id, "test_copper", "Copper", 0xBA7050,
			TagKey.create(Registries.ITEM, id), id.withSuffix("_dye"), id.withSuffix("_concrete"), null, null);
		FluidType type = DyeFluidType.create(variant).create(FluidType.Properties.create(), id, id);
		MappedRegistry<Fluid> registry = (MappedRegistry<Fluid>) BuiltInRegistries.FLUID;
		registry.unfreeze();
		Fluid fluid = new WaterFluid.Source() {
			@Override
			public FluidType getFluidType() {
				return type;
			}
		};
		Registry.register(registry, id, fluid);
		registry.freeze();
		FluidStack stack = new FluidStack(fluid, 1000);
		check(id.equals(DragonsPlusColoring.color(stack)), "Namespaced dye identity must not collapse to a vanilla color");
		check(DragonsPlusColoring.tint(stack) == 0xBA7050, "Particles must use the variant RGB");
		FluidStack snapshot = stack.copyWithAmount(1);
		stack.setAmount(0);
		check(id.equals(DragonsPlusColoring.color(snapshot)), "Emission snapshot must survive tank depletion");
		System.out.println("DragonsPlusColoring: modern variant identity, tint and emission snapshot passed");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
