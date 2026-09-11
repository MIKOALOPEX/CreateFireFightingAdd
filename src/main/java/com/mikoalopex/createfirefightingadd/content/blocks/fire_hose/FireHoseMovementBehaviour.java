package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

/**
 * Forwards Create contraption ticks to the moving-endpoint registry.
 */
public class FireHoseMovementBehaviour implements MovementBehaviour {
	public static final FireHoseMovementBehaviour INSTANCE = new FireHoseMovementBehaviour();

	@Override
	public void tick(MovementContext context) {
		MountedFluidStorage storage = context.getFluidStorage();
		if (storage instanceof FireHoseMountedFluidStorage hoseStorage) {
			if (context.world.isClientSide && context.blockEntityData != null)
				HoseRouteRenderer.track(context, HoseRoute.read(context.blockEntityData.getCompound("HoseRoute")),
					hoseStorage.getEndpointId());
			FireHoseMovingEndpoints.update(context, hoseStorage);
		}
	}

	@Override
	public boolean disableBlockEntityRendering() {
		return true;
	}
}
