package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;
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
import net.neoforged.fml.ModList;

public class BallCouplingBlockEntity extends KineticBlockEntity implements MenuProvider {
    /** Centers of the named ball element in each top model, measured in model pixels. */
    private static final double[] BALL_CENTERS = {5, 9, 13, 17, 20};

    private UUID endpoint = UUID.randomUUID();
    private UUID partner;
    private UUID pair;
    private UUID previousPartner;
    private Object constraint;
    private Object savedReference;
    private boolean searching;
    private boolean powered;
    private int searchTicks;
    private int nextSearch;
    private int pendingTicks;
    private boolean connectionEstablished;
    private int mode;
    private CouplingInterfaceMode interfaceMode;
    private int lowerAngle = -45;
    private int upperAngle = 45;
    private Vec3 renderPartnerAnchor;
    private Vec3 renderPartnerNormal;
    private UUID renderPartnerBody;

    public BallCouplingBlockEntity(BlockPos pos, BlockState state) {
        super(BallCouplings.BLOCK_ENTITY.get(), pos, state);
        interfaceMode = ((BallCouplingBlock) state.getBlock()).isTop()
            ? CouplingInterfaceMode.PASSIVE : CouplingInterfaceMode.ACTIVE;
    }

    public static BallCouplingBlockEntity create(BlockPos pos, BlockState state) {
        if (ModList.get().isLoaded("sable"))
            return new com.mikoalopex.createfirefightingadd.integration.sable.SableBallCouplingBlockEntity(pos, state);
        return new BallCouplingBlockEntity(pos, state);
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
    public Vec3 renderPartnerAnchor() { return renderPartnerAnchor; }
    public Vec3 renderPartnerNormal() { return renderPartnerNormal; }
    public UUID renderPartnerBody() { return renderPartnerBody; }

    boolean searches() { return interfaceMode != CouplingInterfaceMode.PASSIVE; }

    boolean ownsConnection(BallCouplingBlockEntity other) {
        return interfaceMode == CouplingInterfaceMode.ACTIVE
            || interfaceMode == CouplingInterfaceMode.FREE && endpoint.compareTo(other.endpoint) < 0;
    }

    private CouplingPhysics.Endpoint physicsEndpoint() {
        Vec3 p = localAnchor(), n = worldNormal();
        return new CouplingPhysics.Endpoint(worldLevel().dimension(), SableStructureCompat.containingSubLevelId(this),
            new org.joml.Vector3d(p.x, p.y, p.z), new org.joml.Vector3d(n.x, n.y, n.z));
    }

    private Object createConstraint(BallCouplingBlockEntity other, UUID id) {
        return CouplingPhysics.update(constraint != null ? constraint : savedReference, new CouplingPhysics.Request(
            com.mikoalopex.createfirefightingadd.CreateFireFightingAdd.path("ball_coupling"), id,
            physicsEndpoint(), other.physicsEndpoint(), mode, lowerAngle, upperAngle));
    }

    public int status() {
        if (!CouplingPhysics.available())
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

    public BallCouplingBlockEntity partner() {
        return level == null || level.isClientSide || partner == null
            ? null
            : BallCouplingIndex.of(worldLevel()).find(partner);
    }

    public boolean active() {
        return getBlockState().getValue(BallCouplingBlock.CONNECTED);
    }

    public boolean powered() {
        return powered;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide)
            return;

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

        boolean next = level.hasNeighborSignal(worldPosition);
        boolean changed = next != powered;
        powered = next;
        setPoweredState(next);

        if (!next && !(partner != null && interfaceMode == CouplingInterfaceMode.PASSIVE)) {
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
        if (!searches() || !powered || partner != null)
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

        BallCouplingIndex.of(worldLevel()).update(this);
        if (pair != null && CouplingSavedData.get(level.getServer()).consume(pair)) {
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

    private void searchForPartner() {
        if (!powered || !searching || !CouplingPhysics.available())
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
            if (!other.powered || other.partner != null || !ownsConnection(other))
                continue;
            UUID id = UUID.randomUUID();
            Object created = createConstraint(other, id);
            if (created == null)
                continue;

            constraint = created;
            connectionEstablished = other.connectionEstablished = false;
            pair = id;
            partner = other.endpoint;
            other.pair = id;
            other.partner = endpoint;
            other.mode = mode;
            other.lowerAngle = lowerAngle;
            other.upperAngle = upperAngle;
            searching = false;
            pendingTicks = 0;
            previousPartner = null;
            notifyUpdate();
            other.notifyUpdate();
            break;
        }
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

        BallCouplingBlockEntity other = partner();
        if (other != null && interfaceMode.accepts(other.interfaceMode) && !ownsConnection(other)
                && endpoint.equals(other.partner) && Objects.equals(pair, other.pair)) {
            other.disconnect();
            return;
        }

        CouplingKinetics.disconnect(this);
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

    public void cycleLength(Player player) {
        if (partner != null) {
            if (player != null)
                player.displayClientMessage(Component.translatable("ball_coupling.connected_length"), true);
            return;
        }
        level.setBlock(worldPosition, getBlockState().cycle(BallCouplingBlock.LENGTH), Block.UPDATE_ALL);
    }

    public void setMode(int value) {
        applySettings(value, interfaceMode.ordinal(), lowerAngle, upperAngle);
    }

    public void applySettings(int value, int role, int lower, int upper) {
        if (level == null || level.isClientSide) return;
        if (value < 0 || value > 2 || role < 0 || role > 2 || lower < -180 || upper > 180 || lower > upper)
            return;
        if (mode == value && interfaceMode.ordinal() == role && lowerAngle == lower && upperAngle == upper)
            return;
        if (interfaceMode.ordinal() != role) disconnect();
        mode = value;
        interfaceMode = CouplingInterfaceMode.values()[role];
        lowerAngle = lower;
        upperAngle = upper;
        BallCouplingBlockEntity other = partner();
        if (other != null && !ownsConnection(other))
            other.applySettings(value, other.interfaceMode.ordinal(), lower, upper);
        if (other != null && ownsConnection(other)) {
            other.mode = mode;
            other.lowerAngle = lowerAngle;
            other.upperAngle = upperAngle;
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
        if (level != null && !level.isClientSide) {
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
        if (!packet && pair != null)
            tag.put("CouplingReference", CouplingPhysics.saveReference(constraint != null ? constraint : savedReference));
        CompoundTag config = new CompoundTag();
        config.putInt("Version", 1);
        config.putInt("Interface", interfaceMode.ordinal());
        config.putInt("Lower", lowerAngle);
        config.putInt("Upper", upperAngle);
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
        super.read(tag, registries, packet);
        if (tag.hasUUID("CouplingEndpoint"))
            endpoint = tag.getUUID("CouplingEndpoint");
        partner = tag.hasUUID("CouplingPartner") ? tag.getUUID("CouplingPartner") : null;
        pair = tag.hasUUID("CouplingPair") ? tag.getUUID("CouplingPair") : null;
        mode = Math.clamp(tag.getInt("CouplingMode"), 0, 2);
        connectionEstablished = pair != null && tag.getBoolean("CouplingEstablished");
        if (!packet)
            savedReference = pair == null ? null : CouplingPhysics.restoreReference(tag.getCompound("CouplingReference"));
        if (tag.contains("CouplingConfig")) {
            CompoundTag config = tag.getCompound("CouplingConfig");
            interfaceMode = CouplingInterfaceMode.values()[Math.clamp(config.getInt("Interface"), 0, 2)];
            lowerAngle = Math.clamp(config.getInt("Lower"), -180, 180);
            upperAngle = Math.clamp(config.getInt("Upper"), lowerAngle, 180);
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
        return Component.translatable("block.createfirefightingadd.ball_coupling_base");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BallCouplingMenu(id, inventory, this);
    }
}
