package com.mikoalopex.createfirefightingadd.content.equipment.backtank;

import java.util.List;
import java.util.Optional;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.api.backtank.MultipurposeBacktankFluidApi;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

public class MultipurposeBacktankBlock extends HorizontalKineticBlock
		implements IBE<MultipurposeBacktankBlockEntity>, SimpleWaterloggedBlock {
	private static final VoxelShape BODY = Shapes.or(
		Block.box(2.5, 0, 2.5, 13.5, 13, 13.5),
		Block.box(5.5, 0, 5.5, 10.5, 16, 10.5));

	public MultipurposeBacktankBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(HORIZONTAL_FACING, Direction.NORTH)
			.setValue(BlockStateProperties.WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.WATERLOGGED);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
		return super.getStateForPlacement(context)
			.setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(BlockStateProperties.WATERLOGGED)
			? Fluids.WATER.getSource(false)
			: super.getFluidState(state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
			LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (state.getValue(BlockStateProperties.WATERLOGGED))
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		return state;
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block otherBlock, BlockPos neighbourPos,
			boolean isMoving) {
		super.neighborChanged(state, level, pos, otherBlock, neighbourPos, isMoving);
		if (level.isClientSide || !neighbourPos.equals(pos.relative(Direction.DOWN)))
			return;
		withBlockEntityDo(level, pos, MultipurposeBacktankBlockEntity::schedulePumpNetworkUpdate);
	}

	@Override
	public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
		return face == Direction.UP;
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide || stack == null)
			return;

		withBlockEntityDo(level, pos, be -> {
			be.setAirLevel(stack.getOrDefault(AllDataComponents.BACKTANK_AIR, 0));
			be.setFluid(MultipurposeBacktankFluidApi.getFluid(stack, level.registryAccess()));
			if (stack.has(DataComponents.CUSTOM_NAME))
				be.setCustomName(stack.getHoverName());
		});
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return getBlockEntityOptional(level, pos)
			.map(MultipurposeBacktankBlockEntity::getComparatorOutput)
			.orElse(0);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (player == null || player instanceof FakePlayer || player.isShiftKeyDown())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!stack.isEmpty() || !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if (!level.isClientSide) {
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.75f, 1.0f);
			player.setItemSlot(EquipmentSlot.CHEST, getCloneItemStack(level, pos, state));
			level.destroyBlock(pos, false);
		}
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = super.getDrops(state, builder);
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (!(blockEntity instanceof MultipurposeBacktankBlockEntity backtank))
			return drops;

		return drops.stream()
			.peek(stack -> {
				if (stack.is(CreateFireFightingAdd.MULTIPURPOSE_BACKTANK_ITEM.get()))
					copyStorageToStack(stack, backtank);
			})
			.toList();
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		Item item = asItem();
		if (item instanceof BacktankItem.BacktankBlockItem placeable)
			item = placeable.getActualItem();
		ItemStack stack = new ItemStack(item);
		Optional<MultipurposeBacktankBlockEntity> be = getBlockEntityOptional(level, pos);
		be.ifPresent(backtank -> copyStorageToStack(stack, backtank));
		return stack;
	}

	private static void copyStorageToStack(ItemStack stack, MultipurposeBacktankBlockEntity be) {
		stack.set(AllDataComponents.BACKTANK_AIR, be.getAirLevel());
		if (be.getLevel() != null)
			MultipurposeBacktankFluidApi.setFluid(stack, be.getLevel().registryAccess(), be.getFluid());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return BODY;
	}

	@Override
	public Class<MultipurposeBacktankBlockEntity> getBlockEntityClass() {
		return MultipurposeBacktankBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MultipurposeBacktankBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.MULTIPURPOSE_BACKTANK_BE.get();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!level.isClientSide && !state.is(newState.getBlock())) {
			BlockPos pipePos = pos.relative(Direction.DOWN);
			FluidPropagator.propagateChangedPipe(level, pipePos, level.getBlockState(pipePos));
		}
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
