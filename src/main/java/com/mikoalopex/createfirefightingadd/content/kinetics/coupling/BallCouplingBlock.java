package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BallCouplingBlock extends KineticBlock implements IBE<BallCouplingBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty LENGTH = IntegerProperty.create("length", 0, 4);
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final boolean top;

    public BallCouplingBlock(Properties properties, boolean top) {
        super(properties);
        this.top = top;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
            .setValue(LENGTH, 0).setValue(CONNECTED, false).setValue(POWERED, false));
    }

    public boolean isTop() { return top; }

    @Override protected MapCodec<? extends KineticBlock> codec() {
        return simpleCodec(p -> new BallCouplingBlock(p, top));
    }

    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LENGTH, CONNECTED, POWERED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace())
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return StressCouplingCompatibility.enabled() && face == state.getValue(FACING).getOpposite();
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BallCouplingBlockEntity be) {
            if (!context.getLevel().isClientSide) be.cycleLength(context.getPlayer());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())
            return InteractionResult.PASS;
        if (!StressCouplingCompatibility.enabled()) {
            StressCouplingCompatibility.notifyPlayer(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BallCouplingBlockEntity be)
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, neighborPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BallCouplingBlockEntity be)
            be.updateSignal();
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof BallCouplingBlockEntity be)
            be.disconnect();
        super.onRemove(state, level, pos, next, moving);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(BallCouplings.ITEM.get());
    }

    /** Matches the +Y model's blockstate rotation, including horizontal roll. */
    public static Vec3 orient(Vec3 p, Direction facing) {
        return switch (facing) {
            case UP -> p;
            case DOWN -> new Vec3(p.x, -p.y, -p.z);
            case NORTH -> new Vec3(p.x, p.z, -p.y);
            case SOUTH -> new Vec3(-p.x, p.z, p.y);
            case WEST -> new Vec3(-p.y, p.z, -p.x);
            case EAST -> new Vec3(p.y, p.z, p.x);
        };
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof BallCouplingBlockEntity be) {
            int length = state.getValue(LENGTH);
            boolean small = be.interfaceMode() == CouplingInterfaceMode.PASSIVE;
            double height = be.interfaceMode() == CouplingInterfaceMode.FREE ? 9 + 4 * length
                : small ? new double[] {8, 12, 16, 20, 23}[length] : 10 + 4 * length;
            return Shapes.or(orientedBox(state, small ? 3 : 0, 0, small ? 3 : 0, small ? 13 : 16, small ? 3 : 5, small ? 13 : 16),
                orientedBox(state, 4, 3, 4, 12, height, 12));
        }
        double height = top ? new double[] {8, 12, 16, 20, 23}[state.getValue(LENGTH)] : 12;
        VoxelShape shape = Shapes.empty();
        double[][] boxes = top ? new double[][] {{3, 0, 3, 13, 3, 13}, {5, 2, 5, 11, height, 11}}
            : new double[][] {{0, 0, 0, 16, 5, 16}, {6, 5, 6, 10, 7, 10},
                {4, 7, 4, 5, 12, 12}, {11, 7, 4, 12, 12, 12},
                {5, 7, 4, 11, 12, 5}, {5, 7, 11, 11, 12, 12}};
        for (double[] box : boxes) {
            Vec3 a = orient(new Vec3(box[0]-8, box[1]-8, box[2]-8), state.getValue(FACING));
            Vec3 b = orient(new Vec3(box[3]-8, box[4]-8, box[5]-8), state.getValue(FACING));
            shape = Shapes.or(shape, Block.box(Math.min(a.x,b.x)+8, Math.min(a.y,b.y)+8, Math.min(a.z,b.z)+8,
                Math.max(a.x,b.x)+8, Math.max(a.y,b.y)+8, Math.max(a.z,b.z)+8));
        }
        return shape;
    }

    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.or(super.getBlockSupportShape(state, level, pos),
            orientedBox(state, 0, 0, 0, 16, 1, 16));
    }

    private static VoxelShape orientedBox(BlockState state, double x1, double y1, double z1, double x2, double y2, double z2) {
        Vec3 a = orient(new Vec3(x1 - 8, y1 - 8, z1 - 8), state.getValue(FACING));
        Vec3 b = orient(new Vec3(x2 - 8, y2 - 8, z2 - 8), state.getValue(FACING));
        return Block.box(Math.min(a.x, b.x) + 8, Math.min(a.y, b.y) + 8, Math.min(a.z, b.z) + 8,
            Math.max(a.x, b.x) + 8, Math.max(a.y, b.y) + 8, Math.max(a.z, b.z) + 8);
    }

    @Override public Class<BallCouplingBlockEntity> getBlockEntityClass() { return BallCouplingBlockEntity.class; }
    @Override public BlockEntityType<? extends BallCouplingBlockEntity> getBlockEntityType() { return BallCouplings.BLOCK_ENTITY.get(); }
}
