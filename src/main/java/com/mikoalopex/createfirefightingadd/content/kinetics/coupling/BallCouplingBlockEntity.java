package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.api.coupling.StressCouplingApi;
import com.mikoalopex.createfirefightingadd.api.coupling.StressCouplingEndpoint;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingAlignment;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

public class BallCouplingBlockEntity extends KineticBlockEntity implements MenuProvider, StressCouplingEndpoint {
    /** Centers of the named ball element in each top model, measured in model pixels. */
    private static final double[] BALL_CENTERS = {5, 9, 13, 17, 20};

    private UUID endpoint = UUID.randomUUID();
    private UUID partner;
    private UUID pair;
    private UUID previousPartner;
    private Object constraint;
    private Object savedReference;
    private CouplingAlignment.Session alignment;
    private long alignmentRetryUntil;
    private boolean searching;
    private boolean powered;
    private int searchTicks;
    private int nextSearch;
    private int pendingTicks;
    private boolean connectionEstablished;
    private long guiPairCooldownUntil;
    private int mode;
    private CouplingInterfaceMode interfaceMode;
    private int lowerAngle = -45;
    private int upperAngle = 45;
    private boolean flipRange;
    private Vec3 renderPartnerAnchor;
    private Vec3 renderPartnerNormal;
    private UUID renderPartnerBody;

    public BallCouplingBlockEntity(BlockPos pos, BlockState state) {
        super(BallCouplings.BLOCK_ENTITY.get(), pos, state);
        interfaceMode = CouplingInterfaceMode.FREE;
    }

