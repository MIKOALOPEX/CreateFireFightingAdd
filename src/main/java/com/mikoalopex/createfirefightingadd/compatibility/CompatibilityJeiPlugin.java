package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.ArrayList;
import java.util.List;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class CompatibilityJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    private static final List<ItemStack> HIDDEN = new ArrayList<>();
    @Override public ResourceLocation getPluginUid() { return CreateFireFightingAdd.path("compatibility"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; HIDDEN.clear(); refresh(); }
    @Override public void onRuntimeUnavailable() { runtime = null; HIDDEN.clear(); }

    public static void refresh() {
        if (runtime == null) return;
        var manager = runtime.getIngredientManager();
        var restored = HIDDEN.stream().filter(ComponentPolicies::visible).toList();
        if (!restored.isEmpty()) {
            manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, restored);
            HIDDEN.removeAll(restored);
        }
        var hidden = new ArrayList<ItemStack>();
        for (var entry : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(entry);
            if (!ComponentPolicies.visible(stack) && HIDDEN.stream().noneMatch(previous -> previous.is(entry)))
                hidden.add(stack);
        }
        if (!hidden.isEmpty()) {
            manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
            HIDDEN.addAll(hidden);
        }
    }
}
