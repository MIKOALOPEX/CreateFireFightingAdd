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

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LENGTH, CONNECTED, POWERED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace())
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BallCouplingBlockEntity be) {
            if (top && !context.getLevel().isClientSide) be.cycleLength(context.getPlayer());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (top) return InteractionResult.PASS;
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

    @Override public Class<BallCouplingBlockEntity> getBlockEntityClass() { return BallCouplingBlockEntity.class; }
    @Override public BlockEntityType<? extends BallCouplingBlockEntity> getBlockEntityType() { return BallCouplings.BLOCK_ENTITY.get(); }
}