    public static BallCouplingBlockEntity create(BlockPos pos, BlockState state) {
        return StressCouplingCompatibility.create(pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean isTop() {
        return ((BallCouplingBlock) getBlockState().getBlock()).isTop();
    }

    public UUID endpointId() {
        return endpoint;
    }

    public UUID partnerId() {
        return partner;
    }

    public UUID pairId() {
        return pair;
    }

    public int mode() {
        return mode;
    }

    public CouplingInterfaceMode interfaceMode() { return interfaceMode; }
    public int lowerAngle() { return lowerAngle; }
    public int upperAngle() { return upperAngle; }
    public boolean flipRange() { return flipRange; }
    public Vec3 renderPartnerAnchor() { return renderPartnerAnchor; }
    public Vec3 renderPartnerNormal() { return renderPartnerNormal; }
    public UUID renderPartnerBody() { return renderPartnerBody; }

    boolean searches() { return interfaceMode != CouplingInterfaceMode.PASSIVE; }

    boolean ownsConnection(BallCouplingBlockEntity other) {
        return interfaceMode == CouplingInterfaceMode.ACTIVE
            || interfaceMode == CouplingInterfaceMode.FREE && endpoint.compareTo(other.endpoint) < 0;
    }

    private CouplingPhysics.Endpoint physicsEndpoint() {
        Vec3 p = localAnchor(), n = worldNormal(), t = worldTangent();
        return new CouplingPhysics.Endpoint(worldLevel().dimension(), SableStructureCompat.containingSubLevelId(this),
            new org.joml.Vector3d(p.x, p.y, p.z), new org.joml.Vector3d(n.x, n.y, n.z),
            new org.joml.Vector3d(t.x, t.y, t.z));
    }

    private Object createConstraint(BallCouplingBlockEntity other, UUID id) {
        if (!StressCouplingCompatibility.enabled()) return null;
        return CouplingPhysics.update(constraint != null ? constraint : savedReference, new CouplingPhysics.Request(
            com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.path("ball_coupling"), id,
            physicsEndpoint(), other.physicsEndpoint(), mode, lowerAngle, upperAngle, flipRange));
    }

    public int status() {
        if (!StressCouplingCompatibility.enabled())
            return 4;
        if (active())
            return 1;
        if (partner != null)
            return 3;
        return searching ? 2 : 0;
    }

    void wakeSearch() {
        if (searching && powered)
            nextSearch = Math.min(nextSearch, 1);
    }

    public void requestConnectionSearch() {
        if (!StressCouplingCompatibility.enabled()) return;
        if (level == null || level.isClientSide || partner != null)
            return;
        updateSignal();
        beginSearch(true);
        BallCouplingIndex.of(worldLevel()).wakeNearby(this);
    }

    public Level worldLevel() {
        return SableStructureCompat.worldLevel(this);
    }

    /** Exact anchor in the owning block or Sable sublevel's model coordinates. */
    public Vec3 localAnchor() {
        int length = getBlockState().getValue(BallCouplingBlock.LENGTH);
        double offset = interfaceMode == CouplingInterfaceMode.FREE ? length / 4.0
            : interfaceMode == CouplingInterfaceMode.PASSIVE ? (BALL_CENTERS[length] - 8) / 16
            : length / 4.0;
        return getBlockPos().getCenter().add(BallCouplingBlock.orient(
            new Vec3(0, offset, 0), getBlockState().getValue(BallCouplingBlock.FACING)));
    }

    public Vec3 worldAnchor() {
        return SableStructureCompat.transformPositionToWorld(this, localAnchor());
    }

    public Vec3 worldNormal() {
        Vec3 localNormal = Vec3.atLowerCornerOf(getBlockState().getValue(BallCouplingBlock.FACING).getNormal());
        return SableStructureCompat.transformNormalToWorld(this, localNormal).normalize();
    }

    public Vec3 worldTangent() {
        Vec3 localTangent = BallCouplingBlock.orient(new Vec3(1, 0, 0),
            getBlockState().getValue(BallCouplingBlock.FACING));
        return SableStructureCompat.transformNormalToWorld(this, localTangent).normalize();
    }

    public BallCouplingBlockEntity partner() {
        return !StressCouplingCompatibility.enabled() || level == null || level.isClientSide || partner == null
            ? null
            : BallCouplingIndex.of(worldLevel()).find(partner);
    }

    public boolean active() {
        return StressCouplingCompatibility.enabled() && getBlockState().getValue(BallCouplingBlock.CONNECTED);
    }

    public boolean powered() {
        return powered;
    }

    @Override public UUID stressCouplingEndpointId() { return endpoint; }
    @Override public UUID stressCouplingPartnerId() { return partner; }
    @Override public Level stressCouplingLevel() { return worldLevel(); }
    @Override public Vec3 stressCouplingAnchor() { return worldAnchor(); }
    @Override public Vec3 stressCouplingNormal() { return worldNormal(); }
    @Override public StressCouplingApi.InterfaceMode stressCouplingInterfaceMode() {
        return StressCouplingApi.InterfaceMode.values()[interfaceMode.ordinal()];
    }
    @Override public StressCouplingApi.ConnectionState stressCouplingState() {
        return switch (status()) {
            case 1 -> StressCouplingApi.ConnectionState.CONNECTED;
            case 2 -> StressCouplingApi.ConnectionState.SEARCHING;
            case 3 -> StressCouplingApi.ConnectionState.ALIGNING;
            case 4 -> StressCouplingApi.ConnectionState.UNAVAILABLE;
            default -> StressCouplingApi.ConnectionState.IDLE;
        };
    }
    @Override public boolean stressCouplingPowered() { return powered; }
    @Override public void stressCouplingRequestSearch() { requestConnectionSearch(); }
    @Override public void stressCouplingDisconnect() { disconnect(); }


    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide)
            return;

        if (!StressCouplingCompatibility.enabled()) {
            clearDisabledConnection();
            return;
        }
        BallCouplingBlockEntity existing = BallCouplingIndex.of(worldLevel()).find(endpoint);
        if (existing != null && existing != this && !existing.isRemoved()) {
            endpoint = UUID.randomUUID();
            partner = null;
            pair = null;
            savedReference = null;
            searching = false;
            setActive(false);
            setChanged();
        }

        BallCouplingIndex.of(worldLevel()).update(this);
        powered = level.hasNeighborSignal(worldPosition);
        setPoweredState(powered);
        if (powered && partner == null) {
            if (!searches())
                BallCouplingIndex.of(worldLevel()).wakeNearby(this);
            else
                beginSearch(true);
        }
    }

