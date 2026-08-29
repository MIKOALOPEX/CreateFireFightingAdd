package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class FirefighterHandbookMenu extends AbstractContainerMenu {
	private final InteractionHand hand;
	private FirefighterHandbookSnapshot snapshot;

	public FirefighterHandbookMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, inventory, readHand(buffer), FirefighterHandbookSnapshot.read(buffer));
	}

	public FirefighterHandbookMenu(int containerId, Inventory inventory, InteractionHand hand,
			FirefighterHandbookSnapshot snapshot) {
		super(CreateFireFightingAdd.FIREFIGHTER_HANDBOOK_MENU.get(), containerId);
		this.hand = hand;
		this.snapshot = snapshot;
	}

	public InteractionHand hand() {
		return hand;
	}

	public FirefighterHandbookSnapshot snapshot() {
		return snapshot;
	}

	public void setSnapshot(FirefighterHandbookSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public ItemStack getHandbookStack(Player player) {
		return player.getItemInHand(hand);
	}

	@Override
	public boolean stillValid(Player player) {
		return getHandbookStack(player).getItem() instanceof FirefighterHandbookItem;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	private static InteractionHand readHand(RegistryFriendlyByteBuf buffer) {
		int index = buffer.readVarInt();
		InteractionHand[] hands = InteractionHand.values();
		return index >= 0 && index < hands.length ? hands[index] : InteractionHand.MAIN_HAND;
	}
}
