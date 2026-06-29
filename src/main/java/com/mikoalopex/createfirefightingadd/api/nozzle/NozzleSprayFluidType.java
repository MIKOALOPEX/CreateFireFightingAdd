package com.mikoalopex.createfirefightingadd.api.nozzle;

/**
 * Coarse spray categories exposed to addon integrations. The exact fluid stack
 * is also available from the hit context when an integration needs to inspect
 * tags, components, or another mod's fluid registry entry.
 */
public enum NozzleSprayFluidType {
	WATER,
	LAVA,
	MILK,
	POTION,
	DRAGON_BREATH,
	FLAMMABLE,
	UNSUPPORTED
}
