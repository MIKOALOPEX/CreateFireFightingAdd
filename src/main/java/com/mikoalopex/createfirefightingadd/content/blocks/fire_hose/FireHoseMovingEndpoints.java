package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.content.contraptions.ContraptionFluidAccess;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Runtime bridge between static hose endpoints and endpoints carried by Create contraptions.
 */
final class FireHoseMovingEndpoints {
	private static final double ENDPOINT_OFFSET = 0.5 - 4.0 / 16.0;
	private static final double TIME_TO_SNAP = 3.0;
	private static final int DUPLICATE_GRACE_TICKS = 2;
	private static final int STATIC_LEASE_TICKS = 8;
	private static final Map<Level, Map<EndpointKey, MovingEndpoint>> MOVING = new WeakHashMap<>();
	private static final Map<Level, Map<StaticLeaseKey, StaticLease>> STATIC_LEASES = new WeakHashMap<>();
	private static final Map<Level, Map<BlockPos, Replacement>> REPLACEMENTS = new WeakHashMap<>();
	private static final Map<Level, Long> LAST_CLEANUP = new WeakHashMap<>();

	private FireHoseMovingEndpoints() {
	}

	static void update(MovementContext context, FireHoseMountedFluidStorage storage) {
		if (context.position == null || context.world == null)
			return;

		UUID runtimeId = runtimeId(context);
		storage.setRuntimeId(runtimeId);
		MovingEndpoint endpoint = MovingEndpoint.of(context, storage, runtimeId);
		MOVING.computeIfAbsent(context.world, unused -> new HashMap<>()).put(endpoint.key(), endpoint);
		cleanup(context.world);

		if (context.world.isClientSide) {
			submitRender(context, storage, endpoint);
			return;
		}

		if (!validateStaticPartner(context.world, storage, endpoint))
			return;

		checkDistance(context.world, storage, endpoint);
		bridgeStaticPartner(context, storage, endpoint);
	}

	static boolean isMovingPartner(Level level, @Nullable BlockPos originPos, FireHoseBlockEntity owner) {
		return getAccepted(level, originPos, owner) != null;
	}

	@Nullable
	static Vec3 worldCenter(Level level, @Nullable BlockPos originPos, FireHoseBlockEntity owner) {
		MovingEndpoint endpoint = getAccepted(level, originPos, owner);
		return endpoint == null ? null : endpoint.worldCenter();
	}

	static boolean disconnect(Level level, @Nullable BlockPos originPos, @Nullable BlockPos requester) {
		if (level == null || originPos == null)
			return false;
		cleanup(level);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return false;

		boolean disconnected = false;
		for (MovingEndpoint endpoint : endpoints.values()) {
			if (!endpoint.storage().getOriginPos().equals(originPos))
				continue;
			if (requester != null && !requester.equals(endpoint.storage().getPartnerPos()))
				continue;
			disconnect(endpoint, requester);
			disconnected = true;
		}
		return disconnected;
	}

	static boolean disconnectAttachedTo(Level level, BlockPos staticEndpointPos) {
		if (level == null)
			return false;
		cleanup(level);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return false;

		boolean disconnected = false;
		for (MovingEndpoint endpoint : endpoints.values()) {
			if (!staticEndpointPos.equals(endpoint.storage().getPartnerPos()))
				continue;
			disconnect(endpoint, staticEndpointPos);
			disconnected = true;
		}
		return disconnected;
	}

	@Nullable
	static Replacement replacement(Level level, @Nullable BlockPos originPos) {
		if (level == null || originPos == null)
			return null;
		cleanup(level);
		Map<BlockPos, Replacement> replacements = REPLACEMENTS.get(level);
		return replacements == null ? null : replacements.get(originPos);
	}

