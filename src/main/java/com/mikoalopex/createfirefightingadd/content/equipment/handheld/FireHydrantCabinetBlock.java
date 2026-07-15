package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FireHydrantCabinetBlock extends Block
		implements IBE<FireHydrantCabinetBlockEntity>, FireFightingWrenchableBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 9, 16, 16, 16);
	public static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 7);
	public static final VoxelShape WEST_SHAPE = Block.box(9, 0, 0, 16, 16, 16);
	public static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 7, 16, 16);

	public FireHydrantCabinetBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection();
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (stack.getItem() instanceof HandheldNozzleControllerItem controller && player.isShiftKeyDown())
			return controller.tryBindToCabinet(stack, level, pos, player);

		if (player.isShiftKeyDown())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		openMenu(level, pos, player);
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (player.isShiftKeyDown())
			return InteractionResult.PASS;
		openMenu(level, pos, player);
		return InteractionResult.SUCCESS;
	}

	private static void openMenu(Level level, BlockPos pos, Player player) {
		if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer))
			return;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof FireHydrantCabinetBlockEntity cabinet) {
			cabinet.startOpen();
			serverPlayer.openMenu(cabinet, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof FireHydrantCabinetBlockEntity cabinet)
			cabinet.clearBinding(null);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!level.isClientSide && !state.is(newState.getBlock())
			&& level.getBlockEntity(pos) instanceof FireHydrantCabinetBlockEntity cabinet)
			cabinet.dropContents(level, pos);
		IBE.onRemove(state, level, pos, newState);
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
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state.getValue(FACING));
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	private static VoxelShape shapeFor(Direction facing) {
		return switch (facing) {
			case SOUTH -> SOUTH_SHAPE;
			case EAST -> WEST_SHAPE;
			case WEST -> EAST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}

	@Override
	public Class<FireHydrantCabinetBlockEntity> getBlockEntityClass() {
		return FireHydrantCabinetBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FireHydrantCabinetBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.FIRE_HYDRANT_CABINET_BE.get();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}

	@Override
	public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state,
			BlockEntityType<S> blockEntityType) {
		return blockEntityType == getBlockEntityType()
			? (tickerLevel, pos, tickerState, be) ->
				FireHydrantCabinetBlockEntity.tick(tickerLevel, pos, tickerState, (FireHydrantCabinetBlockEntity) be)
			: null;
	}
}
