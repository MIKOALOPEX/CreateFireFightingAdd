package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class FireHoseConnectorBlock extends AxisPipeBlock
		implements IBE<FireHoseConnectorBlockEntity>, ProperWaterloggedBlock {
	public static final MapCodec<FireHoseConnectorBlock> CODEC = simpleCodec(FireHoseConnectorBlock::new);
	public static final EnumProperty<Axis> REDSTONE_AXIS = EnumProperty.create("redstone_axis", Axis.class);

	public FireHoseConnectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(AXIS, Axis.X)
			.setValue(REDSTONE_AXIS, Axis.Z)
			.setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis pipeAxis = context.getClickedFace().getAxis();
		Axis redstoneAxis = firstPerpendicularAxis(pipeAxis);
		FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
		return defaultBlockState()
			.setValue(AXIS, pipeAxis)
			.setValue(REDSTONE_AXIS, redstoneAxis)
			.setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
			LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
		BlockState normalizedState = normalizeState(state);
		if (normalizedState != state)
			return normalizedState;

		if (state.getValue(BlockStateProperties.WATERLOGGED))
			world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		if (direction.getAxis() == pipeAxis(state))
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return state;
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		BlockState normalizedState = normalizeState(state);
		if (!level.isClientSide && normalizedState != state) {
			level.setBlock(pos, normalizedState, Block.UPDATE_ALL);
			return;
		}
		super.onPlace(state, level, pos, oldState, movedByPiston);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block otherBlock,
			BlockPos neighborPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, otherBlock, neighborPos, isMoving);
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof FireHoseConnectorBlockEntity connector)
			connector.onNeighborChanged();
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState normalizedState = normalizeState(state);
		if (normalizedState != state) {
			level.setBlock(pos, normalizedState, Block.UPDATE_ALL);
			return;
		}

		FluidPropagator.propagateChangedPipe(level, pos, state);
		if (level.getBlockEntity(pos) instanceof FireHoseConnectorBlockEntity connector)
			connector.refreshAttachedEndpoint();
	}

	@Override
	public InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
		Direction clickedFace = context.getClickedFace();
		if (clickedFace.getAxis() == pipeAxis(state))
			return InteractionResult.PASS;
		if (clickedFace.getAxis() != dialAxis(state))
			return InteractionResult.PASS;

		Level level = context.getLevel();
		if (!level.isClientSide) {
			Axis redstoneAxis = dialAxis(state);
			level.setBlock(context.getClickedPos(), state.setValue(REDSTONE_AXIS, redstoneAxis), Block.UPDATE_ALL);
		}
		return InteractionResult.SUCCESS;
	}

	static Axis pipeAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	static Axis redstoneAxis(BlockState state) {
		Axis pipeAxis = pipeAxis(state);
		Axis redstoneAxis = state.getValue(REDSTONE_AXIS);
		return redstoneAxis == pipeAxis ? firstPerpendicularAxis(pipeAxis) : redstoneAxis;
	}

	static Axis dialAxis(BlockState state) {
		return dialAxis(pipeAxis(state), redstoneAxis(state));
	}

	private static Axis dialAxis(Axis pipeAxis, Axis redstoneAxis) {
		for (Axis axis : Iterate.axes)
			if (axis != pipeAxis && axis != redstoneAxis)
				return axis;
		return Axis.Y;
	}

	private static Axis firstPerpendicularAxis(Axis axis) {
		for (Axis candidate : Iterate.axes)
			if (candidate != axis)
				return candidate;
		return Axis.Y;
	}

	private static BlockState normalizeState(BlockState state) {
		Axis pipeAxis = state.getValue(AXIS);
		Axis redstoneAxis = state.getValue(REDSTONE_AXIS);
		return pipeAxis == redstoneAxis
			? state.setValue(REDSTONE_AXIS, firstPerpendicularAxis(pipeAxis))
			: state;
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(AXIS, BlockStateProperties.WATERLOGGED, REDSTONE_AXIS);
	}

	@Override
	public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		return shapeFor(pipeAxis(state), redstoneAxis(state));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		return shapeFor(pipeAxis(state), redstoneAxis(state));
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return shapeFor(pipeAxis(state), redstoneAxis(state));
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos) {
		return redstoneSupportShape(redstoneAxis(state));
	}

	private static VoxelShape shapeFor(Axis pipeAxis, Axis redstoneAxis) {
		Axis dialAxis = dialAxis(pipeAxis, redstoneAxis);
		VoxelShape pipe = boxForAxes(pipeAxis, 0, 16, redstoneAxis, 3, 13, dialAxis, 3, 13);
		VoxelShape center = boxForAxes(pipeAxis, 2, 14, redstoneAxis, 2, 14, dialAxis, 2, 14);
		VoxelShape redstone = boxForAxes(redstoneAxis, 0, 16, pipeAxis, 4, 12, dialAxis, 4, 12);
		return Shapes.or(pipe, center, redstone);
	}

	private static VoxelShape redstoneSupportShape(Axis redstoneAxis) {
		Axis firstCrossAxis = firstPerpendicularAxis(redstoneAxis);
		Axis secondCrossAxis = dialAxis(redstoneAxis, firstCrossAxis);
		VoxelShape negativeFace = boxForAxes(redstoneAxis, 0, 1, firstCrossAxis, 0, 16, secondCrossAxis, 0, 16);
		VoxelShape positiveFace = boxForAxes(redstoneAxis, 15, 16, firstCrossAxis, 0, 16, secondCrossAxis, 0, 16);
		return Shapes.or(negativeFace, positiveFace);
	}

	private static VoxelShape boxForAxes(Axis firstAxis, double firstMin, double firstMax,
			Axis secondAxis, double secondMin, double secondMax, Axis thirdAxis, double thirdMin, double thirdMax) {
		double[] min = new double[3];
		double[] max = new double[3];
		setAxisBounds(min, max, firstAxis, firstMin, firstMax);
		setAxisBounds(min, max, secondAxis, secondMin, secondMax);
		setAxisBounds(min, max, thirdAxis, thirdMin, thirdMax);
		return Block.box(min[0], min[1], min[2], max[0], max[1], max[2]);
	}

	private static void setAxisBounds(double[] min, double[] max, Axis axis, double axisMin, double axisMax) {
		int index = axis.ordinal();
		min[index] = axisMin;
		max[index] = axisMax;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED)
			? Fluids.WATER.getSource(false)
			: super.getFluidState(state);
	}

	@Override
	public Axis getAxis(BlockState state) {
		return pipeAxis(state);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		Axis pipeAxis = rotateAxis(state.getValue(AXIS), rotation);
		Axis redstoneAxis = rotateAxis(state.getValue(REDSTONE_AXIS), rotation);
		if (pipeAxis == redstoneAxis)
			redstoneAxis = firstPerpendicularAxis(pipeAxis);
		return state
			.setValue(AXIS, pipeAxis)
			.setValue(REDSTONE_AXIS, redstoneAxis);
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(Direction.NORTH));
	}

	private static Axis rotateAxis(Axis axis, Rotation rotation) {
		return rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)).getAxis();
	}

	@Override
	protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
		return false;
	}

	@Override
	public BlockEntityType<? extends FireHoseConnectorBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.FIRE_HOSE_CONNECTOR_BE.get();
	}

	@Override
	public Class<FireHoseConnectorBlockEntity> getBlockEntityClass() {
		return FireHoseConnectorBlockEntity.class;
	}

	@Override
	public MapCodec<? extends AxisPipeBlock> codec() {
		return CODEC;
	}
}
