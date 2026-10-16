package com.mikoalopex.createfirefightingadd.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.BallCouplings;
import com.mikoalopex.createfirefightingadd.content.blocks.copycat_door.CopycatDoorBlock;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Groups the creative tab visually without changing its searchable item list. */
public final class CreativeSections {
    private static final int WIDTH = 162;
    private static final int ROW_HEIGHT = 18;
    private static final long FRAME_MILLIS = 250;
    private static final ResourceLocation[] FIRE = textures("firefighting", 2);
    private static final ResourceLocation[] POWER = textures("power_train", 4);
    private static final List<Header> HEADERS = new ArrayList<>();
    private static int hoveredSection = -1;
    private static long hoverStarted;

    private record Header(int row, boolean power) {}

    private CreativeSections() {}

    private static ResourceLocation[] textures(String name, int count) {
        ResourceLocation[] result = new ResourceLocation[count];
        for (int i = 0; i < count; i++)
            result[i] = CreateFireFightingAdd.path("textures/gui/title/" + name + "_" + (i + 1) + ".png");
        return result;
    }

    public static List<ItemStack> arrange(Collection<ItemStack> original) {
        List<ItemStack> fire = new ArrayList<>();
        List<ItemStack> power = new ArrayList<>();
        for (ItemStack stack : original) {
            if (stack.isEmpty()) continue;
            boolean transmission = stack.is(CreateFireFightingAdd.SMART_ROPE_CONNECTOR_ITEM.get())
                || stack.is(CreateFireFightingAdd.ROPE_HOOK_ITEM.get())
                || stack.is(CreateFireFightingAdd.ROPE_MASTER_ITEM.get())
                || stack.is(BallCouplings.ITEM.get())
                || stack.getItem() instanceof net.minecraft.world.item.BlockItem item && item.getBlock() instanceof CopycatDoorBlock;
            (transmission ? power : fire).add(stack);
        }
        HEADERS.clear();
        hoveredSection = -1;
        List<ItemStack> result = new ArrayList<>();
        append(result, fire, false);
        append(result, power, true);
        return result;
    }

    private static void append(List<ItemStack> result, List<ItemStack> items, boolean power) {
        if (items.isEmpty()) return;
        while (result.size() % 9 != 0) result.add(ItemStack.EMPTY);
        HEADERS.add(new Header(result.size() / 9, power));
        for (int i = 0; i < 9; i++) result.add(ItemStack.EMPTY);
        result.addAll(items);
    }

    public static void resetHover() {
        hoveredSection = -1;
    }

    public static void render(GuiGraphics graphics, int left, int top, int firstRow, int mouseX, int mouseY) {
        int hovered = -1;
        for (int i = 0; i < HEADERS.size(); i++) {
            int row = HEADERS.get(i).row() - firstRow;
            if (row >= 0 && row < 5 && mouseX >= left && mouseX < left + WIDTH
                    && mouseY >= top + row * ROW_HEIGHT && mouseY < top + (row + 1) * ROW_HEIGHT)
                hovered = i;
        }
        long now = Util.getMillis();
        if (hovered != hoveredSection) {
            hoveredSection = hovered;
            hoverStarted = now;
        }
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < HEADERS.size(); i++) {
            Header header = HEADERS.get(i);
            int row = header.row() - firstRow;
            if (row < 0 || row >= 5) continue;
            int y = top + row * ROW_HEIGHT;
            int frame = hovered == i ? (header.power() ? (int) ((now - hoverStarted) / FRAME_MILLIS % 4) : 1) : 0;
            graphics.blit((header.power() ? POWER : FIRE)[frame], left, y, 0, 0, WIDTH, ROW_HEIGHT, WIDTH, ROW_HEIGHT);
            Component title = Component.translatable("createfirefightingadd.creative_section."
                + (header.power() ? "power_train" : "firefighting"));
            // Resource-pack fonts can be wider than the space reserved in the banner.
            float scale = Math.min(1f, 68f / Math.max(1, font.width(title)));
            graphics.pose().pushPose();
            graphics.pose().translate(left + 4, y + (ROW_HEIGHT - font.lineHeight * scale) / 2f, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, title, 0, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }
}
