package com.mikoalopex.createfirefightingadd.mixin.client;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.client.CreativeSections;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeSectionScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;

    protected CreativeSectionScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        shift = At.Shift.AFTER))
    private void firefighting$banners(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (selectedTab != CreateFireFightingAdd.CREATIVE_TAB.get()) {
            CreativeSections.resetHover();
            return;
        }
        int row = ((CreativeSectionMenuAccessor) menu).firefighting$rowForScroll(scrollOffs);
        // Draw above empty slots with depth testing, before tooltips.
        graphics.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            CreativeSections.render(graphics, leftPos + 8, topPos + 17, row, mouseX, mouseY);
            graphics.flush();
        } finally {
            RenderSystem.disableDepthTest();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void firefighting$resetHover(CallbackInfo ci) {
        CreativeSections.resetHover();
    }
}
