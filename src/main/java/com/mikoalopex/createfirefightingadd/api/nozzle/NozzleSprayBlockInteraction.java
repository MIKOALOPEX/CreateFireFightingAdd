package com.mikoalopex.createfirefightingadd.api.nozzle;

/**
 * Registry callback for adding nozzle spray behaviour to blocks owned by any
 * mod without requiring those blocks to implement an interface.
 */
public interface NozzleSprayBlockInteraction {
	boolean shouldReceive(NozzleSprayHitContext context);

	/**
	 * Called after a server-side nozzle spray sample reaches a matching block.
	 * This callback is observational/additive only; it cannot cancel built-in
	 * nozzle effects.
	 */
	void onHit(NozzleSprayHitContext context);
}