	static boolean canRestoreConnection(Level level, FireHoseMountedFluidStorage storage, BlockPos partnerPos) {
		FireHoseBlockEntity staticPartner = getStaticEndpoint(level, partnerPos);
		if (staticPartner != null) {
			if (isReciprocalStaticPartner(staticPartner, storage))
				return true;
			UUID runtimeId = storage.getRuntimeId();
			return runtimeId != null
				&& staticPartner.isAcceptedMovingPartner(storage.getOriginPos(), storage.getEndpointId(), runtimeId);
		}

		if (getPartner(level, storage) != null)
			return true;

		if (storage.getPartnerEndpointId() != null)
			return true;

		return !level.isLoaded(partnerPos);
	}

	static void replaceMovingEndpoint(Level level, FireHoseMountedFluidStorage storage, BlockPos replacementPos,
			@Nullable UUID replacementSubLevel) {
		BlockPos originPos = storage.getOriginPos();
		UUID runtimeId = storage.getRuntimeId();
		remove(level, storage);
		REPLACEMENTS.computeIfAbsent(level, unused -> new HashMap<>())
			.put(originPos, new Replacement(replacementPos, replacementSubLevel, level.getGameTime() + 100));

		BlockPos partnerPos = storage.getPartnerPos();
		if (partnerPos == null)
			return;

		FireHoseBlockEntity staticPartner = getStaticEndpoint(level, partnerPos);
		if (staticPartner != null && runtimeId != null
			&& staticPartner.isAcceptedMovingPartner(originPos, storage.getEndpointId(), runtimeId)) {
			staticPartner.clearMovingPartnerRuntime(runtimeId);
			staticPartner.setPartnerPos(replacementPos, replacementSubLevel);
		} else if (staticPartner != null && isReciprocalStaticPartner(staticPartner, storage)) {
			staticPartner.setPartnerPos(replacementPos, replacementSubLevel);
		}

		MovingEndpoint movingPartner = getPartner(level, storage);
		if (movingPartner != null && originPos.equals(movingPartner.storage().getPartnerPos()))
			movingPartner.storage().replacePartner(replacementPos, replacementSubLevel);
	}

	private static boolean validateStaticPartner(Level level, FireHoseMountedFluidStorage storage,
			MovingEndpoint endpoint) {
		BlockPos partnerPos = storage.getPartnerPos();
		if (partnerPos == null)
			return true;

		FireHoseBlockEntity staticPartner = getStaticEndpoint(level, partnerPos);
		if (staticPartner == null) {
			if (getPartner(level, storage, endpoint.context()) != null) {
				storage.clearPartnerConflict();
				return true;
			}
			if (hasMountedPartner(endpoint.context(), storage)) {
				storage.clearPartnerConflict();
				return true;
			}
			if (!level.isLoaded(partnerPos))
				return true;
			storage.disconnect();
			remove(level, endpoint);
			return false;
		}

		if (!storage.getOriginPos().equals(staticPartner.getFireHosePartnerPos())
			|| !storage.getEndpointId().equals(staticPartner.getFireHosePartnerEndpointId())) {
			storage.disconnect();
			remove(level, endpoint);
			return false;
		}

		LeaseResult lease = reserveStaticPartner(level, staticPartner, storage, endpoint);
		if (lease == LeaseResult.ACCEPTED) {
			if (!staticPartner.isAcceptedMovingPartner(storage.getOriginPos(), storage.getEndpointId(), endpoint.runtimeId()))
				staticPartner.clearMovingPartnerRuntime(null);
			staticPartner.acceptMovingPartner(storage.getOriginPos(), storage.getEndpointId(), endpoint.runtimeId());
			storage.clearPartnerConflict();
			return true;
		}

		if (lease == LeaseResult.ACTIVE_CONFLICT || hasActiveAcceptedEndpoint(level, staticPartner, storage.getOriginPos())) {
			if (storage.notePartnerConflict(DUPLICATE_GRACE_TICKS)) {
				storage.disconnect();
				remove(level, endpoint);
			}
			return false;
		}
		storage.clearPartnerConflict();
		return false;
	}

