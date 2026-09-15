package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class BallCouplingScreen extends AbstractContainerScreen<BallCouplingMenu> {
    private int selectedMode, selectedRole, lower = -45, upper = 45;
    private int dropdown = -1, dragging = -1;
    private boolean initialized;
    private Button modeButton, roleButton;
    private EditBox lowerInput, upperInput;

    public BallCouplingScreen(BallCouplingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 278;
        imageHeight = 274;
        inventoryLabelX = 59;
        inventoryLabelY = 178;
    }

    @Override protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> dropdown = dropdown == 0 ? -1 : 0)
            .bounds(leftPos + 10, topPos + 30, 126, 20).build());
        roleButton = addRenderableWidget(Button.builder(Component.empty(), b -> dropdown = dropdown == 1 ? -1 : 1)
            .bounds(leftPos + 142, topPos + 30, 126, 20).build());
        lowerInput = addRenderableWidget(new EditBox(font, leftPos + 181, topPos + 89, 66, 18, text("lower")));
        upperInput = addRenderableWidget(new EditBox(font, leftPos + 181, topPos + 130, 66, 18, text("upper")));
        lowerInput.setMaxLength(4);
        upperInput.setMaxLength(4);
        addRenderableWidget(Button.builder(text("apply"), b -> submit()).bounds(leftPos + 181, topPos + 153, 66, 20).build());
        updateFields();
    }

    private static Component text(String key) { return Component.translatable("ball_coupling." + key); }

    private void updateFields() {
        lowerInput.setValue(Integer.toString(lower));
        upperInput.setValue(Integer.toString(upper));
    }

    private void submit() {
        if (!initialized) return;
        try {
            int l = Integer.parseInt(lowerInput.getValue()), u = Integer.parseInt(upperInput.getValue());
            if (l < -180 || u > 180 || l > u) return;
            lower = l; upper = u;
            PacketDistributor.sendToServer(new BallCouplingSettingsPacket(menu.containerId, selectedMode, selectedRole, lower, upper));
        } catch (NumberFormatException ignored) {
            // Incomplete input remains local until a valid range is supplied.
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        if (!initialized && menu.synchronizedSettings()) {
            selectedMode = menu.mode(); selectedRole = menu.interfaceMode();
            lower = menu.lowerAngle(); upper = menu.upperAngle();
            updateFields(); initialized = true;
        }
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff393c36);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xffc6c6c6);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xff373737);
            g.fill(x, y, x + 16, y + 16, 0xff8b8b8b);
        }
        modeButton.setMessage(text("mode." + selectedMode));
        roleButton.setMessage(text("interface." + selectedRole));
        g.drawString(font, text("constraint"), leftPos + 10, topPos + 19, 0x404040, false);
        g.drawString(font, text("interface"), leftPos + 142, topPos + 19, 0x404040, false);
        lowerInput.visible = upperInput.visible = selectedMode == 1;
        g.drawString(font, text("status." + menu.status()), leftPos + 10, topPos + 55, 0x404040, false);
        if (selectedMode == 1) {
            int cx = leftPos + 83, cy = topPos + 119;
            for (int degree = -180; degree < 180; degree++) {
                double angle = Math.toRadians(degree);
                int color = degree >= lower && degree <= upper ? 0xff75a66a : 0xff72746d;
                int x = cx + (int) Math.round(Math.sin(angle) * 44), y = cy - (int) Math.round(Math.cos(angle) * 44);
                g.fill(x - 1, y - 1, x + 2, y + 2, color);
            }
            drawHandle(g, cx, cy, lower, 0xffb75c38);
            drawHandle(g, cx, cy, upper, 0xff456b9c);
            g.drawString(font, text("lower"), leftPos + 181, topPos + 77, 0x404040, false);
            g.drawString(font, text("upper"), leftPos + 181, topPos + 118, 0x404040, false);
        }
    }

    private void drawHandle(GuiGraphics g, int cx, int cy, int value, int color) {
        double angle = Math.toRadians(value);
        for (int r = 0; r <= 44; r++) {
            int x = cx + (int) Math.round(Math.sin(angle) * r), y = cy - (int) Math.round(Math.cos(angle) * r);
            g.fill(x, y, x + 2, y + 2, color);
        }
        int x = cx + (int) Math.round(Math.sin(angle) * 44), y = cy - (int) Math.round(Math.cos(angle) * 44);
        g.fill(x - 3, y - 3, x + 4, y + 4, color);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        if (dropdown >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            int x = leftPos + (dropdown == 0 ? 10 : 142), y = topPos + 50;
            for (int i = 0; i < 3; i++) {
                boolean hover = mouseX >= x && mouseX < x + 126 && mouseY >= y + i * 20 && mouseY < y + (i + 1) * 20;
                g.fill(x, y + i * 20, x + 126, y + (i + 1) * 20, hover ? 0xff8c9a87 : 0xff53594f);
                g.drawString(font, text((dropdown == 0 ? "mode." : "interface.") + i), x + 5, y + i * 20 + 6, 0xffffff, false);
            }
            g.pose().popPose();
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!initialized) return true;
        if (dropdown >= 0) {
            int dx = leftPos + (dropdown == 0 ? 10 : 142), dy = topPos + 50;
            if (button == 0 && x >= dx && x < dx + 126 && y >= dy && y < dy + 60) {
                int value = (int) (y - dy) / 20;
                if (dropdown == 0) selectedMode = value; else selectedRole = value;
            }
            dropdown = -1;
            return true;
        }
        if (button == 0 && selectedMode == 1) {
            double dx = x - leftPos - 83, dy = y - topPos - 119;
            double distance = dx * dx + dy * dy;
            if (distance >= 25 && distance <= 2500) {
                double angle = Math.toDegrees(Math.atan2(dx, -dy));
                dragging = Math.abs(Math.IEEEremainder(angle - lower, 360)) <= Math.abs(Math.IEEEremainder(angle - upper, 360)) ? 0 : 1;
                dragAngle(x, y);
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private void dragAngle(double x, double y) {
        int angle = (int) Math.round(Math.toDegrees(Math.atan2(x - leftPos - 83, -(y - topPos - 119))));
        if (dragging == 0) lower = Math.min(angle, upper); else upper = Math.max(angle, lower);
        updateFields();
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging >= 0) { dragAngle(x, y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging >= 0) { dragging = -1; submit(); return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && dropdown >= 0) { dropdown = -1; return true; }
        if (lowerInput.isFocused() || upperInput.isFocused()) {
            if (key == 257 || key == 335) { submit(); return true; }
            if (key != 256) {
                if (lowerInput.isFocused()) lowerInput.keyPressed(key, scan, modifiers);
                else upperInput.keyPressed(key, scan, modifiers);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
