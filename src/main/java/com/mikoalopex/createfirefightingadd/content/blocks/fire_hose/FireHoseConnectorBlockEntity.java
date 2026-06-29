package com.mikoalopex.createfirefightingadd.content.blocks.fire_hose;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class FireHoseConnectorBlockEntity extends SmartBlockEntity {
	private static final String TAG_ATTACHED_POS = "AttachedPos";
	private static final String TAG_ATTACHED_SUB_LEVEL = "AttachedSubLevel";
	private static final String TAG_ATTACHED_ENDPOINT_ID = "AttachedEndpointId";
	private static final String TAG_CACHED_POS = "CachedPos";
	private static final String TAG_CACHED_SUB_LEVEL = "CachedSubLevel";
	private static final String TAG_CACHED_ENDPOINT_ID = "CachedEndpointId";
	private static final String TAG_POWERED = "Powered";

	@Nullable
	private BlockPos attachedPos;
	@Nullable
	private UUID attachedSubLevel;
	@Nullable
	private UUID attachedEndpointId;
	@Nullable
	private BlockPos cachedPos;
	@Nullable
	private UUID cachedSubLevel;
	@Nullable
	private UUID cachedEndpointId;
	private boolean powered;
	private ScrollOptionBehaviour<FireHoseConnectorMode> mode;

	public FireHoseConnectorBlockEntity(BlockPos pos, BlockState state) {
		this(CreateFireFightingAdd.FIRE_HOSE_CONNECTOR_BE.get(), pos, state);
	}

	public FireHoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new ConnectorPipeBehaviour(this));
		registerAwardables(behaviours, FluidPropagator.getSharedTriggers());

		mode = new ScrollOptionBehaviour<>(FireHoseConnectorMode.class,
			Component.translatable("createfirefightingadd.fire_hose_connector.mode"),
			this, new ModeValueBoxTransform());
		behaviours.add(mode);
	}

	public void onNeighborChanged() {
		refreshAttachedEndpoint();
		boolean nowPowered = hasRedstoneSignal();
		if (nowPowered && !powered)
			executeMode();
		powered = nowPowered;
		setChanged();
	}

	public void refreshAttachedEndpoint() {
		FireHoseBlockEntity attached = findAttachedEndpoint();
		if (attached == null) {
			attachedPos = null;
			attachedSubLevel = null;
			attachedEndpointId = null;
			setChanged();
			return;
		}

		boolean changedEndpoint = attachedPos == null
			|| !attachedPos.equals(attached.getBlockPos())
			|| !attached.getFireHoseEndpointId().equals(attachedEndpointId);
		attachedPos = attached.getBlockPos();
		attachedSubLevel = SableStructureCompat.containingSubLevelId(level, attached.getBlockPos());
		attachedEndpointId = attached.getFireHoseEndpointId();
		if (changedEndpoint)
			rememberPartner(attached);
		else if (attached.getFireHosePartnerPos() != null
			&& !attached.getFireHosePartnerPos().equals(cachedPos))
			rememberPartner(attached);
		setChanged();
	}

	public static void refreshAdjacentTo(FireHoseBlockEntity hose) {
		if (hose == null || hose.getLevel() == null || hose.getLevel().isClientSide)
			return;
		Level level = hose.getLevel();
		for (Direction direction : Iterate.directions) {
			BlockEntity be = level.getBlockEntity(hose.getBlockPos().relative(direction));
			if (be instanceof FireHoseConnectorBlockEntity connector)
				connector.refreshAttachedEndpoint();
		}
	}

	private void executeMode() {
		if (level == null)
			return;
		FireHoseConnectorMode connectorMode = getMode();
		FireHoseBlockEntity attached = findAttachedEndpoint();
		if (attached == null)
			return;

		rememberPartner(attached);
		if (connectorMode == FireHoseConnectorMode.IDLE) {
			highlightCachedOrClear();
			return;
		}

		if (connectorMode == FireHoseConnectorMode.FIXED) {
			tryCachedConnection(attached, true);
			return;
		}

		if (tryCachedConnection(attached, false))
			return;

		tryFreeConnection(attached);
	}

	private boolean tryCachedConnection(FireHoseBlockEntity attached, boolean clearMissing) {
		if (level == null || cachedPos == null || !endpointMatches(cachedPos, cachedEndpointId)) {
			if (clearMissing)
				clearCache();
			return false;
		}
		FireHoseBlockEntity cached = findEndpoint(cachedPos, cachedEndpointId);
		if (cached == null) {
			if (clearMissing)
				clearCache();
			return false;
		}
		if (!FireHoseConnections.isWithinRange(attached, cached)) {
			if (clearMissing)
				clearCache();
			return false;
		}
		if (FireHoseConnections.tryConnect(attached, cached)
			!= FireHoseConnections.Result.SUCCESS)
			return false;
		highlight(cached, 0x70FF33);
		return true;
	}

	private void tryFreeConnection(FireHoseBlockEntity attached) {
		if (level == null)
			return;
		FireHoseConnections.ConnectionAttempt attempt =
			FireHoseConnections.tryConnectFirstFreeEndpoint(attached);
		if (attempt.successful()) {
			remember(attempt.endpoint());
			highlight(attempt.endpoint(), 0x70FF33);
		}
	}

	private FireHoseConnectorMode getMode() {
		return mode == null ? FireHoseConnectorMode.IDLE : mode.get();
	}

	private void highlightCachedOrClear() {
		if (cachedPos != null && endpointMatches(cachedPos, cachedEndpointId)) {
			highlight(cachedPos, 0x70FF33);
			return;
		}
		clearCache();
		tellNearby(Component.translatable("createfirefightingadd.fire_hose_connector.cached_missing"));
	}

	private boolean hasRedstoneSignal() {
		if (level == null)
			return false;
		for (Direction direction : Iterate.directions) {
			if (direction.getAxis() != FireHoseConnectorBlock.redstoneAxis(getBlockState()))
				continue;
			if (level.hasSignal(worldPosition.relative(direction), direction))
				return true;
		}
		return false;
	}

	@Nullable
	private FireHoseBlockEntity findAttachedEndpoint() {
		if (level == null)
			return null;
		for (Direction direction : Iterate.directions) {
			if (direction.getAxis() != FireHoseConnectorBlock.pipeAxis(getBlockState()))
				continue;
			BlockEntity be = level.getBlockEntity(worldPosition.relative(direction));
			if (be instanceof FireHoseBlockEntity hose)
				return hose;
		}
		return null;
	}

	private void rememberPartner(FireHoseBlockEntity hose) {
		BlockPos partnerPos = hose.getFireHosePartnerPos();
		UUID partnerId = hose.getFireHosePartnerEndpointId();
		if (partnerPos == null || partnerId == null)
			return;
		cachedPos = partnerPos;
		cachedSubLevel = hose.getFireHosePartnerSubLevel();
		cachedEndpointId = partnerId;
	}

	private void remember(FireHoseBlockEntity hose) {
		cachedPos = hose.getBlockPos();
		cachedSubLevel = SableStructureCompat.containingSubLevelId(hose.getLevel(), hose.getBlockPos());
		cachedEndpointId = hose.getFireHoseEndpointId();
		setChanged();
	}

	private boolean endpointMatches(BlockPos pos, @Nullable UUID endpointId) {
		return findEndpoint(pos, endpointId) != null;
	}

	@Nullable
	private FireHoseBlockEntity findEndpoint(BlockPos pos, @Nullable UUID endpointId) {
		if (level == null || endpointId == null)
			return null;
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof FireHoseBlockEntity hose && endpointId.equals(hose.getFireHoseEndpointId()))
			return hose;
		return null;
	}

	private void clearCache() {
		cachedPos = null;
		cachedSubLevel = null;
		cachedEndpointId = null;
		setChanged();
	}

	private void highlight(BlockPos pos, int color) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		Vec3 center = SableStructureCompat.projectToWorld(level, Vec3.atCenterOf(pos));
		highlight(serverLevel, pos, center, color);
	}

	private void highlight(FireHoseBlockEntity hose, int color) {
		if (!(SableStructureCompat.worldLevel(hose) instanceof ServerLevel serverLevel))
			return;
		Vec3 center = SableStructureCompat.transformPositionToWorld(hose, hose.getBlockPos().getCenter());
		highlight(serverLevel, hose.getBlockPos(), center, color);
	}

	private void highlight(ServerLevel serverLevel, BlockPos pos, Vec3 center, int color) {
		DustParticleOptions particle = new DustParticleOptions(new net.createmod.catnip.theme.Color(color).asVectorF(), 1);
		for (int i = 0; i < 24; i++) {
			double ox = (serverLevel.random.nextDouble() - 0.5) * 0.9;
			double oy = (serverLevel.random.nextDouble() - 0.5) * 0.9;
			double oz = (serverLevel.random.nextDouble() - 0.5) * 0.9;
			serverLevel.sendParticles(particle, center.x + ox, center.y + oy, center.z + oz, 1, 0, 0, 0, 0);
		}

		FireHoseConnectorHighlightPacket packet = new FireHoseConnectorHighlightPacket(pos, color);
		for (ServerPlayer player : serverLevel.getPlayers(player -> player.distanceToSqr(center) < 64 * 64))
			PacketDistributor.sendToPlayer(player, packet);
	}

	private void tellNearby(Component message) {
		if (!(level instanceof ServerLevel serverLevel))
			return;
		Vec3 center = Vec3.atCenterOf(worldPosition);
		for (Player player : serverLevel.players()) {
			if (player.distanceToSqr(center) <= 32 * 32)
				player.displayClientMessage(message, true);
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		writeLink(tag, TAG_ATTACHED_POS, TAG_ATTACHED_SUB_LEVEL, attachedPos, attachedSubLevel);
		if (attachedEndpointId != null)
			tag.putUUID(TAG_ATTACHED_ENDPOINT_ID, attachedEndpointId);
		writeLink(tag, TAG_CACHED_POS, TAG_CACHED_SUB_LEVEL, cachedPos, cachedSubLevel);
		if (cachedEndpointId != null)
			tag.putUUID(TAG_CACHED_ENDPOINT_ID, cachedEndpointId);
		tag.putBoolean(TAG_POWERED, powered);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		SableStructureCompat.LinkedBlockRef attached = SableStructureCompat.readLinkedBlock(
			tag, TAG_ATTACHED_POS, TAG_ATTACHED_SUB_LEVEL);
		attachedPos = attached.pos();
		attachedSubLevel = attached.subLevelId();
		attachedEndpointId = tag.hasUUID(TAG_ATTACHED_ENDPOINT_ID) ? tag.getUUID(TAG_ATTACHED_ENDPOINT_ID) : null;

		SableStructureCompat.LinkedBlockRef cached = SableStructureCompat.readLinkedBlock(
			tag, TAG_CACHED_POS, TAG_CACHED_SUB_LEVEL);
		cachedPos = cached.pos();
		cachedSubLevel = cached.subLevelId();
		cachedEndpointId = tag.hasUUID(TAG_CACHED_ENDPOINT_ID) ? tag.getUUID(TAG_CACHED_ENDPOINT_ID) : null;
		powered = tag.getBoolean(TAG_POWERED);
	}

	private static void writeLink(CompoundTag tag, String posTag, String subLevelTag,
			@Nullable BlockPos pos, @Nullable UUID subLevelId) {
		if (pos != null)
			SableStructureCompat.writeLinkedBlock(tag, posTag, subLevelTag, pos, subLevelId);
	}

	@Override
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(2);
	}

	private static class ModeValueBoxTransform extends ValueBoxTransform.Sided {
		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 15);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() == FireHoseConnectorBlock.dialAxis(state);
		}
	}

	private static class ConnectorPipeBehaviour extends StraightPipeFluidTransportBehaviour {
		public ConnectorPipeBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return state.getBlock() instanceof FireHoseConnectorBlock
				&& direction.getAxis() == FireHoseConnectorBlock.pipeAxis(state);
		}
	}
}
