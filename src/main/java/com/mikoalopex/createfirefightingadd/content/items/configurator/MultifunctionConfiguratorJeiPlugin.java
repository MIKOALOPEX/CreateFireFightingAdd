package com.mikoalopex.createfirefightingadd.content.items.configurator;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleFluidInputHelper;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@JeiPlugin
public class MultifunctionConfiguratorJeiPlugin implements IModPlugin {
	private static final ResourceLocation UID = CreateFireFightingAdd.path("multifunction_configurator");

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGhostIngredientHandler(MultifunctionConfiguratorScreen.class, new GhostHandler());
	}

	private static class GhostHandler implements IGhostIngredientHandler<MultifunctionConfiguratorScreen> {
		@Override
		public <I> List<Target<I>> getTargetsTyped(MultifunctionConfiguratorScreen screen, ITypedIngredient<I> ingredient,
			boolean doStart) {
			return ingredient.getIngredient(NeoForgeTypes.FLUID_STACK)
				.<List<Target<I>>>map(fluid -> fluid.isEmpty() ? List.of() : List.of(cast(new FluidTarget(screen))))
				.orElseGet(() -> ingredient.getItemStack()
					.filter(stack -> NozzleFluidInputHelper.fluidFromContainer(stack).isPresent())
					.<List<Target<I>>>map(stack -> List.of(cast(new ItemTarget(screen))))
					.orElseGet(List::of));
		}

		@Override
		public void onComplete() {
		}
	}

	private record FluidTarget(MultifunctionConfiguratorScreen screen) implements Target<FluidStack> {
		@Override
		public Rect2i getArea() {
			return screen.jeiFluidDropArea();
		}

		@Override
		public void accept(FluidStack ingredient) {
			screen.addRuleFromFluid(ingredient);
		}
	}

	private record ItemTarget(MultifunctionConfiguratorScreen screen) implements Target<ItemStack> {
		@Override
		public Rect2i getArea() {
			return screen.jeiFluidDropArea();
		}

		@Override
		public void accept(ItemStack ingredient) {
			screen.addRuleFromContainer(ingredient);
		}
	}

	@SuppressWarnings("unchecked")
	private static <I> Target<I> cast(Target<?> target) {
		return (Target<I>) target;
	}
}
