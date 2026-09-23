package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.SMART_ROPE_CONNECTOR_BE;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.util.DirectionalAxisShaper;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A single attachment point that can retain several independent rope strands. */
public class SmartRopeConnectorBlock extends AbstractDirectionalAxisBlock
	implements IBE<SmartRopeConnectorBlockEntity>, dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener,
        dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape {

	public static final MapCodec<SmartRopeConnectorBlock> CODEC = simpleCodec(SmartRopeConnectorBlock::new);
	private static final DirectionalAxisShaper SHAPE = DirectionalAxisShaper.make(Shapes.or(
		box(3, 0, 3, 13, 2, 13),
		box(6, 2, 3, 10, 6, 13)));
	private static final DirectionalAxisShaper PHYSICS_SHAPE =
        DirectionalAxisShaper.make(box(1, 0, 1, 15, 0.25, 15));

    static {
        com.simibubi.create.impl.contraption.BlockMovementChecksImpl.registerAttachedCheck((state, level, pos, direction) -> {
            BlockState adjacent = level.getBlockState(pos.relative(direction));
            if (state.getBlock() instanceof SmartRopeConnectorBlock && state.getValue(FACING) == direction.getOpposite()
                || adjacent.getBlock() instanceof SmartRopeConnectorBlock && adjacent.getValue(FACING) == direction)
                return com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.SUCCESS;
            return com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS;
        });
    }

	public SmartRopeConnectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public Class<SmartRopeConnectorBlockEntity> getBlockEntityClass() {
		return SmartRopeConnectorBlockEntity.class;
	}

	@Override
	@SuppressWarnings("unchecked")
	public BlockEntityType<? extends SmartRopeConnectorBlockEntity> getBlockEntityType() {
		return (BlockEntityType<SmartRopeConnectorBlockEntity>) (BlockEntityType<?>) SMART_ROPE_CONNECTOR_BE.get();
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.is(SimTags.Items.DESTROYS_ROPE)) {
			if (level.isClientSide())
				return ItemInteractionResult.SUCCESS;
			if (level.getBlockEntity(pos) instanceof SmartRopeConnectorBlockEntity connector)
				return connector.cutLastConnection((ServerPlayer) player)
					? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SmartRopeConnectorBlockEntity connector && !connector.isMoving())
			connector.destroyAllConnections(null, level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS));
		IBE.onRemove(state, level, pos, newState);
	}

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public void beforeMove(net.minecraft.server.level.ServerLevel origin, net.minecraft.server.level.ServerLevel target,
            BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (origin.getBlockEntity(oldPos) instanceof SmartRopeConnectorBlockEntity smart) smart.beginMove();
    }

    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel origin, net.minecraft.server.level.ServerLevel target,
            BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (target.getBlockEntity(newPos) instanceof SmartRopeConnectorBlockEntity smart) smart.finishMove(target);
    }
}
