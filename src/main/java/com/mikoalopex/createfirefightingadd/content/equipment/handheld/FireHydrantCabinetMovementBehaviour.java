package com.mikoalopex.createfirefightingadd.content.equipment.handheld;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;

/**
 * Fire hydrant cabinets can be carried by Create contraptions, but their
 * handheld-nozzle inventory, binding and fluid interactions remain world-block
 * behavior only.
 */
public class FireHydrantCabinetMovementBehaviour implements MovementBehaviour {
	public static final FireHydrantCabinetMovementBehaviour INSTANCE = new FireHydrantCabinetMovementBehaviour();

	private FireHydrantCabinetMovementBehaviour() {
	}
}
