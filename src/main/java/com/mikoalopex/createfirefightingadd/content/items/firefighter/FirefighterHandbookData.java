package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class FirefighterHandbookData {
	private static final String ROOT_TAG = "FirefighterHandbook";
	private static final String ID_TAG = "Id";
	private static final String OWNER_TAG = "Owner";
	private static final String OWNER_NAME_TAG = "OwnerName";
	private static final String COUNT_TAG = "Extinguished";
	private static final String COMPLETED_TAG = "Completed";

	private FirefighterHandbookData() {
	}

	public static boolean isBound(ItemStack stack) {
		return owner(stack).isPresent();
	}

	public static Optional<UUID> owner(ItemStack stack) {
		CompoundTag root = readRoot(stack);
		return root.hasUUID(OWNER_TAG) ? Optional.of(root.getUUID(OWNER_TAG)) : Optional.empty();
	}

	public static int count(ItemStack stack) {
		return Math.max(0, readRoot(stack).getInt(COUNT_TAG));
	}

	public static boolean completed(ItemStack stack) {
		CompoundTag root = readRoot(stack);
		return root.getBoolean(COMPLETED_TAG) || root.getInt(COUNT_TAG) >= FirefighterHandbookSnapshot.GOAL;
	}

	public static FirefighterHandbookSnapshot snapshotFromStack(ItemStack stack, boolean serverRecordsEnabled) {
		CompoundTag root = readRoot(stack);
		UUID owner = root.hasUUID(OWNER_TAG) ? root.getUUID(OWNER_TAG) : null;
		String name = root.getString(OWNER_NAME_TAG);
		int count = Math.max(0, root.getInt(COUNT_TAG));
		return new FirefighterHandbookSnapshot(serverRecordsEnabled, owner != null, completed(stack),
			owner, name, count, false, List.of(), List.of());
	}

	public static void bind(ItemStack stack, ServerPlayer player, int count) {
		updateRoot(stack, root -> {
			if (!root.hasUUID(ID_TAG))
				root.putUUID(ID_TAG, UUID.randomUUID());
			root.putUUID(OWNER_TAG, player.getUUID());
			root.putString(OWNER_NAME_TAG, player.getGameProfile().getName());
			root.putInt(COUNT_TAG, Math.max(0, count));
			root.putBoolean(COMPLETED_TAG, count >= FirefighterHandbookSnapshot.GOAL);
		});
	}

	public static void setCount(ItemStack stack, int count) {
		updateRoot(stack, root -> {
			int clamped = Math.max(0, count);
			root.putInt(COUNT_TAG, clamped);
			if (clamped >= FirefighterHandbookSnapshot.GOAL)
				root.putBoolean(COMPLETED_TAG, true);
		});
	}

	public static void syncFromServer(ItemStack stack, FirefighterRecordStore.PlayerRecord record) {
		updateRoot(stack, root -> {
			if (!root.hasUUID(ID_TAG))
				root.putUUID(ID_TAG, UUID.randomUUID());
			root.putUUID(OWNER_TAG, record.id());
			root.putString(OWNER_NAME_TAG, record.name());
			root.putInt(COUNT_TAG, record.extinguished());
			root.putBoolean(COMPLETED_TAG, record.extinguished() >= FirefighterHandbookSnapshot.GOAL);
		});
	}

	private static CompoundTag readRoot(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null)
			return new CompoundTag();
		CompoundTag tag = data.copyTag();
		return tag.contains(ROOT_TAG) ? tag.getCompound(ROOT_TAG) : new CompoundTag();
	}

	private static void updateRoot(ItemStack stack, Consumer<CompoundTag> updater) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag root = tag.contains(ROOT_TAG) ? tag.getCompound(ROOT_TAG) : new CompoundTag();
		updater.accept(root);
		tag.put(ROOT_TAG, root);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}
}
