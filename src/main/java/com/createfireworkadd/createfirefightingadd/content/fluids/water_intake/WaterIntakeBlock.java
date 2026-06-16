package com.createfireworkadd.createfirefightingadd.content.fluids.water_intake;

import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class WaterIntakeBlock extends DirectionalKineticBlock
		implements IBE<WaterIntakeBlockEntity> {

	private static final VoxelShape SHAPE = Shapes.block();

	public WaterIntakeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Direction dir = ctx.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, dir);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos,
			BlockState state, Direction face) {
		Direction facing = state.getValue(FACING);
		if (facing.getAxis() == Direction.Axis.Y)
			return face.getAxis().isHorizontal();
		return face == facing.getClockWise()
			|| face == facing.getCounterClockWise();
	}

	@Override
	public Direction.Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X
			? Direction.Axis.Z : Direction.Axis.X;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level,
			BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (level.isClientSide())
			return ItemInteractionResult.SUCCESS;

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof WaterIntakeBlockEntity intake))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		IFluidHandler tank = intake.getTankCapability();
		FluidStack fluid = tank.getFluidInTank(0);

		if (stack.is(Items.BUCKET)) {
			if (fluid.getAmount() < 1000)
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
			stack.shrink(1);
			ItemStack filled = new ItemStack(Items.WATER_BUCKET);
			if (stack.isEmpty()) {
				player.setItemInHand(hand, filled);
			} else {
				player.getInventory().placeItemBackInInventory(filled);
			}
			return ItemInteractionResult.SUCCESS;
		}

		if (stack.is(Items.GLASS_BOTTLE)) {
			if (fluid.getAmount() < 250)
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			tank.drain(250, IFluidHandler.FluidAction.EXECUTE);
			stack.shrink(1);
			ItemStack filled = new ItemStack(Items.POTION); // water bottle
			if (stack.isEmpty()) {
				player.setItemInHand(hand, filled);
			} else {
				player.getInventory().placeItemBackInInventory(filled);
			}
			return ItemInteractionResult.SUCCESS;
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public Class<WaterIntakeBlockEntity> getBlockEntityClass() {
		return WaterIntakeBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends WaterIntakeBlockEntity> getBlockEntityType() {
		return Createfirefightingadd.WATER_INTAKE_BE.get();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos,
			BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof WaterIntakeBlockEntity)
				level.removeBlockEntity(pos);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
