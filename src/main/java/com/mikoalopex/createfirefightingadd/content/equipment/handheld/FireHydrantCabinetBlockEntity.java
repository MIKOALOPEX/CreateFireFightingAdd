package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.AbstractSprayDeviceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class FireHydrantCabinetBlockEntity extends BlockEntity implements MenuProvider {
	public static final int SLOT_HOSE = 0;
	public static final int SLOT_NOZZLE = 1;
	public static final int SLOT_BUCKET = 2;
	public static final int FLUID_CAPACITY = 1000;
	private static final String INVENTORY_TAG = "Inventory";
	private static final String FLUID_TAG = "Fluid";
	private static final String ID_TAG = "HydrantId";
	private static final String BOUND_PLAYER_TAG = "BoundPlayer";
	private static final String DOOR_OPEN_TAG = "DoorOpen";
	private static final int FLUID_SYNC_INTERVAL = 10;

	private final ItemStackHandler inventory = new ItemStackHandler(3) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return switch (slot) {
				case SLOT_HOSE -> stack.is(CreateFireFightingAdd.FIRE_HOSE_ITEM.get());
				case SLOT_NOZZLE -> stack.is(CreateFireFightingAdd.CONE_NOZZLE_ITEM.get())
					|| stack.is(CreateFireFightingAdd.FLAT_NOZZLE_ITEM.get());
				case SLOT_BUCKET -> stack.is(Items.BUCKET);
				default -> false;
			};
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		protected void onContentsChanged(int slot) {
			disconnectIfConnectionSlotChanged(slot);
			markUpdated();
		}
	};

	private final IItemHandler automationInventory = new IItemHandler() {
		@Override
		public int getSlots() {
			return inventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return isSlotInRange(slot) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			int targetSlot = slotForInput(stack);
			if (targetSlot < 0)
				return stack;
			return inventory.insertItem(targetSlot, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (!isSlotInRange(slot))
				return ItemStack.EMPTY;
			return inventory.extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return isSlotInRange(slot) ? inventory.getSlotLimit(slot) : 0;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return isSlotInRange(slot) && slotForInput(stack) >= 0;
		}
	};

	private final FluidTank tank = new FluidTank(FLUID_CAPACITY,
		stack -> !stack.isEmpty() && AbstractSprayDeviceBlockEntity.isFluidSupportedForSpray(level, stack)) {
		@Override
		protected void onContentsChanged() {
			markFluidChanged();
		}
	};

	private UUID hydrantId = UUID.randomUUID();
	private @Nullable UUID boundPlayer;
	private int openCount;
	private boolean syncedDoorOpen;
	private boolean fluidSyncQueued;
	private boolean suppressConnectionSlotDisconnect;
	private long lastFluidSyncGameTime = -FLUID_SYNC_INTERVAL;
	private final LerpedFloat doorAnimation = LerpedFloat.linear();

	public FireHydrantCabinetBlockEntity(BlockPos pos, BlockState state) {
		super(CreateFireFightingAdd.FIRE_HYDRANT_CABINET_BE.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, FireHydrantCabinetBlockEntity be) {
		be.doorAnimation.chase(be.isDoorOpen() ? 1.0 : 0.0, 0.18, LerpedFloat.Chaser.EXP);
		be.doorAnimation.tickChaser();
		if (!level.isClientSide) {
			be.fillBucketFromTank();
			be.flushQueuedFluidSync();
		}
	}

	public IItemHandler getItemHandler(@Nullable Direction side) {
		return automationInventory;
	}

	public ItemStackHandler getItemStackHandler() {
		return inventory;
	}

	public @Nullable IFluidHandler getFluidHandler(@Nullable Direction side) {
		// The cabinet model has custom facing remaps; expose all sides so pipe IO stays predictable.
		return tank;
	}

	public UUID getHydrantId() {
		return hydrantId;
	}

	public FluidStack getFluid() {
		return tank.getFluid();
	}

	public HandheldNozzleType getNozzleType() {
		return HandheldNozzleType.fromNozzleStack(inventory.getStackInSlot(SLOT_NOZZLE));
	}

	public boolean hasHose() {
		return inventory.getStackInSlot(SLOT_HOSE).is(CreateFireFightingAdd.FIRE_HOSE_ITEM.get());
	}

	public boolean canServeHandheldNozzle() {
		return hasHose() && getNozzleType().hasNozzle();
	}

	public boolean isDoorOpen() {
		if (level != null && level.isClientSide)
			return syncedDoorOpen;
		return openCount > 0 || boundPlayer != null;
	}

	public boolean isBoundTo(UUID playerId) {
		return boundPlayer != null && boundPlayer.equals(playerId);
	}

	public boolean hasActiveBinding() {
		return boundPlayer != null;
	}

	public LerpedFloat getDoorAnimation() {
		return doorAnimation;
	}

	public void bindTo(Player player) {
		boolean wasOpen = isDoorOpen();
		boundPlayer = player.getUUID();
		updateDoorState(wasOpen);
	}

	public void clearBinding(@Nullable UUID playerId) {
		if (playerId != null && boundPlayer != null && !boundPlayer.equals(playerId))
			return;
		if (boundPlayer != null) {
			boolean wasOpen = isDoorOpen();
			boundPlayer = null;
			updateDoorState(wasOpen);
		}
	}

	public void forceClearBinding() {
		if (level instanceof ServerLevel serverLevel)
			HandheldNozzleSprayHandler.forceClearCabinetBinding(serverLevel, worldPosition, hydrantId);
		clearBinding(null);
	}

	public void startOpen() {
		boolean wasOpen = isDoorOpen();
		openCount++;
		updateDoorState(wasOpen);
	}

	public void stopOpen() {
		boolean wasOpen = isDoorOpen();
		openCount = Math.max(0, openCount - 1);
		updateDoorState(wasOpen);
	}

	public FluidStack drainForHandheldSpray(int amount, FluidAction action) {
		if (amount <= 0 || !canServeHandheldNozzle())
			return FluidStack.EMPTY;
		return tank.drain(amount, action);
	}

	public void dropContents(Level level, BlockPos pos) {
		forceClearBinding();
		suppressConnectionSlotDisconnect = true;
		try {
			for (int i = 0; i < inventory.getSlots(); i++) {
				ItemStack stack = inventory.getStackInSlot(i);
				if (!stack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
					inventory.setStackInSlot(i, ItemStack.EMPTY);
				}
			}
		} finally {
			suppressConnectionSlotDisconnect = false;
		}
	}

	private void disconnectIfConnectionSlotChanged(int slot) {
		if (suppressConnectionSlotDisconnect || level == null || level.isClientSide)
			return;
		if (slot == SLOT_HOSE || slot == SLOT_NOZZLE)
			forceClearBinding();
	}

	private static boolean isSlotInRange(int slot) {
		return slot >= 0 && slot < 3;
	}

	private static int slotForInput(ItemStack stack) {
		if (stack.isEmpty())
			return -1;
		if (stack.is(CreateFireFightingAdd.FIRE_HOSE_ITEM.get()))
			return SLOT_HOSE;
		if (stack.is(CreateFireFightingAdd.CONE_NOZZLE_ITEM.get())
			|| stack.is(CreateFireFightingAdd.FLAT_NOZZLE_ITEM.get()))
			return SLOT_NOZZLE;
		if (stack.is(Items.BUCKET))
			return SLOT_BUCKET;
		return -1;
	}

	private void fillBucketFromTank() {
		ItemStack bucketSlot = inventory.getStackInSlot(SLOT_BUCKET);
		if (!bucketSlot.is(Items.BUCKET) || tank.getFluidAmount() < 1000)
			return;

		Item bucketItem = tank.getFluid().getFluid().getBucket();
		if (bucketItem == Items.AIR)
			return;

		tank.drain(1000, FluidAction.EXECUTE);
		inventory.setStackInSlot(SLOT_BUCKET, new ItemStack(bucketItem));
	}

	private void markUpdated() {
		setChanged();
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void markFluidChanged() {
		setChanged();
		if (level == null || level.isClientSide)
			return;

		long gameTime = level.getGameTime();
		if (gameTime - lastFluidSyncGameTime >= FLUID_SYNC_INTERVAL) {
			lastFluidSyncGameTime = gameTime;
			fluidSyncQueued = false;
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			return;
		}
		fluidSyncQueued = true;
	}

	private void flushQueuedFluidSync() {
		if (!fluidSyncQueued || level == null)
			return;
		long gameTime = level.getGameTime();
		if (gameTime - lastFluidSyncGameTime < FLUID_SYNC_INTERVAL)
			return;
		lastFluidSyncGameTime = gameTime;
		fluidSyncQueued = false;
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void updateDoorState(boolean wasOpen) {
		boolean isOpen = isDoorOpen();
		syncedDoorOpen = isOpen;
		if (level != null && !level.isClientSide && wasOpen != isOpen)
			playDoorSound(isOpen);
		markUpdated();
	}

	private void playDoorSound(boolean open) {
		if (level == null)
			return;
		level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
			open ? CreateFireFightingAdd.FIRE_HYDRANT_CABINET_OPEN_SOUND.get()
				: CreateFireFightingAdd.FIRE_HYDRANT_CABINET_CLOSE_SOUND.get(),
			SoundSource.BLOCKS, 0.8f, 1.0f);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.createfirefightingadd.fire_hydrant_cabinet");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		if (level == null || !level.isClientSide)
			startOpen();
		return new FireHydrantCabinetMenu(containerId, playerInventory, this);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put(INVENTORY_TAG, inventory.serializeNBT(registries));
		CompoundTag fluidTag = new CompoundTag();
		tank.writeToNBT(registries, fluidTag);
		tag.put(FLUID_TAG, fluidTag);
		tag.putUUID(ID_TAG, hydrantId);
		if (boundPlayer != null)
			tag.putUUID(BOUND_PLAYER_TAG, boundPlayer);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains(INVENTORY_TAG))
			inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_TAG));
		if (tag.contains(FLUID_TAG))
			tank.readFromNBT(registries, tag.getCompound(FLUID_TAG));
		hydrantId = tag.hasUUID(ID_TAG) ? tag.getUUID(ID_TAG) : UUID.randomUUID();
		boundPlayer = tag.hasUUID(BOUND_PLAYER_TAG) ? tag.getUUID(BOUND_PLAYER_TAG) : null;
		syncedDoorOpen = tag.contains(DOOR_OPEN_TAG) ? tag.getBoolean(DOOR_OPEN_TAG) : boundPlayer != null;
		openCount = 0;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		tag.putBoolean(DOOR_OPEN_TAG, isDoorOpen());
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		loadAdditional(tag, registries);
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