	private static void checkDistance(Level level, FireHoseMountedFluidStorage storage, MovingEndpoint endpoint) {
		BlockPos partnerPos = storage.getPartnerPos();
		if (partnerPos == null) {
			storage.setSnappingTime(0);
			return;
		}

		Vec3 partnerCenter = partnerCenter(level, storage, endpoint.context());
		if (partnerCenter == null)
			return;

		double maxDistance = Config.hoseMaxLength * Config.hoseSnapMultiplier;
		if (endpoint.worldCenter().distanceToSqr(partnerCenter) > maxDistance * maxDistance)
			storage.setSnappingTime(storage.getSnappingTime() + 1.0 / 20.0);
		else
			storage.setSnappingTime(0);

		if (storage.getSnappingTime() > TIME_TO_SNAP)
			disconnect(endpoint, null);
	}

	private static void disconnect(MovingEndpoint endpoint, @Nullable BlockPos requester) {
		Level level = endpoint.context().world;
		BlockPos originPos = endpoint.storage().getOriginPos();
		BlockPos partnerPos = endpoint.storage().getPartnerPos();
		UUID runtimeId = endpoint.runtimeId();
		MovingEndpoint movingPartner = getPartner(level, endpoint.storage(), endpoint.context());
		endpoint.storage().disconnect();

		if (partnerPos == null)
			return;
		if (requester != null && !requester.equals(partnerPos))
			return;

		FireHoseBlockEntity staticPartner = getStaticEndpoint(level, partnerPos);
		if (staticPartner != null && originPos.equals(staticPartner.getFireHosePartnerPos())
			&& staticPartner.isAcceptedMovingPartner(originPos, endpoint.storage().getEndpointId(), runtimeId)) {
			staticPartner.clearMovingPartnerRuntime(runtimeId);
			staticPartner.clearFireHoseConnection();
		}

		if (movingPartner != null && originPos.equals(movingPartner.storage().getPartnerPos()))
			movingPartner.storage().disconnect();
	}

	private static void bridgeStaticPartner(MovementContext context, FireHoseMountedFluidStorage storage,
			MovingEndpoint endpoint) {
		BlockPos partnerPos = storage.getPartnerPos();
		if (partnerPos == null)
			return;
		FireHoseBlockEntity partner = getStaticEndpoint(context.world, partnerPos);
		if (partner == null)
			return;
		if (!partner.isAcceptedMovingPartner(storage.getOriginPos(), storage.getEndpointId(), endpoint.runtimeId()))
			return;

		int rate = (int) partner.getActivePumpSpeed();
		if (rate <= 0)
			return;

		IFluidHandler staticTank = partner.getSharedFluidHandlerForMovement();
		if (partner.isPulling()) {
			transferToContraption(context, storage, staticTank, rate);
		} else {
			transferFromContraption(context, storage, staticTank, rate);
		}
	}

	private static void transferToContraption(MovementContext context, FireHoseMountedFluidStorage storage,
			IFluidHandler staticTank, int rate) {
		int moved = 0;
		IFluidHandler target = ContraptionFluidAccess.mountedFluids(context, context.localPos);
		if (target != null)
			moved = transfer(staticTank, target, rate);
		if (moved < rate)
			transfer(staticTank, storage, rate - moved);
	}

	private static void transferFromContraption(MovementContext context, FireHoseMountedFluidStorage storage,
			IFluidHandler staticTank, int rate) {
		int moved = 0;
		IFluidHandler source = ContraptionFluidAccess.mountedFluids(context, context.localPos);
		if (source != null)
			moved = transfer(source, staticTank, rate);
		if (moved < rate)
			transfer(storage, staticTank, rate - moved);
	}

