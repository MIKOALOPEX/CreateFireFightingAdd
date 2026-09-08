package com.mikoalopex.createfirefightingadd.content.blocks.traffic_cone;

import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TrafficConeBlock extends Block implements FireFightingWrenchableBlock, EntityBlock {
	private static final VoxelShape SHAPE = box(5, 0, 5, 11, 16, 11);
	private static final VoxelShape SUPPORT_SHAPE =
		Shapes.or(box(0, 0, 0, 16, 1, 16), box(0, 15, 0, 16, 16, 16));

	public TrafficConeBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide && placer instanceof Player player
			&& level.getBlockEntity(pos) instanceof TrafficConeBlockEntity be)
			be.setDirection(player.getViewVector(1.0f));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.PASS;

		if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrafficConeBlockEntity be)
			be.setDirection(player.getViewVector(1.0f));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
			LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		return level.getFluidState(pos).is(FluidTags.WATER) ? Blocks.AIR.defaultBlockState()
			: state;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, Fluid fluid) {
		return fluid.is(FluidTags.WATER) || super.canBeReplaced(state, fluid);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return SUPPORT_SHAPE;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrafficConeBlockEntity(pos, state);
	}
}
