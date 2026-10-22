package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class RockerBlock extends HandCrankBlock {
    public RockerBlock(Properties properties) { super(properties); }
    @Override public BlockEntityType<? extends HandCrankBlockEntity> getBlockEntityType() { return Rockers.BLOCK_ENTITY.get(); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RockerBlockEntity be) || player.isSpectator()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        if (stack.is(AllItems.WRENCH.get())) {
            int side = RockerGeometry.selectionSide(local, be.facing(), state.getShape(level, pos).bounds());
            if (side != 0) be.select(side, player);
        } else if (!be.dial(local)) be.use(player);
        return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);
        return InteractionResult.SUCCESS;
    }
}
