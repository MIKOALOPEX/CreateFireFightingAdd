package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Carries particle appearance and local drift; the emitter owns the spray trajectory and reach. */
public record SprayParticleOptions(Vector3f color, float scale, float mist, float travelLimit) implements ParticleOptions {

	public static final MapCodec<SprayParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(SprayParticleOptions::color),
		Codec.floatRange(0.1f, 8f).fieldOf("scale").forGetter(SprayParticleOptions::scale),
		Codec.floatRange(0f, 1f).optionalFieldOf("mist", 0.5f).forGetter(SprayParticleOptions::mist),
		Codec.floatRange(0f, 2f).optionalFieldOf("travel_limit", 0.6f).forGetter(SprayParticleOptions::travelLimit)
	).apply(instance, SprayParticleOptions::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, SprayParticleOptions> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VECTOR3F, SprayParticleOptions::color,
		ByteBufCodecs.FLOAT, SprayParticleOptions::scale,
		ByteBufCodecs.FLOAT, SprayParticleOptions::mist,
		ByteBufCodecs.FLOAT, SprayParticleOptions::travelLimit,
		SprayParticleOptions::new);

	public SprayParticleOptions(Vector3f color, float scale) {
		this(color, scale, 0.5f, 0.6f);
	}

	public SprayParticleOptions {
		color = new Vector3f(bounded(color.x, 0f, 1f), bounded(color.y, 0f, 1f), bounded(color.z, 0f, 1f));
		scale = bounded(scale, 0.1f, 8f);
		mist = bounded(mist, 0f, 1f);
		travelLimit = bounded(travelLimit, 0f, 2f);
	}

	private static float bounded(float value, float min, float max) {
		return Float.isFinite(value) ? Mth.clamp(value, min, max) : min;
	}

	@Override
	public ParticleType<?> getType() {
		return CreateFireFightingAdd.SPRAY_PARTICLE.get();
	}
}
