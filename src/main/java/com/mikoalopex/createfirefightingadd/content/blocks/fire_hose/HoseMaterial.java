package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * One independently paid-for copycat surface. The payment is stored with the
 * surface, never as a reference to a player's live stack.
 */
public final class HoseMaterial {
	private BlockState state;
	private ItemStack payment = ItemStack.EMPTY;

	/** Fallback state for a surface that is not filled; only debris may use it. */
	public BlockState state() {
		return state == null ? AllBlocks.COPYCAT_BASE.getDefaultState() : state;
	}

	public boolean filled() {
		return state != null;
	}

	public int emission() {
		return state == null ? 0 : state.getLightEmission();
	}

	public int emission(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
		return state == null ? 0 : state.getLightEmission(level, pos);
	}

	public ItemStack payment() {
		return payment;
	}

	public void fill(BlockState material, ItemStack paid) {
		clear();
		state = material;
		payment = paid.copyWithCount(1);
	}

	/** Moves ownership of an existing payment; the source becomes empty. */
	public void moveFrom(HoseMaterial other) {
		if (other == this || filled())
			throw new IllegalStateException("Material transfer requires a distinct empty destination");
		state = other.state;
		payment = other.payment;
		other.clear();
	}

	public ItemStack clear() {
		ItemStack result = payment;
		state = null;
		payment = ItemStack.EMPTY;
		return result;
	}

	/** Drops ownership without returning an item, used after a creative removal. */
	public void forgetPayment() {
		payment = ItemStack.EMPTY;
	}

	public boolean cycle() {
		if (state == null)
			return false;
		// Copycats+ cycles visual properties, not the Cartesian product of redstone internals.
		// Lamps take priority so one click always changes their visible on/off state.
		if (state.hasProperty(BlockStateProperties.LIT))
			state = state.cycle(BlockStateProperties.LIT);
		else if (state.hasProperty(com.simibubi.create.content.redstone.RoseQuartzLampBlock.POWERING))
			state = state.cycle(com.simibubi.create.content.redstone.RoseQuartzLampBlock.POWERING);
		else if (state.hasProperty(BlockStateProperties.FACING))
			state = state.cycle(BlockStateProperties.FACING);
		else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			state = state.cycle(BlockStateProperties.HORIZONTAL_FACING);
		else if (state.hasProperty(BlockStateProperties.AXIS))
			state = state.cycle(BlockStateProperties.AXIS);
		else if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
			state = state.cycle(BlockStateProperties.HORIZONTAL_AXIS);
		else if (state.hasProperty(BlockStateProperties.OPEN))
			state = state.cycle(BlockStateProperties.OPEN);
		else
			return false;
		return true;
	}

	public void read(CompoundTag tag, HolderLookup.Provider registries) {
		state = tag.contains("Material")
			? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Material"))
			: null;
		if (state != null && state.isAir())
			state = null;
		payment = state == null ? ItemStack.EMPTY : ItemStack.parseOptional(registries, tag.getCompound("Item"));
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		if (state != null)
			tag.put("Material", NbtUtils.writeBlockState(state));
		if (!payment.isEmpty())
			tag.put("Item", payment.save(registries));
		return tag;
	}
}
