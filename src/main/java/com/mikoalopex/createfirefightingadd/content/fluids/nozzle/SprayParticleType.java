package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Allows the provider to apply the configurable view distance instead of vanilla's particle cutoff. */
public final class SprayParticleType extends ParticleType<SprayParticleOptions> {

	public SprayParticleType() {
		super(true);
	}

	@Override
	public MapCodec<SprayParticleOptions> codec() {
		return SprayParticleOptions.CODEC;
	}

	@Override
	public StreamCodec<? super RegistryFriendlyByteBuf, SprayParticleOptions> streamCodec() {
		return SprayParticleOptions.STREAM_CODEC;
	}
}
