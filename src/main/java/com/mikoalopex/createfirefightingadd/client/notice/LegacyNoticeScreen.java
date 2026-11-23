package com.mikoalopex.createfirefightingadd.client.notice;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * One-time client notice about save compatibility when an older release of the
 * other project is installed next to this split release.
 *
 * <p>The screen replaces only a plain title screen, returns to it when closed,
 * and stores the acknowledgement in the client config directory. It never
 * pauses a world or intercepts world loading.</p>
 */
public final class LegacyNoticeScreen extends Screen {
    private static final int PANEL_BG = 0xF0160505;
    private static final int PANEL_ACCENT = 0xFFC62828;
    private static final int PANEL_BORDER = 0xFF7F0000;
    private static final int TITLE_COLOR = 0xFFFF5252;
    private static final int BODY_COLOR = 0xFFF2DEDE;
    private static final int DETECTED_COLOR = 0xFFFFAB91;

    private final Screen parent;
    private final LegacyNoticePolicy.Detected detected;
    private List<FormattedCharSequence> messageLines = List.of();
    private List<FormattedCharSequence> detectedLines = List.of();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public LegacyNoticeScreen(Screen parent, LegacyNoticePolicy.Detected detected) {
        super(Component.translatable("createfirefightingadd.legacy_notice.title"));
        this.parent = parent;
        this.detected = detected;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(460, Math.max(160, this.width - 24));
        int textWidth = this.panelW - 28;
        this.messageLines = this.font.split(
            Component.translatable("createfirefightingadd.legacy_notice.message"), textWidth);
        this.detectedLines = this.font.split(
            Component.translatable("createfirefightingadd.legacy_notice.detected",
                this.detected.modId(), this.detected.version()),
            textWidth);

        int titleBlock = 12 + 9 + 10;
        int bodyBlock = (this.messageLines.size() + this.detectedLines.size()) * (9 + 2);
        this.panelH = titleBlock + bodyBlock + 12;
        int buttonWidth = Math.min(170, this.panelW - 16);
        int blockHeight = this.panelH + 14 + 20;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = Math.max(6, (this.height - blockHeight) / 2);
        int centerX = this.width / 2;
        int buttonY = this.panelY + this.panelH + 14;
        this.addRenderableWidget(Button.builder(
                Component.translatable("createfirefightingadd.legacy_notice.close"), button -> this.onClose())
            .bounds(centerX - buttonWidth / 2, buttonY, buttonWidth, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int right = this.panelX + this.panelW;
        int bottom = this.panelY + this.panelH;
        graphics.fill(this.panelX, this.panelY, right, bottom, PANEL_BG);
        graphics.fill(this.panelX, this.panelY, right, this.panelY + 2, PANEL_ACCENT);
        graphics.renderOutline(this.panelX, this.panelY, this.panelW, this.panelH, PANEL_BORDER);

        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, this.panelY + 12, TITLE_COLOR);
        int textX = this.panelX + 14;
        int textY = this.panelY + 12 + 9 + 10;
        for (FormattedCharSequence line : this.messageLines) {
            graphics.drawString(this.font, line, textX, textY, BODY_COLOR);
            textY += 9 + 2;
        }
        for (FormattedCharSequence line : this.detectedLines) {
            graphics.drawString(this.font, line, textX, textY, DETECTED_COLOR);
            textY += 9 + 2;
        }
    }

    @Override
    public void onClose() {
        LegacyNoticeClientState.acknowledge();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
