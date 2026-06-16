package com.createfireworkadd.createfirefightingadd.content.blocks.flow_meter;

import com.createfireworkadd.createfirefightingadd.Createfirefightingadd;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Experimental flow monitor. It may be changed or removed in a future version.
 * <p>
 * In-line pipe segment that monitors fluid pressure and flow rate. Data is
 * viewable through Engineer's Goggles (see {@link FlowMeterBlockEntity}).
 */
public class FlowMeterBlock extends Block implements IBE<FlowMeterBlockEntity> {

	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final VoxelShape SHAPE_X = box(0, 3, 3, 16, 13, 13);
	private static final VoxelShape SHAPE_Y = box(3, 0, 3, 13, 16, 13);
	private static final VoxelShape SHAPE_Z = box(3, 3, 0, 13, 13, 16);

	public FlowMeterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING).getAxis()) {
			case X -> SHAPE_X;
			case Y -> SHAPE_Y;
			case Z -> SHAPE_Z;
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction direction = context.getNearestLookingDirection().getOpposite();
		return defaultBlockState().setValue(FACING, direction);
	}

	@Override
	public Class<FlowMeterBlockEntity> getBlockEntityClass() {
		return FlowMeterBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FlowMeterBlockEntity> getBlockEntityType() {
		return Createfirefightingadd.FLOW_METER_BE.get();
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		super.onPlace(state, level, pos, oldState, moved);
		FluidPropagator.propagateChangedPipe(level, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
		if (!state.is(newState.getBlock()))
			FluidPropagator.propagateChangedPipe(level, pos, state);
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}
}
