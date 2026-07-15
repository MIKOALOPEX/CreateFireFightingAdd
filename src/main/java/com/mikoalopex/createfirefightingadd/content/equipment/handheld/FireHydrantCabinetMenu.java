package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class FireHydrantCabinetMenu extends AbstractContainerMenu {
	public static final int CABINET_SLOT_COUNT = 3;
	public static final int CABINET_BACKGROUND_X = 21;
	public static final int NOZZLE_SLOT_X = CABINET_BACKGROUND_X + 15;
	public static final int NOZZLE_SLOT_Y = 60;
	public static final int HOSE_SLOT_X = CABINET_BACKGROUND_X + 52;
	public static final int HOSE_SLOT_Y = 49;
	public static final int BUCKET_SLOT_X = CABINET_BACKGROUND_X + 64;
	public static final int BUCKET_SLOT_Y = 81;
	private static final int PLAYER_INVENTORY_X = 8;
	private static final int PLAYER_INVENTORY_BACKGROUND_Y = 132;
	private static final int PLAYER_INVENTORY_Y = PLAYER_INVENTORY_BACKGROUND_Y + 18;
	private static final int PLAYER_HOTBAR_Y = PLAYER_INVENTORY_BACKGROUND_Y + 76;

	private final FireHydrantCabinetBlockEntity cabinet;

	public FireHydrantCabinetMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, inventory, getCabinet(inventory, buffer.readBlockPos()));
	}

	public FireHydrantCabinetMenu(int containerId, Inventory inventory, FireHydrantCabinetBlockEntity cabinet) {
		super(CreateFireFightingAdd.FIRE_HYDRANT_CABINET_MENU.get(), containerId);
		this.cabinet = cabinet;

		addSlot(new SlotItemHandler(cabinet.getItemStackHandler(), FireHydrantCabinetBlockEntity.SLOT_HOSE,
			HOSE_SLOT_X, HOSE_SLOT_Y));
		addSlot(new SlotItemHandler(cabinet.getItemStackHandler(), FireHydrantCabinetBlockEntity.SLOT_NOZZLE,
			NOZZLE_SLOT_X, NOZZLE_SLOT_Y));
		addSlot(new SlotItemHandler(cabinet.getItemStackHandler(), FireHydrantCabinetBlockEntity.SLOT_BUCKET,
			BUCKET_SLOT_X, BUCKET_SLOT_Y));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++)
				addSlot(new Slot(inventory, col + row * 9 + 9,
					PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
		}
		for (int col = 0; col < 9; col++)
			addSlot(new Slot(inventory, col, PLAYER_INVENTORY_X + col * 18, PLAYER_HOTBAR_Y));
	}

	private static FireHydrantCabinetBlockEntity getCabinet(Inventory inventory, BlockPos pos) {
		BlockEntity be = inventory.player.level().getBlockEntity(pos);
		if (be instanceof FireHydrantCabinetBlockEntity cabinet)
			return cabinet;
		throw new IllegalStateException("Missing fire hydrant cabinet at " + pos);
	}

	public FireHydrantCabinetBlockEntity getCabinet() {
		return cabinet;
	}

	@Override
	public boolean stillValid(Player player) {
		return cabinet.getLevel() == player.level()
			&& !cabinet.isRemoved()
			&& player.distanceToSqr(cabinet.getBlockPos().getX() + 0.5,
				cabinet.getBlockPos().getY() + 0.5,
				cabinet.getBlockPos().getZ() + 0.5) <= 64.0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem())
			return result;

		ItemStack stack = slot.getItem();
		result = stack.copy();
		if (index < CABINET_SLOT_COUNT) {
			if (!moveItemStackTo(stack, CABINET_SLOT_COUNT, slots.size(), true))
				return ItemStack.EMPTY;
		} else if (!moveItemStackToCabinet(stack)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty())
			slot.setByPlayer(ItemStack.EMPTY);
		else
			slot.setChanged();
		return result;
	}

	private boolean moveItemStackToCabinet(ItemStack stack) {
		for (int i = 0; i < CABINET_SLOT_COUNT; i++) {
			Slot slot = slots.get(i);
			if (slot.mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false))
				return true;
		}
		return false;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!player.level().isClientSide)
			cabinet.stopOpen();
	}
}
