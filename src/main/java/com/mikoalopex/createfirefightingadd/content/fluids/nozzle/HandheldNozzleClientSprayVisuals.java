package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerItem;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleType;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class HandheldNozzleClientSprayVisuals {
	private static final List<LightweightProjectile> PROJECTILES = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static String activeKey = "";
	private static AbstractSprayDeviceBlockEntity.FluidBehavior lastBehavior =
		AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED;
	private static String lastFuelPath = "";
	private static Vector3f lastPotionColor = new Vector3f(1, 1, 1);
	private static int lastTrailParticles = 3;

	private HandheldNozzleClientSprayVisuals() {
	}

	public static void tick(LocalPlayer player, ItemStack stack, boolean spraying) {
		if (player == null || stack.isEmpty()) {
			clear();
			return;
		}

		HandheldNozzleControllerItem.readBinding(stack).ifPresentOrElse(binding -> {
			String key = binding.dimension().location() + "|" + binding.pos().asLong() + "|" + binding.hydrantId();
			if (!key.equals(activeKey)) {
				clearProjectiles();
				activeKey = key;
			}
			if (!player.level().dimension().equals(binding.dimension())) {
				tickExisting(player.level());
				return;
			}
			if (!(player.level().getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
				|| !cabinet.getHydrantId().equals(binding.hydrantId())) {
				tickExisting(player.level());
				return;
			}

			FluidStack fluid = cabinet.getFluid();
			if (fluid.isEmpty()) {
				tickExisting(player.level());
				return;
			}
			AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
				AbstractSprayDeviceBlockEntity.classifyFluidForSpray(player.level(), fluid);
			if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED) {
				tickExisting(player.level());
				return;
			}

			HandheldNozzleType nozzleType = binding.nozzleType();
			lastBehavior = behavior;
			lastFuelPath = SprayProjectileVisuals.fuelPath(fluid);
			lastPotionColor = potionColor(fluid);
			lastTrailParticles = trailParticlesPerTick(nozzleType);

			if (spraying) {
				Vec3 direction = player.getLookAngle().normalize();
				Vec3 origin = player.getEyePosition().add(direction.scale(0.75)).add(0.0, -0.18, 0.0);
				SprayShape shape = projectileShape(nozzleType);
				boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
					|| behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA;
				for (int i = 0; i < Config.serverProjectilesPerTick; i++) {
					Vec3 dir = shape.randomSprayDirection(direction, RANDOM);
					LightweightProjectile projectile = new LightweightProjectile(origin,
						dir.scale(projectileSpeed(nozzleType)), projectileLifetime(nozzleType), behavior,
						(float) AbstractSprayDeviceBlockEntity.PUSH_STREAM_SPEED,
						new Vec3(0.0, -projectileGravity(nozzleType), 0.0), projectileFriction(nozzleType));
					if (ignited && behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE) {
						projectile.ignited = true;
						projectile.ignitedAtAge = 0;
					}
					PROJECTILES.add(projectile);
				}
			}

			tickExisting(player.level());
		}, HandheldNozzleClientSprayVisuals::clear);
	}

	private static void tickExisting(Level level) {
		if (PROJECTILES.isEmpty())
			return;
		if (lastBehavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED) {
			PROJECTILES.clear();
			return;
		}
		SprayProjectileVisuals.tickClientProjectiles(level, PROJECTILES, lastBehavior,
			lastFuelPath, lastPotionColor, lastTrailParticles, pos -> pos);
	}

	private static void clear() {
		clearProjectiles();
		activeKey = "";
	}

	private static void clearProjectiles() {
		PROJECTILES.clear();
		lastBehavior = AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED;
		lastFuelPath = "";
		lastPotionColor = new Vector3f(1, 1, 1);
		lastTrailParticles = 3;
	}

	private static SprayShape projectileShape(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> new FanSprayShape(Config.flatNozzleMaxDistance, 60.0, 4.0);
			default -> new ConeSprayShape(Config.coneNozzleMaxDistance, 10.0);
		};
	}

	private static double projectileSpeed(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> Config.flatNozzleSpeed;
			default -> Config.coneNozzleSpeed;
		};
	}

	private static double projectileGravity(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> Config.flatNozzleGravity;
			default -> Config.coneNozzleGravity;
		};
	}

	private static double projectileFriction(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> Config.flatNozzleFriction;
			default -> Config.coneNozzleFriction;
		};
	}

	private static int trailParticlesPerTick(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> 4;
			default -> 3;
		};
	}

	private static int projectileLifetime(HandheldNozzleType type) {
		double f = projectileFriction(type);
		double v = projectileSpeed(type);
		double d = Math.min(maxRange(type), v / (1.0 - f));
		double target = 1.0 - d * (1.0 - f) / v;
		if (target <= 0)
			target = 0.001;
		int ticks = (int) (Math.log(target) / Math.log(f));
		return Math.max(20, ticks);
	}

	private static int maxRange(HandheldNozzleType type) {
		return switch (type) {
			case FLAT -> Config.flatNozzleMaxDistance;
			default -> Config.coneNozzleMaxDistance;
		};
	}

	private static Vector3f potionColor(FluidStack fluid) {
		PotionContents contents = fluid.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		int rgb = contents.getColor();
		return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
	}
}
