package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import java.util.Objects;
import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.BallCouplingMenu;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.CouplingSavedData;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingAlignment;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;
import com.simibubi.create.content.fluids.FluidPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Controls physical pairing and delegates fluid transport to the hose endpoint. */
public class FluidCouplingBlockEntity extends FireHoseBlockEntity implements MenuProvider {
    private FluidCouplingSettings settings = FluidCouplingSettings.DEFAULT;
    private UUID endpoint = UUID.randomUUID(), partner, pair;
    private UUID pitchTarget;
    private Object constraint, reference;
    private CouplingAlignment.Session alignment;
    private boolean powered, connected, established, restoring, cleaned;
    private int pendingTicks, recoveryTicks, searchTicks, nextSearch;
    private long cooldownUntil;
    private float pitch, previousPitch, flow, gearPhase, previousGearPhase;
    private float deployment, previousDeployment;
    private int transferred;
    private Vec3 renderPartnerAnchor, renderPartnerNormal;
    private UUID renderPartnerBody;

    public FluidCouplingBlockEntity(BlockPos pos, BlockState state) { super(FluidCouplings.BLOCK_ENTITY.get(), pos, state); }
    public FluidCouplingSettings settings() { return settings; }
    public UUID endpointId() { return endpoint; }
    public UUID partnerId() { return partner; }
    public UUID pairId() { return pair; }
    public boolean active() { return connected && FluidCouplings.enabled(); }
    public boolean powered() { return powered; }
    public Direction facing() { return getBlockState().getValue(FluidCouplingBlock.FACING); }
    public Level worldLevel() { return SableStructureCompat.worldLevel(this); }
    public Vec3 localAnchor() { return Vec3.atLowerCornerOf(worldPosition).add(FluidCouplingGeometry.modelPoint(
        settings.type().contact(settings.height(), pitch), facing())); }
    public Vec3 worldAnchor() { return SableStructureCompat.transformPositionToWorld(this, localAnchor()); }
    public Vec3 worldNormal() { return SableStructureCompat.transformNormalToWorld(this, Vec3.atLowerCornerOf(facing().getNormal())).normalize(); }
    public Vec3 renderPartnerAnchor() { return renderPartnerAnchor; }
    public Vec3 renderPartnerNormal() { return renderPartnerNormal; }
    public UUID renderPartnerBody() { return renderPartnerBody; }
    public float pitch(float partial) { return previousPitch + (pitch - previousPitch) * partial; }
    public float gearPhase(float partial) { return previousGearPhase + (gearPhase - previousGearPhase) * partial; }
    public float deployment(float partial) { return previousDeployment + (deployment - previousDeployment) * Math.clamp(partial, 0, 1); }
    public int status() { return !FluidCouplings.enabled() ? 4 : connected ? 1 : partner != null ? 3 : powered ? 2 : 0; }

    public FluidCouplingBlockEntity partner() {
        return level == null || level.isClientSide || partner == null ? null : FluidCouplingIndex.of(worldLevel()).find(partner);
    }
    private CouplingSavedData saved() { return CouplingSavedData.get(level.getServer(), FluidCouplingJoint.FEATURE); }
    private boolean owns(FluidCouplingBlockEntity other) {
        return settings.type().activation() == FluidCouplingInterface.Activation.ACTIVE
            || settings.type() == FluidCouplingInterfaces.FREE && endpoint.compareTo(other.endpoint) < 0;
    }
    private CouplingPhysics.Endpoint physicsEndpoint() {
        return FluidCouplingJoint.endpoint(worldLevel().dimension(), SableStructureCompat.containingSubLevelId(this),
            worldPosition, facing(), settings, v -> SableStructureCompat.transformNormalToWorld(this, v));
    }

