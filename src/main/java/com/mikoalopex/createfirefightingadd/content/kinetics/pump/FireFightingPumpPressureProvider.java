package com.mikoalopex.createfirefightingadd.content.kinetics.pump;

public interface FireFightingPumpPressureProvider {

	/**
	 * The actual fluid pressure this pump writes into Create pipe networks.
	 */
	float CreateFireFightingAdd$getFluidPressure();

	/**
	 * Maximum pipe distance reached by this pump's pressure propagation.
	 */
	int CreateFireFightingAdd$getPumpRange();
}