    /** Refreshes external redstone power from every adjacent face. */
    public void updateSignal() {
        if (level == null || level.isClientSide)
            return;
        if (!StressCouplingCompatibility.enabled()) {
            clearDisabledConnection();
            return;
        }

        boolean next = level.hasNeighborSignal(worldPosition);
        boolean changed = next != powered;
        powered = next;
        setPoweredState(next);
        if (changed && next) alignmentRetryUntil = 0;

        if (changed && next && guiPairCooldownUntil > 0) {
            guiPairCooldownUntil = 0;
            BallCouplingBlockEntity previous = previousPartner == null ? null
                : BallCouplingIndex.of(worldLevel()).find(previousPartner);
            if (previous != null && endpoint.equals(previous.previousPartner)) {
                previous.guiPairCooldownUntil = 0;
                previous.setChanged();
            }
            setChanged();
        }

        if (!next && !(partner != null && interfaceMode == CouplingInterfaceMode.PASSIVE && connectionEstablished)) {
            if (partner != null || searching)
                disconnect();
            return;
        }

        if (partner != null) {
            BallCouplingBlockEntity other = partner();
            if (other != null && !other.powered && other.interfaceMode != CouplingInterfaceMode.PASSIVE)
                disconnect();
            return;
        }

        if (!searches()) {
            if (changed)
                BallCouplingIndex.of(worldLevel()).wakeNearby(this);
        } else {
            beginSearch(changed);
            if (changed) BallCouplingIndex.of(worldLevel()).wakeNearby(this);
        }
    }

    private void beginSearch(boolean resetBackoff) {
        if (!StressCouplingCompatibility.enabled() || !searches() || !powered || partner != null)
            return;
        boolean wasSearching = searching;
        searching = true;
        if (resetBackoff) {
            searchTicks = 0;
            nextSearch = 0;
        }
        if (!wasSearching) setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        if (!StressCouplingCompatibility.enabled()) {
            clearDisabledConnection();
            return;
        }

        BallCouplingIndex.of(worldLevel()).update(this);
        if (pair != null && CouplingSavedData.get(level.getServer()).consume(pair)) {
            if (alignment != null) { alignment.close(); alignment = null; }
            CouplingKinetics.disconnect(this);
            CouplingPhysics.remove(constraint);
            constraint = null;
            partner = null;
            pair = null;
            savedReference = null;
            setActive(false);
            beginSearch(true);
            notifyUpdate();
        }

        updateSignal();
        if (!searches())
            return;

        if (partner != null) {
            BallCouplingBlockEntity other = partner();
            if (other == null || ownsConnection(other)) maintainConnection();
            return;
        }

        searchForPartner();
    }

    private void maintainConnection() {
        BallCouplingBlockEntity other = partner();
        if (other == null) {
            if (alignment != null) { cancelAlignment(); return; }
            setActive(false);
            CouplingKinetics.disconnect(this);
            if (constraint != null) savedReference = constraint;
            CouplingPhysics.remove(constraint);
            constraint = null;
            return;
        }
        if (!powered || (!other.powered && (interfaceMode == CouplingInterfaceMode.FREE || !connectionEstablished))
                || !interfaceMode.accepts(other.interfaceMode) || !endpoint.equals(other.partner) || !Objects.equals(pair, other.pair)) {
            disconnect();
            return;
        }

        if (alignment != null) {
            if (alignment.failed() || ++pendingTicks > 60) {
                cancelAlignment();
                return;
            }
            if (!withinConnectionRange(other))
                return;
            alignment.close();
            alignment = other.alignment = null;
            pendingTicks = 0;
            constraint = createConstraint(other, pair);
            if (constraint == null) { cancelAlignment(); return; }
        }

        if (constraint == null)
            constraint = createConstraint(other, pair);
        boolean ready = CouplingPhysics.active(constraint);
        setActive(ready);
        other.setActive(ready);
        if (ready) {
            if (!connectionEstablished) {
                connectionEstablished = other.connectionEstablished = true;
                setChanged();
                other.setChanged();
            }
            pendingTicks = 0;
            CouplingKinetics.connect(this, other);
        } else {
            CouplingKinetics.disconnect(this);
            if (++pendingTicks > 200)
                disconnect();
        }
    }

    private boolean withinConnectionRange(BallCouplingBlockEntity other) {
        Vec3 a = worldAnchor(), b = other.worldAnchor();
        return Math.abs(a.x - b.x) <= 0.75 && Math.abs(a.y - b.y) <= 0.75
            && Math.abs(a.z - b.z) <= 0.75;
    }

