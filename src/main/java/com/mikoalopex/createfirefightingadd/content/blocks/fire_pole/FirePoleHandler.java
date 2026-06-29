package com.mikoalopex.createfirefightingadd.content.blocks.fire_pole;

import java.util.List;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class FirePoleHandler {
	private static final double GRAB_RADIUS = 1.5;
	private static final double GRAB_RADIUS_SQR = GRAB_RADIUS * GRAB_RADIUS;
	private static final double SLIDE_SPEED = -0.18;

	private FirePoleHandler() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (player.isSpectator() || player.getAbilities().flying)
			return;
		if (player.onGround())
			return;
		if (!isNearFirePole(player))
			return;

		Vec3 motion = player.getDeltaMovement();
		if (motion.y < SLIDE_SPEED) {
			player.setDeltaMovement(motion.x, SLIDE_SPEED, motion.z);
			player.fallDistance = 0;
		} else if (motion.y < 0) {
			player.fallDistance = 0;
		}
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity instanceof Player && isNearFirePole(entity)) {
			event.setCanceled(true);
			entity.fallDistance = 0;
		}
	}

	public static boolean isNearFirePole(LivingEntity entity) {
		Level level = entity.level();
		if (level == null)
			return false;

		List<Vec3> worldSamples = bodySamples(entity);
		for (Vec3 sample : worldSamples)
			if (isNearFirePoleInLevel(level, sample))
				return true;

		return SableStructureCompat.hasFirePoleNearEntity(level, entity.getBoundingBox(), worldSamples, GRAB_RADIUS_SQR);
	}

	private static List<Vec3> bodySamples(LivingEntity entity) {
		AABB box = entity.getBoundingBox();
		double x = (box.minX + box.maxX) * 0.5;
		double z = (box.minZ + box.maxZ) * 0.5;
		return List.of(
			new Vec3(x, box.minY + 0.1, z),
			new Vec3(x, (box.minY + box.maxY) * 0.5, z),
			new Vec3(x, box.maxY - 0.1, z)
		);
	}

	private static boolean isNearFirePoleInLevel(Level level, Vec3 position) {
		BlockPos center = BlockPos.containing(position);
		for (int x = -2; x <= 2; x++) {
			for (int y = -3; y <= 3; y++) {
				for (int z = -2; z <= 2; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (!level.getBlockState(pos).is(CreateFireFightingAdd.FIRE_POLE.get()))
						continue;
					if (isInsidePoleCylinder(position, pos, GRAB_RADIUS_SQR))
						return true;
				}
			}
		}
		return false;
	}

	private static boolean isInsidePoleCylinder(Vec3 position, BlockPos polePos, double radiusSqr) {
		Vec3 start = new Vec3(polePos.getX() + 0.5, polePos.getY(), polePos.getZ() + 0.5);
		Vec3 end = new Vec3(polePos.getX() + 0.5, polePos.getY() + 1.0, polePos.getZ() + 0.5);
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr < 1.0E-6)
			return false;

		double t = position.subtract(start).dot(segment) / lengthSqr;
		if (t < 0.0 || t > 1.0)
			return false;
		Vec3 closest = start.add(segment.scale(t));
		return position.distanceToSqr(closest) <= radiusSqr;
	}

}
