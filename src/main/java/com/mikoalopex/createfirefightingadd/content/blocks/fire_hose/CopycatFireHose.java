package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The saved route owns one body payment; endpoint snapshots are never refund
 * authorities. Port payments live on each copycat endpoint instead, so both
 * surfaces can be recovered exactly once.
 */
public final class CopycatFireHose {
	public static BlockState material(Level level, HoseRoute route) {
		if (route == null || !route.copycat.contains("Material"))
			return null;
		BlockState state = NbtUtils.readBlockState(
			level.registryAccess().lookupOrThrow(Registries.BLOCK), route.copycat.getCompound("Material"));
		return state.isAir() ? null : state;
	}

	public static BlockState acceptedState(Level level, BlockPos pos, ItemStack stack,
			BlockHitResult hit) {
		return ((CopycatBlock) AllBlocks.COPYCAT_PANEL.get())
			.getAcceptedBlockState(level, pos, stack, hit.getDirection());
	}

	public static boolean bodyFilled(HoseRoute route) {
		return route != null && route.copycat.contains("Material");
	}

	public static void fillBody(Level level, HoseRoute route, BlockState state, ItemStack payment) {
		route.copycat.put("Material", NbtUtils.writeBlockState(state));
		if (payment.isEmpty())
			route.copycat.remove("Item");
		else
			route.copycat.put("Item", payment.copyWithCount(1).save(level.registryAccess()));
	}

	public static boolean cycleBody(Level level, HoseRoute route) {
		BlockState state = material(level, route);
		if (state == null)
			return false;
		route.copycat.put("Material", NbtUtils.writeBlockState(cycle(state)));
		return true;
	}

	public static BlockState cycle(BlockState state) {
		if (state.hasProperty(BlockStateProperties.LIT))
			return state.cycle(BlockStateProperties.LIT);
		if (state.hasProperty(com.simibubi.create.content.redstone.RoseQuartzLampBlock.POWERING))
			return state.cycle(com.simibubi.create.content.redstone.RoseQuartzLampBlock.POWERING);
		if (state.hasProperty(BlockStateProperties.FACING))
			return state.cycle(BlockStateProperties.FACING);
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			return state.cycle(BlockStateProperties.HORIZONTAL_FACING);
		if (state.hasProperty(BlockStateProperties.AXIS))
			return state.cycle(BlockStateProperties.AXIS);
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
			return state.cycle(BlockStateProperties.HORIZONTAL_AXIS);
		if (state.hasProperty(BlockStateProperties.OPEN))
			return state.cycle(BlockStateProperties.OPEN);
		return state;
	}

	public static ItemStack clearBody(Level level, HoseRoute route) {
		if (route == null || !route.copycat.contains("Material"))
			return ItemStack.EMPTY;
		ItemStack payment = ItemStack.parseOptional(level.registryAccess(), route.copycat.getCompound("Item"));
		route.copycat = new CompoundTag();
		return payment;
	}

	/** Called once when the authoritative route is discarded. */
	static void refund(Level level, HoseRoute route) {
		ItemStack item = clearBody(level, route);
		if (item.isEmpty() || route.nodes.isEmpty())
			return;
		HoseRoute.Node node = route.nodes.stream().filter(n -> n.resolve(level) != null)
			.findFirst().orElse(route.nodes.getFirst());
		Block.popResource(level, node.pos(), item);
	}

	private CopycatFireHose() {
	}
}
