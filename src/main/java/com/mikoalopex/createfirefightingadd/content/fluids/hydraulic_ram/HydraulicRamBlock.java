package com.mikoalopex.createfirefightingadd.content.fluids.hydraulic_ram;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class HydraulicRamBlock extends HorizontalDirectionalBlock
		implements IBE<HydraulicRamBlockEntity>, IWrenchable, FireFightingWrenchableBlock {
	public static final MapCodec<HydraulicRamBlock> CODEC = simpleCodec(HydraulicRamBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	private static final VoxelShape SHAPE_SOUTH = Shapes.or(
		box(4, 4, 2, 12, 12, 8),
		box(5, 13, 9, 11, 26, 15),
		box(4, 15, 8, 12, 25, 16));
	private static final VoxelShape SHAPE_NORTH = Shapes.or(
		box(4, 4, 8, 12, 12, 14),
		box(5, 13, 1, 11, 26, 7),
		box(4, 15, 0, 12, 25, 8));
	private static final VoxelShape SHAPE_EAST = Shapes.or(
		box(2, 4, 4, 8, 12, 12),
		box(9, 13, 5, 15, 26, 11),
		box(8, 15, 4, 16, 25, 12));
	private static final VoxelShape SHAPE_WEST = Shapes.or(
		box(8, 4, 4, 14, 12, 12),
		box(1, 13, 5, 7, 26, 11),
		box(0, 15, 4, 8, 25, 12));

	public HydraulicRamBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction direction = context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, direction);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case NORTH -> SHAPE_NORTH;
			case EAST -> SHAPE_EAST;
			case WEST -> SHAPE_WEST;
			default -> SHAPE_SOUTH;
		};
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.isClientSide) {
			BlockState flipped = state.setValue(FACING, state.getValue(FACING).getOpposite());
			level.setBlock(pos, flipped, Block.UPDATE_ALL);
			level.scheduleTick(pos, this, 1, TickPriority.HIGH);
			if (level.getBlockEntity(pos) instanceof HydraulicRamBlockEntity ram)
				ram.onDirectionChanged();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		super.onPlace(state, level, pos, oldState, moved);
		if (!state.is(oldState.getBlock()))
			notifyPipeEnds(level, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
		if (!state.is(newState.getBlock()))
			notifyPipeEnds(level, pos, state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
			LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (direction.getAxis() == state.getValue(FACING).getAxis())
			level.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return state;
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
			BlockPos neighborPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, neighborPos, isMoving);
		if (!level.isClientSide)
			level.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		notifyPipeEnds(level, pos, state);
		if (level.getBlockEntity(pos) instanceof HydraulicRamBlockEntity ram)
			ram.onDirectionChanged();
	}

	private static void notifyPipeEnds(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);
		for (Direction side : new Direction[] { facing, facing.getOpposite() }) {
			BlockPos pipePos = pos.relative(side);
			FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
		}
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return CreateFireFightingAdd.HYDRAULIC_RAM_ITEM.get().getDefaultInstance();
	}

	@Override
	public Class<HydraulicRamBlockEntity> getBlockEntityClass() {
		return HydraulicRamBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends HydraulicRamBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.HYDRAULIC_RAM_BE.get();
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	public static Direction inputSide(BlockState state) {
		return state.getValue(FACING).getOpposite();
	}

	public static Direction outputSide(BlockState state) {
		return state.getValue(FACING);
	}

	public static boolean isHorizontal(Direction direction) {
		return Plane.HORIZONTAL.test(direction);
	}
}
