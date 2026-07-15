package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class FireHydrantCabinetScreen extends AbstractContainerScreen<FireHydrantCabinetMenu> {
	private static final ResourceLocation CABINET_BACKGROUND =
		CreateFireFightingAdd.path("textures/gui/fire_hydrant_cabinet/box_null.png");
	private static final ResourceLocation CABINET_ITEMS =
		CreateFireFightingAdd.path("textures/gui/fire_hydrant_cabinet/items.png");
	private static final ResourceLocation PLAYER_INVENTORY =
		CreateFireFightingAdd.path("textures/gui/fire_hydrant_cabinet/create_player_inventory.png");
	private static final int CABINET_WIDTH = 134;
	private static final int CABINET_HEIGHT = 128;
	private static final int PLAYER_INVENTORY_WIDTH = 176;
	private static final int PLAYER_INVENTORY_HEIGHT = 108;
	private static final int PLAYER_INVENTORY_Y = 132;
	private static final int NOZZLE_OVERLAY_X = 10;
	private static final int CONE_OVERLAY_Y = 29;
	private static final int FLAT_OVERLAY_Y = 30;
	private static final int HOSE_OVERLAY_X = 40;
	private static final int HOSE_OVERLAY_Y = 29;

	public FireHydrantCabinetScreen(FireHydrantCabinetMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = PLAYER_INVENTORY_WIDTH;
		imageHeight = PLAYER_INVENTORY_Y + PLAYER_INVENTORY_HEIGHT;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int cabinetX = leftPos + (imageWidth - CABINET_WIDTH) / 2;
		guiGraphics.blit(CABINET_BACKGROUND, cabinetX, topPos, 0, 0, CABINET_WIDTH, CABINET_HEIGHT,
			CABINET_WIDTH, CABINET_HEIGHT);
		renderCabinetContents(guiGraphics, cabinetX, topPos);
		guiGraphics.blit(PLAYER_INVENTORY, leftPos, topPos + PLAYER_INVENTORY_Y, 0, 0,
			PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT, PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT);
	}

	private void renderCabinetContents(GuiGraphics guiGraphics, int cabinetX, int cabinetY) {
		if (menu.getSlot(FireHydrantCabinetBlockEntity.SLOT_HOSE).hasItem())
			guiGraphics.blit(CABINET_ITEMS, cabinetX + HOSE_OVERLAY_X, cabinetY + HOSE_OVERLAY_Y,
				29, 0, 44, 49, 256, 256);

		HandheldNozzleType nozzleType = HandheldNozzleType.fromNozzleStack(
			menu.getSlot(FireHydrantCabinetBlockEntity.SLOT_NOZZLE).getItem());
		if (nozzleType == HandheldNozzleType.CONE) {
			guiGraphics.blit(CABINET_ITEMS, cabinetX + NOZZLE_OVERLAY_X, cabinetY + CONE_OVERLAY_Y,
				0, 0, 24, 49, 256, 256);
		} else if (nozzleType == HandheldNozzleType.FLAT) {
			guiGraphics.blit(CABINET_ITEMS, cabinetX + NOZZLE_OVERLAY_X, cabinetY + FLAT_OVERLAY_Y,
				0, 53, 24, 48, 256, 256);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}
}
