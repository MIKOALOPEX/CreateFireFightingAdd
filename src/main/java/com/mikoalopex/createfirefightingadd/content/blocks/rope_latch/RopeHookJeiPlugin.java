package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.util.ArrayList;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class RopeHookJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() {
        return CreateFireFightingAdd.path("rope_hook");
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        var hidden = new ArrayList<ItemStack>();
        hidden.add(new ItemStack(CreateFireFightingAdd.ROPE_LATCH_ITEM.get()));
        if (!RopeLatchCompatibility.available()) hidden.add(new ItemStack(CreateFireFightingAdd.ROPE_HOOK_ITEM.get()));
        if (!RopeLatchCompatibility.available()) hidden.add(new ItemStack(CreateFireFightingAdd.ROPE_MASTER_ITEM.get()));
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
    }
}
