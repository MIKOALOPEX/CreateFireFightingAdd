package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

/** Local model axes shared by the visual rod and its rope-direction projection. */
final class RopeHookOrientation {
    private RopeHookOrientation() {}

    static Quaternionf rotation(BlockState state) {
        Direction facing = state.getValue(RopeLatchBlock.FACING);
        boolean first = state.getValue(RopeLatchBlock.AXIS_ALONG_FIRST_COORDINATE);
        boolean vertical = facing.getAxis() == Direction.Axis.Y;
        int yaw = switch (facing) {
            case EAST -> 270;
            case NORTH -> 180;
            case WEST -> 90;
            default -> vertical && !first ? 90 : 0;
        };
        int pitch = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 270;
        boolean turned = !vertical && (facing.getAxis() == Direction.Axis.X ? first : !first);
        return new Quaternionf().rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(-pitch)).rotateY(turned ? (float) Math.PI / 2 : 0);
    }
}
