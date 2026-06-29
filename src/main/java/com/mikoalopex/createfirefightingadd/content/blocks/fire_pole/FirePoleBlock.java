package com.mikoalopex.createfirefightingadd.content.blocks.fire_pole;

import java.util.List;
import java.util.function.Predicate;

import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;
import com.simibubi.create.api.contraption.ContraptionMovementSetting;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FirePoleBlock extends Block
	implements ContraptionMovementSetting.MovementSettingProvider, FireFightingWrenchableBlock {
	public static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new PlacementHelper());
	public static final BooleanProperty TOP_END = BooleanProperty.create("top_end");
	public static final BooleanProperty BOTTOM_END = BooleanProperty.create("bottom_end");
	public static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

	public FirePoleBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(TOP_END, true).setValue(BOTTOM_END, true));
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return true;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TOP_END, BOTTOM_END);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return updateEndStates(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
			BlockPos pos, BlockPos neighborPos) {
		if (direction != Direction.UP && direction != Direction.DOWN)
			return state;
		return updateEndStates(state, level, pos);
	}

	private static BlockState updateEndStates(BlockState state, LevelAccessor level, BlockPos pos) {
		return state
			.setValue(TOP_END, !isFirePole(level.getBlockState(pos.above())))
			.setValue(BOTTOM_END, !isFirePole(level.getBlockState(pos.below())));
	}

	private static boolean isFirePole(BlockState state) {
		return state.getBlock() instanceof FirePoleBlock;
	}

	@Override
	public ContraptionMovementSetting getContraptionMovementSetting() {
		return ContraptionMovementSetting.MOVABLE;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (player.isShiftKeyDown() || !player.mayBuild())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
		if (helper.matchesItem(stack))
			return helper.getOffset(player, level, state, pos, hitResult)
				.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult);

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@MethodsReturnNonnullByDefault
	private static class PlacementHelper implements IPlacementHelper {
		@Override
		public Predicate<ItemStack> getItemPredicate() {
			return stack -> stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof FirePoleBlock;
		}

		@Override
		public Predicate<BlockState> getStatePredicate() {
			return state -> state.getBlock() instanceof FirePoleBlock;
		}

		@Override
		public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
				BlockHitResult ray) {
			List<Direction> directions =
				IPlacementHelper.orderedByDistance(pos, ray.getLocation(),
					direction -> direction.getAxis() == Direction.Axis.Y);

			for (Direction direction : directions) {
				int poles = attachedPoles(level, pos, direction);
				BlockPos newPos = pos.relative(direction, poles + 1);
				if (level.getBlockState(newPos).canBeReplaced())
					return PlacementOffset.success(newPos, placed -> placed);
			}

			return PlacementOffset.fail();
		}

		private int attachedPoles(Level level, BlockPos pos, Direction direction) {
			BlockPos checkPos = pos.relative(direction);
			int count = 0;
			while (level.getBlockState(checkPos).getBlock() instanceof FirePoleBlock) {
				count++;
				checkPos = checkPos.relative(direction);
			}
			return count;
		}
	}
}
