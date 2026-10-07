package com.mikoalopex.createfirefightingadd.mixin.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface CreativeSectionMenuAccessor {
    @Invoker("getRowIndexForScroll")
    int firefighting$rowForScroll(float scroll);
}