    private void searchForPartner() {
        if (!powered || !searching || !CouplingPhysics.available()
                || guiPairCooldownUntil > level.getGameTime() || alignmentRetryUntil > level.getGameTime())
            return;

        searchTicks = Math.min(searchTicks + 1, 600);
        if (nextSearch-- > 0)
            return;
        nextSearch = searchTicks < 20 ? 1 : searchTicks < 200 ? 9 : searchTicks < 600 ? 19 : 99;

        List<BallCouplingBlockEntity> candidates = BallCouplingIndex.of(worldLevel()).nearby(this);
        candidates.sort(Comparator.comparing((BallCouplingBlockEntity be) -> be.endpoint.equals(previousPartner))
            .thenComparingDouble(be -> be.worldAnchor().distanceToSqr(worldAnchor()))
            .thenComparing(be -> be.endpoint));

        for (BallCouplingBlockEntity other : candidates) {
            if (!other.powered || other.partner != null || !ownsConnection(other)
                    || other.guiPairCooldownUntil > level.getGameTime()
                    || other.alignmentRetryUntil > level.getGameTime())
                continue;
            UUID id = UUID.randomUUID();
            CouplingAlignment.Session attempt = CouplingAlignment.begin(physicsEndpoint(), other.physicsEndpoint(), mode,
                interfaceMode == CouplingInterfaceMode.FREE);
            if (attempt == null)
                continue;

            alignment = other.alignment = attempt;
            savedReference = other.savedReference = null;
            connectionEstablished = other.connectionEstablished = false;
            pair = id;
            partner = other.endpoint;
            other.pair = id;
            other.partner = endpoint;
            other.mode = mode;
            other.lowerAngle = lowerAngle;
            other.upperAngle = upperAngle;
            other.flipRange = flipRange;
            searching = false;
            pendingTicks = 0;
            previousPartner = null;
            notifyUpdate();
            other.notifyUpdate();
            break;
        }
    }

    private void cancelAlignment() {
        BallCouplingBlockEntity other = partner();
        alignmentRetryUntil = level.getGameTime() + 40;
        if (other != null) other.alignmentRetryUntil = other.level.getGameTime() + 40;
        disconnect();
    }

    private void setActive(boolean active) {
        boolean changed = active() != active;
        setStateProperty(BallCouplingBlock.CONNECTED, active);
        if (changed && level != null && !level.isClientSide) notifyUpdate();
    }

    private void setPoweredState(boolean powered) {
        setStateProperty(BallCouplingBlock.POWERED, powered);
    }

    private void setStateProperty(BooleanProperty property, boolean value) {
        if (level == null)
            return;
        BlockState state = getBlockState();
        if (state.getValue(property) != value)
            level.setBlock(worldPosition, state.setValue(property, value), Block.UPDATE_CLIENTS);
    }

    public void disconnect() {
        if (level == null || level.isClientSide)
            return;
        if (!StressCouplingCompatibility.enabled()) {
            clearDisabledConnection();
            return;
        }

        BallCouplingBlockEntity other = partner();
        if (other != null && interfaceMode.accepts(other.interfaceMode) && !ownsConnection(other)
                && endpoint.equals(other.partner) && Objects.equals(pair, other.pair)) {
            other.disconnect();
            return;
        }

        CouplingKinetics.disconnect(this);
        if (alignment != null) { alignment.close(); alignment = null; }
        CouplingPhysics.remove(constraint);
        constraint = null;
        if (partner != null && other == null)
            CouplingSavedData.get(level.getServer()).invalidate(pair);
        previousPartner = partner;
        partner = null;
        pair = null;
        savedReference = null;
        searching = false;
        pendingTicks = 0;
        connectionEstablished = false;
        setActive(false);
        beginSearch(true);
        notifyUpdate();

        if (other != null && endpoint.equals(other.partner)) {
            if (other.alignment != null) { other.alignment.close(); other.alignment = null; }
            CouplingPhysics.remove(other.constraint);
            other.constraint = null;
            other.partner = null;
            other.pair = null;
            other.savedReference = null;
            other.searching = false;
            other.connectionEstablished = false;
            other.setActive(false);
            other.beginSearch(true);
            other.notifyUpdate();
            BallCouplingIndex.of(worldLevel()).wakeNearby(other);
        }
    }

