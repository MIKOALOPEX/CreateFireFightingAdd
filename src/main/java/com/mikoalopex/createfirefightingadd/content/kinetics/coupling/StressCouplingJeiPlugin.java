package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class StressCouplingJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return CreateFireFightingAdd.path("stress_coupling");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!StressCouplingCompatibility.visible())
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(BallCouplings.ITEM.get())));
    }
}
