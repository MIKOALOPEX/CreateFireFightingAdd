package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

import java.util.ArrayList;
import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRuleSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class FireExtinguisherBlockEntity extends BlockEntity {
	private static final String BOTTLES_TAG = "Bottles";
	private static final String NEXT_FILL_INDEX_TAG = "NextFillIndex";

	private final List<ItemStack> bottles = new ArrayList<>(FireExtinguisherBlock.MAX_BOTTLES);
	private final IFluidHandler fluidHandler = new PlacedBottleFluidHandler();
	private int nextFillIndex;
	private boolean skipDropsOnce;

	public FireExtinguisherBlockEntity(BlockPos pos, BlockState state) {
		super(CreateFireFightingAdd.FIRE_EXTINGUISHER_BE.get(), pos, state);
	}

	public IFluidHandler getFluidHandler(Direction side) {
		return fluidHandler;
	}

	public int getBottleCount() {
		return bottles.size();
	}

	public InteractionResult addBottle(@Nullable Player player, ItemStack stack) {
		if (bottles.size() >= FireExtinguisherBlock.MAX_BOTTLES || stack.isEmpty()
			|| !stack.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
			return InteractionResult.PASS;
		if (!level.isClientSide) {
			bottles.add(stack.copyWithCount(1));
			if (player != null && !player.getAbilities().instabuild)
				stack.shrink(1);
			syncCount();
		}
		return InteractionResult.SUCCESS;
	}

	public InteractionResult removeBottle(Player player, int index) {
		if (index < 0 || index >= bottles.size())
			return InteractionResult.PASS;
		if (!level.isClientSide) {
			ItemStack stack = bottles.remove(index);
			if (!player.getInventory().add(stack))
				Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
					worldPosition.getZ() + 0.5, stack);
			if (nextFillIndex > index)
				nextFillIndex--;
			syncCount();
			if (bottles.isEmpty())
				level.removeBlock(worldPosition, false);
		}
		return InteractionResult.SUCCESS;
	}

	public boolean applyRules(int index, NozzleSprayRuleSet rules, HolderLookup.Provider registries) {
		if (index < 0 || index >= bottles.size())
			return false;
		FireExtinguisherItem.setSprayRules(bottles.get(index), registries, rules);
		markUpdated();
		return true;
	}

	public void dropContents(Level level, BlockPos pos) {
		if (skipDropsOnce) {
			skipDropsOnce = false;
			bottles.clear();
			return;
		}
		for (ItemStack stack : bottles)
			if (!stack.isEmpty())
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
		bottles.clear();
	}

	public void skipNextDrops() {
		skipDropsOnce = true;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (ItemStack stack : bottles)
			list.add(stack.saveOptional(registries));
		tag.put(BOTTLES_TAG, list);
		tag.putInt(NEXT_FILL_INDEX_TAG, nextFillIndex);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		bottles.clear();
		if (tag.contains(BOTTLES_TAG, Tag.TAG_LIST)) {
			ListTag list = tag.getList(BOTTLES_TAG, Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size() && bottles.size() < FireExtinguisherBlock.MAX_BOTTLES; i++) {
				ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
				if (stack.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
					bottles.add(stack.copyWithCount(1));
			}
		}
		nextFillIndex = Math.floorMod(tag.getInt(NEXT_FILL_INDEX_TAG), FireExtinguisherBlock.MAX_BOTTLES);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		loadAdditional(tag, registries);
	}

	@Override
	public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	private void syncCount() {
		if (level != null && !bottles.isEmpty()) {
			int count = Math.max(1, Math.min(FireExtinguisherBlock.MAX_BOTTLES, bottles.size()));
			BlockState state = getBlockState();
			if (state.getValue(FireExtinguisherBlock.COUNT) != count)
				level.setBlock(worldPosition, state.setValue(FireExtinguisherBlock.COUNT, count), Block.UPDATE_ALL);
		}
		markUpdated();
	}

	private void markUpdated() {
		setChanged();
		if (level != null && !level.isClientSide)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private class PlacedBottleFluidHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return FireExtinguisherBlock.MAX_BOTTLES;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			if (tank < 0 || tank >= bottles.size())
				return FluidStack.EMPTY;
			return FireExtinguisherItem.getFluid(bottles.get(tank));
		}

		@Override
		public int getTankCapacity(int tank) {
			return FireExtinguisherItem.CAPACITY;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return tank >= 0 && tank < bottles.size() && canFillBottle(tank, stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty() || bottles.isEmpty())
				return 0;
			int remaining = resource.getAmount();
			int filled = 0;
			int start = Math.floorMod(nextFillIndex, bottles.size());
			for (int checks = 0; checks < bottles.size() && remaining > 0; checks++) {
				int index = (start + checks) % bottles.size();
				if (!canFillBottle(index, resource))
					continue;
				ItemStack bottle = bottles.get(index);
				FluidStack stored = FireExtinguisherItem.getFluid(bottle);
				int room = FireExtinguisherItem.CAPACITY - stored.getAmount();
				int moved = Math.min(room, remaining);
				if (moved <= 0)
					continue;
				if (action.execute()) {
					FluidStack result = stored.isEmpty()
						? resource.copyWithAmount(moved)
						: stored.copyWithAmount(stored.getAmount() + moved);
					bottle.set(CreateFireFightingAdd.FIRE_EXTINGUISHER_FLUID.get(),
						SimpleFluidContent.copyOf(result));
					nextFillIndex = (index + 1) % bottles.size();
				}
				filled += moved;
				remaining -= moved;
			}
			if (action.execute() && filled > 0)
				markUpdated();
			return filled;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}

		private boolean canFillBottle(int index, FluidStack resource) {
			FluidStack stored = FireExtinguisherItem.getFluid(bottles.get(index));
			return stored.isEmpty()
				|| stored.getAmount() < FireExtinguisherItem.CAPACITY
					&& FluidStack.isSameFluidSameComponents(stored, resource);
		}
	}
}
