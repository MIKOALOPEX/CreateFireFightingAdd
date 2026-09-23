package com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Stores one standard endpoint proxy and the independent owners needed by multi-rope links. */
public class SmartRopeConnectorBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity {
	public static final int CAPACITY = 8;
	private static final String TAG_OWNED = "OwnedRopes";
	private static final String TAG_EXTERNAL = "ExternalRopes";
	private static final String TAG_BEHAVIOR = "Behavior";
	private static final String TAG_ID = "Id";
	private static final String TAG_OWNER_POS = "OwnerPos";

	private RopeStrandHolderBehavior endpointProxy;
	private final LinkedHashMap<UUID, RopeStrandHolderBehavior> ownedRopes = new LinkedHashMap<>();
	private final LinkedHashMap<UUID, BlockPos> externalRopes = new LinkedHashMap<>();
	private final LinkedHashMap<UUID, RopeStrandHolderBehavior> clientRopes = new LinkedHashMap<>();
	private int auditTicks;
	private int clientConnectionCount;
	private boolean moving;

	public SmartRopeConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(endpointProxy = new RopeStrandHolderBehavior(this));
	}

	@Override
	public RopeStrandHolderBehavior getBehavior() {
		return endpointProxy;
	}

	@Override
	public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(SmartRopeConnectorBlock.FACING);
		return pos.getCenter().add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-3.0 / 16.0));
	}

	@Override
	public Vec3 getVisualAttachmentPoint(BlockPos pos, BlockState state) {
		Direction facing = state.getValue(SmartRopeConnectorBlock.FACING);
		return pos.getCenter().add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-4.0 / 16.0));
	}

	public boolean hasCapacity() {
		return connectionCount() < CAPACITY;
	}

	public int connectionCount() {
		if (level != null && level.isClientSide) return clientConnectionCount;
		return ownedRopes.size() + externalRopes.size();
	}

    public RopeStrandHolderBehavior owner(UUID id) { return ownedRopes.get(id); }

    private Vec3 getAttachmentPoint() { return getAttachmentPoint(worldPosition, getBlockState()); }

    public boolean isMoving() { return moving; }

    public void beginMove() { moving = true; }

    /** Reuses the live physics object when Sable relocates the endpoint into a sublevel. */
    public void finishMove(net.minecraft.server.level.ServerLevel destination) {
        moving = false;
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(destination);
        UUID sublevel = com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat
            .containingSubLevelId(destination, worldPosition);
        for (UUID id : allConnectionIds()) {
            RopeStrandHolderBehavior holder = ownedRopes.get(id);
            ServerRopeStrand strand = manager.getStrand(id);
            if (holder != null) {
                if (strand != null) holder.takeOwnedStrand(strand);
                else strand = holder.getOwnedStrand();
            }
            if (strand == null) continue;
            RopeAttachmentPoint point = holder != null ? RopeAttachmentPoint.START : RopeAttachmentPoint.END;
            strand.addAttachment(destination, point, new RopeAttachment(point, sublevel, worldPosition));
            strand.getTrackingPlayers().clear();
            if (holder != null) {
                RopeAttachment end = strand.getAttachment(RopeAttachmentPoint.END);
                if (end != null && destination.getBlockEntity(end.blockAttachment()) instanceof SmartRopeConnectorBlockEntity remote)
                    remote.addExternal(id, worldPosition);
            }
        }
        notifyUpdate();
    }

	void addOwned(RopeStrandHolderBehavior owner) {
		ServerRopeStrand strand = owner.getOwnedStrand();
		if (strand == null)
			return;
		ownedRopes.put(strand.getUUID(), owner);
		setChanged();
		notifyUpdate();
	}

	void addExternal(UUID id, BlockPos ownerPos) {
		externalRopes.put(id, ownerPos.immutable());
		setChanged();
		notifyUpdate();
	}

	void removeConnection(UUID id) {
		ownedRopes.remove(id);
		externalRopes.remove(id);
		setChanged();
		notifyUpdate();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null)
			return;
		if (level.isClientSide) {
			invalidateRenderBoundingBox();
			return;
		}
		if (ownedRopes.isEmpty() && externalRopes.isEmpty()) return;

		for (RopeStrandHolderBehavior owner : List.copyOf(ownedRopes.values()))
			owner.tick();

		if (++auditTicks >= 10) {
			auditTicks = 0;
			auditConnections();
		}
	}

	private void auditConnections() {
		boolean changed = ownedRopes.entrySet().removeIf(entry -> entry.getValue().getOwnedStrand() == null);
		ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
		for (var entry : externalRopes.entrySet()) {
            ServerRopeStrand strand = manager.getStrand(entry.getKey());
            if (strand == null) continue;
            RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
            if (start != null && !start.blockAttachment().equals(entry.getValue())) {
                entry.setValue(start.blockAttachment().immutable());
                changed = true;
            }
        }
		changed |= externalRopes.entrySet().removeIf(entry -> {
            if (manager.getStrand(entry.getKey()) != null || !level.isLoaded(entry.getValue())) return false;
            BlockEntity be = level.getBlockEntity(entry.getValue());
            if (be instanceof SmartRopeConnectorBlockEntity smart) return smart.owner(entry.getKey()) == null;
            if (be instanceof SmartBlockEntity smart) {
                RopeStrandHolderBehavior holder = smart.getBehaviour(RopeStrandHolderBehavior.TYPE);
                return holder == null || !holder.isAttached();
            }
            return true;
        });
        if (changed) notifyUpdate();
	}

	public boolean cutLastConnection(ServerPlayer player) {
		UUID id = lastConnection();
		return id != null && destroyConnection(id, player, true);
	}

	public void destroyAllConnections(@Nullable ServerPlayer player, boolean returnItems) {
		for (UUID id : new ArrayList<>(allConnectionIds()))
			destroyConnection(id, player, returnItems);
	}

	private boolean destroyConnection(UUID id, @Nullable ServerPlayer player, boolean returnItem) {
		RopeStrandHolderBehavior owner = ownedRopes.get(id);
		if (owner != null) {
			ServerRopeStrand strand = owner.getOwnedStrand();
			SmartRopeConnections.destroyRope(owner, player, getAttachmentPoint(), returnItem);
			removeRemoteRecord(strand, id);
			removeConnection(id);
			return true;
		}

		if (level == null)
			return false;
		ServerRopeStrand strand = ServerLevelRopeManager.getOrCreate(level).getStrand(id);
		if (strand == null) {
			removeConnection(id);
			return false;
		}
		RopeAttachment start = strand.getAttachment(RopeAttachmentPoint.START);
		if (start == null)
			return false;
		BlockEntity ownerBe = level.getBlockEntity(start.blockAttachment());
		if (ownerBe instanceof SmartRopeConnectorBlockEntity smartOwner)
			return smartOwner.destroyConnection(id, player, returnItem);
		if (ownerBe instanceof SmartBlockEntity smartBe) {
			RopeStrandHolderBehavior nativeOwner = smartBe.getBehaviour(RopeStrandHolderBehavior.TYPE);
			if (nativeOwner != null) {
				SmartRopeConnections.destroyRope(nativeOwner, player, getAttachmentPoint(), returnItem);
				removeConnection(id);
				return true;
			}
		}
		return false;
	}

	private void removeRemoteRecord(@Nullable ServerRopeStrand strand, UUID id) {
		if (strand == null || level == null)
			return;
		RopeAttachment end = strand.getAttachment(RopeAttachmentPoint.END);
		if (end != null && level.getBlockEntity(end.blockAttachment()) instanceof SmartRopeConnectorBlockEntity remote)
			remote.removeConnection(id);
	}

	@Nullable
	private UUID lastConnection() {
		UUID last = null;
		for (UUID id : allConnectionIds())
			last = id;
		return last;
	}

	private List<UUID> allConnectionIds() {
		List<UUID> ids = new ArrayList<>(ownedRopes.keySet());
		ids.addAll(externalRopes.keySet());
		return ids;
	}

	public void receiveClientRope(int interpolationTick, List<org.joml.Vector3d> points, UUID id,
			@Nullable BlockPos start, @Nullable BlockPos end) {
		RopeStrandHolderBehavior holder = clientRopes.computeIfAbsent(id, ignored -> new RopeStrandHolderBehavior(this));
		holder.receiveClientStrand(interpolationTick, points, id, start, end);
		holder.giveFakeClientStrand(holder.getClientStrand());
		invalidateRenderBoundingBox();
	}

	public void stopClientRope(UUID id) {
		RopeStrandHolderBehavior holder = clientRopes.get(id);
        if (holder != null) holder.receiveClientStrandStopped();
	}

	public Iterable<RopeStrandHolderBehavior> getClientRopes() {
		return clientRopes.values();
	}

	@Override
	public AABB getRenderBoundingBox() {
		AABB bounds = super.getRenderBoundingBox();
		for (RopeStrandHolderBehavior holder : clientRopes.values()) {
			ClientRopeStrand strand = holder.getClientStrand();
			if (strand != null && strand.getBounds() != null)
				bounds = bounds.minmax(strand.getBounds().inflate(3));
		}
		return bounds;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("ConnectionCount", connectionCount());
		ListTag owned = new ListTag();
		for (Map.Entry<UUID, RopeStrandHolderBehavior> entry : ownedRopes.entrySet()) {
			CompoundTag rope = new CompoundTag();
			rope.putUUID(TAG_ID, entry.getKey());
			CompoundTag behavior = new CompoundTag();
			entry.getValue().write(behavior, registries, clientPacket);
			rope.put(TAG_BEHAVIOR, behavior);
			owned.add(rope);
		}
		tag.put(TAG_OWNED, owned);

		ListTag external = new ListTag();
		for (Map.Entry<UUID, BlockPos> entry : externalRopes.entrySet()) {
			CompoundTag rope = new CompoundTag();
			rope.putUUID(TAG_ID, entry.getKey());
			rope.putLong(TAG_OWNER_POS, entry.getValue().asLong());
			external.add(rope);
		}
		tag.put(TAG_EXTERNAL, external);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (clientPacket) {
            clientConnectionCount = tag.getInt("ConnectionCount");
            java.util.Set<UUID> ids = new java.util.HashSet<>();
            for (Tag raw : tag.getList(TAG_OWNED, Tag.TAG_COMPOUND)) ids.add(((CompoundTag) raw).getUUID(TAG_ID));
            clientRopes.entrySet().removeIf(entry -> {
                if (ids.contains(entry.getKey())) return false;
                entry.getValue().unload();
                return true;
            });
            return;
        }

		ownedRopes.clear();
		for (Tag raw : tag.getList(TAG_OWNED, Tag.TAG_COMPOUND)) {
			CompoundTag rope = (CompoundTag) raw;
			RopeStrandHolderBehavior owner = new RopeStrandHolderBehavior(this);
			owner.read(rope.getCompound(TAG_BEHAVIOR), registries, false);
			ownedRopes.put(rope.getUUID(TAG_ID), owner);
		}
		externalRopes.clear();
		for (Tag raw : tag.getList(TAG_EXTERNAL, Tag.TAG_COMPOUND)) {
			CompoundTag rope = (CompoundTag) raw;
			externalRopes.put(rope.getUUID(TAG_ID), BlockPos.of(rope.getLong(TAG_OWNER_POS)));
		}
	}

    @Override
    public void invalidate() {
        if (!moving) for (RopeStrandHolderBehavior holder : ownedRopes.values()) holder.unload();
        for (RopeStrandHolderBehavior holder : clientRopes.values()) holder.unload();
        super.invalidate();
    }
}
