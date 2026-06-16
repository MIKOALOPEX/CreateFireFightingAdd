package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_ITEM;

public class FireHoseBlock extends WrenchableDirectionalBlock
        implements IBE<FireHoseBlockEntity>, BlockSubLevelAssemblyListener, IWrenchable {

    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(
            Block.box(3, 0, 3, 13, 4, 13), Direction.UP);

    public FireHoseBlock(Properties properties) {
        super(properties);
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
        Boolean black = null;
        if (stack.is(Items.BLACK_DYE))
            black = true;
        else if (stack.is(Items.WHITE_DYE))
            black = false;

        if (black == null)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide())
            return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof FireHoseBlockEntity hose))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        boolean changed = hose.setBlackHose(black);
        if (changed && !player.getAbilities().instabuild)
            stack.shrink(1);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel newLevel, BlockState newState,
                            BlockPos oldPos, BlockPos newPos) {
        if (newLevel.getBlockEntity(oldPos) instanceof FireHoseBlockEntity hose) {
            hose.assembling = true;
        }
    }

    @Override
    public void afterMove(ServerLevel oldLevel, ServerLevel newLevel, BlockState state,
                           BlockPos oldPos, BlockPos newPos) {
        if (newLevel.getBlockEntity(newPos) instanceof FireHoseBlockEntity hose) {
            FireHoseBlockEntity partner = hose.getPairedHose();
            if (partner != null) {
                SubLevel subLevel = Sable.HELPER.getContaining(newLevel, newPos);
                partner.setPartnerPos(newPos, subLevel != null ? subLevel.getUniqueId() : null);
            }
        }
    }

    @Override
    public Class<FireHoseBlockEntity> getBlockEntityClass() {
        return FireHoseBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FireHoseBlockEntity> getBlockEntityType() {
        return com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.FIRE_HOSE_BE.get();
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
                                    ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
    }
}
