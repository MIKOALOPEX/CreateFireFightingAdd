package com.mikoalopex.createfirefightingadd.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Groups the creative tab visually without changing its searchable item list. */
public final class CreativeSections {
    private static final int WIDTH = CreativeSectionLayout.BANNER_WIDTH;
    private static final int ROW_HEIGHT = CreativeSectionLayout.BANNER_HEIGHT;
    private static final ResourceLocation[] FIRE = textures("firefighting", 2);
    private static final List<Header> HEADERS = new ArrayList<>();
    private static int hoveredSection = -1;
    private static long hoverStarted;

    private record Header(int row, int items) {}

    /**
     * Slate UI replaces the creative grid with its own layout. Its screen keeps
     * the real grid origin, visible row count and scroll row in private fields
     * ({@code gridX}, {@code scrollRow}, {@code cols}, {@code rows}); reading
     * them keeps the banner on the same heading row Slate draws while staying an
     * optional, reflection-only link.
     */
    private static final class SlateView {
        private static final String SCREEN_CLASS = "dev.fallingcloud.slate.menu.client.creative.SlateCreativeScreen";
        private static boolean looked;
        private static boolean broken;
        private static Class<?> type;
        private static Field gridX;
        private static Field scrollRow;
        private static Field columns;
        private static Field visibleRows;

        static boolean owns(Object screen) {
            look();
            return type != null && type.isInstance(screen);
        }

        /** Returns {gridX, scrollRow, columns, visibleRows}, or null. */
        static int[] metrics(Object screen) {
            if (broken || !owns(screen))
                return null;
            try {
                int x = gridX.getInt(screen);
                int scroll = scrollRow.getInt(screen);
                int cols = columns.getInt(screen);
                int visible = visibleRows.getInt(screen);
                if (x < 0 || scroll < 0 || cols < CreativeSectionLayout.VANILLA_COLUMNS || visible < 1)
                    return null;
                return new int[] {x, scroll, cols, visible};
            } catch (IllegalAccessException | RuntimeException e) {
                broken = true;
                CreateFireFightingAdd.LOGGER.warn(
                    "Slate creative screen layout is not readable; section banners stay hidden there", e);
                return null;
            }
        }

        private static void look() {
            if (looked)
                return;
            looked = true;
            try {
                type = Class.forName(SCREEN_CLASS);
                gridX = field("gridX");
                scrollRow = field("scrollRow");
                columns = field("cols");
                visibleRows = field("rows");
            } catch (ClassNotFoundException | LinkageError absent) {
                type = null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                broken = true;
                CreateFireFightingAdd.LOGGER.warn(
                    "Slate creative screen layout is not readable; section banners stay hidden there", e);
            }
        }

        private static Field field(String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    private CreativeSections() {}

    private static ResourceLocation[] textures(String name, int count) {
        ResourceLocation[] result = new ResourceLocation[count];
        for (int i = 0; i < count; i++)
            result[i] = CreateFireFightingAdd.path("textures/gui/title/" + name + "_" + (i + 1) + ".png");
        return result;
    }

    public static List<ItemStack> arrange(Collection<ItemStack> original) {
        List<ItemStack> fire = new ArrayList<>();
        for (ItemStack stack : original) {
            if (stack.isEmpty()) continue;
            fire.add(stack);
        }
        HEADERS.clear();
        hoveredSection = -1;
        hoverStarted = 0;
        List<ItemStack> result = new ArrayList<>();
        append(result, fire);
        return result;
    }

    private static void append(List<ItemStack> result, List<ItemStack> items) {
        if (items.isEmpty()) return;
        while (result.size() % 9 != 0) result.add(ItemStack.EMPTY);
        HEADERS.add(new Header(result.size() / 9, items.size()));
        for (int i = 0; i < 9; i++) result.add(ItemStack.EMPTY);
        result.addAll(items);
    }

    public static void resetHover() {
        hoveredSection = -1;
        hoverStarted = 0;
    }

    /**
     * Draws section banners on the creative screen's real grid. In the vanilla
     * layout the anchor is the first picker cell; under Slate it is the grid
     * origin and scroll row that Slate itself uses for headings. Banners are
     * skipped when the active grid geometry cannot be read.
     */
    public static void render(GuiGraphics graphics, CreativeModeInventoryScreen screen,
            CreativeModeInventoryScreen.ItemPickerMenu menu, int leftPos, int topPos,
            int vanillaFirstRow, int mouseX, int mouseY) {
        if (HEADERS.isEmpty()) {
            resetHover();
            return;
        }

        boolean slate = SlateView.owns(screen);
        int left;
        int top;
        int columns;
        int visibleRows;
        boolean reflow;
        int scrollRow;

        if (slate) {
            int[] metrics = SlateView.metrics(screen);
            if (metrics == null) {
                resetHover();
                return;
            }
            left = leftPos + metrics[0];
            top = topPos + CreativeSectionLayout.SLATE_GRID_TOP;
            scrollRow = metrics[1];
            columns = metrics[2];
            visibleRows = metrics[3];
            reflow = true;
        } else {
            if (menu.slots.isEmpty()) {
                resetHover();
                return;
            }
            Slot first = menu.slots.get(0);
            if (first.x <= -1000 || first.y <= -1000) {
                resetHover();
                return;
            }
            left = leftPos + first.x - 1;
            top = topPos + first.y - 1;
            scrollRow = vanillaFirstRow;
            columns = CreativeSectionLayout.VANILLA_COLUMNS;
            visibleRows = CreativeSectionLayout.VANILLA_VISIBLE_ROWS;
            reflow = false;
        }

        int[] itemCounts = new int[HEADERS.size()];
        for (int i = 0; i < HEADERS.size(); i++)
            itemCounts[i] = HEADERS.get(i).items();

        int hovered = -1;
        for (int i = 0; i < HEADERS.size(); i++) {
            int row = reflow
                ? CreativeSectionLayout.slateRow(i, itemCounts, columns) - scrollRow
                : CreativeSectionLayout.vanillaRow(HEADERS.get(i).row(), scrollRow);
            if (row < 0 || row >= visibleRows) continue;
            int y = top + row * ROW_HEIGHT;
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT)
                hovered = i;
        }

        long now = Util.getMillis();
        if (hovered != hoveredSection) {
            hoveredSection = hovered;
            hoverStarted = now;
        }

        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        for (int i = 0; i < HEADERS.size(); i++) {
            int row = reflow
                ? CreativeSectionLayout.slateRow(i, itemCounts, columns) - scrollRow
                : CreativeSectionLayout.vanillaRow(HEADERS.get(i).row(), scrollRow);
            if (row < 0 || row >= visibleRows) continue;
            int y = row * ROW_HEIGHT;
            int frame = hovered == i ? 1 : 0;
            graphics.blit(FIRE[frame], 0, y, 0, 0, WIDTH, ROW_HEIGHT, WIDTH, ROW_HEIGHT);
            Component title = Component.translatable("createfirefightingadd.creative_section.firefighting");
            // Resource-pack fonts can be wider than the space reserved in the banner.
            float scale = Math.min(1f, 68f / Math.max(1, font.width(title)));
            graphics.pose().pushPose();
            graphics.pose().translate(4, y + (ROW_HEIGHT - font.lineHeight * scale) / 2f, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, title, 0, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }
}
