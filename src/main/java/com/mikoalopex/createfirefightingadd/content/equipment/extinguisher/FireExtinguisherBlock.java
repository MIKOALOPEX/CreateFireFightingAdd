package com.mikoalopex.createfirefightingadd.content.equipment.extinguisher;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.FireFightingWrenchableBlock;
import com.mikoalopex.createfirefightingadd.content.fluids.nozzle.NozzleSprayRuleSet;
import com.mikoalopex.createfirefightingadd.content.items.configurator.MultifunctionConfiguratorItem;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FireExtinguisherBlock extends Block
		implements IBE<FireExtinguisherBlockEntity>, FireFightingWrenchableBlock, EntityBlock {
	public static final int MAX_BOTTLES = 4;
	public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, MAX_BOTTLES);

	private static final VoxelShape BOTTLE_NW = box(1, 0, 9, 7, 16, 15);
	private static final VoxelShape BOTTLE_NE = box(9, 0, 9, 15, 16, 15);
	private static final VoxelShape BOTTLE_SW = box(1, 0, 1, 7, 16, 7);
	private static final VoxelShape BOTTLE_SE = box(9, 0, 1, 15, 16, 7);
	private static final VoxelShape SINGLE = box(5, 0, 5, 11, 16, 11);
	private static final VoxelShape TWO = Shapes.or(BOTTLE_NW, BOTTLE_NE);
	private static final VoxelShape THREE = Shapes.or(BOTTLE_NW, BOTTLE_NE, BOTTLE_SW);
	private static final VoxelShape FOUR = Shapes.or(BOTTLE_NW, BOTTLE_NE, BOTTLE_SW, BOTTLE_SE);

	public FireExtinguisherBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(COUNT, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COUNT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof FireExtinguisherBlockEntity be)
			be.addBottle(null, stack.copyWithCount(1));
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.getBlockEntity(pos) instanceof FireExtinguisherBlockEntity be) {
			if (stack.getItem() instanceof MultifunctionConfiguratorItem configurator)
				return tryConfigureBottle(configurator, stack, level, player, be, bottleIndex(state, hitResult))
					? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			if (stack.is(CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get()))
				return be.addBottle(player, stack) == InteractionResult.SUCCESS
					? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (level.getBlockEntity(pos) instanceof FireExtinguisherBlockEntity be)
			return be.removeBottle(player, bottleIndex(state, hitResult));
		return InteractionResult.PASS;
	}

	private static boolean tryConfigureBottle(MultifunctionConfiguratorItem configurator, ItemStack stack,
			Level level, Player player, FireExtinguisherBlockEntity be, int index) {
		if (level.isClientSide)
			return true;
		NozzleSprayRuleSet rules = NozzleSprayRuleSet.fromStack(stack, player.registryAccess());
		if (rules.isEmpty()) {
			player.displayClientMessage(
				Component.translatable("createfirefightingadd.configurator.nozzle.no_rules"),
				true);
			return true;
		}
		if (be.applyRules(index, rules, player.registryAccess())) {
			player.displayClientMessage(
				Component.translatable("createfirefightingadd.configurator.nozzle.applied"),
				true);
			return true;
		}
		return false;
	}

	public static int bottleIndex(BlockState state, BlockHitResult hitResult) {
		int count = state.getValue(COUNT);
		if (count <= 1)
			return 0;
		Vec3 local = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(hitResult.getBlockPos()));
		double x = Mth.clamp(local.x, 0.0, 1.0);
		double z = Mth.clamp(local.z, 0.0, 1.0);
		double[][] centers = switch (count) {
			case 2 -> new double[][] {{0.25, 0.5}, {0.75, 0.5}};
			case 3 -> new double[][] {{0.25, 0.75}, {0.75, 0.75}, {0.25, 0.25}};
			default -> new double[][] {{0.25, 0.75}, {0.75, 0.75}, {0.25, 0.25}, {0.75, 0.25}};
		};
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < centers.length; i++) {
			double dx = x - centers[i][0];
			double dz = z - centers[i][1];
			double distance = dx * dx + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
			Player player) {
		return CreateFireFightingAdd.FIRE_EXTINGUISHER_ITEM.get().getDefaultInstance();
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide && player.isCreative()
			&& level.getBlockEntity(pos) instanceof FireExtinguisherBlockEntity be)
			be.skipNextDrops();
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!level.isClientSide && !state.is(newState.getBlock())
			&& level.getBlockEntity(pos) instanceof FireExtinguisherBlockEntity be)
			be.dropContents(level, pos);
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(COUNT)) {
			case 2 -> TWO;
			case 3 -> THREE;
			case 4 -> FOUR;
			default -> SINGLE;
		};
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}

	@Override
	public boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public Class<FireExtinguisherBlockEntity> getBlockEntityClass() {
		return FireExtinguisherBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FireExtinguisherBlockEntity> getBlockEntityType() {
		return CreateFireFightingAdd.FIRE_EXTINGUISHER_BE.get();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return IBE.super.newBlockEntity(pos, state);
	}
}
