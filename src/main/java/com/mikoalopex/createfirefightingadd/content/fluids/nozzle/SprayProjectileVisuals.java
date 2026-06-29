package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.List;
import java.util.function.Function;

import com.mikoalopex.createfirefightingadd.Config;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import net.neoforged.neoforge.fluids.FluidStack;

import org.joml.Vector3f;

final class SprayProjectileVisuals {
	private static final Vector3f BLUE = new Vector3f(0.3f, 0.55f, 1.0f);
	private static final Vector3f LIGHT_BLUE = new Vector3f(0.6f, 0.8f, 1.0f);
	private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
	private static final Vector3f LAVA_RED = new Vector3f(1.0f, 0.2f, 0.05f);
	private static final Vector3f LAVA_ORANGE = new Vector3f(1.0f, 0.5f, 0.1f);
	private static final Vector3f LAVA_YELLOW = new Vector3f(0.95f, 0.7f, 0.15f);
	private static final Vector3f DRAGON_PURPLE = new Vector3f(0.62f, 0.25f, 1.0f);
	private static final Vector3f DRAGON_PINK = new Vector3f(1.0f, 0.42f, 0.88f);
	private static final Vector3f DRAGON_PALE = new Vector3f(0.86f, 0.72f, 1.0f);
	private static final Vector3f DRAGON_WHITE = new Vector3f(1.0f, 0.92f, 1.0f);
	private static final Vector3f FUEL_AMBER = new Vector3f(0.95f, 0.65f, 0.15f);
	private static final Vector3f FUEL_DARK = new Vector3f(0.4f, 0.25f, 0.1f);
	private static final Vector3f FUEL_LIGHT = new Vector3f(0.9f, 0.75f, 0.3f);

	private static final Vector3f[] BIODIESEL_COLORS = {
		new Vector3f(161f/255f, 101f/255f, 87f/255f),
		new Vector3f(193f/255f, 132f/255f, 86f/255f),
		new Vector3f(214f/255f, 171f/255f, 93f/255f)
	};
	private static final Vector3f[] DIESEL_COLORS = {
		new Vector3f(155f/255f, 115f/255f, 84f/255f),
		new Vector3f(180f/255f, 142f/255f, 84f/255f),
		new Vector3f(214f/255f, 201f/255f, 94f/255f)
	};
	private static final Vector3f[] GASOLINE_COLORS = {
		new Vector3f(164f/255f, 136f/255f, 119f/255f),
		new Vector3f(180f/255f, 154f/255f, 124f/255f),
		new Vector3f(217f/255f, 210f/255f, 140f/255f)
	};
	private static final Vector3f[] PLANT_OIL_COLORS = {
		new Vector3f(129f/255f, 121f/255f, 81f/255f),
		new Vector3f(105f/255f, 101f/255f, 77f/255f),
		new Vector3f(173f/255f, 163f/255f, 103f/255f)
	};
	private static final Vector3f[] ETHANOL_COLORS = {
		new Vector3f(179f/255f, 183f/255f, 164f/255f),
		new Vector3f(163f/255f, 160f/255f, 145f/255f),
		new Vector3f(181f/255f, 189f/255f, 172f/255f)
	};
	private static final Vector3f[] CRUDE_OIL_COLORS = {
		new Vector3f(0.08f, 0.08f, 0.08f),
		new Vector3f(0.15f, 0.15f, 0.15f),
		new Vector3f(0.25f, 0.25f, 0.25f),
		new Vector3f(0.35f, 0.35f, 0.35f),
		new Vector3f(0.45f, 0.45f, 0.45f)
	};

	private SprayProjectileVisuals() {
	}

	static void spawnProjectiles(List<LightweightProjectile> projectiles, Vec3 origin, Vec3 baseDirection,
			SprayShape shape, int count, int lifetime, double speed,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, boolean ignited,
			Vec3 gravity, double friction, RandomSource random) {
		for (int i = 0; i < count; i++) {
			Vec3 dir = shape.randomSprayDirection(baseDirection, random);
			LightweightProjectile proj = new LightweightProjectile(origin, dir.scale(speed), lifetime,
				behavior, (float) AbstractSprayDeviceBlockEntity.PUSH_STREAM_SPEED, gravity, friction);
			if (ignited && behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE) {
				proj.ignited = true;
				proj.ignitedAtAge = 0;
			}
			projectiles.add(proj);
		}
	}

