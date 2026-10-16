package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.foundation.block.IHaveBigOutline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Single-block doors share Create's material inventory and wrench removal path. */
public abstract class CopycatDoorBlock extends CopycatBlock implements IHaveBigOutline {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty VISIBLE = SlidingDoorBlock.VISIBLE;

    protected CopycatDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false).setValue(POWERED, false).setValue(VISIBLE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, POWERED, VISIBLE);
    }

    @Override
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return CopycatDoors.BLOCK_ENTITY.get();
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (this instanceof MultidirectionalTrapdoorBlock) return null;
        return type == CopycatDoors.BLOCK_ENTITY.get() ? (world, pos, current, entity) ->
            ((CopycatDoorBlockEntity) entity).tick() : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return this instanceof MultidirectionalTrapdoorBlock || state.getValue(VISIBLE)
            ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DoorGeometry.shape(state);
    }

    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter level, BlockPos pos, BlockPos other, BlockState state) {
        return !state.getValue(OPEN) && state.getValue(VISIBLE)
            && level.getBlockState(other).equals(state);
    }

    @Override
    public boolean canFaceBeOccluded(BlockState state, Direction face) { return false; }

    @Override
    public boolean shouldFaceAlwaysRender(BlockState state, Direction face) { return true; }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        InteractionResult material = super.onWrenched(state, context);
        if (material.consumesAction()) return material;
        // Sneak removal calls this method first; do not rotate the block being collected.
        if (!context.getPlayer().isShiftKeyDown()) {
            context.getLevel().setBlock(context.getClickedPos(), getRotatedBlockState(state, context.getClickedFace()), 3);
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // Let the wrench's useOn dispatch rotation, material removal or sneak pickup.
        if (stack.getItem() instanceof WrenchItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) setOpen(level, pos, state, !state.getValue(OPEN), player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public void setOpen(Level level, BlockPos pos, BlockState state, boolean open, Player player) {
        if (state.getValue(OPEN) == open) return;
        boolean trapdoor = this instanceof MultidirectionalTrapdoorBlock;
        level.setBlock(pos, state.setValue(OPEN, open).setValue(VISIBLE, trapdoor), 3);
        level.playSound(null, pos, trapdoor ? (open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE)
            : (open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE),
            SoundSource.BLOCKS, .5f, 1);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        state = state.setValue(POWERED, powered);
        level.setBlock(pos, state, 2);
        setOpen(level, pos, state, powered, null);
    }
}
