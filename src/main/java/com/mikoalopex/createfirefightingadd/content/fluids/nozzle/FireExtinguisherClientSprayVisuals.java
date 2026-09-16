package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import org.joml.Vector3f;

import com.mikoalopex.createfirefightingadd.ClientConfig;
import com.mikoalopex.createfirefightingadd.content.equipment.extinguisher.FireExtinguisherItem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FireExtinguisherClientSprayVisuals {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int BASE_PARTICLES_PER_TICK = 14;
	private static final double MIN_DISTANCE = 0.25;
	private static final double EDGE_MARGIN = 0.4;
	private static final double TAN_HALF_ANGLE = Math.tan(Math.toRadians(FireExtinguisherSprayEffects.HALF_ANGLE));
	private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
	private static final Vector3f WATER_BLUE = new Vector3f(0.3f, 0.55f, 1.0f);
	private static final Vector3f WATER_LIGHT = new Vector3f(0.72f, 0.88f, 1.0f);
	private static final Vector3f LAVA_ORANGE = new Vector3f(1.0f, 0.45f, 0.05f);
	private static final Vector3f LAVA_YELLOW = new Vector3f(1.0f, 0.72f, 0.12f);
	private static final Vector3f DRAGON_PURPLE = new Vector3f(0.62f, 0.25f, 1.0f);
	private static final Vector3f DRAGON_PINK = new Vector3f(1.0f, 0.42f, 0.88f);

	private FireExtinguisherClientSprayVisuals() {
	}

	public static void tick(Player player, ItemStack stack, InteractionHand hand, boolean spraying) {
		if (player == null || stack.isEmpty())
			return;

		FluidStack fluid = FireExtinguisherItem.getFluid(stack);
		if (fluid.isEmpty())
			return;

		NozzleSprayRuleSet customRules = FireExtinguisherItem.getSprayRules(stack, player.registryAccess());
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyFluidForSpray(player.level(), fluid, customRules);
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED)
			return;

		NozzleSprayRule customRule = null;
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.CUSTOM) {
			customRule = NozzleGlobalSprayRules.findGlobalRule(fluid).orElse(null);
			if (customRule == null)
				customRule = customRules.findForSpray(fluid).orElse(null);
		}

		if (spraying) {
			Vec3 direction = player.getLookAngle().normalize();
			Vec3 origin = FireExtinguisherSprayEffects.sprayOrigin(player, hand, direction);
			boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
				|| behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA
				|| customRule != null && (customRule.igniting() || customRule.flammable());
			NozzleParticlePalette particles = customRule == null ? NozzleParticleColors.defaultPalette(fluid)
				: customRule.particles();
			spawnConeMist(player.level(), origin, direction, behavior, fluid, particles, ignited);
		}
	}

	private static void spawnConeMist(Level level, Vec3 origin, Vec3 direction,
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior, FluidStack fluid, NozzleParticlePalette particles,
			boolean ignited) {
		if (ClientConfig.particleDensity <= 0.0)
			return;

		int count = Math.max(1, (int) Math.ceil(BASE_PARTICLES_PER_TICK * ClientConfig.particleDensity));
		Vec3[] perps = ConeSprayShape.perpendiculars(direction);
		double maxDistance = Math.max(MIN_DISTANCE, FireExtinguisherSprayEffects.RANGE - EDGE_MARGIN);
		for (int i = 0; i < count; i++) {
			double distance = MIN_DISTANCE + RANDOM.nextDouble() * (maxDistance - MIN_DISTANCE);
			double radius = Math.sqrt(RANDOM.nextDouble()) * distance * TAN_HALF_ANGLE;
			double angle = RANDOM.nextDouble() * Math.PI * 2.0;
			Vec3 pos = origin.add(direction.scale(distance))
				.add(perps[0].scale(Math.cos(angle) * radius))
				.add(perps[1].scale(Math.sin(angle) * radius));

			double drift = 0.004 + RANDOM.nextDouble() * 0.012;
			Vec3 velocity = direction.scale(drift)
				.add(perps[0].scale((RANDOM.nextDouble() - 0.5) * 0.015))
				.add(perps[1].scale((RANDOM.nextDouble() - 0.5) * 0.015))
				.add(0.0, (RANDOM.nextDouble() - 0.5) * 0.01, 0.0);
			spawnParticle(level, behavior, fluid, particles, ignited, pos, velocity, distance);
		}
	}

	public static void clear(int entityId) {
	}

	public static void clearAll() {
	}

	private static void spawnParticle(Level level, AbstractSprayDeviceBlockEntity.FluidBehavior behavior,
			FluidStack fluid, NozzleParticlePalette particles, boolean ignited, Vec3 pos, Vec3 velocity,
			double distance) {
		float size = 1.4f + RANDOM.nextFloat() * 1.2f
			+ (float) (distance / Math.max(1.0, FireExtinguisherSprayEffects.RANGE)) * 0.8f;
		switch (behavior) {
			case LAVA -> {
				level.addParticle(new SprayParticleOptions(pick(LAVA_ORANGE, LAVA_YELLOW), size, 1f, 0.08f),
					pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
				SprayProjectileVisuals.spawnLavaSpark(level, RANDOM, pos, velocity.x, velocity.y, velocity.z);
			}
			case DRAGON_BREATH -> {
				level.addParticle(new SprayParticleOptions(pick(DRAGON_PURPLE, DRAGON_PINK), size * 1.1f, 1f, 0.08f),
					pos.x, pos.y, pos.z, velocity.x * 0.6, velocity.y * 0.6 + 0.01, velocity.z * 0.6);
				if (RANDOM.nextFloat() < 0.08f)
					level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z,
						velocity.x * 0.2, velocity.y * 0.2 + 0.01, velocity.z * 0.2);
			}
			case FLAMMABLE -> {
				if (ignited && RANDOM.nextFloat() < 0.14f) {
					level.addParticle(ParticleTypes.LAVA, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
				} else {
					level.addParticle(new SprayParticleOptions(particles.pickVector(RANDOM), size, 1f, 0.08f),
						pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
				}
			}
			case MILK -> level.addParticle(new SprayParticleOptions(WHITE, size, 1f, 0.08f),
				pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
			case POTION -> level.addParticle(new SprayParticleOptions(SprayProjectileVisuals.potionColor(fluid), size, 1f, 0.08f),
				pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
			case CUSTOM -> level.addParticle(new SprayParticleOptions(particles.pickVector(RANDOM), size, 1f, 0.08f),
				pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
			default -> level.addParticle(new SprayParticleOptions(waterColor(distance), size, 1f, 0.08f),
				pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private static Vector3f waterColor(double distance) {
		double progress = distance / Math.max(1.0, FireExtinguisherSprayEffects.RANGE);
		if (RANDOM.nextFloat() < 0.35f + 0.35f * progress)
			return WHITE;
		return RANDOM.nextBoolean() ? WATER_LIGHT : WATER_BLUE;
	}

	private static Vector3f pick(Vector3f first, Vector3f second) {
		return RANDOM.nextBoolean() ? first : second;
	}
}
