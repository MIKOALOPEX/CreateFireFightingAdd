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
    private boolean searching;
    private boolean powered;
    private int searchTicks;
    private int nextSearch;
    private int pendingTicks;
    private int mode;

    public BallCouplingBlockEntity(BlockPos pos, BlockState state) {
        super(BallCouplings.BLOCK_ENTITY.get(), pos, state);
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
        double offset = isTop()
            ? (BALL_CENTERS[getBlockState().getValue(BallCouplingBlock.LENGTH)] - 8) / 16
            : 0;
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
            searching = false;
            setActive(false);
            setChanged();
        }

        BallCouplingIndex.of(worldLevel()).update(this);
        powered = level.hasNeighborSignal(worldPosition);
        setPoweredState(powered);
        if (powered && partner == null) {
            if (isTop())
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

        if (!next) {
            if (partner != null || searching)
                disconnect();
            return;
        }

        if (partner != null) {
            BallCouplingBlockEntity other = partner();
            if (other != null && !other.powered)
                disconnect();
            return;
        }

        if (isTop()) {
            if (changed)
                BallCouplingIndex.of(worldLevel()).wakeNearby(this);
        } else {
            beginSearch(changed);
        }
    }

    private void beginSearch(boolean resetBackoff) {
        if (isTop() || !powered || partner != null)
            return;
        searching = true;
        if (resetBackoff) {
            searchTicks = 0;
            nextSearch = 0;
        }
        setChanged();
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
            setActive(false);
            beginSearch(true);
            notifyUpdate();
        }

        updateSignal();
        if (isTop())
            return;

        if (partner != null) {
            maintainConnection();
            return;
        }

        searchForPartner();
    }

    private void maintainConnection() {
        BallCouplingBlockEntity other = partner();
        if (other == null) {
            setActive(false);
            CouplingKinetics.disconnect(this);
            CouplingPhysics.remove(constraint);
            constraint = null;
            return;
        }
        if (!powered || !other.powered || !endpoint.equals(other.partner) || !Objects.equals(pair, other.pair)) {
            disconnect();
            return;
        }

        if (constraint == null)
            constraint = CouplingPhysics.connect(this, other, pair, mode);
        boolean ready = CouplingPhysics.active(constraint);
        setActive(ready);
        other.setActive(ready);
        if (ready) {
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
            if (!other.powered || other.partner != null)
                continue;
            UUID id = UUID.randomUUID();
            Object created = CouplingPhysics.connect(this, other, id, mode);
            if (created == null)
                continue;

            constraint = created;
            pair = id;
            partner = other.endpoint;
            other.pair = id;
            other.partner = endpoint;
            searching = false;
            pendingTicks = 0;
            previousPartner = null;
            notifyUpdate();
            other.notifyUpdate();
            break;
        }
    }

    private void setActive(boolean active) {
        setStateProperty(BallCouplingBlock.CONNECTED, active);
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
        if (isTop() && other != null && endpoint.equals(other.partner)) {
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
        searching = false;
        pendingTicks = 0;
        setActive(false);
        beginSearch(true);
        notifyUpdate();

        if (other != null && endpoint.equals(other.partner)) {
            other.partner = null;
            other.pair = null;
            other.searching = false;
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
        if (value < 0 || value > 2 || value == mode)
            return;
        BallCouplingBlockEntity other = partner();
        if (other != null) {
            Object replaced = CouplingPhysics.connect(this, other, pair, value);
            if (replaced == null)
                return;
            constraint = replaced;
            CouplingKinetics.disconnect(this);
            setActive(false);
            other.setActive(false);
        }
        mode = value;
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
            CouplingPhysics.remove(constraint);
            constraint = null;
            BallCouplingBlockEntity other = partner();
            if (other != null) {
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
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        if (tag.hasUUID("CouplingEndpoint"))
            endpoint = tag.getUUID("CouplingEndpoint");
        partner = tag.hasUUID("CouplingPartner") ? tag.getUUID("CouplingPartner") : null;
        pair = tag.hasUUID("CouplingPair") ? tag.getUUID("CouplingPair") : null;
        mode = Math.clamp(tag.getInt("CouplingMode"), 0, 2);
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
