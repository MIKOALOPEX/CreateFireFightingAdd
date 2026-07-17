package com.mikoalopex.createfirefightingadd.content.fluids.nozzle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.FireHydrantCabinetBlockEntity;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleControllerItem;
import com.mikoalopex.createfirefightingadd.content.equipment.handheld.HandheldNozzleType;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class HandheldNozzleClientSprayVisuals {
	private static final int DEBUG_KEY_PREFIX = 0x48000000;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<Integer, VisualState> STATES = new HashMap<>();

	private HandheldNozzleClientSprayVisuals() {
	}

	public static void tick(Player player, ItemStack stack, boolean spraying) {
		if (player == null || stack.isEmpty()) {
			if (player != null)
				clear(player.getId());
			return;
		}

		HandheldNozzleControllerItem.readBinding(stack).ifPresentOrElse(
			binding -> tick(player, binding, spraying),
			() -> clear(player.getId()));
	}

	public static void tick(Player player, HandheldNozzleControllerItem.Binding binding, boolean spraying) {
		if (player == null || binding == null) {
			if (player != null)
				clear(player.getId());
			return;
		}

		VisualState state = STATES.computeIfAbsent(player.getId(), ignored -> new VisualState());
		String key = binding.dimension().location() + "|" + binding.pos().asLong() + "|" + binding.hydrantId();
		if (!key.equals(state.activeKey)) {
			state.clearProjectiles();
			state.activeKey = key;
		}
		if (!player.level().dimension().equals(binding.dimension())) {
			tickExisting(player.level(), state);
			return;
		}
		if (!(player.level().getBlockEntity(binding.pos()) instanceof FireHydrantCabinetBlockEntity cabinet)
			|| !cabinet.getHydrantId().equals(binding.hydrantId())) {
			tickExisting(player.level(), state);
			return;
		}

		FluidStack fluid = cabinet.getFluid();
		if (fluid.isEmpty()) {
			tickExisting(player.level(), state);
			return;
		}
		AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.classifyFluidForSpray(player.level(), fluid);
		if (behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED) {
			tickExisting(player.level(), state);
			return;
		}

		HandheldNozzleType nozzleType = binding.nozzleType();
		state.behavior = behavior;
		state.fuelPath = SprayProjectileVisuals.fuelPath(fluid);
		state.potionColor = potionColor(fluid);
		state.trailParticles = trailParticlesPerTick(nozzleType);

		if (spraying) {
			Vec3 direction = player.getLookAngle().normalize();
			Vec3 origin = player.getEyePosition().add(direction.scale(0.75)).add(0.0, -0.18, 0.0);
			SprayShape shape = projectileShape(nozzleType);
			boolean ignited = behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.FLAMMABLE
				|| behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.LAVA;
			SprayProjectileVisuals.spawnProjectiles(state.projectiles, origin, direction, shape,
				Config.serverProjectilesPerTick, projectileLifetime(nozzleType), projectileSpeed(nozzleType),
				behavior, ignited, new Vec3(0.0, -projectileGravity(nozzleType), 0.0),
				projectileFriction(nozzleType), RANDOM);
			SprayDebugRenderer.submitDynamicSpray(DEBUG_KEY_PREFIX ^ player.getId(), origin, direction,
				nozzleType != HandheldNozzleType.FLAT, maxRange(nozzleType), projectileSpeed(nozzleType),
				projectileGravity(nozzleType), projectileFriction(nozzleType), projectileLifetime(nozzleType),
				player.level().getGameTime() + 2);
		}

		tickExisting(player.level(), state);
	}

	private static void tickExisting(Level level, VisualState state) {
		if (state.projectiles.isEmpty())
			return;
		if (state.behavior == AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED) {
			state.projectiles.clear();
			return;
		}
		SprayProjectileVisuals.tickClientProjectiles(level, state.projectiles, state.behavior,
			state.fuelPath, state.potionColor, state.trailParticles, pos -> pos);
	}

	public static void clear(int entityId) {
		STATES.remove(entityId);
	}

	public static void clearAll() {
		STATES.clear();
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

	private static final class VisualState {
		private final List<LightweightProjectile> projectiles = new ArrayList<>();
		private String activeKey = "";
		private AbstractSprayDeviceBlockEntity.FluidBehavior behavior =
			AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED;
		private String fuelPath = "";
		private Vector3f potionColor = new Vector3f(1, 1, 1);
		private int trailParticles = 3;

		private void clearProjectiles() {
			projectiles.clear();
			behavior = AbstractSprayDeviceBlockEntity.FluidBehavior.UNSUPPORTED;
			fuelPath = "";
			potionColor = new Vector3f(1, 1, 1);
			trailParticles = 3;
		}
	}
}
