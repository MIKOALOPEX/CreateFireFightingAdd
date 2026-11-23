package com.mikoalopex.createfirefightingadd.api.flowmeter;

/** Read-only access to the latest fluid meter sample. Call from the server thread. */
@FunctionalInterface
public interface IFlowMeter {
	FlowMeterReading getReading();
}
