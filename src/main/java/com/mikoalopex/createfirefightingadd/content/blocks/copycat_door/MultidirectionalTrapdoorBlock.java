package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class MultidirectionalTrapdoorBlock extends CopycatDoorBlock implements TransformableBlock {
    public static final DirectionProperty NORMAL = DirectionProperty.create("normal");
    public static final IntegerProperty EDGE = IntegerProperty.create("edge", 0, 3);

    public MultidirectionalTrapdoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NORMAL, Direction.UP).setValue(EDGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORMAL, EDGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction normal = context.getClickedFace().getOpposite();
        Vec3 offset = context.getClickLocation().subtract(context.getClickedPos().getCenter());
        Direction hinge = DoorGeometry.placementHinge(normal, offset, context.getHorizontalDirection());
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return DoorGeometry.orient(defaultBlockState(), normal, hinge).setValue(OPEN, powered).setValue(POWERED, powered);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction face) {
        return DoorGeometry.orient(state, rotate(state.getValue(NORMAL), face), rotate(DoorGeometry.hingeSide(state), face));
    }

    private static Direction rotate(Direction direction, Direction axis) {
        if (direction.getAxis() == axis.getAxis()) return direction;
        return axis.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? direction.getClockWise(axis.getAxis()) : direction.getCounterClockWise(axis.getAxis());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return DoorGeometry.orient(state, rotation.rotate(state.getValue(NORMAL)), rotation.rotate(DoorGeometry.hingeSide(state)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return DoorGeometry.orient(state, mirror.mirror(state.getValue(NORMAL)), mirror.mirror(DoorGeometry.hingeSide(state)));
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        return DoorGeometry.orient(state, transform.rotateFacing(transform.mirrorFacing(state.getValue(NORMAL))),
            transform.rotateFacing(transform.mirrorFacing(DoorGeometry.hingeSide(state))));
    }
}
