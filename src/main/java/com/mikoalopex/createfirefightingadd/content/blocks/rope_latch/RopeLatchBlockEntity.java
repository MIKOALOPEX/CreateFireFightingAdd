package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.util.List;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.content.blocks.smart_rope_connector.SmartRopeConnectorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Search is explicitly armed; bound ropes are addressed by UUID instead of searched again. */
public class RopeLatchBlockEntity extends SmartBlockEntity {
    private UUID ropeId;
    private int node;
    private int tailSegments = -1;
    private BlockPos ownerPos;
    private long searchUntil;
    private long cooldownUntil;
    private boolean lastPowered;
    private PinnedRopeHandle pin;
    private ServerRopeStrand boundRope;
    private Vec3 lastAnchor;
    private boolean moving;

    public RopeLatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean isMoving() { return moving; }
    public void beginMove() { moving = true; detachPhysics(); }
    public void finishMove() { moving = false; }

    public void sampleSignal() {
        if (!(level instanceof ServerLevel server) || moving) return;
        boolean powered = server.hasNeighborSignal(worldPosition);
        boolean rising = powered && !lastPowered;
        if (lastPowered != powered) {
            lastPowered = powered;
            setChanged();
        }
        if (getBlockState().getValue(RopeLatchBlock.POWERED) != powered)
            server.setBlock(worldPosition, getBlockState().setValue(RopeLatchBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        long now = server.getGameTime();
        if (!rising || now < cooldownUntil) return;
        if (ropeId != null) {
            release();
            cooldownUntil = now + 100;
        } else if (now >= searchUntil) {
            searchUntil = now + 600;
            RopeLatchIndex.request(server);
        }
        notifyUpdate();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || moving) return;
        // Sampling also handles redstone providers that omit neighbour notifications.
        sampleSignal();
        if (ropeId != null) {
            maintain(server);
            return;
        }
        long now = server.getGameTime();
        if (now >= searchUntil || now < cooldownUntil) return;
        RopeLatchIndex.request(server);
        if (Math.floorMod(now + worldPosition.asLong(), 10) != 0) return;
        Vec3 world = SableStructureCompat.transformPositionToWorld(this, anchor());
        RopeLatchIndex.Hit hit = RopeLatchIndex.search(server, world);
        if (hit == null) return;
        try {
            hit.rope().updatePose();
            if (hit.rope().getPoints().get(hit.point()).distanceSquared(world.x, world.y, world.z)
                    > RopeLatchIndex.RADIUS * RopeLatchIndex.RADIUS) return;
            if (attach(server, hit.rope(), hit.point())) {
                ropeId = hit.rope().getUUID();
                node = hit.point();
                tailSegments = pin.tailSegments();
                searchUntil = 0;
                notifyUpdate();
            }
        } catch (RuntimeException exception) {
            searchUntil = 0;
            setChanged();
            CreateFireFightingAdd.LOGGER.warn("Rope latch at {} could not capture a rope; search stopped.", worldPosition, exception);
        }
    }

    private void maintain(ServerLevel server) {
        if (boundRope != null) rememberOwner(boundRope);
        if (pin != null && !pin.isRemoved() && boundRope != null
                && PinnedRopeHandle.handle(boundRope) == pin) {
            if (node != pin.pinnedNode()) {
                node = pin.pinnedNode();
                setChanged();
            }
            Vec3 anchor = anchor();
            // Assembly callbacks handle body changes; ordinary movement needs no body lookup.
            if (!anchor.equals(lastAnchor)) {
                detachPhysics();
            } else return;
        }
        if (Math.floorMod(server.getGameTime() + worldPosition.asLong(), 10) != 0) return;
        ServerRopeStrand rope = ServerLevelRopeManager.getOrCreate(server).getStrand(ropeId);
        if (rope == null) {
            if (ownerPos != null && server.isLoaded(ownerPos) && !ownerRetainsRope(server)) release();
            return;
        }
        if (!rope.isActive()) return;
        if (PinnedRopeHandle.handle(rope) instanceof PinnedRopeHandle other && other != pin) {
            release();
            return;
        }
        // Winches change the start of the strand, while the material distance to END stays fixed.
        if (tailSegments >= 1) node = rope.getPoints().size() - 1 - tailSegments;
        if (node < 1 || node >= rope.getPoints().size() - 1) {
            release();
            return;
        }
        try {
            attach(server, rope, node);
        } catch (RuntimeException exception) {
            release();
            CreateFireFightingAdd.LOGGER.warn("Rope latch at {} could not restore its attachment; released.", worldPosition, exception);
        }
    }

    private boolean attach(ServerLevel server, ServerRopeStrand rope, int point) {
        UUID bodyId = SableStructureCompat.containingSubLevelId(this);
        ServerSubLevel body = null;
        if (bodyId != null) {
            var container = SubLevelContainer.getContainer(server);
            if (container == null || !(container.getSubLevel(bodyId) instanceof ServerSubLevel sublevel)) return false;
            body = sublevel;
        }
        Vec3 local = anchor();
        PinnedRopeHandle created = PinnedRopeHandle.attach(server, rope, point,
            new Vector3d(local.x, local.y, local.z), body);
        if (created == null) return false;
        pin = created;
        tailSegments = created.tailSegments();
        boundRope = rope;
        rememberOwner(rope);
        lastAnchor = local;
        return true;
    }

    private void rememberOwner(ServerRopeStrand rope) {
        var start = rope.getAttachment(RopeAttachmentPoint.START);
        if (start != null && !start.blockAttachment().equals(ownerPos)) {
            ownerPos = start.blockAttachment().immutable();
            setChanged();
        }
    }

    private boolean ownerRetainsRope(ServerLevel server) {
        var be = server.getBlockEntity(ownerPos);
        if (be instanceof SmartRopeConnectorBlockEntity smart) return smart.owner(ropeId) != null;
        if (!(be instanceof SmartBlockEntity smart)) return false;
        var holder = smart.getBehaviour(RopeStrandHolderBehavior.TYPE);
        var rope = holder == null ? null : holder.getOwnedStrand();
        return rope != null && ropeId.equals(rope.getUUID());
    }

    private Vec3 anchor() {
        return worldPosition.getCenter().add(Vec3.atLowerCornerOf(getBlockState()
            .getValue(RopeLatchBlock.FACING).getNormal()).scale(-3.0 / 16.0));
    }

    public void release() {
        detachPhysics();
        ropeId = null;
        tailSegments = -1;
        ownerPos = null;
        searchUntil = 0;
        setChanged();
    }

    private void detachPhysics() {
        if (pin != null && boundRope != null && level instanceof ServerLevel server) {
            try {
                pin.release(server, boundRope);
            } catch (RuntimeException exception) {
                // Never leave a live solver attachment pointing at a removed block.
                if (boundRope.isActive()) SubLevelPhysicsSystem.get(server).removeObject(boundRope);
                CreateFireFightingAdd.LOGGER.warn("Rope latch at {} released its physics object after a restore failure.", worldPosition, exception);
            }
        }
        pin = null;
        boundRope = null;
    }

    @Override
    public void invalidate() {
        detachPhysics();
        super.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (ropeId != null) tag.putUUID("PinnedRope", ropeId);
        else tag.remove("PinnedRope");
        tag.putInt("PinnedNode", pin != null && !pin.isRemoved() ? pin.pinnedNode() : node);
        tag.putInt("TailSegments", tailSegments);
        if (ownerPos != null) tag.putLong("RopeOwner", ownerPos.asLong());
        else tag.remove("RopeOwner");
        tag.putLong("SearchUntil", searchUntil);
        tag.putLong("CooldownUntil", cooldownUntil);
        tag.putBoolean("LastPowered", lastPowered);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ropeId = tag.hasUUID("PinnedRope") ? tag.getUUID("PinnedRope") : null;
        node = tag.getInt("PinnedNode");
        tailSegments = tag.contains("TailSegments") ? tag.getInt("TailSegments") : -1;
        ownerPos = tag.contains("RopeOwner") ? BlockPos.of(tag.getLong("RopeOwner")) : null;
        searchUntil = tag.getLong("SearchUntil");
        cooldownUntil = tag.getLong("CooldownUntil");
        lastPowered = tag.getBoolean("LastPowered");
    }
}
