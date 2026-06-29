package com.mikoalopex.createfirefightingadd.api.kinetics;

/**
 * Optional compatibility hook for blocks that write pressure into Create fluid
 * networks without exposing their kinetic state through Create's standard block
 * entity classes.
 */
public interface PressureSourceStressProvider {

	/**
	 * @return the source rotation speed that should be mirrored by the turbine.
	 */
	float createFireFightingAdd$getPressureSourceSpeed();

	/**
	 * @return the stress impact paid by this pressure source, in Create's xRPM
	 *         units.
	 */
	float createFireFightingAdd$getPressureSourceStressImpact();
}
