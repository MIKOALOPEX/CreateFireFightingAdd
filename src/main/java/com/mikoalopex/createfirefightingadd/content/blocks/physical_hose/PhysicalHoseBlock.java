package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Physical hose endpoint with an adjustable cross-section.
 * The native rope simulation controls the connected hose body.
 */
public class PhysicalHoseBlock extends FireHoseBlock {
    public enum Size implements net.minecraft.util.StringRepresentable {
        EIGHT(8), SIX(6), FOUR(4);
        public final int width;
        Size(int width) { this.width = width; }
        public Size next() { return values()[(ordinal() + 1) % values().length]; }
        @Override public String getSerializedName() { return Integer.toString(width); }
        public static Size of(int width) { return width == 4 ? FOUR : width == 6 ? SIX : EIGHT; }
    }
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Size> SIZE =
        net.minecraft.world.level.block.state.properties.EnumProperty.create("size", Size.class);

    public PhysicalHoseBlock(Properties properties, int width) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SIZE, Size.of(width)));
    }

    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIZE);
    }

    @Override public net.minecraft.world.InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        if (!context.getLevel().isClientSide && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof PhysicalHoseBlockEntity hose)
            hose.cycleSize();
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        int edge = (14 - state.getValue(SIZE).width) / 2;
        return net.createmod.catnip.math.VoxelShaper.forDirectional(box(edge, 0, edge, 16 - edge, 4, 16 - edge),
            net.minecraft.core.Direction.UP).get(state.getValue(FACING));
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(PhysicalHoses.ITEM.get());
    }

    @Override public BlockEntityType<? extends FireHoseBlockEntity> getBlockEntityType() { return PhysicalHoses.BLOCK_ENTITY.get(); }

    @Override public ContraptionMovementSetting getContraptionMovementSetting() { return ContraptionMovementSetting.UNMOVABLE; }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.SHEARS)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PhysicalHoseBlockEntity hose) {
                boolean connected = hose.attached();
                hose.disconnectPhysical();
                if (connected && !hose.attached())
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
