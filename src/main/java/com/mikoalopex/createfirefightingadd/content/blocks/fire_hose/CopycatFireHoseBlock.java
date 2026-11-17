package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.COPYCAT_FIRE_HOSE_BE;
import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.COPYCAT_FIRE_HOSE_ITEM;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Independent copycat fire-hose endpoint.
 *
 * <p>The saved route owns the shared body disguise; this endpoint owns the port
 * disguise and its payment. A right-click with a block fills the body first and
 * the port second. A wrench recovers one surface per click in the same order,
 * and only an endpoint with both surfaces empty rotates natively.</p>
 */
public class CopycatFireHoseBlock extends FireHoseBlock {

	public CopycatFireHoseBlock(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return COPYCAT_FIRE_HOSE_ITEM.get().getDefaultInstance();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// The port quad mapping depends on the stored material, so it cannot be a baked blockstate model.
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.is(Items.SHEARS))
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		if (!(level.getBlockEntity(pos) instanceof CopycatFireHoseBlockEntity hose))
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		if (!player.mayBuild())
			return ItemInteractionResult.FAIL;
		BlockState material = CopycatFireHose.acceptedState(level, pos, stack, hit);
		if (material == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (level.isClientSide)
			return ItemInteractionResult.SUCCESS;
		return hose.applyCopycatMaterial(material, stack, player)
			? ItemInteractionResult.SUCCESS
			: ItemInteractionResult.FAIL;
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && !player.mayBuild())
			return InteractionResult.FAIL;
		if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CopycatFireHoseBlockEntity hose) {
			boolean recovered = context.getLevel().isClientSide
				? hose.hasCopycatMaterials()
				: hose.releaseCopycatMaterials(context.getClickedPos(), player);
			// Recovering a surface consumes the click; only an already-empty
			// copycat endpoint falls through to Create's native rotation.
			if (recovered)
				return InteractionResult.SUCCESS;
		}
		return super.onWrenched(state, context);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		CopycatFireHoseBlockEntity copycat = level.getBlockEntity(pos) instanceof CopycatFireHoseBlockEntity be
			? be
			: null;
		// Route disposal returns the body payment and removes the block entity.
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (copycat != null && !state.is(newState.getBlock()) && !level.isClientSide
			&& !movedByPiston && !copycat.assembling)
			copycat.dropPortMaterial(pos);
	}

	@Override
	public BlockEntityType<? extends FireHoseBlockEntity> getBlockEntityType() {
		return COPYCAT_FIRE_HOSE_BE.get();
	}
}
