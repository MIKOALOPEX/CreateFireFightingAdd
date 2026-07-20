package com.mikoalopex.createfirefightingadd.content.kinetics.turbine;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

public class PipelineTurbineBlock extends KineticBlock
		implements IBE<PipelineTurbineBlockEntity>, SimpleWaterloggedBlock, IWrenchable {
	public static final MapCodec<PipelineTurbineBlock> CODEC = simpleCodec(PipelineTurbineBlock::new);
	public static final EnumProperty<Axis> PIPE_AXIS = EnumProperty.create("pipe_axis", Axis.class);
	public static final EnumProperty<Axis> SHAFT_AXIS = EnumProperty.create("shaft_axis", Axis.class);

	public PipelineTurbineBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(PIPE_AXIS, Axis.X)
			.setValue(SHAFT_AXIS, Axis.Y)
			.setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Axis pipeAxis = context.getClickedFace().getAxis();
		FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
		return defaultBlockState()
			.setValue(PIPE_AXIS, pipeAxis)
			.setValue(SHAFT_AXIS, firstPerpendicularAxis(pipeAxis))
			.setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
			LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		if (direction.getAxis() == pipeAxis(state))
			level.scheduleTick(pos, this, 1, TickPriority.HIGH);
		return state;
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block otherBlock,
			BlockPos neighborPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, otherBlock, neighborPos, isMoving);
		if (!level.isClientSide)
			level.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		FluidPropagator.propagateChangedPipe(level, pos, state);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return CreateFireFightingAdd.PIPELINE_TURBINE_ITEM.get().getDefaultInstance();
	}

	@Override
	public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == shaftAxis(state);
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.isClientSide) {
			BlockState rotated = rotateByWrenchFace(state, context.getClickedFace());
			level.setBlock(pos, rotated, Block.UPDATE_ALL);
			level.scheduleTick(pos, this, 1, TickPriority.HIGH);
			if (level.getBlockEntity(pos) instanceof PipelineTurbineBlockEntity turbine)
				turbine.scanNow();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return shaftAxis(state);
	}

	public static Axis pipeAxis(BlockState state) {
		return state.getValue(PIPE_AXIS);
	}

	public static Axis shaftAxis(BlockState state) {
		return state.getValue(SHAFT_AXIS);
	}

	public static Axis dialAxis(BlockState state) {
		return dialAxis(pipeAxis(state), shaftAxis(state));
	}

	private static Axis dialAxis(Axis pipeAxis, Axis shaftAxis) {
		for (Axis axis : Iterate.axes)
			if (axis != pipeAxis && axis != shaftAxis)
				return axis;
		return Axis.Z;
	}

	private static Axis firstPerpendicularAxis(Axis axis) {
		for (Axis candidate : Iterate.axes)
			if (candidate != axis)
				return candidate;
		return Axis.Y;
	}

	private static BlockState rotateByWrenchFace(BlockState state, Direction clickedFace) {
		Axis pipeAxis = pipeAxis(state);
		Axis shaftAxis = shaftAxis(state);
		Axis clickedAxis = clickedFace.getAxis();
		if (clickedAxis == pipeAxis)
			return state.setValue(SHAFT_AXIS, dialAxis(state));

		Axis newShaftAxis = shaftAxis == clickedAxis ? pipeAxis : shaftAxis;
		return state
			.setValue(PIPE_AXIS, clickedAxis)
			.setValue(SHAFT_AXIS, newShaftAxis);
	}

	private static Axis rotateAxis(Axis axis, Rotation rotation) {
		return rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)).getAxis();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PIPE_AXIS, SHAFT_AXIS, BlockStateProperties.WATERLOGGED);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(pipeAxis(state), shaftAxis(state));
	}

	private static VoxelShape shapeFor(Axis pipeAxis, Axis shaftAxis) {
		Axis dialAxis = dialAxis(pipeAxis, shaftAxis);
		VoxelShape firstPort = boxForAxes(pipeAxis, 0, 3, shaftAxis, 2, 14, dialAxis, 2, 14);
		VoxelShape secondPort = boxForAxes(pipeAxis, 13, 16, shaftAxis, 2, 14, dialAxis, 2, 14);
		VoxelShape center = boxForAxes(pipeAxis, 1, 15, shaftAxis, 4, 12, dialAxis, 1, 15);
		return Shapes.or(firstPort, secondPort, center);
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
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state
			.setValue(PIPE_AXIS, rotateAxis(pipeAxis(state), rotation))
			.setValue(SHAFT_AXIS, rotateAxis(shaftAxis(state), rotation));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(Direction.NORTH));
	}

	@Override
	public Class<PipelineTurbineBlockEntity> getBlockEntityClass() {
		return PipelineTurbineBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PipelineTurbineBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.PIPELINE_TURBINE_BE.get();
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected MapCodec<? extends KineticBlock> codec() {
		return CODEC;
	}
}
