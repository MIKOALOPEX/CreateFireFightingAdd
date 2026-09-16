package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class BallCouplingScreen extends AbstractContainerScreen<BallCouplingMenu> {
    private static final ResourceLocation UNPOWERED = CreateFireFightingAdd.path("textures/gui/ball_coupling/coupling_unpowered.png");
    private static final ResourceLocation POWERED = CreateFireFightingAdd.path("textures/gui/ball_coupling/coupling_powered.png");
    private static final ResourceLocation SWITCH = CreateFireFightingAdd.path("textures/gui/ball_coupling/coupling_switch.png");
    private int selectedMode, selectedRole, lower = -45, upper = 45;
    private int dropdown = -1, dragging = -1;
    private boolean initialized, checkHeld, switchDisconnected, flipRange;
    private Button modeButton, roleButton, flipButton;
    private EditBox lowerInput, upperInput;

    public BallCouplingScreen(BallCouplingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 280;
        imageHeight = 190;
    }

    @Override protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> dropdown = dropdown == 0 ? -1 : 0)
            .bounds(leftPos + 10, topPos + 36, 126, 18).build());
        roleButton = addRenderableWidget(Button.builder(Component.empty(), b -> dropdown = dropdown == 1 ? -1 : 1)
            .bounds(leftPos + 143, topPos + 36, 126, 18).build());
        lowerInput = addRenderableWidget(new EditBox(font, leftPos + 198, topPos + 82, 70, 14, text("lower")));
        upperInput = addRenderableWidget(new EditBox(font, leftPos + 198, topPos + 109, 70, 14, text("upper")));
        flipButton = addRenderableWidget(Button.builder(text("flip_range"), b -> flipRange = !flipRange)
            .bounds(leftPos + 198, topPos + 136, 70, 17).build());
        lowerInput.setMaxLength(4);
        upperInput.setMaxLength(4);
        updateFields();
    }

    private static Component text(String key) { return Component.translatable("ball_coupling." + key); }

    private void updateFields() {
        lowerInput.setValue(Integer.toString(lower));
        upperInput.setValue(Integer.toString(upper));
    }

    private boolean submit() {
        if (!initialized) return false;
        try {
            int l = Integer.parseInt(lowerInput.getValue()), u = Integer.parseInt(upperInput.getValue());
            if (l < -180 || u > 180 || l > u) return false;
            lower = l; upper = u;
            PacketDistributor.sendToServer(new BallCouplingSettingsPacket(menu.containerId, selectedMode, selectedRole,
                lower, upper, flipRange));
            return true;
        } catch (NumberFormatException ignored) {
            // Incomplete input remains local until a valid range is supplied.
            return false;
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        if (!initialized && menu.synchronizedSettings()) {
            selectedMode = menu.mode(); selectedRole = menu.interfaceMode();
            lower = menu.lowerAngle(); upper = menu.upperAngle(); flipRange = menu.flipRange();
            updateFields(); initialized = true;
        }
        boolean checkHovered = mouseX >= leftPos + 248 && mouseX < leftPos + 276
            && mouseY >= topPos + 158 && mouseY < topPos + 187;
        g.blit(checkHeld && checkHovered ? POWERED : UNPOWERED,
            leftPos, topPos, 0, 0, imageWidth, imageHeight, 280, 190);
        if (!menu.connected()) switchDisconnected = false;
        g.blit(SWITCH, leftPos + 199, topPos + 157, 6, menu.connected() && !switchDisconnected ? 34 : 2,
            40, 24, 128, 128);
        modeButton.setMessage(text("mode." + selectedMode));
        roleButton.setMessage(text("interface." + selectedRole));
        g.drawString(font, text("title").copy().withStyle(net.minecraft.ChatFormatting.BOLD),
            leftPos + 8, topPos + 6, 0xffffff, false);
        g.drawString(font, text("constraint").copy().withStyle(net.minecraft.ChatFormatting.BOLD),
            leftPos + 10, topPos + 26, 0x353535, false);
        g.drawString(font, text("interface").copy().withStyle(net.minecraft.ChatFormatting.BOLD),
            leftPos + 143, topPos + 26, 0x353535, false);
        lowerInput.visible = upperInput.visible = selectedMode == 1;
        flipButton.visible = selectedMode == 1;
        if (selectedMode == 1) {
            int cx = leftPos + 96, cy = topPos + 127;
            for (int degree = -180; degree < 180; degree++) {
                double angle = Math.toRadians(degree);
                boolean allowed = degree >= lower && degree <= upper;
                int color = allowed != flipRange ? 0xff75a66a : 0xff72746d;
                int x = cx + (int) Math.round(Math.sin(angle) * 44), y = cy - (int) Math.round(Math.cos(angle) * 44);
                g.fill(x - 1, y - 1, x + 2, y + 2, color);
            }
            drawHandle(g, cx, cy, lower, 0xffb75c38);
            drawHandle(g, cx, cy, upper, 0xff456b9c);
            g.drawString(font, text("lower"), leftPos + 198, topPos + 72, 0x353535, false);
            g.drawString(font, text("upper"), leftPos + 198, topPos + 99, 0x353535, false);
            g.drawString(font, text("allowed_arc"), leftPos + 198, topPos + 126, 0x353535, false);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

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
            int x = leftPos + (dropdown == 0 ? 10 : 143), y = topPos + 54;
            for (int i = 0; i < 3; i++) {
                boolean hover = mouseX >= x && mouseX < x + 126 && mouseY >= y + i * 20 && mouseY < y + (i + 1) * 20;
                g.fill(x, y + i * 20, x + 126, y + (i + 1) * 20, 0xff434343);
                g.fill(x + 1, y + i * 20 + 1, x + 125, y + (i + 1) * 20 - 1, 0xff8b8b8b);
                g.drawString(font, text((dropdown == 0 ? "mode." : "interface.") + i),
                    x + 5, y + i * 20 + 6, hover ? 0xffffff : 0xffe2e2e2, false);
            }
            g.pose().popPose();
        }
        if (selectedMode == 1) {
            g.fill(leftPos + 261, topPos + 142, leftPos + 266, topPos + 147,
                flipRange ? 0xff75a66a : 0xff72746d);
        }
        if (initialized && mouseX >= leftPos + 248 && mouseX < leftPos + 276
                && mouseY >= topPos + 158 && mouseY < topPos + 187)
            g.renderTooltip(font, text("apply"), mouseX, mouseY);
        else if (initialized && menu.connected() && !switchDisconnected
                && mouseX >= leftPos + 199 && mouseX < leftPos + 239
                && mouseY >= topPos + 157 && mouseY < topPos + 181)
            g.renderTooltip(font, text("disconnect"), mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!initialized) return true;
        if (dropdown >= 0) {
            int dx = leftPos + (dropdown == 0 ? 10 : 143), dy = topPos + 54;
            if (button == 0 && x >= dx && x < dx + 126 && y >= dy && y < dy + 60) {
                int value = (int) (y - dy) / 20;
                if (dropdown == 0) selectedMode = value; else selectedRole = value;
            }
            dropdown = -1;
            return true;
        }
        if (button == 0 && x >= leftPos + 248 && x < leftPos + 276
                && y >= topPos + 158 && y < topPos + 187) {
            checkHeld = true;
            submit();
            return true;
        }
        if (button == 0 && menu.connected() && !switchDisconnected
                && x >= leftPos + 199 && x < leftPos + 239
                && y >= topPos + 157 && y < topPos + 181) {
            switchDisconnected = true;
            PacketDistributor.sendToServer(new BallCouplingDisconnectPacket(menu.containerId));
            return true;
        }
        if (button == 0 && selectedMode == 1) {
            double dx = x - leftPos - 96, dy = y - topPos - 127;
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
        int angle = (int) Math.round(Math.toDegrees(Math.atan2(x - leftPos - 96, -(y - topPos - 127))));
        if (dragging == 0) lower = Math.min(angle, upper); else upper = Math.max(angle, lower);
        updateFields();
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (checkHeld) return true;
        if (dragging >= 0) { dragAngle(x, y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && checkHeld) { checkHeld = false; return true; }
        if (dragging >= 0) { dragging = -1; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && dropdown >= 0) { dropdown = -1; return true; }
        if (lowerInput.isFocused() || upperInput.isFocused()) {
            if (key == 257 || key == 335) { return true; }
            if (key != 256) {
                if (lowerInput.isFocused()) lowerInput.keyPressed(key, scan, modifiers);
                else upperInput.keyPressed(key, scan, modifiers);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
