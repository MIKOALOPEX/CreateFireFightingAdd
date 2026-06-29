package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

final class SprayEntityEffects {
	private SprayEntityEffects() {
	}

	static void applyWaterContact(Level level, Entity entity) {
		if (entity instanceof LivingEntity living && living.isSensitiveToWater())
			living.hurt(level.damageSources().drown(), 1.0f);

		if (entity.isOnFire()) {
			entity.clearFire();
			level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
				SoundSource.NEUTRAL, 0.7f, 1.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.4f);
		}
	}
}
