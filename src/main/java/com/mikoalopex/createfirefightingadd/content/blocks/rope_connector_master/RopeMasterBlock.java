package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Only the master face controls an adjacent endpoint; every face accepts redstone. */
public class RopeMasterBlock extends WrenchableDirectionalBlock implements IBE<RopeMasterBlockEntity> {
    public static final MapCodec<RopeMasterBlock> CODEC = simpleCodec(RopeMasterBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public RopeMasterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RopeMasterBlockEntity master)
            master.sampleSignal();
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RopeMasterBlockEntity master)
            player.openMenu(master, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof RopeMasterBlockEntity master)
            master.dropInventory();
        IBE.onRemove(state, level, pos, next);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> box(0, 3.5, 4, 16, 12.5, 12);
            case Y -> box(4, 0, 3.5, 12, 16, 12.5);
            case Z -> box(4, 3.5, 0, 12, 12.5, 16);
        };
    }

    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override public Class<RopeMasterBlockEntity> getBlockEntityClass() { return RopeMasterBlockEntity.class; }
    @Override public BlockEntityType<? extends RopeMasterBlockEntity> getBlockEntityType() {
        return CreateFireFightingAdd.ROPE_MASTER_BE.get();
    }
}
