package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import com.mojang.serialization.MapCodec;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.StressCouplingCompatibility;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FluidCouplingBlock extends WrenchableDirectionalBlock implements IBE<FluidCouplingBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return FluidCouplings.enabled() ? new ItemStack(FluidCouplings.ITEM.get()) : ItemStack.EMPTY;
    }
    public FluidCouplingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(POWERED, false));
    }
    @Override protected MapCodec<? extends WrenchableDirectionalBlock> codec() { return simpleCodec(FluidCouplingBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(POWERED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace())
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.getItem() instanceof WrenchItem ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return InteractionResult.PASS;
        if (!FluidCouplings.enabled()) StressCouplingCompatibility.notifyPlayer(player);
        else if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidCouplingBlockEntity be)
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!FluidCouplings.enabled()) { StressCouplingCompatibility.notifyPlayer(context.getPlayer()); return InteractionResult.SUCCESS; }
        if (!context.getLevel().isClientSide && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FluidCouplingBlockEntity be)
            be.cycleHeight(context.getPlayer());
        return InteractionResult.SUCCESS;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidCouplingBlockEntity be) be.updateSignal();
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof FluidCouplingBlockEntity be) be.disconnect();
        IBE.onRemove(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }
    private VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos, boolean collision) {
        var type = level.getBlockEntity(pos) instanceof FluidCouplingBlockEntity be ? be.settings().type() : FluidCouplingInterfaces.FREE;
        double height = switch (type) {
            case FREE -> 8;
            case RECEPTACLE, HOSE_DROGUE -> 20;
            case BOOM_NOZZLE -> 23.475;
            case HOSE_PROBE -> 25;
        };
        if (collision) height -= 4;
        return Shapes.or(box(state, 0, 0, 0, 16, type == FluidCouplingInterfaces.RECEPTACLE ? 6 : 4, 16),
            box(state, 4, 3, 4, 12, height, 12));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, level, pos, false); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(state, level, pos, true); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return box(state, 0, 0, 0, 16, 1, 16); }
    private VoxelShape box(BlockState state, double x, double y, double z, double X, double Y, double Z) {
        Vec3 a = FluidCouplingGeometry.modelPoint(new Vec3(x, y, z), state.getValue(FACING));
        Vec3 b = FluidCouplingGeometry.modelPoint(new Vec3(X, Y, Z), state.getValue(FACING));
        return Shapes.box(Math.min(a.x,b.x), Math.min(a.y,b.y), Math.min(a.z,b.z), Math.max(a.x,b.x), Math.max(a.y,b.y), Math.max(a.z,b.z));
    }
    @Override public Class<FluidCouplingBlockEntity> getBlockEntityClass() { return FluidCouplingBlockEntity.class; }
    @Override public BlockEntityType<? extends FluidCouplingBlockEntity> getBlockEntityType() { return FluidCouplings.BLOCK_ENTITY.get(); }
}
