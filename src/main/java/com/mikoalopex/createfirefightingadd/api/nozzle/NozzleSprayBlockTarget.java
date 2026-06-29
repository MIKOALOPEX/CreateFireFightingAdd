package com.mikoalopex.createfirefightingadd.api.nozzle;

/**
 * Optional interface for blocks or block entities that want to react directly
 * when hit by a Firefighting Add nozzle spray.
 */
public interface NozzleSprayBlockTarget {
	default boolean shouldReceiveNozzleSprayHit(NozzleSprayHitContext context) {
		return true;
	}

	/**
	 * Called after a server-side nozzle spray sample reaches this block. This
	 * callback is observational/additive only; it cannot cancel built-in nozzle
	 * effects such as extinguishing, ignition, or block conversion.
	 */
	void onNozzleSprayHit(NozzleSprayHitContext context);
}
