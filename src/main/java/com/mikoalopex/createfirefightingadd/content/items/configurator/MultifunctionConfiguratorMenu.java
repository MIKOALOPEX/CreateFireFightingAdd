package com.mikoalopex.createfirefightingadd.content.items.configurator;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MultifunctionConfiguratorMenu extends AbstractContainerMenu {
	public static final int INPUT_SLOT_X = 30;
	public static final int INPUT_SLOT_Y = 25;
	public static final int PLAYER_INVENTORY_BACKGROUND_X = 52;
	public static final int PLAYER_INVENTORY_BACKGROUND_Y = 231;
	private static final int PLAYER_INVENTORY_X = PLAYER_INVENTORY_BACKGROUND_X + 8;
	private static final int PLAYER_INVENTORY_Y = PLAYER_INVENTORY_BACKGROUND_Y + 18;
	private static final int PLAYER_HOTBAR_Y = PLAYER_INVENTORY_BACKGROUND_Y + 76;

	private final InteractionHand hand;
	private final Container input = new SimpleContainer(1);

	public MultifunctionConfiguratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, inventory, readHand(buffer));
	}

	public MultifunctionConfiguratorMenu(int containerId, Inventory inventory, InteractionHand hand) {
		super(CreateFireFightingAdd.MULTIFUNCTION_CONFIGURATOR_MENU.get(), containerId);
		this.hand = hand;

		addSlot(new Slot(input, 0, INPUT_SLOT_X, INPUT_SLOT_Y));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++)
				addSlot(new Slot(inventory, col + row * 9 + 9,
					PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
		}
		for (int col = 0; col < 9; col++)
			addSlot(new Slot(inventory, col, PLAYER_INVENTORY_X + col * 18, PLAYER_HOTBAR_Y));
	}

	public InteractionHand getHand() {
		return hand;
	}

	public ItemStack getToolStack(Player player) {
		return player.getItemInHand(hand);
	}

	public ItemStack getInputStack() {
		return input.getItem(0);
	}

	@Override
	public boolean stillValid(Player player) {
		return getToolStack(player).getItem() instanceof MultifunctionConfiguratorItem;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem())
			return result;

		ItemStack stack = slot.getItem();
		result = stack.copy();
		if (index == 0) {
			if (!moveItemStackTo(stack, 1, slots.size(), true))
				return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, 0, 1, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty())
			slot.setByPlayer(ItemStack.EMPTY);
		else
			slot.setChanged();
		return result;
	}

	private static InteractionHand readHand(RegistryFriendlyByteBuf buffer) {
		int index = buffer.readVarInt();
		InteractionHand[] hands = InteractionHand.values();
		return index >= 0 && index < hands.length ? hands[index] : InteractionHand.MAIN_HAND;
	}
}