	static void tickClientProjectiles(Level level, List<LightweightProjectile> projectiles,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, String fuelPath, Vector3f potionColor,
			int trailParticlesPerTick, Function<Vec3, Vec3> renderPositionMapper) {
		projectiles.removeIf(proj -> {
			proj.tick();

			if (proj.hasLostForwardMomentum() && proj.momentumLostTick < 0)
				proj.momentumLostTick = proj.age;

			boolean inMistGrace = proj.momentumLostTick >= 0 && proj.age <= proj.momentumLostTick + 10;
			if (!inMistGrace) {
				BlockHitResult blockHit = level.clip(new ClipContext(
					proj.prevPosition, proj.position,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
				if (blockHit.getType() != HitResult.Type.MISS)
					return true;
			} else if (proj.age > proj.momentumLostTick + 10) {
				return true;
			}

			spawnTrailParticles(level, proj, behavior, fuelPath, potionColor, trailParticlesPerTick,
				renderPositionMapper);
			return proj.isExpired();
		});
	}

	static Vector3f potionColor(FluidStack fluid) {
		PotionContents contents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		int rgb = contents.getColor();
		return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
	}

	static String fuelPath(FluidStack fluid) {
		if (fluid.isEmpty())
			return "";
		ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
		return id == null ? "" : id.getPath();
	}

	private static void spawnTrailParticles(Level level, LightweightProjectile proj,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, String fuelPath, Vector3f potionColor,
			int trailParticlesPerTick, Function<Vec3, Vec3> renderPositionMapper) {
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED || behavior == null)
			return;

		double progress = (double) proj.age / proj.maxLifetime;
		double transitionDenominator = Math.max(0.01, 1.0 - Config.mistTransitionStart);
		double timeMist = clamp((progress - Config.mistTransitionStart) / transitionDenominator, 0.0, 1.0);
		double mistFactor = proj.hasLostForwardMomentum() ? 1.0 : timeMist;

		int baseCount = trailParticlesPerTick;
		double countScale = 1.0 + mistFactor * 2.0;
		int count = Math.max(1, (int) (baseCount * countScale));

		float streamSize = 2.6f;
		float mistSize = 5.0f;
		float baseSize = streamSize + (float) mistFactor * (mistSize - streamSize);

		double streamSpread = 0.2;
		double mistSpread = Config.mistSpreadRadius;
		double spreadRadius = streamSpread + mistFactor * (mistSpread - streamSpread);

		for (int i = 0; i < count; i++) {
			double t = level.random.nextDouble();
			double baseX = proj.prevPosition.x + (proj.position.x - proj.prevPosition.x) * t;
			double baseY = proj.prevPosition.y + (proj.position.y - proj.prevPosition.y) * t;
			double baseZ = proj.prevPosition.z + (proj.position.z - proj.prevPosition.z) * t;

			Vec3 particlePos = randomTrailPosition(level.random, new Vec3(baseX, baseY, baseZ), spreadRadius, mistFactor);
			particlePos = renderPositionMapper.apply(particlePos);

			float size = baseSize + level.random.nextFloat() * 0.8f;
			double streamWeight = 1.0 - mistFactor;
			double velX = proj.velocity.x * 0.1 * streamWeight + (level.random.nextDouble() - 0.5) * 0.04 * mistFactor;
			double velY = proj.velocity.y * 0.1 * streamWeight - 0.03 * mistFactor
				+ (level.random.nextDouble() - 0.5) * 0.02 * mistFactor;
			double velZ = proj.velocity.z * 0.1 * streamWeight + (level.random.nextDouble() - 0.5) * 0.04 * mistFactor;

			spawnColoredParticle(level, behavior, proj, fuelPath, potionColor, size, particlePos, velX, velY, velZ, t);
		}
	}

	private static Vec3 randomTrailPosition(RandomSource random, Vec3 base, double spreadRadius, double mistFactor) {
		if (mistFactor < 0.01) {
			return base.add(
				(random.nextDouble() - 0.5) * spreadRadius * 2.0,
				(random.nextDouble() - 0.5) * spreadRadius * 0.6,
				(random.nextDouble() - 0.5) * spreadRadius * 2.0);
		}

		if (mistFactor < 0.5) {
			double blend = mistFactor / 0.5;
			Vec3 box = base.add(
				(random.nextDouble() - 0.5) * spreadRadius * 2.0,
				(random.nextDouble() - 0.5) * spreadRadius * 0.6,
				(random.nextDouble() - 0.5) * spreadRadius * 2.0);
			Vec3 sphere = randomPointInSphere(random, base, spreadRadius);
			return box.add(sphere.subtract(box).scale(blend));
		}

		return randomPointInSphere(random, base, spreadRadius);
	}

	private static Vec3 randomPointInSphere(RandomSource random, Vec3 base, double radius) {
		double theta = random.nextDouble() * 2.0 * Math.PI;
		double phi = Math.acos(1.0 - 2.0 * random.nextDouble());
		double r = Math.cbrt(random.nextDouble()) * radius;
		return base.add(
			r * Math.sin(phi) * Math.cos(theta),
			r * Math.sin(phi) * Math.sin(theta),
			r * Math.cos(phi));
	}

	private static void spawnColoredParticle(Level level, AbstractSprayDeviceBlockEntity.FluidBehavior behavior,
			LightweightProjectile proj, String fuelPath, Vector3f potionColor, float size, Vec3 pos,
			double velX, double velY, double velZ, double partialAge) {
		switch (behavior) {
			case LAVA -> {
				Vector3f lavaColor = level.random.nextFloat() < 0.5f
					? new Vector3f(1.0f, 0.4f, 0.0f)
					: new Vector3f(1.0f, 0.7f, 0.1f);
				level.addParticle(new DustParticleOptions(lavaColor, size), pos.x, pos.y, pos.z, velX, velY, velZ);
			}
			case DRAGON_BREATH -> {
				Vector3f color = pickDragonBreathColor(level.random);
				level.addParticle(new DustParticleOptions(color, size * 1.1f),
					pos.x, pos.y, pos.z, velX * 0.6, velY * 0.6 + 0.01, velZ * 0.6);
				if (level.random.nextFloat() < 0.08f)
					level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, velX * 0.2, velY * 0.2 + 0.01, velZ * 0.2);
			}
			case FLAMMABLE -> {
				double particleAge = proj.age - 1 + partialAge;
				if (proj.ignited && proj.ignitedAtAge >= 0 && particleAge >= proj.ignitedAtAge) {
					if (level.random.nextBoolean())
						level.addParticle(ParticleTypes.LAVA, pos.x, pos.y, pos.z, velX, velY, velZ);
				} else {
					Vector3f[] fuelColors = getFuelColorsByPath(fuelPath);
					Vector3f fuelColor = fuelColors[level.random.nextInt(fuelColors.length)];
					level.addParticle(new DustParticleOptions(fuelColor, size), pos.x, pos.y, pos.z, velX, velY, velZ);
				}
			}
			case MILK -> level.addParticle(new DustParticleOptions(WHITE, size), pos.x, pos.y, pos.z, velX, velY, velZ);
			case POTION -> level.addParticle(new DustParticleOptions(potionColor, size), pos.x, pos.y, pos.z, velX, velY, velZ);
			default -> {
				Vector3f color;
				double progress = (double) proj.age / proj.maxLifetime;
				if (level.random.nextFloat() < 0.6f * progress)
					color = WHITE;
				else
					color = level.random.nextFloat() < 0.5f ? LIGHT_BLUE : BLUE;
				level.addParticle(new DustParticleOptions(color, size), pos.x, pos.y, pos.z, velX, velY, velZ);
			}
		}
	}

	private static Vector3f[] getFuelColorsByPath(String path) {
		return switch (path) {
			case "biodiesel" -> BIODIESEL_COLORS;
			case "diesel" -> DIESEL_COLORS;
			case "gasoline" -> GASOLINE_COLORS;
			case "plant_oil" -> PLANT_OIL_COLORS;
			case "ethanol" -> ETHANOL_COLORS;
			case "crude_oil" -> CRUDE_OIL_COLORS;
			default -> new Vector3f[]{FUEL_AMBER, FUEL_DARK, FUEL_LIGHT};
		};
	}

	private static Vector3f pickDragonBreathColor(RandomSource random) {
		float r = random.nextFloat();
		if (r < 0.38f)
			return DRAGON_PURPLE;
		if (r < 0.68f)
			return DRAGON_PINK;
		if (r < 0.90f)
			return DRAGON_PALE;
		return DRAGON_WHITE;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
