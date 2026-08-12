package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ExtensionLadderBlock extends Block implements IBE<ExtensionLadderBlockEntity>,
	ContraptionMovementSetting.MovementSettingProvider {
	private static final VoxelShape SHAPE = box(4, 0, 4, 12, 4, 12);

	public ExtensionLadderBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
		BlockPos pos, CollisionContext context) {
		return SHAPE;
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
		return ContraptionMovementSetting.MOVABLE;
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
