package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.util.DirectionalAxisShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
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
        implements IBE<RopeLatchBlockEntity>, BlockSubLevelCollisionShape, BlockSubLevelAssemblyListener {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final MapCodec<RopeLatchBlock> CODEC = simpleCodec(RopeLatchBlock::new);
    private static final DirectionalAxisShaper OUTLINE_SHAPE =
        DirectionalAxisShaper.make(box(5, 0, 5, 11, 8, 11));
    private static final DirectionalAxisShaper COLLISION_SHAPE =
        DirectionalAxisShaper.make(box(5, 0, 5, 11, 3, 11));

    public RopeLatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
    @Override public Class<RopeLatchBlockEntity> getBlockEntityClass() { return RopeLatchBlockEntity.class; }
    @Override
    public ItemStack getCloneItemStack(LevelReader level,
            BlockPos pos, BlockState state) {
        return new ItemStack(CreateFireFightingAdd.ROPE_HOOK_ITEM.get());
    }
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
        return OUTLINE_SHAPE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE.get(state.getValue(FACING), state.getValue(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        // Keep the hook out of Sable's collision solver without removing player collision.
        return Shapes.empty();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof RopeLatchBlockEntity latch && !latch.isMoving())
            latch.release();
        IBE.onRemove(state, level, pos, replacement);
    }

    @Override
    public void beforeMove(ServerLevel origin, ServerLevel target,
            BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (origin.getBlockEntity(oldPos) instanceof RopeLatchBlockEntity latch) latch.beginMove();
    }

    @Override
    public void afterMove(ServerLevel origin, ServerLevel target,
            BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (target.getBlockEntity(newPos) instanceof RopeLatchBlockEntity latch) latch.finishMove();
    }
}