    public void disconnectFromGui() {
        if (level == null || level.isClientSide || partner == null)
            return;
        BallCouplingBlockEntity other = partner();
        long until = level.getGameTime() + 100;
        guiPairCooldownUntil = until;
        previousPartner = partner;
        setChanged();
        if (other != null) {
            other.guiPairCooldownUntil = other.level == null ? until : other.level.getGameTime() + 100;
            other.previousPartner = endpoint;
            other.setChanged();
        }
        disconnect();
    }

    public void cycleLength(Player player) {
        if (!StressCouplingCompatibility.enabled()) {
            StressCouplingCompatibility.notifyPlayer(player);
            return;
        }
        if (partner != null) {
            if (player != null)
                player.displayClientMessage(Component.translatable("ball_coupling.connected_length"), true);
            return;
        }
        level.setBlock(worldPosition, getBlockState().cycle(BallCouplingBlock.LENGTH), Block.UPDATE_ALL);
    }

    public void setMode(int value) {
        applySettings(value, interfaceMode.ordinal(), lowerAngle, upperAngle, flipRange);
    }

    public void applySettings(int value, int role, int lower, int upper, boolean flip) {
        if (!StressCouplingCompatibility.enabled()) return;
        if (level == null || level.isClientSide) return;
        if (value < 0 || value > 2 || role < 0 || role > 2 || lower < -180 || upper > 180 || lower > upper)
            return;
        if (mode == value && interfaceMode.ordinal() == role && lowerAngle == lower && upperAngle == upper
                && flipRange == flip)
            return;
        if (interfaceMode.ordinal() != role || alignment != null) disconnect();
        mode = value;
        interfaceMode = CouplingInterfaceMode.values()[role];
        lowerAngle = lower;
        upperAngle = upper;
        flipRange = flip;
        BallCouplingBlockEntity other = partner();
        if (other != null && !ownsConnection(other))
            other.applySettings(value, other.interfaceMode.ordinal(), lower, upper, flip);
        if (other != null && ownsConnection(other)) {
            other.mode = mode;
            other.lowerAngle = lowerAngle;
            other.upperAngle = upperAngle;
            other.flipRange = flipRange;
            other.notifyUpdate();
            CouplingKinetics.disconnect(this);
            Object updated = createConstraint(other, pair);
            if (updated == null) {
                disconnect();
            } else {
                constraint = updated;
            }
            setActive(false);
            other.setActive(false);
        }
        searching = false;
        beginSearch(true);
        BallCouplingIndex.of(worldLevel()).update(this);
        BallCouplingIndex.of(worldLevel()).wakeNearby(this);
        notifyUpdate();
    }

    @Override
    public float getGeneratedSpeed() {
        return CouplingKinetics.generated(this);
    }

    @Override
    public float calculateAddedStressCapacity() {
        return 0;
    }

    @Override
    public float calculateStressApplied() {
        return 0;
    }

