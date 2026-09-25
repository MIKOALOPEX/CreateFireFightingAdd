package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.util.DirectionalAxisShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Redstone pulses arm or release the latch; a steady signal never retriggers it. */
public class RopeLatchBlock extends AbstractDirectionalAxisBlock
        implements IBE<RopeLatchBlockEntity>, BlockSubLevelCollisionShape,
        dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final MapCodec<RopeLatchBlock> CODEC = simpleCodec(RopeLatchBlock::new);
    private static final DirectionalAxisShaper SHAPE = DirectionalAxisShaper.make(Shapes.or(
        box(3, 0, 3, 13, 2, 13), box(6, 2, 3, 10, 6, 13)));
    private static final DirectionalAxisShaper PHYSICS_SHAPE =
        DirectionalAxisShaper.make(box(1, 0, 1, 15, 0.25, 15));

    public RopeLatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
    @Override public Class<RopeLatchBlockEntity> getBlockEntityClass() { return RopeLatchBlockEntity.class; }
    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockEntityType<? extends RopeLatchBlockEntity> getBlockEntityType() {
        return (BlockEntityType) CreateFireFightingAdd.ROPE_LATCH_BE.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RopeLatchBlockEntity latch)
            latch.sampleSignal();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return PHYSICS_SHAPE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof RopeLatchBlockEntity latch && !latch.isMoving())
            latch.release();
        IBE.onRemove(state, level, pos, replacement);
    }

    @Override
    public void beforeMove(net.minecraft.server.level.ServerLevel origin, net.minecraft.server.level.ServerLevel target,
            BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (origin.getBlockEntity(oldPos) instanceof RopeLatchBlockEntity latch) latch.beginMove();
    }

    @Override
    public void afterMove(net.minecraft.server.level.ServerLevel origin, net.minecraft.server.level.ServerLevel target,
            BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (target.getBlockEntity(newPos) instanceof RopeLatchBlockEntity latch) latch.finishMove();
    }
}
