package com.mikoalopex.createfirefightingadd.content.blocks.copycat_door;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;

/** Retains Create's station/elevator policy without its two-block door mutation. */
public class ShortDoorMovementBehaviour extends SlidingDoorMovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        var info = context.contraption.getBlocks().get(context.localPos);
        if (info == null) return;
        if (!context.world.isClientSide)
            tickOpen(context, info.state().getValue(CopycatDoorBlock.OPEN));
        else if (context.contraption.getBlockEntityClientSide(context.localPos) instanceof CopycatDoorBlockEntity entity)
            entity.animate(info.state());
    }

    @Override
    protected void tickOpen(MovementContext context, boolean current) {
        boolean open = shouldOpen(context);
        if (!shouldUpdate(context, open) || current == open) return;
        var info = context.contraption.getBlocks().get(context.localPos);
        if (info == null) return;
        CopycatDoorInteraction.setOpen(context.contraption.entity, info, open);

        // Platform doors are queried only at the position used by Create itself.
        var facing = getDoorFacing(context);
        BlockPos platform = BlockPos.containing(context.position).relative(facing);
        var state = context.world.getBlockState(platform);
        if (state.getBlock() instanceof ShortSlidingDoorBlock door
                && state.getValue(DoorBlock.FACING).getAxis() == facing.getAxis())
            door.setOpen(context.world, platform, state, open, null);
        else if (state.getBlock() instanceof DoorBlock door
                && state.getValue(DoorBlock.FACING).getAxis() == facing.getAxis())
            door.setOpen(null, context.world, state, platform, open);
    }
}
