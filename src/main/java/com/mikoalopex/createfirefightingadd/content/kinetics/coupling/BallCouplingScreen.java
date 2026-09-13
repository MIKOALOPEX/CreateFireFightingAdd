package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BallCouplingScreen extends AbstractContainerScreen<BallCouplingMenu> {
    private final Button[] modes = new Button[3];
    public BallCouplingScreen(BallCouplingMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=176;imageHeight=196;inventoryLabelY=100;
    }
    @Override protected void init() {
        super.init();
        for(int i=0;i<3;i++) {
            final int mode=i;
            modes[i]=addRenderableWidget(Button.builder(Component.translatable("ball_coupling.mode."+i), b -> {
                if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,mode);
            }).bounds(leftPos+7+i*55,topPos+25,52,20).build());
        }
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff393c36);
        g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xffc6c6c6);
        for(var slot:menu.slots) {
            int x=leftPos+slot.x,y=topPos+slot.y;
            g.fill(x-1,y-1,x+17,y+17,0xff373737);g.fill(x,y,x+16,y+16,0xff8b8b8b);
        }
        for(int i=0;i<3;i++) modes[i].active=i!=menu.mode();
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        super.renderLabels(g,mouseX,mouseY);
        g.drawString(font,Component.translatable("ball_coupling.status."+menu.status()),8,62,0x404040,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
    }
}