	private static int transfer(IFluidHandler source, IFluidHandler target, int rate) {
		if (rate <= 0)
			return 0;
		FluidStack simulated = source.drain(rate, IFluidHandler.FluidAction.SIMULATE);
		if (simulated.isEmpty())
			return 0;
		int accepted = target.fill(simulated, IFluidHandler.FluidAction.SIMULATE);
		if (accepted <= 0)
			return 0;
		FluidStack drained = source.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty())
			return 0;
		return target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
	}

	private static void submitRender(MovementContext context, FireHoseMountedFluidStorage storage,
			MovingEndpoint endpoint) {
		BlockPos partnerPos = storage.getPartnerPos();
		if (partnerPos == null)
			return;

		MovingEndpoint movingPartner = getPartner(context.world, storage, context);
		FireHoseBlockEntity staticPartner = movingPartner == null ? getStaticEndpoint(context.world, partnerPos) : null;
		if (movingPartner != null && !storage.getOriginPos().equals(movingPartner.storage().getPartnerPos()))
			return;
		if (staticPartner != null && (!storage.getOriginPos().equals(staticPartner.getFireHosePartnerPos())
			|| !storage.getEndpointId().equals(staticPartner.getFireHosePartnerEndpointId())
			|| !staticPartner.getFireHoseEndpointId().equals(storage.getPartnerEndpointId())))
			return;
		if (staticPartner != null && getAccepted(context.world, storage.getOriginPos(), staticPartner) != endpoint)
			return;
		if (movingPartner == null && staticPartner == null && context.world.isLoaded(partnerPos))
			return;
		Vec3 partnerCenter = movingPartner != null ? movingPartner.worldCenter()
			: staticPartner != null ? staticPartner.getWorldCenterVec()
			: null;
		if (partnerCenter == null)
			return;

		if (storage.isController()) {
			Direction partnerFacing = movingPartner != null ? movingPartner.facing()
				: staticPartner != null ? staticPartner.getFacingDirection()
				: endpoint.facing();
			FireHoseDynamicRenderer.submit(endpoint.renderKey(), endpoint.worldCenter(), partnerCenter,
				endpoint.facing(), partnerFacing, storage.isBlackHose(), context.world.getGameTime() + 2);
			return;
		}

		if (staticPartner != null && staticPartner.isController()) {
			FireHoseDynamicRenderer.submit(endpoint.renderKey(), staticPartner.getWorldCenterVec(), endpoint.worldCenter(),
				staticPartner.getFacingDirection(), endpoint.facing(), storage.isBlackHose(), context.world.getGameTime() + 2);
		}
	}

	@Nullable
	private static MovingEndpoint getAccepted(Level level, @Nullable BlockPos originPos, FireHoseBlockEntity owner) {
		if (level == null || originPos == null)
			return null;
		cleanup(level);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return null;

		MovingEndpoint bestCandidate = null;
		double bestDistance = Double.MAX_VALUE;
		for (MovingEndpoint endpoint : endpoints.values()) {
			if (!endpoint.storage().getOriginPos().equals(originPos))
				continue;
			UUID expectedEndpointId = owner.getFireHosePartnerEndpointId();
			if (expectedEndpointId == null || !expectedEndpointId.equals(endpoint.storage().getEndpointId()))
				continue;
			UUID endpointPartnerId = endpoint.storage().getPartnerEndpointId();
			if (endpointPartnerId == null || !endpointPartnerId.equals(owner.getFireHoseEndpointId()))
				continue;
			if (!owner.getBlockPos().equals(endpoint.storage().getPartnerPos()))
				continue;
			if (isStaticLeaseHolder(level, owner, endpoint.storage(), endpoint)
				&& owner.isAcceptedMovingPartner(originPos, endpoint.storage().getEndpointId(), endpoint.runtimeId()))
				return endpoint;

			double distance = owner.getWorldCenterVec().distanceToSqr(endpoint.worldCenter());
			if (distance < bestDistance) {
				bestDistance = distance;
				bestCandidate = endpoint;
			}
		}

		if (bestCandidate != null
			&& reserveStaticPartner(level, owner, bestCandidate.storage(), bestCandidate) == LeaseResult.ACCEPTED) {
			if (!owner.isAcceptedMovingPartner(originPos, bestCandidate.storage().getEndpointId(), bestCandidate.runtimeId()))
				owner.clearMovingPartnerRuntime(null);
			owner.acceptMovingPartner(originPos, bestCandidate.storage().getEndpointId(), bestCandidate.runtimeId());
			bestCandidate.storage().clearPartnerConflict();
			return bestCandidate;
		}
		if (bestCandidate != null && hasActiveAcceptedEndpoint(level, owner, originPos)
			&& bestCandidate.storage().notePartnerConflict(DUPLICATE_GRACE_TICKS)) {
			bestCandidate.storage().disconnect();
			remove(level, bestCandidate);
		}
		return null;
	}

	@Nullable
	private static MovingEndpoint getPartner(Level level, FireHoseMountedFluidStorage requester) {
		return getPartner(level, requester, null);
	}

	@Nullable
	private static MovingEndpoint getPartner(Level level, FireHoseMountedFluidStorage requester,
			@Nullable MovementContext requesterContext) {
		BlockPos partnerPos = requester.getPartnerPos();
		UUID partnerEndpointId = requester.getPartnerEndpointId();
		if (level == null || partnerPos == null || partnerEndpointId == null)
			return null;
		cleanup(level);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return null;

		MovingEndpoint fallback = null;
		for (MovingEndpoint endpoint : endpoints.values()) {
			FireHoseMountedFluidStorage candidate = endpoint.storage();
			if (!candidate.getOriginPos().equals(partnerPos))
				continue;
			if (!partnerEndpointId.equals(candidate.getEndpointId()))
				continue;
			if (!requester.getOriginPos().equals(candidate.getPartnerPos()))
				continue;
			if (!requester.getEndpointId().equals(candidate.getPartnerEndpointId()))
				continue;
			if (requesterContext != null && sameContraption(requesterContext, endpoint.context()))
				return endpoint;
			if (fallback == null)
				fallback = endpoint;
		}
		return requesterContext != null && hasMountedPartner(requesterContext, requester) ? null : fallback;
	}

	private static boolean hasMountedPartner(MovementContext context, FireHoseMountedFluidStorage requester) {
		if (context == null || context.contraption == null)
			return false;
		BlockPos partnerPos = requester.getPartnerPos();
		UUID partnerEndpointId = requester.getPartnerEndpointId();
		if (partnerPos == null || partnerEndpointId == null)
			return false;

		for (MountedFluidStorage mounted : context.contraption.getStorage().getFluids().storages.values()) {
			if (!(mounted instanceof FireHoseMountedFluidStorage candidate) || candidate == requester)
				continue;
			if (!partnerPos.equals(candidate.getOriginPos()))
				continue;
			if (!partnerEndpointId.equals(candidate.getEndpointId()))
				continue;
			if (!requester.getOriginPos().equals(candidate.getPartnerPos()))
				continue;
			if (!requester.getEndpointId().equals(candidate.getPartnerEndpointId()))
				continue;
			return true;
		}
		return false;
	}

	private static boolean sameContraption(MovementContext first, MovementContext second) {
		if (first.contraption == null || second.contraption == null)
			return false;
		if (first.contraption == second.contraption)
			return true;
		if (first.contraption.entity == null || second.contraption.entity == null)
			return false;
		return first.contraption.entity.getUUID().equals(second.contraption.entity.getUUID());
	}

	private static boolean isReciprocalStaticPartner(FireHoseBlockEntity staticPartner,
			FireHoseMountedFluidStorage storage) {
		UUID partnerEndpointId = storage.getPartnerEndpointId();
		return partnerEndpointId != null
			&& partnerEndpointId.equals(staticPartner.getFireHoseEndpointId())
			&& storage.getEndpointId().equals(staticPartner.getFireHosePartnerEndpointId());
	}

	private static boolean hasActiveAcceptedEndpoint(Level level, FireHoseBlockEntity owner, BlockPos originPos) {
		UUID runtimeId = owner.getMovingPartnerRuntimeId();
		UUID expectedEndpointId = owner.getFireHosePartnerEndpointId();
		if (runtimeId == null || expectedEndpointId == null)
			return false;
		cleanup(level);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return false;
		MovingEndpoint endpoint = endpoints.get(new EndpointKey(originPos, runtimeId));
		if (endpoint == null)
			return false;
		FireHoseMountedFluidStorage storage = endpoint.storage();
		return expectedEndpointId.equals(storage.getEndpointId())
			&& owner.getBlockPos().equals(storage.getPartnerPos())
			&& owner.getFireHoseEndpointId().equals(storage.getPartnerEndpointId());
	}

	private static LeaseResult reserveStaticPartner(Level level, FireHoseBlockEntity owner,
			FireHoseMountedFluidStorage storage, MovingEndpoint endpoint) {
		StaticLeaseKey key = new StaticLeaseKey(owner.getBlockPos(), owner.getFireHoseEndpointId(),
			storage.getOriginPos(), storage.getEndpointId());
		long now = level.getGameTime();
		Map<StaticLeaseKey, StaticLease> leases = STATIC_LEASES.computeIfAbsent(level, unused -> new HashMap<>());
		StaticLease lease = leases.get(key);
		if (lease == null || now > lease.expiresAt() || !isRuntimeActive(level, storage.getOriginPos(), lease.runtimeId())) {
			leases.put(key, new StaticLease(endpoint.runtimeId(), now + STATIC_LEASE_TICKS));
			return LeaseResult.ACCEPTED;
		}
		if (lease.runtimeId().equals(endpoint.runtimeId())) {
			leases.put(key, new StaticLease(endpoint.runtimeId(), now + STATIC_LEASE_TICKS));
			return LeaseResult.ACCEPTED;
		}
		return LeaseResult.ACTIVE_CONFLICT;
	}

	private static boolean isStaticLeaseHolder(Level level, FireHoseBlockEntity owner,
			FireHoseMountedFluidStorage storage, MovingEndpoint endpoint) {
		StaticLeaseKey key = new StaticLeaseKey(owner.getBlockPos(), owner.getFireHoseEndpointId(),
			storage.getOriginPos(), storage.getEndpointId());
		Map<StaticLeaseKey, StaticLease> leases = STATIC_LEASES.get(level);
		if (leases == null)
			return false;
		StaticLease lease = leases.get(key);
		return lease != null && lease.runtimeId().equals(endpoint.runtimeId());
	}

	private static boolean isRuntimeActive(Level level, BlockPos originPos, UUID runtimeId) {
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		return endpoints != null && endpoints.containsKey(new EndpointKey(originPos, runtimeId));
	}

	@Nullable
	private static MovingEndpoint get(Level level, @Nullable BlockPos originPos, @Nullable BlockPos requesterOrigin) {
		if (level == null || originPos == null)
			return null;
		cleanup(level);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return null;

		for (MovingEndpoint endpoint : endpoints.values()) {
			if (!endpoint.storage().getOriginPos().equals(originPos))
				continue;
			if (requesterOrigin != null && !requesterOrigin.equals(endpoint.storage().getPartnerPos()))
				continue;
			return endpoint;
		}
		return null;
	}

	@Nullable
	private static FireHoseBlockEntity getStaticEndpoint(Level level, BlockPos pos) {
		if (level == null)
			return null;
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof FireHoseBlockEntity hose ? hose : null;
	}

	@Nullable
	private static Vec3 partnerCenter(Level level, FireHoseMountedFluidStorage storage,
			@Nullable MovementContext context) {
		BlockPos partnerPos = storage.getPartnerPos();
		if (partnerPos == null)
			return null;
		MovingEndpoint moving = getPartner(level, storage, context);
		if (moving != null)
			return moving.worldCenter();
		FireHoseBlockEntity staticEndpoint = getStaticEndpoint(level, partnerPos);
		if (staticEndpoint != null)
			return staticEndpoint.getWorldCenterVec();
		return level.isLoaded(partnerPos) ? null : SableStructureCompat.projectToWorld(level, Vec3.atCenterOf(partnerPos));
	}

	private static UUID runtimeId(MovementContext context) {
		if (context.contraption != null && context.contraption.entity != null) {
			UUID entityId = context.contraption.entity.getUUID();
			BlockPos localPos = context.localPos == null ? BlockPos.ZERO : context.localPos;
			return UUID.nameUUIDFromBytes((entityId + ":" + localPos.asLong()).getBytes(StandardCharsets.UTF_8));
		}
		BlockPos localPos = context.localPos == null ? BlockPos.ZERO : context.localPos;
		return UUID.nameUUIDFromBytes(("fire_hose:" + localPos.asLong()).getBytes(StandardCharsets.UTF_8));
	}

	private static void remove(Level level, FireHoseMountedFluidStorage storage) {
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints == null)
			return;
		UUID runtimeId = storage.getRuntimeId();
		if (runtimeId != null) {
			endpoints.remove(new EndpointKey(storage.getOriginPos(), runtimeId));
			return;
		}
		endpoints.entrySet().removeIf(entry -> entry.getValue().storage() == storage);
	}

	private static void remove(Level level, MovingEndpoint endpoint) {
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints != null)
			endpoints.remove(endpoint.key());
	}

	private static void cleanup(Level level) {
		if (level == null)
			return;
		long gameTime = level.getGameTime();
		Long lastCleanup = LAST_CLEANUP.get(level);
		if (lastCleanup != null && lastCleanup == gameTime)
			return;
		LAST_CLEANUP.put(level, gameTime);
		Map<EndpointKey, MovingEndpoint> endpoints = MOVING.get(level);
		if (endpoints != null) {
			for (Iterator<Map.Entry<EndpointKey, MovingEndpoint>> it = endpoints.entrySet().iterator(); it.hasNext(); ) {
				MovingEndpoint endpoint = it.next().getValue();
				if (gameTime > endpoint.expiresAt())
					it.remove();
			}
		}
		Map<BlockPos, Replacement> replacements = REPLACEMENTS.get(level);
		if (replacements != null)
			replacements.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAt());
		Map<StaticLeaseKey, StaticLease> staticLeases = STATIC_LEASES.get(level);
		if (staticLeases != null)
			staticLeases.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAt()
				|| !isRuntimeActive(level, entry.getKey().movingOrigin(), entry.getValue().runtimeId()));
	}

	private record EndpointKey(BlockPos originPos, UUID runtimeId) {
	}

	private record StaticLeaseKey(BlockPos staticPos, UUID staticEndpointId, BlockPos movingOrigin,
			UUID movingEndpointId) {
	}

	private record StaticLease(UUID runtimeId, long expiresAt) {
	}

	private enum LeaseResult {
		ACCEPTED,
		ACTIVE_CONFLICT
	}

	private record MovingEndpoint(MovementContext context, FireHoseMountedFluidStorage storage,
			Vec3 worldCenter, Direction facing, long expiresAt,
			int renderKey, UUID runtimeId, EndpointKey key) {
		private static MovingEndpoint of(MovementContext context, FireHoseMountedFluidStorage storage,
				UUID runtimeId) {
			Vec3 normal = Vec3.atLowerCornerOf(storage.getFacing().getNormal());
			Vec3 rotatedNormal = context.rotation.apply(normal);
			Direction facing = Direction.getNearest(rotatedNormal.x, rotatedNormal.y, rotatedNormal.z);
			Vec3 center = context.position.subtract(rotatedNormal.normalize().scale(ENDPOINT_OFFSET));
			Vec3 worldCenter = SableStructureCompat.projectToWorld(context.world, center);
			int renderKey = 31 * runtimeId.hashCode() + context.localPos.hashCode();
			return new MovingEndpoint(context, storage, worldCenter, facing,
				context.world.getGameTime() + 2, renderKey, runtimeId,
				new EndpointKey(storage.getOriginPos(), runtimeId));
		}
	}

	record Replacement(BlockPos pos, @Nullable UUID subLevel, long expiresAt) {
	}
}
