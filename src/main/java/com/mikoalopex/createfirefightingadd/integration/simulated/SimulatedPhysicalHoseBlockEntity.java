package com.mikoalopex.createfirefightingadd.integration.simulated;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlock;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoses;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Adapts physical hose endpoints to native strand ownership, collision and persistence.
 * Bracket bindings replace the solver handle while retaining strand identity.
 */
public final class SimulatedPhysicalHoseBlockEntity extends PhysicalHoseBlockEntity implements RopeStrandHolderBlockEntity {
    private Holder holder;
    private boolean anchorFailureReported;

    public SimulatedPhysicalHoseBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    public static void probe() throws ReflectiveOperationException { Holder.probe(); }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(holder = new Holder(this));
    }

    @Override protected boolean hasPhysicsBackend() { return true; }
    @Override public RopeStrandHolderBehavior getBehavior() { return holder; }
    @Override public boolean attached() { return holder != null && holder.isAttached(); }

    public void refreshLink() {
        var strand = holder.getAttachedStrand();
        if (strand == null) return;
        var remote = strand.getAttachment(holder.ownsRope() ? RopeAttachmentPoint.END : RopeAttachmentPoint.START);
        if (remote == null || !(level.getBlockEntity(remote.blockAttachment()) instanceof SimulatedPhysicalHoseBlockEntity other)) return;
        if (!other.getFireHoseEndpointId().equals(getFireHosePartnerEndpointId())) return;
        setFireHoseConnection(holder.ownsRope(), other.worldPosition, remote.subLevelID(), other.getFireHoseEndpointId(), false, getHoseAppearanceId());
        for (var binding : anchors) {
            var bracket = binding.resolve(level);
            if (bracket != null) { bracket.physicalOwner = worldPosition; bracket.notifyUpdate(); }
        }
        if (!anchors.isEmpty()) rebuildAnchors();
        other.setFireHoseConnection(other.holder.ownsRope(), worldPosition, SableStructureCompat.containingSubLevelId(level, worldPosition),
            getFireHoseEndpointId(), false, getHoseAppearanceId());
    }

    @Override public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        return pos.getCenter().add(Vec3.atLowerCornerOf(state.getValue(FireHoseBlock.FACING).getNormal()).scale(-0.25));
    }

    @Override public boolean connectPhysical(PhysicalHoseBlockEntity target) {
        if (!PhysicalHoses.enabled() || !(target instanceof SimulatedPhysicalHoseBlockEntity other)
                || other == this || level == null || level.isClientSide || level != other.level
                || width() != other.width() || attached() || other.attached()) return false;
        Vec3 a = Sable.HELPER.projectOutOfSubLevel(level, getAttachmentPoint(worldPosition, getBlockState()));
        Vec3 b = Sable.HELPER.projectOutOfSubLevel(level, other.getAttachmentPoint(other.worldPosition, other.getBlockState()));
        if (a.distanceTo(b) < 0.25 || a.distanceTo(b) > Config.hoseMaxLength) return false;
        if (!holder.createRope(other.holder, false)) return false;
        var appearance = com.mikoalopex.createfirefightingadd.api.fire_hose.FireHoseAppearances.selectForConnection(
            getHoseAppearanceId(), other.getHoseAppearanceId());
        setFireHoseConnection(true, other.worldPosition, SableStructureCompat.containingSubLevelId(level, other.worldPosition),
            other.getFireHoseEndpointId(), false, appearance);
        other.setFireHoseConnection(false, worldPosition, SableStructureCompat.containingSubLevelId(level, worldPosition),
            getFireHoseEndpointId(), false, appearance);
        return true;
    }

    @Override public void disconnectPhysical() {
        if (level == null || level.isClientSide) return;
        if (holder.ownsRope()) holder.destroyRope(null, null, false);
        else if (getPairedHose() instanceof SimulatedPhysicalHoseBlockEntity other && other.holder.ownsRope())
            other.holder.destroyRope(null, null, false);
    }

    @Override protected boolean transportEnabled() {
        if (!PhysicalHoses.enabled() || level == null || level.isClientSide || !attached()) return false;
        var strand = holder.getAttachedStrand();
        return strand != null && strand.isActive() && strand.areAttachmentsLoaded((net.minecraft.server.level.ServerLevel) level)
            && getPairedHose() instanceof SimulatedPhysicalHoseBlockEntity other && other.attached() && other.width() == width();
    }

    @Override protected boolean prepareTransportTick() {
        if (level != null && !level.isClientSide && PhysicalHoses.enabled() && attached() && !holder.ownsRope()
                && partnerPos != null && level.hasChunkAt(partnerPos)) {
            var remote = level.getBlockEntity(partnerPos);
            if (!(remote instanceof PhysicalHoseBlockEntity)
                    || remote instanceof SimulatedPhysicalHoseBlockEntity other && (!other.attached() || other.getPairedHose() != this))
                holder.detachRope();
        }
        if (level != null && !level.isClientSide && !attached() && partnerPos != null) clearLink();
        return super.prepareTransportTick();
    }

    @Override public com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseAnchors.Snapshot anchorSnapshot() {
        var rope = holder == null || !holder.ownsRope() ? null : holder.getAttachedStrand();
        if (rope == null || !rope.isActive()) return null;
        rope.updatePose();
        var points = rope.getPoints().stream().map(p -> new Vec3(p.x, p.y, p.z)).toList();
        return new com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoseAnchors.Snapshot(
            rope.getUUID(), points, rope.getExtension());
    }

    @Override public boolean rebuildAnchors() {
        var rope = holder == null || !holder.ownsRope() ? null : holder.getAttachedStrand();
        if (rope == null) return false;
        try {
            boolean installed = PhysicalHoseAnchorHandle.install(this, rope);
            if (installed) anchorFailureReported = false;
            return installed;
        }
        catch (RuntimeException exception) {
            if (!anchorFailureReported) {
                anchorFailureReported = true;
                com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.LOGGER.warn("Could not update physical hose brackets at {}", worldPosition);
                com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.LOGGER.debug("Physical hose bracket update failure", exception);
            }
            return false;
        }
    }

    @Override public void tick() {
        super.tick();
        if (level == null || level.isClientSide || holder == null || !holder.ownsRope()) return;
        var rope = holder.getAttachedStrand();
        if (rope == null) return;
        if (!rope.getUUID().equals(bracketStrand) || bracketFirstLength != rope.getExtension()) {
            bracketStrand = rope.getUUID(); bracketFirstLength = rope.getExtension(); notifyUpdate();
        }
        if ((!anchors.isEmpty() || anchorsDirty) && rope.isActive() && Math.floorMod(level.getGameTime() + worldPosition.asLong(), 10) == 0) {
            boolean changed = anchors.removeIf(binding -> level.isLoaded(binding.pos()) && binding.resolve(level) == null);
            if (changed || anchorsDirty || !(PhysicalHoseAnchorHandle.handle(rope) instanceof PhysicalHoseAnchorHandle)) {
                anchorsDirty = !rebuildAnchors();
                if (changed) notifyUpdate();
            }
        }
    }

    private void clearLink() {
        if (!anchors.isEmpty()) clearAnchors();
        if (partnerPos != null) setFireHoseConnection(false, null, null, null, false, getHoseAppearanceId());
    }

    @Override public List<Vec3> renderPoints(float partial) {
        if (!PhysicalHoses.enabled() || holder == null || !holder.ownsRope()) return List.of();
        var strand = holder.getClientStrand();
        if (strand == null) return List.of();
        var containing = Sable.HELPER.getContaining(this);
        List<Vec3> result = new ArrayList<>();
        for (var point : strand.getPoints()) {
            Vector3d position = point.renderPos(partial, new Vector3d());
            if (containing instanceof ClientSubLevel sublevel) sublevel.renderPose().transformPositionInverse(position);
            result.add(new Vec3(position.x - worldPosition.getX(), position.y - worldPosition.getY(), position.z - worldPosition.getZ()));
        }
        return result;
    }

    @Override public EndFrame renderEnd(boolean start) {
        var endpoint = start ? this : getPairedHose();
        if (endpoint == null) return null;
        var facing = endpoint.getBlockState().getValue(FireHoseBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 up = facing == net.minecraft.core.Direction.UP ? new Vec3(0, 0, -1)
            : facing == net.minecraft.core.Direction.DOWN ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 point = endpoint.getBlockPos().getCenter().add(normal.scale(-0.25));
        if (!start) {
            var transform = com.mikoalopex.createfirefightingadd.integration.sable.SableStructureClientCompat.transformFireHoseTarget(
                this, getPartnerSubLevelID(), new Vector3d(point.x, point.y, point.z), new Vector3d(normal.x, normal.y, normal.z));
            var edge = transform.ownerOrientation().invert(new org.joml.Quaterniond()).mul(transform.partnerOrientation())
                .transform(new Vector3d(up.x, up.y, up.z));
            point = new Vec3(transform.partnerCenter().x, transform.partnerCenter().y, transform.partnerCenter().z);
            normal = new Vec3(transform.partnerNormal().x, transform.partnerNormal().y, transform.partnerNormal().z);
            up = new Vec3(edge.x, edge.y, edge.z);
        }
        return new EndFrame(point.subtract(Vec3.atLowerCornerOf(worldPosition)), normal, up);
    }

    /** Always suppresses rope-item refunds: the two hose heads are the recoverable items. */
    private static final class Holder extends RopeStrandHolderBehavior {
        private static MethodHandle create, destroy;
        private final SimulatedPhysicalHoseBlockEntity hose;
        Holder(SimulatedPhysicalHoseBlockEntity hose) { super(hose); this.hose = hose; }

        static void probe() throws ReflectiveOperationException {
            var lookup = MethodHandles.lookup();
            create = lookup.findSpecial(RopeStrandHolderBehavior.class, "createRope",
                MethodType.methodType(boolean.class, RopeStrandHolderBehavior.class, boolean.class), Holder.class);
            destroy = lookup.findSpecial(RopeStrandHolderBehavior.class, "destroyRope",
                MethodType.methodType(void.class, ServerPlayer.class, Vec3.class, boolean.class), Holder.class);
        }

        // Simulated 1.3 adds item-drop arguments that are absent from the base compile API.
        public boolean createRope(RopeStrandHolderBehavior target, boolean dropItem) {
            if (!(target instanceof Holder other) || other == this || isAttached() || other.isAttached()
                    || hose.width() != other.hose.width()) return false;
            try { return (boolean) create.invokeExact(this, target, false); }
            catch (RuntimeException | Error error) { throw error; }
            catch (Throwable error) { throw new IllegalStateException("Cannot create physical hose strand", error); }
        }

        public void destroyRope(ServerPlayer player, Vec3 pos, boolean returnItem) {
            // Native hovered-rope packets also accept a wrench; hoses are cut only with shears.
            if (player != null && (player.isSpectator() || !player.mayBuild()
                    || !player.getMainHandItem().is(net.minecraft.world.item.Items.SHEARS))) return;
            boolean connected = isAttached();
            try { destroy.invokeExact(this, player, pos, false); }
            catch (RuntimeException | Error error) { throw error; }
            catch (Throwable error) { throw new IllegalStateException("Cannot remove physical hose strand", error); }
            if (!isAttached()) hose.clearLink();
            if (player != null && connected && !isAttached())
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHEEP_SHEAR,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        @Override public void detachRope() { super.detachRope(); hose.clearLink(); }
    }
}
