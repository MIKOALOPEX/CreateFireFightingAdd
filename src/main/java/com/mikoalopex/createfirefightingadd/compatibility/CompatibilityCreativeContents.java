package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.function.Predicate;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Filters tab contents through the event's mutation API. */
public final class CompatibilityCreativeContents {
    public static void filter(BuildCreativeModeTabContentsEvent event, Predicate<ItemStack> visible) {
        var parent = event.getParentEntries().stream().filter(stack -> !visible.test(stack)).toList();
        var search = event.getSearchEntries().stream().filter(stack -> !visible.test(stack)).toList();
        parent.forEach(stack -> event.remove(stack, TabVisibility.PARENT_TAB_ONLY));
        search.forEach(stack -> event.remove(stack, TabVisibility.SEARCH_TAB_ONLY));
    }
    private CompatibilityCreativeContents() {}
}
