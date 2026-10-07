package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RopeMasterScreen extends AbstractContainerScreen<RopeMasterMenu> {
    public RopeMasterScreen(RopeMasterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 148;
        inventoryLabelY = 54;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff373737);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xffc6c6c6);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x - 1, y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xff373737);
            graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xffffffff);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xff8b8b8b);
        }
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        super.render(graphics, x, y, partialTick);
        renderTooltip(graphics, x, y);
    }
}
