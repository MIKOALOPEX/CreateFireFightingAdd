package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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

public class ConeNozzleBlock extends Block implements IBE<ConeNozzleBlockEntity> {

	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final VoxelShape SHAPE_UP = box(4, 0, 4, 12, 16, 12);
	private static final VoxelShape SHAPE_DOWN = box(4, 0, 4, 12, 16, 12);
	private static final VoxelShape SHAPE_NORTH = box(4, 4, 0, 12, 12, 16);
	private static final VoxelShape SHAPE_SOUTH = box(4, 4, 0, 12, 12, 16);
	private static final VoxelShape SHAPE_EAST = box(0, 4, 4, 16, 12, 12);
	private static final VoxelShape SHAPE_WEST = box(0, 4, 4, 16, 12, 12);

	public ConeNozzleBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case UP -> SHAPE_UP;
			case DOWN -> SHAPE_DOWN;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case EAST -> SHAPE_EAST;
			case WEST -> SHAPE_WEST;
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
	public Class<ConeNozzleBlockEntity> getBlockEntityClass() {
		return ConeNozzleBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ConeNozzleBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.CONE_NOZZLE_BE.get();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
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
