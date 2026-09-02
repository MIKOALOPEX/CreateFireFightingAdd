package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_BE;
import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_ITEM;

import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances;
import com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseEndpointModel;
import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A placeable endpoint for a two-ended fire hose connection.
 *
 * <p>Connection identity and fluid behaviour live in
 * {@link FireHoseBlockEntity}; this block owns placement, interaction, shape,
 * and lifecycle hooks.</p>
 */
public class FireHoseBlock extends WrenchableDirectionalBlock
		implements IBE<FireHoseBlockEntity>, IWrenchable, ContraptionMovementSetting.MovementSettingProvider {

	public static final EnumProperty<FireHoseEndpointModel> APPEARANCE =
		EnumProperty.create("appearance", FireHoseEndpointModel.class);
	public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

	private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(
		Block.box(3, 0, 3, 13, 4, 13), Direction.UP);

	public FireHoseBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(APPEARANCE, FireHoseEndpointModel.DEFAULT)
			.setValue(CONNECTED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(APPEARANCE, CONNECTED);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return FIRE_HOSE_ITEM.get().getDefaultInstance();
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return true;
	}

	@Override
	public ContraptionMovementSetting getContraptionMovementSetting() {
		return ContraptionMovementSetting.MOVABLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE.get(state.getValue(FACING));
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
			LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (level.getBlockEntity(pos) instanceof FireHoseBlockEntity hose)
			HoseFluidTransferBehaviour.onPipeChangeNearby(hose, direction);
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.is(Items.SHEARS)) {
			if (level.isClientSide())
				return ItemInteractionResult.SUCCESS;
			if (level.getBlockEntity(pos) instanceof FireHoseBlockEntity hose) {
				boolean hadConnection = hose.getFireHosePartnerPos() != null;
				FireHoseConnections.disconnect(hose);
				if (hadConnection)
					level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return ItemInteractionResult.SUCCESS;
		}

		ResourceLocation appearance = FireHoseAppearances.fromDyeItem(stack).orElse(null);
		if (appearance == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (level.isClientSide())
			return ItemInteractionResult.SUCCESS;
		if (!(level.getBlockEntity(pos) instanceof FireHoseBlockEntity hose))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		boolean changed = hose.setHoseAppearance(appearance);
		if (changed && !player.getAbilities().instabuild)
			stack.shrink(1);
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public Class<FireHoseBlockEntity> getBlockEntityClass() {
		return FireHoseBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FireHoseBlockEntity> getBlockEntityType() {
		return FIRE_HOSE_BE.get();
	}
}
