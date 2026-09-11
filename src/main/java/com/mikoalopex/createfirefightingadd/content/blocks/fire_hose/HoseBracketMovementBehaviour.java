package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

/** Create contraptions carry the model and its route identity, without fluid storage. */
public class HoseBracketMovementBehaviour implements MovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        if (!context.world.isClientSide || context.blockEntityData == null)
            return;
        HoseRoute route = HoseRoute.read(context.blockEntityData.getCompound("HoseRoute"));
        if (context.blockEntityData.hasUUID("HoseNodeId"))
            HoseRouteRenderer.track(context, route, context.blockEntityData.getUUID("HoseNodeId"));
    }
}
