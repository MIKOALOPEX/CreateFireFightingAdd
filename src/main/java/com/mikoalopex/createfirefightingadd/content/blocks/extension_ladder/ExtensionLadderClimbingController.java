package com.mikoalopex.createfirefightingadd.content.blocks.extension_ladder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
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
	private static final double SIDE_SPEED = 0.12;
	private static final double TOP_SUPPORT_OVERHANG = 1.0 / 16.0;
	private static final double TOP_SUPPORT_ENTRY_MARGIN = 1.0 / 16.0;
	private static final int LOCAL_SCAN_RADIUS = Mth.ceil(ExtensionLadderGeometry.MAX_LENGTH) + 2;
	private static final int INPUT_MEMORY_TICKS = 5;
	private static final int JUMP_INPUT_MEMORY_TICKS = 5;
	private static final int JUMP_RELEASE_TICKS = 7;
	private static final Map<UUID, MovementInputState> MOVEMENT_INPUTS = new HashMap<>();
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
		if (target == null)
			return;

		applyClimbMotion(player, target, getMovementInput(player), consumeJumpPulse(player));
	}

	public static void clientTick(Player player, int forwardInput, int strafeInput, boolean jumpPulse) {
		if (!player.level().isClientSide || player.isSpectator() || player.getAbilities().flying)
			return;
		if (isJumpReleased(player) && !jumpPulse)
			return;
		ClimbTarget target = findTarget(player);
		if (target == null)
			return;
		applyClimbMotion(player, target,
			new MovementInputState(Mth.clamp(forwardInput, -1, 1), Mth.clamp(strafeInput, -1, 1),
				player.level().getGameTime()), jumpPulse);
	}

	private static void applyClimbMotion(Player player, ClimbTarget target, MovementInputState input,
										 boolean jumpPulse) {
		Vec3 motion = player.getDeltaMovement();
		if (target.mode() == ContactMode.TOP_SUPPORT) {
			applyTopSupport(player, motion, jumpPulse);
			return;
		}
		if (target.mode() == ContactMode.PASSIVE_END)
			return;

		CollisionCorrection correction = resolvePenetration(target, motion);
		if (!correction.touchingSurface())
			return;
		if (jumpPulse) {
			jumpFromLadder(player, motion);
			return;
		}

		ClimbIntent intent = climbIntent(player, target, input);
		if (correction.hasCorrection())
			player.setPos(player.getX() + correction.positionDelta().x, player.getY() + correction.positionDelta().y,
				player.getZ() + correction.positionDelta().z);

		Vec3 normalMotion = target.frame().normal().scale(Math.max(0, motion.dot(target.frame().normal())));
		Vec3 ladderMotion = target.frame().right().scale(intent.sideSpeed())
			.add(normalMotion)
			.add(target.frame().longAxis().scale(intent.axisSpeed()));
		player.setDeltaMovement(ladderMotion.x, ladderMotion.y, ladderMotion.z);
		player.fallDistance = 0;
		player.hasImpulse = true;
	}

	private static void applyTopSupport(Player player, Vec3 motion, boolean jumpPulse) {
		if (jumpPulse) {
			jumpFromLadder(player, motion);
			return;
		}

		if (motion.y >= 0)
			return;

		Vec3 supportedMotion = new Vec3(motion.x, 0, motion.z);
		player.setDeltaMovement(supportedMotion);
		player.setOnGround(true);
		player.fallDistance = 0;
		player.hasImpulse = true;
	}

	private static void jumpFromLadder(Player player, Vec3 previousMotion) {
		JUMP_RELEASES.put(PlayerSideKey.of(player), player.level().getGameTime() + JUMP_RELEASE_TICKS);
		Vec3 jump = new Vec3(previousMotion.x, LADDER_JUMP_UP_SPEED, previousMotion.z);
		player.setDeltaMovement(jump);
		player.setOnGround(false);
		player.fallDistance = 0;
		player.hasImpulse = true;
	}

	private static ClimbIntent climbIntent(Player player, ClimbTarget target, MovementInputState input) {
		Vec3 direction = movementIntent(player, input);
		if (direction.lengthSqr() < COLLISION_EPSILON)
			return ClimbIntent.hold();

		Vec3 climbDirection = horizontalClimbDirection(target);
		if (climbDirection.lengthSqr() < COLLISION_EPSILON)
			return ClimbIntent.hold();

		double along = direction.dot(climbDirection);
		double side = direction.dot(target.frame().right());

		double axisSpeed = along == 0 ? 0 : along * (along > 0 ? CLIMB_SPEED : RETRACT_SPEED);
		double sideSpeed = side * SIDE_SPEED;
		return new ClimbIntent(axisSpeed, sideSpeed);
	}

	private static Vec3 movementIntent(Player player, MovementInputState input) {
		if (input.forwardInput() == 0 && input.strafeInput() == 0)
			return Vec3.ZERO;
		Vec3 forward = ExtensionLadderGeometry.normalizeHorizontal(player.getLookAngle());
		if (forward.lengthSqr() < COLLISION_EPSILON)
			return Vec3.ZERO;
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 intent = forward.scale(input.forwardInput()).add(right.scale(input.strafeInput()));
		if (intent.lengthSqr() < COLLISION_EPSILON)
			return Vec3.ZERO;
		return intent.normalize();
	}

	private static Vec3 horizontalClimbDirection(ClimbTarget target) {
		Vec3 climbDirection = ExtensionLadderGeometry.normalizeHorizontal(target.frame().longAxis());
		if (climbDirection.lengthSqr() < COLLISION_EPSILON)
			climbDirection = ExtensionLadderGeometry.normalizeHorizontal(target.frame().normal().scale(-1));
		return climbDirection;
	}

	public static void setInput(Player player, int forwardInput, int strafeInput, boolean jumpPulse) {
		int clampedForward = Mth.clamp(forwardInput, -1, 1);
		int clampedStrafe = Mth.clamp(strafeInput, -1, 1);
		MOVEMENT_INPUTS.put(player.getUUID(),
			new MovementInputState(clampedForward, clampedStrafe, player.level().getGameTime()));
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
			if (level.isOutsideBuildHeight(pos))
				continue;
			BlockEntity blockEntity;
			if (level instanceof ServerLevel serverLevel) {
				// Climb detection must never load chunks or wait for chunk generation.
				LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
				if (chunk == null)
					continue;
				blockEntity = chunk.getBlockEntity(pos);
			} else {
				if (!level.isLoaded(pos))
					continue;
				blockEntity = level.getBlockEntity(pos);
			}
			if (!(blockEntity instanceof ExtensionLadderBlockEntity ladder) || !ladder.isClimbable())
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
		double climbLength = ladder.getClimbLength();
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
		return new ClimbTarget(frame, distance, contact, climbLength, mode);
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
		// Ends are passive so the player can leave the ladder; only the climb face resolves penetration.
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
		return new CollisionCorrection(frame.normal().scale(push), true);
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

	private static MovementInputState getMovementInput(Player player) {
		MovementInputState state = MOVEMENT_INPUTS.get(player.getUUID());
		if (state == null)
			return MovementInputState.NONE;
		if (player.level().getGameTime() - state.updatedAt() > INPUT_MEMORY_TICKS) {
			MOVEMENT_INPUTS.remove(player.getUUID());
			return MovementInputState.NONE;
		}
		return state;
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
							   double climbLength, ContactMode mode) {
	}

	private record CollisionContact(ExtensionLadderGeometry.Projection centerProjection,
									ExtensionLadderGeometry.Projection feetProjection, AxisInterval alongInterval,
									AxisInterval widthInterval, AxisInterval normalInterval) {
	}

	private record CollisionCorrection(Vec3 positionDelta, boolean touchingSurface) {
		private static final CollisionCorrection NONE = new CollisionCorrection(Vec3.ZERO, false);
		private static final CollisionCorrection TOUCHING = new CollisionCorrection(Vec3.ZERO, true);

		private boolean hasCorrection() {
			return positionDelta.lengthSqr() > COLLISION_EPSILON * COLLISION_EPSILON;
		}
	}

	private record AxisInterval(double min, double max) {
	}

	private record MovementInputState(int forwardInput, int strafeInput, long updatedAt) {
		private static final MovementInputState NONE = new MovementInputState(0, 0, Long.MIN_VALUE);
	}

	private record ClimbIntent(double axisSpeed, double sideSpeed) {
		private static ClimbIntent hold() {
			return new ClimbIntent(0, 0);
		}
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
