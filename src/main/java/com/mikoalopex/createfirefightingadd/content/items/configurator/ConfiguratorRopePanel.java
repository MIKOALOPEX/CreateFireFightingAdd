package com.mikoalopex.createfirefightingadd.content.items.configurator;

import java.util.function.Consumer;
import com.mikoalopex.createfirefightingadd.content.blocks.rope_latch.RopeSlideSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Paged global rope settings editor; drafts survive page changes without depending on a block registry ID. */
final class ConfiguratorRopePanel {
    static final int FOOTER_Y = 171;
    private static final int ROWS_PER_PAGE = 7;
    private static final String[] KEYS = {"Friction", "GeometricFriction", "ResponseGain", "MaxSpeed",
        "GravityAcceleration", "GravityVelocityRetention", "TautRatio", "ScanIntervalTicks", "SearchDurationSeconds", "SwingDamping", "UpperSwingDamping"};
    private final EditBox[] fields = new EditBox[KEYS.length];
    private final String[] draft = new String[KEYS.length];
    private final Font font;
    private int page;
    private boolean shown;

    int page() { return page; }
    int pageCount() { return (KEYS.length + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE; }

    void turnPage(int direction) {
        page = Math.clamp(page + direction, 0, pageCount() - 1);
        visible(shown);
    }

    ConfiguratorRopePanel(Font font, RopeSlideSettings initial) {
        this.font = font;
        setValues(initial);
    }

    void init(int left, int top, Consumer<AbstractWidget> addWidget) {
        for (int i = 0; i < fields.length; i++) {
            final int index = i;
            String key = "createfirefightingadd.configuration.RopeLatch." + KEYS[i];
            fields[i] = new EditBox(font, left + 184, top + 30 + (i % ROWS_PER_PAGE) * 18, 72, 16, Component.translatable(key));
            fields[i].setMaxLength(16);
            fields[i].setValue(draft[i]);
            fields[i].setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
            fields[i].setResponder(value -> draft[index] = value);
            addWidget.accept(fields[i]);
        }
    }

    void visible(boolean visible) {
        shown = visible;
        for (int i = 0; i < fields.length; i++) {
            EditBox field = fields[i];
            field.visible = visible && i / ROWS_PER_PAGE == page;
            field.active = field.visible;
            if (!field.visible) field.setFocused(false);
        }
    }

    void defaults() { setValues(RopeSlideSettings.DEFAULT); }

    private void setValues(RopeSlideSettings s) {
        double[] values = {s.friction(), s.geometricFriction(), s.responseGain(), s.maxSpeed(),
            s.gravityAcceleration(), s.gravityRetention(), s.tautRatio(), s.scanIntervalTicks(), s.searchDurationSeconds(), s.swingDamping(), s.upperSwingDamping()};
        for (int i = 0; i < values.length; i++) {
            draft[i] = i == 7 || i == 8 ? Integer.toString((int) values[i]) : Double.toString(values[i]);
            if (fields[i] != null) fields[i].setValue(draft[i]);
        }
    }

    RopeSlideSettings read() {
        double[] v = new double[KEYS.length];
        double[] min = {0, 0, 0, 0, 0, 0, 0.5, 1, 5, 0, 0};
        double[] max = {1, 1, 10000, 100, 100, 1, 1, 60, 60, 0.99, 0.99};
        try {
            for (int i = 0; i < v.length; i++) {
                v[i] = Double.parseDouble(draft[i]);
                if (!Double.isFinite(v[i]) || v[i] < min[i] || v[i] > max[i]) return null;
                if ((i == 7 || i == 8) && v[i] != Math.rint(v[i])) return null;
            }
        } catch (NumberFormatException exception) { return null; }
        return new RopeSlideSettings(v[0], v[1], v[2], v[3], v[4], v[5], v[6], (int) v[7], (int) v[8], v[9], v[10]);
    }

    void renderLabels(GuiGraphics graphics) {
        for (int i = 0; i < fields.length; i++) {
            if (i / ROWS_PER_PAGE != page) continue;
            String text = Component.translatable("createfirefightingadd.configuration.RopeLatch." + KEYS[i]).getString();
            graphics.drawString(font, font.plainSubstrByWidth(text, 148), 28, 34 + (i % ROWS_PER_PAGE) * 18, 0xFF404040, false);
        }
        String counter = (page + 1) + "/" + pageCount();
        graphics.drawString(font, counter, 222 - font.width(counter) / 2, FOOTER_Y + 3, 0xFF404040, false);
    }
}
