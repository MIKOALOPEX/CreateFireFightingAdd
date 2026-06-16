package com.createfireworkadd.createfirefightingadd.content.kinetics.pump;

public interface FireFightingPumpPressureProvider {

	/**
	 * The actual fluid pressure this pump writes into Create pipe networks.
	 */
	float createfirefightingadd$getFluidPressure();

	/**
	 * Maximum pipe distance reached by this pump's pressure propagation.
	 */
	int createfirefightingadd$getPumpRange();
}
