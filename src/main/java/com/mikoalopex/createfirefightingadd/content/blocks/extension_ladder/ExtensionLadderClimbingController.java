package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class ExtensionLadderClimbingController {
	private static final double NORMAL_GRAB_DISTANCE = 0.36;
	private static final double WIDTH_GRAB_MARGIN = 0.16;
	private static final double LENGTH_GRAB_MARGIN = 0.18;
	private static final double CLIMB_SPEED = 0.13;
	private static final double HOLD_SPEED = -0.015;
	private static final int LOCAL_SCAN_RADIUS = 6;

	private ExtensionLadderClimbingController() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (player.level().isClientSide || player.isSpectator() || player.getAbilities().flying || player.isShiftKeyDown())
			return;

		ClimbTarget target = findTarget(player);
		if (target == null)
			return;

		Vec3 motion = player.getDeltaMovement();
		double input = Math.abs(player.zza) > 0.01 ? player.zza : 0;
		if (input == 0) {
			if (motion.y < HOLD_SPEED)
				player.setDeltaMovement(motion.x * 0.35, HOLD_SPEED, motion.z * 0.35);
			player.fallDistance = 0;
			return;
		}

		double viewSign = Math.signum(player.getLookAngle().dot(target.frame().longAxis()));
		if (viewSign == 0)
			viewSign = 1;
		Vec3 climb = target.frame().longAxis().scale(input * viewSign * CLIMB_SPEED);
		player.setDeltaMovement(climb.x, climb.y, climb.z);
		player.fallDistance = 0;
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		if (event.getEntity() instanceof Player player && findTarget(player) != null) {
			event.setCanceled(true);
			player.fallDistance = 0;
		}
	}

	@Nullable
	private static ClimbTarget findTarget(Player player) {
		Level level = player.level();
		AABB box = player.getBoundingBox().inflate(0.35, 0.15, 0.35);
		List<Vec3> samples = bodySamples(player);
		ClimbTarget worldTarget = scanLevel(level, box, samples);
		if (worldTarget != null)
			return worldTarget;

		for (SableStructureCompat.SubLevelProjection projection :
			SableStructureCompat.projectWorldPositionsToSubLevels(level, samples)) {
			AABB localBox = boxAround(projection.positions()).inflate(0.35, 0.15, 0.35);
			ClimbTarget subLevelTarget = scanLevel(projection.level(), localBox, samples);
			if (subLevelTarget != null)
				return subLevelTarget;
		}
		return null;
	}

	@Nullable
	private static ClimbTarget scanLevel(Level level, AABB searchBox, List<Vec3> worldSamples) {
		BlockPos min = BlockPos.containing(searchBox.minX - LOCAL_SCAN_RADIUS, searchBox.minY - LOCAL_SCAN_RADIUS,
			searchBox.minZ - LOCAL_SCAN_RADIUS);
		BlockPos max = BlockPos.containing(searchBox.maxX + LOCAL_SCAN_RADIUS, searchBox.maxY + LOCAL_SCAN_RADIUS,
			searchBox.maxZ + LOCAL_SCAN_RADIUS);
		ClimbTarget best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!(level.getBlockEntity(pos) instanceof ExtensionLadderBlockEntity ladder) || !ladder.isClimbable())
				continue;
			ClimbTarget target = match(ladder, worldSamples);
			if (target == null || target.distanceSqr() >= bestDistance)
				continue;
			best = target;
			bestDistance = target.distanceSqr();
		}
		return best;
	}

	@Nullable
	private static ClimbTarget match(ExtensionLadderBlockEntity ladder, List<Vec3> worldSamples) {
		ExtensionLadderGeometry.WorldFrame frame = ladder.worldPhysicsFrame(ladder.getPitch(1));
		double best = Double.MAX_VALUE;
		for (Vec3 sample : worldSamples) {
			ExtensionLadderGeometry.Projection projection = frame.project(sample);
			if (!projection.inside(ExtensionLadderGeometry.MAX_LENGTH + LENGTH_GRAB_MARGIN,
				ExtensionLadderGeometry.HALF_WIDTH + WIDTH_GRAB_MARGIN, NORMAL_GRAB_DISTANCE))
				continue;
			if (projection.along() < 0.12)
				continue;
			double distance = projection.normal() * projection.normal() + Math.max(0,
				Math.abs(projection.width()) - ExtensionLadderGeometry.HALF_WIDTH) * 0.25;
			best = Math.min(best, distance);
		}
		return best == Double.MAX_VALUE ? null : new ClimbTarget(frame, best);
	}

	private static List<Vec3> bodySamples(Player player) {
		AABB box = player.getBoundingBox();
		double x = (box.minX + box.maxX) * 0.5;
		double z = (box.minZ + box.maxZ) * 0.5;
		return List.of(
			new Vec3(x, box.minY + 0.15, z),
			new Vec3(x, Mth.lerp(0.45, box.minY, box.maxY), z),
			new Vec3(x, box.maxY - 0.15, z)
		);
	}

	private static AABB boxAround(List<Vec3> positions) {
		if (positions.isEmpty())
			return new AABB(0, 0, 0, 0, 0, 0);
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Vec3 position : positions) {
			minX = Math.min(minX, position.x);
			minY = Math.min(minY, position.y);
			minZ = Math.min(minZ, position.z);
			maxX = Math.max(maxX, position.x);
			maxY = Math.max(maxY, position.y);
			maxZ = Math.max(maxZ, position.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private record ClimbTarget(ExtensionLadderGeometry.WorldFrame frame, double distanceSqr) {
	}
}
