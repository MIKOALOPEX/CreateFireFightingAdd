package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.function.Supplier;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.sounds.SoundEvent;

public enum SprayLoopSound {
	NOZZLE(CreateFireFightingAdd.NOZZLE_SPRAY_SOUND, 0.0, 0.0),
	FIRE_EXTINGUISHER(CreateFireFightingAdd.FIRE_EXTINGUISHER_SPRAY_SOUND, 1.0, 1.0);

	private final Supplier<SoundEvent> sound;
	private final double loopTrimStartSeconds;
	private final double loopTrimEndSeconds;

	SprayLoopSound(Supplier<SoundEvent> sound, double loopTrimStartSeconds, double loopTrimEndSeconds) {
		this.sound = sound;
		this.loopTrimStartSeconds = loopTrimStartSeconds;
		this.loopTrimEndSeconds = loopTrimEndSeconds;
	}

	SoundEvent soundEvent() {
		return sound.get();
	}

	double loopTrimStartSeconds() {
		return loopTrimStartSeconds;
	}

	double loopTrimEndSeconds() {
		return loopTrimEndSeconds;
	}

	static SprayLoopSound byOrdinal(int ordinal) {
		SprayLoopSound[] values = values();
		if (ordinal < 0 || ordinal >= values.length)
			return NOZZLE;
		return values[ordinal];
	}
}