    @Override public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide || !FluidCouplings.enabled()) return;
        var existing = FluidCouplingIndex.of(worldLevel()).find(endpoint);
        if (existing != null && existing != this && !existing.isRemoved()) {
            endpoint = UUID.randomUUID(); partner = pair = null; reference = null; restoring = connected = established = false;
            setChanged();
        }
        FluidCouplingIndex.of(worldLevel()).update(this);
        updateSignal();
    }

    public void wakeSearch() { nextSearch = Math.min(nextSearch, 1); }

    public void updateSignal() {
        if (level == null || level.isClientSide || restoring) return;
        boolean next = level.hasNeighborSignal(worldPosition);
        boolean changed = next != powered;
        powered = next;
        if (getBlockState().getValue(FluidCouplingBlock.POWERED) != powered)
            level.setBlock(worldPosition, getBlockState().setValue(FluidCouplingBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        if (changed && powered) {
            searchTicks = nextSearch = 0; cooldownUntil = 0;
            if (FluidCouplings.enabled()) FluidCouplingIndex.of(worldLevel()).wake(this);
        }
        if (changed && settings.type().hose()) {
            notifyUpdate();
            CreateFireFightingAdd.LOGGER.info("[FLUID-HOSE] endpoint={} type={} powered={} deployment={} connected={}",
                endpoint, settings.type(), powered, deployment, connected);
        }
        if (!powered && partner != null && !(established && settings.type().activation() == FluidCouplingInterface.Activation.PASSIVE)) disconnect();
    }

    @Override public void tick() {
        if (level == null) return;
        previousPitch = pitch;
        previousGearPhase = gearPhase;
        previousDeployment = deployment;
        if (settings.type() == FluidCouplingInterfaces.HOSE_DROGUE) {
            deployment = HoseDeployment.step(deployment, getBlockState().getValue(FluidCouplingBlock.POWERED));
            if (!level.isClientSide && deployment != previousDeployment
                    && (deployment == 0 || deployment == 1 || level.getGameTime() % 5 == 0)) notifyUpdate();
        } else deployment = previousDeployment = 0;
        if (!level.isClientSide) tickPairing();
        else {
            gearPhase += Math.min(30, flow * 0.1f);
            if (gearPhase > 36000) { gearPhase -= 36000; previousGearPhase -= 36000; }
        }
        super.tick();
        if (!level.isClientSide && level.getGameTime() % 5 == 0) {
            float next = transferred / 5f;
            transferred = 0;
            if (flow != next) { flow = next; notifyUpdate(); }
        }
    }

    private void tickPairing() {
        if (!FluidCouplings.enabled()) {
            if (pair != null) disconnect();
            return;
        }
        FluidCouplingIndex.of(worldLevel()).update(this);
        if (pair != null && saved().consume(pair)) { disconnect(); return; }
        if (restoring) {
            if (!cleaned) { releaseConstraint(); connected = false; cleaned = true; }
            if (!established) { disconnect(); return; }
            var other = partner();
            if (++recoveryTicks > 200) { disconnect(); return; }
            if (other == null || !signalLoaded() || !other.signalLoaded()) return;
            if (!reciprocal(other)) { disconnect(); return; }
            restoring = other.restoring = false;
            updateSignal(); other.updateSignal();
        }
        updateSignal();
        var other = partner();
        updatePitch(other != null ? other : findPitchTarget());
        if (partner != null) {
            if (other == null) {
                if (connected) { connected = false; releaseConstraint(); networkChanged(); }
                if (++pendingTicks > 200) disconnect();
                return;
            }
            if (!reciprocal(other)) { disconnect(); return; }
            if (owns(other)) maintain(other);
            return;
        }
        if (!powered || settings.type().activation() == FluidCouplingInterface.Activation.PASSIVE
                || settings.type() == FluidCouplingInterfaces.HOSE_DROGUE && !HoseDeployment.ready(deployment)
                || level.getGameTime() < cooldownUntil) return;
        searchTicks = Math.min(600, searchTicks + 1);
        if (nextSearch-- > 0) return;
        nextSearch = searchTicks < 20 ? 1 : searchTicks < 200 ? 9 : searchTicks < 600 ? 19 : 99;
        pitchTarget = null;
        for (var candidate : FluidCouplingIndex.of(worldLevel()).nearby(this)) {
            if (!candidate.powered || candidate.partner != null || !owns(candidate)
                    || candidate.level.getGameTime() < candidate.cooldownUntil) continue;
            if (Objects.equals(SableStructureCompat.containingSubLevelId(this), SableStructureCompat.containingSubLevelId(candidate))) continue;
            if (pitchTarget == null && settings.type() == FluidCouplingInterfaces.RECEPTACLE)
                pitchTarget = candidate.endpoint;
            // Hard-boom pivots can be 4px farther apart than the visual contact points.
            var session = FluidCouplingIndex.inside(worldAnchor(), candidate.worldAnchor(), 0.75) ? null
                : CouplingAlignment.begin(physicsEndpoint(), candidate.physicsEndpoint(), settings.constraint().mode(),
                    settings.type() == FluidCouplingInterfaces.FREE, settings.type().locksConstraint() ? 3.25 : 1.5);
            if (session == null && !FluidCouplingIndex.inside(worldAnchor(), candidate.worldAnchor(), 0.75)) continue;
            pair = candidate.pair = UUID.randomUUID(); partner = candidate.endpoint; candidate.partner = endpoint;
            alignment = session; pendingTicks = 0; reference = null; candidate.reference = null;
            notifyUpdate(); candidate.notifyUpdate();
            maintain(candidate);
            break;
        }
    }

    private boolean signalLoaded() {
        if (SableStructureCompat.containingSubLevelId(this) != null) return true;
        for (Direction side : Direction.values()) if (!level.hasChunkAt(worldPosition.relative(side))) return false;
        return true;
    }

    private boolean reciprocal(FluidCouplingBlockEntity other) {
        return endpoint.equals(other.partner) && pair != null && pair.equals(other.pair)
            && settings.type().accepts(other.settings.type()) && other.settings.type().accepts(settings.type());
    }

    private void maintain(FluidCouplingBlockEntity other) {
        if (!powered || (!other.powered && (!established || settings.type() == FluidCouplingInterfaces.FREE))) {
            disconnect(); return;
        }
        if (alignment != null) {
            if (FluidCouplingIndex.inside(worldAnchor(), other.worldAnchor(), 0.75)) {
                alignment.close(); alignment = null; pendingTicks = 0;
            } else {
                if (!FluidCouplingIndex.inSearchRange(settings.type(), worldAnchor(), other.worldAnchor())
                        || alignment.failed() || ++pendingTicks > 60) { disconnect(); cooldownUntil = level.getGameTime() + 40; }
                return;
            }
        }
        if (constraint == null && !saved().removalPending(worldLevel().dimension(), pair))
            constraint = CouplingPhysics.update(reference, FluidCouplingJoint.request(pair, settings, other.settings,
                physicsEndpoint(), other.physicsEndpoint()));
        boolean ready = CouplingPhysics.active(constraint);
        if (ready != connected || ready != other.connected) {
            connected = other.connected = ready;
            if (settings.type().hose()) CreateFireFightingAdd.LOGGER.info(
                "[FLUID-HOSE] pair={} connected={} mode={} deployment={} anchorDistance={}",
                pair, ready, settings.constraint().mode(), deployment, worldAnchor().distanceTo(other.worldAnchor()));
            isController = true; other.isController = false;
            networkChanged(); other.networkChanged();
        }
        if (ready) { pendingTicks = 0; established = other.established = true; }
        else if (++pendingTicks > 200) disconnect();
    }

    private FluidCouplingBlockEntity findPitchTarget() {
        if (pitchTarget == null || !powered || level.getGameTime() < cooldownUntil) return null;
        var target = FluidCouplingIndex.of(worldLevel()).find(pitchTarget);
        if (target == null || target.isRemoved() || !target.powered || target.partner != null
                || !settings.type().accepts(target.settings.type())
                || target.level.getGameTime() < target.cooldownUntil
                || Objects.equals(SableStructureCompat.containingSubLevelId(this), SableStructureCompat.containingSubLevelId(target))
                || !FluidCouplingIndex.inSearchRange(settings.type(), worldAnchor(), target.worldAnchor())) {
            pitchTarget = null;
            return null;
        }
        return target;
    }

    private void updatePitch(FluidCouplingBlockEntity other) {
        double target = 0;
        if (settings.type() == FluidCouplingInterfaces.RECEPTACLE && other != null) {
            Vec3 pivot = Vec3.atLowerCornerOf(worldPosition).add(
                FluidCouplingGeometry.modelPoint(FluidCouplingGeometry.PITCH_PIVOT, facing()));
            Vec3 direction = other.worldAnchor().subtract(SableStructureCompat.transformPositionToWorld(this, pivot));
            Vec3 x = SableStructureCompat.transformNormalToWorld(this, FluidCouplingGeometry.orient(new Vec3(1, 0, 0), facing()));
            Vec3 y = worldNormal();
            Vec3 z = SableStructureCompat.transformNormalToWorld(this, FluidCouplingGeometry.orient(new Vec3(0, 0, 1), facing()));
            target = FluidCouplingGeometry.pitchToward(new Vec3(direction.dot(x), direction.dot(y), direction.dot(z)));
        }
        float next = (float) (pitch + (target - pitch) * 0.35);
        if (Math.abs(next - pitch) > 0.01) { pitch = next; if (level.getGameTime() % 2 == 0) notifyUpdate(); }
    }

    private void releaseConstraint() {
        if (constraint != null) reference = constraint;
        if (pair != null && worldLevel() instanceof ServerLevel server) saved().removeConstraint(server, pair);
        else CouplingPhysics.remove(constraint);
        constraint = null;
    }

    public void disconnect() {
        if (level == null || level.isClientSide) return;
        if (pair != null && settings.type().hose()) CreateFireFightingAdd.LOGGER.info(
            "[FLUID-HOSE] pair={} endpoint={} disconnect powered={} deployment={}", pair, endpoint, powered, deployment);
        pitchTarget = null;
        var other = partner();
        releaseConstraint();
        if (alignment != null) { alignment.close(); alignment = null; }
        if (other != null && endpoint.equals(other.partner)) {
            other.releaseConstraint();
            other.partner = other.pair = null; other.reference = null; other.connected = other.established = other.restoring = false;
            other.pitchTarget = null;
            other.pendingTicks = 0; other.networkChanged(); other.wakeSearch();
        } else if (pair != null) saved().invalidate(pair);
        partner = pair = null; reference = null; connected = established = restoring = false; pendingTicks = 0;
        networkChanged();
    }

    public void disconnectFromGui() {
        var other = partner();
        disconnect(); cooldownUntil = level.getGameTime() + 100;
        if (other != null) { other.cooldownUntil = other.level.getGameTime() + 100; other.setChanged(); }
        setChanged();
    }

    public void applySettings(int mode, int role, int lower, int upper, boolean flip) {
        if (role < 0 || role >= FluidCouplingInterfaces.values().length || mode < 0 || mode > 2 || lower < -180 || upper > 180 || lower > upper) return;
        var type = FluidCouplingInterfaces.values()[role];
        var next = type.locksConstraint()
            ? settings.withInterface(type)
            : new FluidCouplingSettings(type, settings.height(), mode, lower, upper, flip);
        if (settings.equals(next)) return;
        disconnect(); settings = next; pitch = previousPitch = 0; cooldownUntil = 0; nextSearch = 0;
        deployment = previousDeployment = 0;
        notifyUpdate();
    }

    public void cycleHeight(Player player) {
        if (partner != null || settings.type() != FluidCouplingInterfaces.FREE) return;
        settings = new FluidCouplingSettings(settings.type(), (settings.height() + 1) % 5, settings.freeMode(),
            settings.lower(), settings.upper(), settings.flipRange());
        nextSearch = 0; notifyUpdate();
    }

    private void networkChanged() {
        updateDisconnectedEndpoint();
        level.invalidateCapabilities(worldPosition);
        BlockPos pipe = worldPosition.relative(facing().getOpposite());
        if (level.hasChunkAt(pipe)) FluidPropagator.propagateChangedPipe(level, pipe, level.getBlockState(pipe));
        notifyUpdate();
    }

    @Override protected boolean prepareTransportTick() {
        if (level == null || level.isClientSide) return false;
        if (!transportEnabled()) { updateDisconnectedEndpoint(); return false; }
        if (!isController) mergeBufferedFluid(partner());
        return true;
    }
    @Override public boolean allowsHoseConnection() { return false; }
    @Override protected boolean transportEnabled() {
        var other = partner();
        return active() && other != null && other.active() && reciprocal(other);
    }
    @Override public FireHoseBlockEntity getPairedHose() { return transportEnabled() ? partner() : null; }
    @Override public IFluidHandler getFluidHandler(Direction side) {
        return side == facing().getOpposite() ? super.getFluidHandler(side) : null;
    }
    @Override public Direction getFacingDirection() { return facing(); }
    @Override protected void onFluidTransferred(int amount) {
        transferred += amount;
        var other = partner(); if (other != null) other.transferred += amount;
    }
    @Override public void invalidate() {
        if (level != null && !level.isClientSide) {
            if (alignment != null) disconnect();
            releaseConstraint(); connected = false;
            var other = partner(); if (other != null) { other.releaseConstraint(); other.connected = false; other.networkChanged(); }
            FluidCouplingIndex.of(worldLevel()).remove(this);
        }
        super.invalidate();
    }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(4); }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.putUUID("CouplingEndpoint", endpoint);
        if (partner != null) tag.putUUID("CouplingPartner", partner);
        if (pair != null) tag.putUUID("CouplingPair", pair);
        tag.put("FluidCouplingSettings", settings.save());
        tag.putBoolean("CouplingEstablished", established);
        tag.putBoolean("FluidConnected", connected);
        tag.putBoolean("CouplingAligning", alignment != null || partner != null && !connected && reference == null);
        tag.putBoolean("CouplingPowered", powered);
        tag.putLong("CouplingGuiPairCooldown", cooldownUntil);
        tag.putFloat("Pitch", pitch); tag.putFloat("Flow", flow);
        tag.putFloat("HoseDeployment", deployment);
        if (!packet && pair != null) tag.put("CouplingReference", CouplingPhysics.saveReference(constraint != null ? constraint : reference));
        var other = partner();
        if (packet && other != null) {
            CompoundTag link = new CompoundTag();
            Vec3 point = other.localAnchor();
            link.putDouble("X", point.x); link.putDouble("Y", point.y); link.putDouble("Z", point.z);
            link.putInt("Facing", other.facing().ordinal());
            UUID body = SableStructureCompat.containingSubLevelId(other);
            if (body != null) link.putUUID("Body", body);
            tag.put("FluidRenderLink", link);
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        if (tag.hasUUID("CouplingEndpoint")) endpoint = tag.getUUID("CouplingEndpoint");
        partner = tag.hasUUID("CouplingPartner") ? tag.getUUID("CouplingPartner") : null;
        pair = tag.hasUUID("CouplingPair") ? tag.getUUID("CouplingPair") : null;
        settings = FluidCouplingSettings.load(tag.getCompound("FluidCouplingSettings"));
        powered = tag.getBoolean("CouplingPowered"); established = tag.getBoolean("CouplingEstablished");
        connected = packet && tag.getBoolean("FluidConnected");
        cooldownUntil = tag.getLong("CouplingGuiPairCooldown");
        previousPitch = pitch;
        pitch = (float) FluidCouplingGeometry.clampPitch(tag.getFloat("Pitch"));
        flow = Float.isFinite(tag.getFloat("Flow")) ? Math.max(0, tag.getFloat("Flow")) : 0;
        deployment = previousDeployment = HoseDeployment.clamp(tag.getFloat("HoseDeployment"));
        if (!packet) {
            reference = pair == null ? null : CouplingPhysics.restoreReference(tag.getCompound("CouplingReference"));
            restoring = pair != null; cleaned = false; recoveryTicks = 0;
        }
        renderPartnerAnchor = renderPartnerNormal = null; renderPartnerBody = null;
        if (packet && tag.contains("FluidRenderLink")) {
            CompoundTag link = tag.getCompound("FluidRenderLink");
            renderPartnerAnchor = new Vec3(link.getDouble("X"), link.getDouble("Y"), link.getDouble("Z"));
            renderPartnerNormal = Vec3.atLowerCornerOf(Direction.values()[Math.clamp(link.getInt("Facing"), 0, 5)].getNormal());
            renderPartnerBody = link.hasUUID("Body") ? link.getUUID("Body") : null;
        }
    }
    @Override public Component getDisplayName() { return Component.translatable("block.createfirefightingadd.fluid_coupling"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return FluidCouplings.enabled() ? new BallCouplingMenu(id, inventory, this) : null;
    }
}
