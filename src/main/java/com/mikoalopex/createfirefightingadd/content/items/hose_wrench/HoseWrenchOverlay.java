package com.mikoalopex.createfirefightingadd.content.items.hose_wrench;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Renders hose-wrench modes using Create's tool-menu layout and HUD texture.
 * RGB tinting preserves the background contrast with an orange accent.
 */
public final class HoseWrenchOverlay implements LayeredDraw.Layer {
    public static final HoseWrenchOverlay INSTANCE = new HoseWrenchOverlay();

    private static final int SLOT = 50;
    private static final int HEIGHT = 30;
    /** Create's tool menu keeps a minimum length of 220 px; two slots fit in it. */
    private static final int WIDTH = Math.max(HoseWrenchMode.values().length * SLOT + 30, 220);

    /** Create's own tool-menu text colour, a cool blue-white. */
    private static final int NATIVE_TEXT = 0xCCDDFF;
    /** The hose-wrench accent this HUD is built around. */
    private static final int ACCENT = 0xE3826C;
    /** Rotates the native text hue toward the accent while preserving luminance. */
    private static final int TEXT = rotateHue(NATIVE_TEXT, hue(ACCENT) - hue(NATIVE_TEXT));

    /**
     * Create's HUD_BACKGROUND texels are {@code #5674b5} and {@code #516a9b}.
     * Dividing {@code #e3826c} by the bright texel gives these channel scales:
     * the bright texel lands exactly on the hose-wrench orange, while the dark
     * texel keeps the same relative contrast as the native HUD background.
     */
    private static final float TINT_R = 0xE3 / (float) 0x56;
    private static final float TINT_G = 0x82 / (float) 0x74;
    private static final float TINT_B = 0x6C / (float) 0xB5;

    private static final float FOCUS_LIFT = 10.0f;
    private static final float FOCUSED_PANEL_ALPHA = 0.875f;
    private static final float IDLE_PANEL_ALPHA = 0.5f;
    private static final float IDLE_ICON_ALPHA = 0.2f;

    private static float focusLift;

    private HoseWrenchOverlay() {
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!HoseWrenchClientState.visible()) {
            focusLift = 0.0f;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }
        float open = HoseWrenchClientState.openness();
        if (open <= 0.02f) {
            return;
        }

        boolean focused = Screen.hasAltDown();
        updateFocusLift(deltaTracker, focused);

        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() - HEIGHT - 75;
        int alpha = Math.round(255.0f * open);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, -focusLift, 0.0f);
        drawPanel(graphics, x, y, open, focused);
        drawHint(graphics, minecraft, y, alpha);
        drawModes(graphics, minecraft, x, y, open, focused, alpha);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static void updateFocusLift(DeltaTracker deltaTracker, boolean focused) {
        // ToolSelectionScreen#update moves 10% of the remaining distance per
        // client tick; reproduce that curve for the fractional ticks elapsed.
        float deltaTicks = Math.max(0.0f, deltaTracker.getRealtimeDeltaTicks());
        float decay = (float) Math.pow(0.9, deltaTicks);
        focusLift += ((focused ? FOCUS_LIFT : 0.0f) - focusLift) * (1.0f - decay);
        if (focusLift < 0.01f) {
            focusLift = 0.0f;
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, float open, boolean focused) {
        AllGuiTextures background = AllGuiTextures.HUD_BACKGROUND;
        float alpha = (focused ? FOCUSED_PANEL_ALPHA : IDLE_PANEL_ALPHA) * open;
        RenderSystem.setShaderColor(TINT_R, TINT_G, TINT_B, alpha);
        graphics.blit(
            background.location,
            x,
            y,
            background.getStartX(),
            background.getStartY(),
            WIDTH,
            HEIGHT,
            background.getWidth(),
            background.getHeight());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawHint(GuiGraphics graphics, Minecraft minecraft, int y, int alpha) {
        Component hint = Component.translatable("createfirefightingadd.hose_wrench.hint");
        graphics.drawCenteredString(
            minecraft.font,
            hint,
            graphics.guiWidth() / 2,
            y - 10,
            withAlpha(TEXT, alpha));
    }

    private static void drawModes(GuiGraphics graphics, Minecraft minecraft, int x, int y, float open,
            boolean focused, int alpha) {
        Font font = minecraft.font;
        int selected = HoseWrenchClientState.mode().ordinal();
        HoseWrenchMode[] modes = HoseWrenchMode.values();
        // The native panel keeps a 220 px minimum length even with two slots.
        // Centre the slot group inside it instead of leaving all the slack on
        // the right edge.
        int contentStart = x + (WIDTH - modes.length * SLOT) / 2;
        for (int index = 0; index < modes.length; index++) {
            int iconX = contentStart + 16 + index * SLOT;
            boolean isSelected = index == selected;
            float iconAlpha = (focused ? 1.0f : IDLE_ICON_ALPHA) * open;
            if (isSelected) {
                iconAlpha = open;
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                graphics.drawCenteredString(
                    font,
                    modes[index].displayName(),
                    iconX + 8,
                    y + 18,
                    withAlpha(TEXT, alpha));
            }
            int iconY = y + 11 - (isSelected ? 10 : 0);
            // Native tool menu draws a black offset copy, then the white icon.
            RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, iconAlpha);
            icon(index).render(graphics, iconX, iconY + 1);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, iconAlpha);
            icon(index).render(graphics, iconX, iconY);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static AllIcons icon(int index) {
        return index == 0 ? AllIcons.I_TOOL_ROTATE : AllIcons.I_TOOL_MIRROR;
    }

    private static float hue(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0f;
        float green = ((rgb >> 8) & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        if (delta < 1.0e-4f) {
            return 0.0f;
        }
        float hue;
        if (max == red) {
            hue = ((green - blue) / delta) % 6.0f;
        } else if (max == green) {
            hue = (blue - red) / delta + 2.0f;
        } else {
            hue = (red - green) / delta + 4.0f;
        }
        hue *= 60.0f;
        return hue < 0.0f ? hue + 360.0f : hue;
    }

    /**
     * Luminance-preserving hue rotation (the standard 3x3 channel matrix used
     * for colour filters). Each channel is clamped before it is packed again.
     */
    private static int rotateHue(int rgb, float degrees) {
        float radians = (float) Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float red = ((rgb >> 16) & 0xFF) / 255.0f;
        float green = ((rgb >> 8) & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;

        float rotatedRed = (0.213f + cos * 0.787f - sin * 0.213f) * red
            + (0.715f - cos * 0.715f - sin * 0.715f) * green
            + (0.072f - cos * 0.072f + sin * 0.928f) * blue;
        float rotatedGreen = (0.213f - cos * 0.213f + sin * 0.143f) * red
            + (0.715f + cos * 0.285f + sin * 0.140f) * green
            + (0.072f - cos * 0.072f - sin * 0.283f) * blue;
        float rotatedBlue = (0.213f - cos * 0.213f - sin * 0.787f) * red
            + (0.715f - cos * 0.715f + sin * 0.715f) * green
            + (0.072f + cos * 0.928f + sin * 0.072f) * blue;

        int outRed = Math.round(Math.max(0.0f, Math.min(1.0f, rotatedRed)) * 255.0f);
        int outGreen = Math.round(Math.max(0.0f, Math.min(1.0f, rotatedGreen)) * 255.0f);
        int outBlue = Math.round(Math.max(0.0f, Math.min(1.0f, rotatedBlue)) * 255.0f);
        return (outRed << 16) | (outGreen << 8) | outBlue;
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
