package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Native double-door matching, with no upper-half placement or power lookup. */
public class ShortSlidingDoorBlock extends CopycatDoorBlock {
    public final boolean folds;

    public ShortSlidingDoorBlock(Properties properties, boolean folds) {
        super(properties);
        this.folds = folds;
        registerDefaultState(defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DoorBlock.FACING, DoorBlock.HINGE, DoorBlock.HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        var pos = context.getClickedPos();
        var hit = context.getClickLocation().subtract(pos.getCenter());
        Direction right = facing.getClockWise();
        DoorHingeSide hinge = hit.x * right.getStepX() + hit.z * right.getStepZ() > 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
        for (Direction side : new Direction[] {right, right.getOpposite()}) {
            BlockState neighbor = context.getLevel().getBlockState(pos.relative(side));
            if (neighbor.is(this) && neighbor.getValue(DoorBlock.FACING) == facing) {
                DoorHingeSide desired = side == right ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
                if (neighbor.getValue(DoorBlock.HINGE) != desired) { hinge = desired; break; }
            }
        }
        return defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge);
    }

    public static BlockPos partnerPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(DoorBlock.FACING);
        return pos.relative(state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT
            ? facing.getClockWise() : facing.getCounterClockWise());
    }

    public static boolean matches(BlockState state, BlockState other) {
        if (!(other.getBlock() instanceof ShortSlidingDoorBlock)) return false;
        // Create's matcher expects opposite OPEN values while propagating a change.
        return SlidingDoorBlock.isDoubleDoor(state.setValue(OPEN, !other.getValue(OPEN)),
            state.getValue(DoorBlock.HINGE), state.getValue(DoorBlock.FACING), other);
    }

    @Override
    public void setOpen(Level level, BlockPos pos, BlockState state, boolean open, Player player) {
        BlockPos otherPos = partnerPos(pos, state);
        BlockState other = level.getBlockState(otherPos);
        super.setOpen(level, pos, state, open, player);
        if (matches(state, other)) super.setOpen(level, otherPos, other, open, player);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this) && !level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        neighborChanged(state, level, pos, this, pos, false);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        BlockPos otherPos = partnerPos(pos, state);
        BlockState other = level.getBlockState(otherPos);
        boolean paired = matches(state, other);
        boolean powered = level.hasNeighborSignal(pos) || paired && level.hasNeighborSignal(otherPos);
        if (powered == state.getValue(POWERED)) return;
        state = state.setValue(POWERED, powered);
        level.setBlock(pos, state, 2);
        if (paired) level.setBlock(otherPos, other.setValue(POWERED, powered), 2);
        setOpen(level, pos, state, powered, null);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        return face.getAxis() == Direction.Axis.Y ? state.cycle(DoorBlock.FACING) : state.cycle(DoorBlock.HINGE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(DoorBlock.FACING, rotation.rotate(state.getValue(DoorBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.setValue(DoorBlock.FACING, mirror.mirror(state.getValue(DoorBlock.FACING)))
            .cycle(DoorBlock.HINGE);
    }
}
