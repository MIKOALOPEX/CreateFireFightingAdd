package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;
import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExtensionLadderBlock extends Block implements IBE<ExtensionLadderBlockEntity>,
	ContraptionMovementSetting.MovementSettingProvider, FireFightingWrenchableBlock {
	private static final VoxelShape SHAPE = box(3, 0, 3, 13, 4, 13);

	public ExtensionLadderBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level,
		BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
		BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		if (!player.mayBuild())
			return InteractionResult.PASS;
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof ExtensionLadderBlockEntity ladder)
			ladder.adjustWithPlayer(player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return true;
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public ContraptionMovementSetting getContraptionMovementSetting() {
		return ContraptionMovementSetting.UNMOVABLE;
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return CreateFireFightingAdd.EXTENSION_LADDER_ITEM.get().getDefaultInstance();
	}

	@Override
	public Class<ExtensionLadderBlockEntity> getBlockEntityClass() {
		return ExtensionLadderBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ExtensionLadderBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.EXTENSION_LADDER_BE.get();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		IBE.onRemove(state, level, pos, newState);
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
