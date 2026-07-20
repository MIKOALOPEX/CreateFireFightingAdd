package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.content.fluids.FluidPropagator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class FireHoseConnections {

	public enum Result {
		SUCCESS,
		MISSING_ENDPOINT,
		SAME_ENDPOINT,
		OUT_OF_RANGE
	}

	public record ConnectionAttempt(Result result, @Nullable FireHoseBlockEntity endpoint) {
		public boolean successful() {
			return result == Result.SUCCESS && endpoint != null;
		}
	}

	private FireHoseConnections() {
	}

	public static Result tryConnect(Level level, BlockPos firstPos, BlockPos secondPos) {
		if (level == null)
			return Result.MISSING_ENDPOINT;
		if (firstPos.equals(secondPos))
			return Result.SAME_ENDPOINT;

		FireHoseBlockEntity first = getEndpoint(level, firstPos);
		FireHoseBlockEntity second = getEndpoint(level, secondPos);
		if (first == null || second == null)
			return Result.MISSING_ENDPOINT;
		return tryConnect(first, second);
	}

	public static Result tryConnect(FireHoseBlockEntity first, FireHoseBlockEntity second) {
		if (first == null || second == null)
			return Result.MISSING_ENDPOINT;
		if (first == second || first.getFireHoseEndpointId().equals(second.getFireHoseEndpointId()))
			return Result.SAME_ENDPOINT;
		if (!isWithinRange(first, second))
			return Result.OUT_OF_RANGE;

		Level firstLevel = first.getLevel();
		Level secondLevel = second.getLevel();
		if (firstLevel == null || secondLevel == null)
			return Result.MISSING_ENDPOINT;

		FireHoseBlockEntity controller = first.isController() || !second.isController() ? first : second;
		FireHoseBlockEntity partner = controller == first ? second : first;
		boolean black = first.isBlackHose() || second.isBlackHose();

		disconnect(first);
		disconnect(second);

		controller.setFireHoseConnection(true, partner.getBlockPos(),
			SableStructureCompat.containingSubLevelId(partner.getLevel(), partner.getBlockPos()),
			partner.getFireHoseEndpointId(), black);
		partner.setFireHoseConnection(false, controller.getBlockPos(),
			SableStructureCompat.containingSubLevelId(controller.getLevel(), controller.getBlockPos()),
			controller.getFireHoseEndpointId(), black);
		markConnectionChanged(controller);
		markConnectionChanged(partner);
		return Result.SUCCESS;
	}

	/**
	 * Finds the nearest unconnected endpoint around {@code origin} and connects it.
	 * The search is performed in world space, then projected into Sable sub-levels
	 * when Sable is present. Moving contraption endpoints are handled separately.
	 */
	public static ConnectionAttempt tryConnectFirstFreeEndpoint(FireHoseBlockEntity origin) {
		FireHoseBlockEntity endpoint = findFirstFreeEndpoint(origin);
		if (endpoint == null)
			return new ConnectionAttempt(Result.MISSING_ENDPOINT, null);
		Result result = tryConnect(origin, endpoint);
		return new ConnectionAttempt(result, result == Result.SUCCESS ? endpoint : null);
	}

	@Nullable
	public static FireHoseBlockEntity findFirstFreeEndpoint(FireHoseBlockEntity origin) {
		return findFirstFreeEndpoint(origin, (int) Math.ceil(Config.hoseMaxLength + 1));
	}

	/**
	 * Returns the first free endpoint found by expanding out from {@code origin}.
	 * This is the reusable search used by the Fire Hose Connector free mode.
	 */
	@Nullable
	public static FireHoseBlockEntity findFirstFreeEndpoint(FireHoseBlockEntity origin, int range) {
		if (origin == null || origin.getLevel() == null)
			return null;
		Level worldLevel = SableStructureCompat.worldLevel(origin);
		if (worldLevel == null)
			return null;

		int searchRange = Math.max(1, range);
		Vec3 center = worldCenter(origin);
		BlockPos centerPos = BlockPos.containing(center);
		for (int radius = 0; radius <= searchRange; radius++) {
			ArrayList<Vec3> shellCenters = new ArrayList<>();
			FireHoseBlockEntity worldEndpoint = findFreeEndpointInWorldShell(
				worldLevel, origin, center, centerPos, radius, searchRange, shellCenters);
			if (worldEndpoint != null)
				return worldEndpoint;

			FireHoseBlockEntity subLevelEndpoint = findFreeEndpointInSubLevels(origin, worldLevel, shellCenters);
			if (subLevelEndpoint != null)
				return subLevelEndpoint;
		}
		return null;
	}

	public static void disconnect(FireHoseBlockEntity hose) {
		if (hose == null)
			return;

		Level level = hose.getLevel();
		BlockPos partnerPos = hose.getFireHosePartnerPos();
		hose.clearFireHoseConnection();
		markConnectionChanged(hose);

		if (level == null)
			return;
		FireHoseMovingEndpoints.disconnectAttachedTo(level, hose.getBlockPos());
		if (partnerPos == null)
			return;
		FireHoseBlockEntity partner = getEndpoint(level, partnerPos);
		if (partner == null) {
			FireHoseMovingEndpoints.disconnect(level, partnerPos, hose.getBlockPos());
			return;
		}
		boolean modernBackReference = hose.getFireHoseEndpointId().equals(partner.getFireHosePartnerEndpointId());
		boolean legacyBackReference = partner.getFireHosePartnerEndpointId() == null;
		if (hose.getBlockPos().equals(partner.getFireHosePartnerPos())
			&& (modernBackReference || legacyBackReference)) {
			partner.clearFireHoseConnection();
			markConnectionChanged(partner);
		}
	}

	public static boolean isWithinRange(Level level, BlockPos firstPos, BlockPos secondPos) {
		double maxLength = Config.hoseMaxLength + 1;
		return SableStructureCompat.distanceSquared(level, firstPos.getCenter(), secondPos.getCenter())
			<= maxLength * maxLength;
	}

	public static boolean isWithinRange(FireHoseBlockEntity first, FireHoseBlockEntity second) {
		if (first == null || second == null || first.getLevel() == null || second.getLevel() == null)
			return false;
		Level firstWorld = SableStructureCompat.worldLevel(first);
		Level secondWorld = SableStructureCompat.worldLevel(second);
		if (firstWorld == null || secondWorld == null || !firstWorld.dimension().equals(secondWorld.dimension()))
			return false;

		double maxLength = Config.hoseMaxLength + 1;
		return worldCenter(first).distanceToSqr(worldCenter(second)) <= maxLength * maxLength;
	}

	public static Vec3 worldCenter(FireHoseBlockEntity hose) {
		return SableStructureCompat.transformPositionToWorld(hose, hose.getBlockPos().getCenter());
	}

	private static FireHoseBlockEntity getEndpoint(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof FireHoseBlockEntity hose ? hose : null;
	}

	@Nullable
	private static FireHoseBlockEntity findFreeEndpointInWorldShell(Level worldLevel, FireHoseBlockEntity origin,
			Vec3 center, BlockPos centerPos, int radius, int range, ArrayList<Vec3> shellCenters) {
		double maxDistance = (double) range * range;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != radius)
						continue;
					BlockPos pos = centerPos.offset(x, y, z);
					Vec3 posCenter = pos.getCenter();
					if (center.distanceToSqr(posCenter) > maxDistance)
						continue;
					shellCenters.add(posCenter);
					BlockEntity be = worldLevel.getBlockEntity(pos);
					if (be instanceof FireHoseBlockEntity hose && isConnectableFreeEndpoint(origin, hose))
						return hose;
				}
			}
		}
		return null;
	}

	@Nullable
	private static FireHoseBlockEntity findFreeEndpointInSubLevels(FireHoseBlockEntity origin, Level worldLevel,
			ArrayList<Vec3> worldCenters) {
		if (worldCenters.isEmpty())
			return null;

		for (SableStructureCompat.SubLevelProjection projection :
				SableStructureCompat.projectWorldPositionsToSubLevels(worldLevel, worldCenters)) {
			Set<Long> seen = new HashSet<>();
			for (Vec3 localCenter : projection.positions()) {
				BlockPos localPos = BlockPos.containing(localCenter);
				if (!seen.add(localPos.asLong()))
					continue;
				BlockEntity be = projection.level().getBlockEntity(localPos);
				if (be instanceof FireHoseBlockEntity hose && isConnectableFreeEndpoint(origin, hose))
					return hose;
			}
		}
		return null;
	}

	private static boolean isConnectableFreeEndpoint(FireHoseBlockEntity origin, FireHoseBlockEntity candidate) {
		return candidate != origin
			&& candidate.getFireHosePartnerPos() == null
			&& !candidate.getFireHoseEndpointId().equals(origin.getFireHoseEndpointId())
			&& isWithinRange(origin, candidate);
	}

	private static void markConnectionChanged(FireHoseBlockEntity hose) {
		hose.onFireHoseConnectionChanged();
		Level level = hose.getLevel();
		if (level == null)
			return;

		BlockPos pos = hose.getBlockPos();
		FluidPropagator.propagateChangedPipe(level, pos, level.getBlockState(pos));
		BlockPos backPos = pos.relative(hose.getBack());
		FluidPropagator.propagateChangedPipe(level, backPos, level.getBlockState(backPos));
	}
}