    @Override
    public void invalidate() {
        if (StressCouplingCompatibility.enabled() && level != null && !level.isClientSide) {
            if (alignment != null) disconnect();
            CouplingKinetics.disconnect(this);
            if (constraint != null) savedReference = constraint;
            CouplingPhysics.remove(constraint);
            constraint = null;
            BallCouplingBlockEntity other = partner();
            if (other != null) {
                if (other.constraint != null) other.savedReference = other.constraint;
                CouplingPhysics.remove(other.constraint);
                other.constraint = null;
                other.setActive(false);
            }
            BallCouplingIndex.of(worldLevel()).remove(this);
        }
        super.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        // Virtual kinetic sources are reconstructed only after the physical joint is active.
        if (!packet && CouplingKinetics.generated(this) != 0) {
            tag.remove("Network");
            tag.remove("Source");
            tag.putFloat("Speed", 0);
        }
        tag.putUUID("CouplingEndpoint", endpoint);
        if (partner != null)
            tag.putUUID("CouplingPartner", partner);
        if (pair != null)
            tag.putUUID("CouplingPair", pair);
        tag.putInt("CouplingMode", mode);
        tag.putBoolean("CouplingEstablished", connectionEstablished);
        // An interrupted attraction is retried from search; it must never restore as a hard joint.
        tag.putBoolean("CouplingAligning", alignment != null);
        if (!packet && guiPairCooldownUntil > 0)
            tag.putLong("CouplingGuiPairCooldown", guiPairCooldownUntil);
        if (!packet && pair != null)
            tag.put("CouplingReference", CouplingPhysics.saveReference(constraint != null ? constraint : savedReference));
        CompoundTag config = new CompoundTag();
        config.putInt("Version", 1);
        config.putInt("Interface", interfaceMode.ordinal());
        config.putInt("Lower", lowerAngle);
        config.putInt("Upper", upperAngle);
        config.putBoolean("FlipRange", flipRange);
        tag.put("CouplingConfig", config);
        if (packet) {
            BallCouplingBlockEntity other = partner();
            if (other != null) {
                Vec3 anchor = other.localAnchor();
                Vec3 normal = Vec3.atLowerCornerOf(other.getBlockState().getValue(BallCouplingBlock.FACING).getNormal());
                CompoundTag link = new CompoundTag();
                link.putDouble("X", anchor.x); link.putDouble("Y", anchor.y); link.putDouble("Z", anchor.z);
                link.putDouble("NX", normal.x); link.putDouble("NY", normal.y); link.putDouble("NZ", normal.z);
                UUID body = SableStructureCompat.containingSubLevelId(other);
                if (body != null) link.putUUID("Body", body);
                tag.put("RenderLink", link);
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        boolean enabled = StressCouplingCompatibility.enabled();
        if (!enabled) {
            // Disabled compatibility keeps settings but discards runtime-only connection state.
            tag = tag.copy();
            tag.remove("Network");
            tag.remove("Source");
            tag.putFloat("Speed", 0);
            tag.remove("CouplingPartner");
            tag.remove("CouplingPair");
            tag.remove("RenderLink");
        }
        super.read(tag, registries, packet);
        if (tag.hasUUID("CouplingEndpoint"))
            endpoint = tag.getUUID("CouplingEndpoint");
        partner = tag.hasUUID("CouplingPartner") ? tag.getUUID("CouplingPartner") : null;
        pair = tag.hasUUID("CouplingPair") ? tag.getUUID("CouplingPair") : null;
        mode = Math.clamp(tag.getInt("CouplingMode"), 0, 2);
        connectionEstablished = pair != null && tag.getBoolean("CouplingEstablished");
        if (!packet && tag.getBoolean("CouplingAligning")) {
            partner = null;
            pair = null;
            connectionEstablished = false;
        }
        if (!packet)
            guiPairCooldownUntil = tag.getLong("CouplingGuiPairCooldown");
        if (!packet)
            savedReference = pair == null ? null : CouplingPhysics.restoreReference(tag.getCompound("CouplingReference"));
        if (tag.contains("CouplingConfig")) {
            CompoundTag config = tag.getCompound("CouplingConfig");
            interfaceMode = CouplingInterfaceMode.values()[Math.clamp(config.getInt("Interface"), 0, 2)];
            lowerAngle = Math.clamp(config.getInt("Lower"), -180, 180);
            upperAngle = Math.clamp(config.getInt("Upper"), lowerAngle, 180);
            flipRange = config.getBoolean("FlipRange");
        }
        if (packet) {
            renderPartnerAnchor = null;
            renderPartnerNormal = null;
            renderPartnerBody = null;
            if (tag.contains("RenderLink")) {
                CompoundTag link = tag.getCompound("RenderLink");
                renderPartnerAnchor = new Vec3(link.getDouble("X"), link.getDouble("Y"), link.getDouble("Z"));
                renderPartnerNormal = new Vec3(link.getDouble("NX"), link.getDouble("NY"), link.getDouble("NZ"));
                renderPartnerBody = link.hasUUID("Body") ? link.getUUID("Body") : null;
            }
        }
        searching = false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.createfirefightingadd.stress_coupling");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return StressCouplingCompatibility.enabled() ? new BallCouplingMenu(id, inventory, this) : null;
    }

    private void clearDisabledConnection() {
        boolean changed = partner != null || pair != null || searching || connectionEstablished
            || getBlockState().getValue(BallCouplingBlock.CONNECTED);
        partner = pair = previousPartner = null;
        savedReference = null;
        searching = connectionEstablished = false;
        if (alignment != null) { alignment.close(); alignment = null; }
        if (constraint != null) { CouplingPhysics.remove(constraint); constraint = null; }
        CouplingKinetics.disconnect(this);
        if (changed) {
            setStateProperty(BallCouplingBlock.CONNECTED, false);
            notifyUpdate();
        }
    }
}
