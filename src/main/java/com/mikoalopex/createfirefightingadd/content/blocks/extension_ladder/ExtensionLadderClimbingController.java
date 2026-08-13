package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class ExtensionLadderClimbingController {
	private static final double CLIMB_SPEED = 0.16;
	private static final double RETRACT_SPEED = 0.13;
	private static final double LADDER_JUMP_UP_SPEED = 0.42;
	private static final double COLLISION_HALF_THICKNESS = 2.0 / 16.0;
	private static final double COLLISION_SKIN = 0.01;
	private static final double COLLISION_EPSILON = 1.0E-4;
	private static final double SURFACE_CONTACT_DISTANCE = 0.08;
	private static final double LOOK_DIRECTION_EPSILON = 0.2;
	private static final double TOP_SUPPORT_OVERHANG = 1.0 / 16.0;
	private static final double TOP_SUPPORT_ENTRY_MARGIN = 1.0 / 16.0;
	private static final int LOCAL_SCAN_RADIUS = 6;
	private static final int LOG_INTERVAL_TICKS = 20;
	private static final int INPUT_MEMORY_TICKS = 5;
	private static final int JUMP_INPUT_MEMORY_TICKS = 5;
	private static final int JUMP_RELEASE_TICKS = 7;
	private static final Map<UUID, Long> LAST_LOG_TICK = new HashMap<>();
	private static final Map<UUID, ForwardInputState> FORWARD_INPUTS = new HashMap<>();
	private static final Map<UUID, Long> JUMP_REQUESTS = new HashMap<>();
	private static final Map<PlayerSideKey, Long> JUMP_RELEASES = new HashMap<>();

	private ExtensionLadderClimbingController() {
	}

	public static void serverTick(Player player) {
		if (player.level().isClientSide || player.isSpectator() || player.getAbilities().flying)
			return;
		if (isJumpReleased(player))
			return;

		ClimbTarget target = findTarget(player);
		if (target == null) {
			logNoTarget(player);
			return;
		}

		applyClimbMotion(player, target, getForwardInput(player), consumeJumpPulse(player), true);
	}

	public static void clientTick(Player player, int forwardInput, boolean jumpPulse) {
		if (!player.level().isClientSide || player.isSpectator() || player.getAbilities().flying)
			return;
		if (isJumpReleased(player) && !jumpPulse)
			return;
		ClimbTarget target = findTarget(player);
		if (target == null)
			return;
		applyClimbMotion(player, target, Mth.clamp(forwardInput, -1, 1), jumpPulse, false);
	}

	private static void applyClimbMotion(Player player, ClimbTarget target, double input, boolean jumpPulse, boolean log) {
		Vec3 motion = player.getDeltaMovement();
		if (target.mode() == ContactMode.TOP_SUPPORT) {
			applyTopSupport(player, target, motion, jumpPulse, log);
			return;
		}
		if (target.mode() == ContactMode.PASSIVE_END) {
			applyPassiveEndCollision(player, target, motion, log);
			return;
		}

		CollisionCorrection correction = resolvePenetration(target, motion);
		if (!correction.touchingSurface()) {
			if (log)
				logNearNoCore(player, target);
			return;
		}
		if (jumpPulse) {
			jumpFromLadder(player, target, motion);
			if (log)
				logJump(player, target, motion, player.getDeltaMovement());
			return;
		}

		double axisSpeed = climbAxisSpeed(player, target, input);
		if (correction.hasCorrection())
			player.setPos(player.getX() + correction.positionDelta().x, player.getY() + correction.positionDelta().y,
				player.getZ() + correction.positionDelta().z);

		Vec3 sideMotion = target.frame().right().scale(motion.dot(target.frame().right()));
		Vec3 ladderMotion = sideMotion.add(target.frame().longAxis().scale(axisSpeed));
		player.setDeltaMovement(ladderMotion.x, ladderMotion.y, ladderMotion.z);
		player.fallDistance = 0;
		player.hasImpulse = true;
		if (log)
			logApplied(player, target, motion, ladderMotion, axisSpeed, correction);
	}

	private static void applyTopSupport(Player player, ClimbTarget target, Vec3 motion, boolean jumpPulse, boolean log) {
		if (jumpPulse) {
			jumpFromLadder(player, target, motion);
			if (log)
				logJump(player, target, motion, player.getDeltaMovement());
			return;
		}

		Vec3 supportedMotion = motion;
		if (motion.y < 0) {
			supportedMotion = new Vec3(motion.x, 0, motion.z);
			player.setDeltaMovement(supportedMotion);
			player.setOnGround(true);
			player.fallDistance = 0;
			player.hasImpulse = true;
		}
		if (log)
			logTopSupport(player, target, motion, supportedMotion);
	}

	private static void applyPassiveEndCollision(Player player, ClimbTarget target, Vec3 motion, boolean log) {
		if (log)
			logPassiveEnd(player, target, motion, motion, CollisionCorrection.NONE);
	}

	private static void jumpFromLadder(Player player, ClimbTarget target, Vec3 previousMotion) {
		JUMP_RELEASES.put(PlayerSideKey.of(player), player.level().getGameTime() + JUMP_RELEASE_TICKS);
		Vec3 jump = new Vec3(previousMotion.x, LADDER_JUMP_UP_SPEED, previousMotion.z);
		player.setDeltaMovement(jump);
		player.setOnGround(false);
		player.fallDistance = 0;
		player.hasImpulse = true;
	}

	private static double climbAxisSpeed(Player player, ClimbTarget target, double input) {
		if (input != 0) {
			double viewSign = climbViewSign(player, target);
			double speed = input > 0 ? CLIMB_SPEED : RETRACT_SPEED;
			return Math.signum(input) * viewSign * speed;
		}
		return 0;
	}

	private static double climbViewSign(Player player, ClimbTarget target) {
		Vec3 look = ExtensionLadderGeometry.normalizeHorizontal(player.getLookAngle());
		if (look.lengthSqr() < COLLISION_EPSILON)
			return 1;

		Vec3 climbDirection = ExtensionLadderGeometry.normalizeHorizontal(target.frame().longAxis());
		if (climbDirection.lengthSqr() < COLLISION_EPSILON)
			climbDirection = ExtensionLadderGeometry.normalizeHorizontal(target.frame().normal().scale(-1));
		if (climbDirection.lengthSqr() < COLLISION_EPSILON)
			return 1;

		double lookAlong = look.dot(climbDirection);
		return Math.abs(lookAlong) < LOOK_DIRECTION_EPSILON ? 1 : Math.signum(lookAlong);
	}

	public static void setInput(Player player, int input, boolean jumpPulse) {
		int clamped = Mth.clamp(input, -1, 1);
		FORWARD_INPUTS.put(player.getUUID(), new ForwardInputState(clamped, player.level().getGameTime()));
		if (jumpPulse)
			JUMP_REQUESTS.put(player.getUUID(), player.level().getGameTime());
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		if (event.getEntity() instanceof Player player && isTouchingClimbSurface(findTarget(player))) {
			event.setCanceled(true);
			player.fallDistance = 0;
		}
	}

	@Nullable
	private static ClimbTarget findTarget(Player player) {
		Level level = player.level();
		AABB box = player.getBoundingBox();
		List<Vec3> samples = boxSamples(box);
		ClimbTarget worldTarget = scanLevel(level, box, box);
		if (worldTarget != null)
			return worldTarget;

		for (SableStructureCompat.SubLevelProjection projection :
			SableStructureCompat.projectWorldPositionsToSubLevels(level, samples)) {
			AABB localBox = boxAround(projection.positions()).inflate(0.35, 0.15, 0.35);
			ClimbTarget subLevelTarget = scanLevel(projection.level(), localBox, box);
			if (subLevelTarget != null)
				return subLevelTarget;
		}
		return null;
	}

	@Nullable
	private static ClimbTarget scanLevel(Level level, AABB searchBox, AABB playerWorldBox) {
		BlockPos min = BlockPos.containing(searchBox.minX - LOCAL_SCAN_RADIUS, searchBox.minY - LOCAL_SCAN_RADIUS,
			searchBox.minZ - LOCAL_SCAN_RADIUS);
		BlockPos max = BlockPos.containing(searchBox.maxX + LOCAL_SCAN_RADIUS, searchBox.maxY + LOCAL_SCAN_RADIUS,
			searchBox.maxZ + LOCAL_SCAN_RADIUS);
		ClimbTarget best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!(level.getBlockEntity(pos) instanceof ExtensionLadderBlockEntity ladder) || !ladder.isClimbable())
				continue;
			ClimbTarget target = match(ladder, playerWorldBox);
			if (target == null || target.distanceSqr() >= bestDistance)
				continue;
			best = target;
			bestDistance = target.distanceSqr();
		}
		return best;
	}

	@Nullable
	private static ClimbTarget match(ExtensionLadderBlockEntity ladder, AABB playerBox) {
		ExtensionLadderGeometry.WorldFrame frame = ladder.worldPhysicsFrame(ladder.getPitch(1));
		double climbLength = ExtensionLadderGeometry.climbLength(ladder.getMoveOffsetPixels(1));
		CollisionContact contact = createContact(frame, playerBox, climbLength);
		if (contact == null)
			return null;
		ContactMode mode = contactMode(contact, climbLength);
		if (mode == null)
			return null;

		double normalDistance = intervalDistance(contact.normalInterval(), ExtensionLadderGeometry.CLIMB_NORMAL_MIN,
			ExtensionLadderGeometry.CLIMB_NORMAL_MAX);
		double widthDistance = intervalDistance(contact.widthInterval(), -ExtensionLadderGeometry.HALF_WIDTH,
			ExtensionLadderGeometry.HALF_WIDTH);
		double alongDistance = intervalDistance(contact.alongInterval(), 0, climbLength);
		double distance = normalDistance * normalDistance + widthDistance * widthDistance * 0.25
			+ alongDistance * alongDistance * 0.1;
		return new ClimbTarget(frame, distance, contact, ladder.getBlockPos(), climbLength, mode);
	}

	@Nullable
	private static CollisionContact createContact(ExtensionLadderGeometry.WorldFrame frame, AABB playerBox,
												  double climbLength) {
		AxisInterval along = projectBox(playerBox, frame.anchor(), frame.longAxis());
		AxisInterval width = projectBox(playerBox, frame.anchor(), frame.right());
		AxisInterval normal = projectBox(playerBox, frame.anchor(), frame.normal());
		if (!overlaps(along, -ExtensionLadderGeometry.CLIMB_LENGTH_PADDING,
			climbLength + TOP_SUPPORT_OVERHANG)
			|| !overlaps(width, -ExtensionLadderGeometry.CLIMB_WIDTH_HALF_EXTENT,
				ExtensionLadderGeometry.CLIMB_WIDTH_HALF_EXTENT)
			|| !overlaps(normal, ExtensionLadderGeometry.CLIMB_NORMAL_MIN, ExtensionLadderGeometry.CLIMB_NORMAL_MAX))
			return null;

		Vec3 center = new Vec3((playerBox.minX + playerBox.maxX) * 0.5, (playerBox.minY + playerBox.maxY) * 0.5,
			(playerBox.minZ + playerBox.maxZ) * 0.5);
		ExtensionLadderGeometry.Projection centerProjection = frame.project(center);
		Vec3 feet = new Vec3(center.x, playerBox.minY, center.z);
		ExtensionLadderGeometry.Projection feetProjection = frame.project(feet);
		return new CollisionContact(centerProjection, feetProjection, along, width, normal);
	}

	@Nullable
	private static ContactMode contactMode(CollisionContact contact, double climbLength) {
		if (isTopSupportContact(contact, climbLength))
			return ContactMode.TOP_SUPPORT;
		if (isActiveClimbContact(contact, climbLength))
			return ContactMode.ACTIVE_CLIMB;
		if (isPassiveEndContact(contact, climbLength))
			return ContactMode.PASSIVE_END;
		return null;
	}

	private static boolean isActiveClimbContact(CollisionContact contact, double climbLength) {
		return contact.centerProjection().along() > COLLISION_EPSILON
			&& contact.feetProjection().along() < climbLength - COLLISION_EPSILON
			&& contact.alongInterval().max() > COLLISION_EPSILON;
	}

	private static boolean isPassiveEndContact(CollisionContact contact, double climbLength) {
		return contact.centerProjection().along() <= COLLISION_EPSILON
			|| contact.feetProjection().along() > climbLength + TOP_SUPPORT_OVERHANG;
	}

	private static boolean isTopSupportContact(CollisionContact contact, double climbLength) {
		double feetAlong = contact.feetProjection().along();
		boolean onTopEdge = feetAlong >= climbLength - COLLISION_EPSILON;
		boolean steppingOverTop = contact.centerProjection().along() > climbLength
			&& feetAlong >= climbLength - TOP_SUPPORT_ENTRY_MARGIN;
		return (onTopEdge || steppingOverTop)
			&& feetAlong <= climbLength + TOP_SUPPORT_OVERHANG
			&& contact.alongInterval().min() <= climbLength + TOP_SUPPORT_OVERHANG;
	}

	private static CollisionCorrection resolvePenetration(ClimbTarget target, Vec3 motion) {
		ExtensionLadderGeometry.WorldFrame frame = target.frame();
		CollisionContact contact = target.contact();
		if (!overlaps(contact.alongInterval(), -ExtensionLadderGeometry.CLIMB_LENGTH_PADDING,
			target.climbLength() + ExtensionLadderGeometry.CLIMB_LENGTH_PADDING)
			|| !overlaps(contact.widthInterval(), -ExtensionLadderGeometry.CLIMB_WIDTH_HALF_EXTENT,
				ExtensionLadderGeometry.CLIMB_WIDTH_HALF_EXTENT))
			return CollisionCorrection.NONE;

		double incoming = motion.dot(frame.normal());
		if (!isFrontSurfaceContact(contact))
			return CollisionCorrection.NONE;
		if (!overlaps(contact.normalInterval(), -COLLISION_HALF_THICKNESS, COLLISION_HALF_THICKNESS)
			|| incoming >= -COLLISION_EPSILON)
			return CollisionCorrection.TOUCHING;

		double push = COLLISION_HALF_THICKNESS - contact.normalInterval().min();
		if (Math.abs(push) < COLLISION_EPSILON)
			return CollisionCorrection.TOUCHING;

		push += Math.signum(push) * COLLISION_SKIN;
		return new CollisionCorrection(frame.normal().scale(push), push, true);
	}

	private static boolean isTouchingClimbSurface(@Nullable ClimbTarget target) {
		if (target == null)
			return false;
		if (target.mode() == ContactMode.TOP_SUPPORT)
			return true;
		if (target.mode() == ContactMode.PASSIVE_END)
			return false;
		CollisionContact contact = target.contact();
		return overlaps(contact.alongInterval(), -ExtensionLadderGeometry.CLIMB_LENGTH_PADDING,
			target.climbLength() + ExtensionLadderGeometry.CLIMB_LENGTH_PADDING)
			&& overlaps(contact.widthInterval(), -ExtensionLadderGeometry.CLIMB_WIDTH_HALF_EXTENT,
				ExtensionLadderGeometry.CLIMB_WIDTH_HALF_EXTENT)
			&& isFrontSurfaceContact(contact);
	}

	private static boolean isFrontSurfaceContact(CollisionContact contact) {
		if (contact.centerProjection().normal() < -COLLISION_EPSILON)
			return false;
		if (overlaps(contact.normalInterval(), -COLLISION_HALF_THICKNESS, COLLISION_HALF_THICKNESS))
			return true;
		double frontGap = contact.normalInterval().min() - COLLISION_HALF_THICKNESS;
		return frontGap >= -COLLISION_EPSILON && frontGap <= SURFACE_CONTACT_DISTANCE;
	}

	private static AxisInterval projectBox(AABB box, Vec3 anchor, Vec3 axis) {
		double halfX = (box.maxX - box.minX) * 0.5;
		double halfY = (box.maxY - box.minY) * 0.5;
		double halfZ = (box.maxZ - box.minZ) * 0.5;
		Vec3 center = new Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5,
			(box.minZ + box.maxZ) * 0.5);
		double projectedCenter = center.subtract(anchor).dot(axis);
		double radius = halfX * Math.abs(axis.x) + halfY * Math.abs(axis.y) + halfZ * Math.abs(axis.z);
		return new AxisInterval(projectedCenter - radius, projectedCenter + radius);
	}

	private static boolean overlaps(AxisInterval interval, double min, double max) {
		return interval.max() >= min && interval.min() <= max;
	}

	private static double intervalDistance(AxisInterval interval, double min, double max) {
		if (interval.max() < min)
			return min - interval.max();
		if (interval.min() > max)
			return interval.min() - max;
		return 0;
	}

	private static void logNoTarget(Player player) {
		if (!shouldLog(player))
			return;
		CreateFireFightingAdd.LOGGER.info("[ExtensionLadderClimb] no_intersection player={} box={}",
			player.getName().getString(), player.getBoundingBox());
	}

	private static void logNearNoCore(Player player, ClimbTarget target) {
		if (!shouldLog(player))
			return;
		ExtensionLadderGeometry.Projection projection = target.contact().centerProjection();
		CreateFireFightingAdd.LOGGER.info(
			"[ExtensionLadderClimb] near_no_core player={} ladder={} along={} feetAlong={} length={} width={} normal={} boxNormal=[{},{}]",
			player.getName().getString(), target.ladderPos(), round(projection.along()),
			round(target.contact().feetProjection().along()), round(target.climbLength()), round(projection.width()),
			round(projection.normal()),
			round(target.contact().normalInterval().min()), round(target.contact().normalInterval().max()));
	}

	private static void logApplied(Player player, ClimbTarget target, Vec3 before, Vec3 after, double axisSpeed,
								   CollisionCorrection correction) {
		if (!shouldLog(player))
			return;
		ExtensionLadderGeometry.Projection projection = target.contact().centerProjection();
		CreateFireFightingAdd.LOGGER.info(
			"[ExtensionLadderClimb] applied player={} ladder={} along={} feetAlong={} length={} width={} normal={} axisSpeed={} input={} collisionPush={} before={} after={}",
			player.getName().getString(), target.ladderPos(), round(projection.along()),
			round(target.contact().feetProjection().along()), round(target.climbLength()), round(projection.width()),
			round(projection.normal()), round(axisSpeed), round(getForwardInput(player)),
			round(correction.normalPush()), before, after);
	}

	private static void logJump(Player player, ClimbTarget target, Vec3 before, Vec3 after) {
		if (!shouldLog(player))
			return;
		ExtensionLadderGeometry.Projection projection = target.contact().centerProjection();
		CreateFireFightingAdd.LOGGER.info(
			"[ExtensionLadderClimb] jump player={} ladder={} along={} feetAlong={} length={} width={} normal={} before={} after={}",
			player.getName().getString(), target.ladderPos(), round(projection.along()),
			round(target.contact().feetProjection().along()), round(target.climbLength()), round(projection.width()),
			round(projection.normal()),
			before, after);
	}

	private static void logPassiveEnd(Player player, ClimbTarget target, Vec3 before, Vec3 after,
									  CollisionCorrection correction) {
		if (!shouldLog(player))
			return;
		ExtensionLadderGeometry.Projection projection = target.contact().centerProjection();
		CreateFireFightingAdd.LOGGER.info(
			"[ExtensionLadderClimb] passive_end player={} ladder={} along={} feetAlong={} length={} width={} normal={} collisionPush={} before={} after={}",
			player.getName().getString(), target.ladderPos(), round(projection.along()),
			round(target.contact().feetProjection().along()), round(target.climbLength()), round(projection.width()),
			round(projection.normal()), round(correction.normalPush()), before, after);
	}

	private static void logTopSupport(Player player, ClimbTarget target, Vec3 before, Vec3 after) {
		if (!shouldLog(player))
			return;
		ExtensionLadderGeometry.Projection projection = target.contact().feetProjection();
		CreateFireFightingAdd.LOGGER.info(
			"[ExtensionLadderClimb] top_support player={} ladder={} feetAlong={} length={} width={} normal={} before={} after={}",
			player.getName().getString(), target.ladderPos(), round(projection.along()), round(target.climbLength()),
			round(projection.width()), round(projection.normal()), before, after);
	}

	private static boolean shouldLog(Player player) {
		long now = player.level().getGameTime();
		Long last = LAST_LOG_TICK.get(player.getUUID());
		if (last != null && now - last < LOG_INTERVAL_TICKS)
			return false;
		LAST_LOG_TICK.put(player.getUUID(), now);
		return true;
	}

	private static double round(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}

	private static int getForwardInput(Player player) {
		ForwardInputState state = FORWARD_INPUTS.get(player.getUUID());
		if (state == null)
			return 0;
		if (player.level().getGameTime() - state.updatedAt() > INPUT_MEMORY_TICKS) {
			FORWARD_INPUTS.remove(player.getUUID());
			return 0;
		}
		return state.input();
	}

	private static boolean consumeJumpPulse(Player player) {
		Long requestedAt = JUMP_REQUESTS.remove(player.getUUID());
		if (requestedAt == null)
			return false;
		return player.level().getGameTime() - requestedAt <= JUMP_INPUT_MEMORY_TICKS;
	}

	private static boolean isJumpReleased(Player player) {
		PlayerSideKey key = PlayerSideKey.of(player);
		Long releaseUntil = JUMP_RELEASES.get(key);
		if (releaseUntil == null)
			return false;
		if (player.level().getGameTime() <= releaseUntil)
			return true;
		JUMP_RELEASES.remove(key);
		return false;
	}

	private static List<Vec3> boxSamples(AABB box) {
		double centerX = (box.minX + box.maxX) * 0.5;
		double centerY = (box.minY + box.maxY) * 0.5;
		double centerZ = (box.minZ + box.maxZ) * 0.5;
		return List.of(
			new Vec3(centerX, centerY, centerZ),
			new Vec3(centerX, box.minY + 0.02, centerZ),
			new Vec3(centerX, box.minY + 0.35, centerZ),
			new Vec3(centerX, box.maxY - 0.15, centerZ),
			new Vec3(box.minX, box.minY + 0.05, box.minZ),
			new Vec3(box.minX, box.minY + 0.05, box.maxZ),
			new Vec3(box.maxX, box.minY + 0.05, box.minZ),
			new Vec3(box.maxX, box.minY + 0.05, box.maxZ),
			new Vec3(box.minX, centerY, box.minZ),
			new Vec3(box.minX, centerY, box.maxZ),
			new Vec3(box.maxX, centerY, box.minZ),
			new Vec3(box.maxX, centerY, box.maxZ)
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

	private record ClimbTarget(ExtensionLadderGeometry.WorldFrame frame, double distanceSqr, CollisionContact contact,
							   BlockPos ladderPos, double climbLength, ContactMode mode) {
	}

	private record CollisionContact(ExtensionLadderGeometry.Projection centerProjection,
									ExtensionLadderGeometry.Projection feetProjection, AxisInterval alongInterval,
									AxisInterval widthInterval, AxisInterval normalInterval) {
	}

	private record CollisionCorrection(Vec3 positionDelta, double normalPush, boolean touchingSurface) {
		private static final CollisionCorrection NONE = new CollisionCorrection(Vec3.ZERO, 0, false);
		private static final CollisionCorrection TOUCHING = new CollisionCorrection(Vec3.ZERO, 0, true);

		private boolean hasCorrection() {
			return positionDelta.lengthSqr() > COLLISION_EPSILON * COLLISION_EPSILON;
		}
	}

	private record AxisInterval(double min, double max) {
	}

	private record ForwardInputState(int input, long updatedAt) {
	}

	private enum ContactMode {
		ACTIVE_CLIMB,
		PASSIVE_END,
		TOP_SUPPORT
	}

	private record PlayerSideKey(UUID playerId, boolean clientSide) {
		private static PlayerSideKey of(Player player) {
			return new PlayerSideKey(player.getUUID(), player.level().isClientSide);
		}
	}
}
