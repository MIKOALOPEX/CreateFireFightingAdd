package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.lang.ref.WeakReference;
import com.mikoalopex.createfirefightingadd.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Long-range colored spray droplets using vanilla generic particle sprites. */
public final class SprayParticle extends TextureSheetParticle {

	private final SpriteSet sprites;
	private final float mist;
	private final float baseSize;
	private final float initialAlpha;
	private double remainingTravel;

	private SprayParticle(ClientLevel level, double x, double y, double z,
			double dx, double dy, double dz, SprayParticleOptions options, SpriteSet sprites) {
		super(level, x, y, z);
		this.sprites = sprites;
		mist = options.mist();
		baseSize = (0.075f + random.nextFloat() * 0.075f) * (options.scale() + 0.2f);
		quadSize = baseSize;
		initialAlpha = Mth.lerp(mist, 0.9f, 0.38f);
		alpha = initialAlpha;
		int baseLifetime = (int) (8.0 / (random.nextDouble() * 0.8 + 0.2));
		lifetime = (int) Math.max(baseLifetime * options.scale(), 1f);
		remainingTravel = options.travelLimit();
		float brightness = 0.92f + random.nextFloat() * 0.08f;
		setColor(options.color().x * brightness, options.color().y * brightness, options.color().z * brightness);
		// Emitters already place particles along the trajectory; drift must not extend that trajectory.
		double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double factor = speed > 0 ? Math.min(0.15, 0.04 / speed) : 0;
		xd = dx * factor;
		yd = dy * factor;
		zd = dz * factor;
		roll = random.nextFloat() * Mth.TWO_PI;
		oRoll = roll;
		setSize(0.06f, 0.06f);
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		oRoll = roll;
		if (++age >= lifetime) {
			remove();
			return;
		}
		yd -= 0.0006 + mist * 0.0012;
		double distance = Math.sqrt(xd * xd + yd * yd + zd * zd);
		double step = distance > 0 ? Math.min(1, remainingTravel / distance) : 0;
		move(xd * step, yd * step, zd * step);
		remainingTravel = Math.max(0, remainingTravel - distance * step);
		xd *= 0.9;
		yd *= 0.9;
		zd *= 0.9;
		float progress = age / (float) lifetime;
		alpha = initialAlpha * (1f - progress * progress);
		roll += mist * 0.015f;
		setSpriteFromAge(sprites);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float growth = Mth.clamp((age + partialTick) * 32f / lifetime, 0f, 1f);
		return baseSize * growth;
	}

	@Override
	public AABB getRenderBoundingBox(float partialTick) {
		return getBoundingBox().inflate(getQuadSize(partialTick));
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static final class Provider implements ParticleProvider<SprayParticleOptions> {
		private static final int MAX_PER_TICK = 512;
		private final SpriteSet sprites;
		private WeakReference<ClientLevel> previousLevel = new WeakReference<>(null);
		private long previousTick = Long.MIN_VALUE;
		private int emitted;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SprayParticleOptions options, ClientLevel level,
				double x, double y, double z, double dx, double dy, double dz) {
			if (ClientConfig.particleDensity <= 0)
				return null;
			if (previousLevel.get() != level || previousTick != level.getGameTime()) {
				previousLevel = new WeakReference<>(level);
				previousTick = level.getGameTime();
				emitted = 0;
			}
			Minecraft minecraft = Minecraft.getInstance();
			double distance = minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z);
			double viewDistance = ClientConfig.sprayParticleViewDistance;
			if (distance > viewDistance * viewDistance
					|| emitted >= Math.max(1, (int) (MAX_PER_TICK * ClientConfig.particleDensity)))
				return null;
			double chance = distance > 64.0 * 64.0 ? 0.35 : distance > 32.0 * 32.0 ? 0.65 : 1.0;
			ParticleStatus status = minecraft.options.particles().get();
			if (status == ParticleStatus.DECREASED)
				chance *= 0.5;
			else if (status == ParticleStatus.MINIMAL)
				chance *= 0.15;
			if (level.random.nextDouble() >= chance)
				return null;
			emitted++;
			return new SprayParticle(level, x, y, z, dx, dy, dz, options, sprites);
		}
	}
}
