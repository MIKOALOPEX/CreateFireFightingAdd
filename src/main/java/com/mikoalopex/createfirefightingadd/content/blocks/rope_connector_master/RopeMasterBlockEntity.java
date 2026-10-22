package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import java.util.List;
import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseConnectorMode;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Stores inventory and redstone requests; delegates rope creation and recovery to the transaction service. */
public class RopeMasterBlockEntity extends SmartBlockEntity implements MenuProvider, IHaveGoggleInformation {
    private static final int SUCCESS_LIME = 0x70FF33;
    private static final int FAILURE_RED = 0xFF5555;
    final SimpleContainer inventory = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return RopeMasterCompatibility.accepts(stack); }
        @Override public void setChanged() { super.setChanged(); RopeMasterBlockEntity.this.setChanged(); }
    };
    private ScrollOptionBehaviour<FireHoseConnectorMode> mode;
    private UUID identity = UUID.randomUUID();
    private BlockPos cachedPartner;
    private UUID cachedStructure;
    private RopeMasterTransactions.Request pending;
    private boolean lastSignal;
    private String failure = "";
    private long highlightUntil;
    private int highlightColor = 0x55aaff;
    private BlockPos claimed;
    private boolean moving;
    private UUID managedRope;
    private boolean selected;
    private ListTag unresolvedInventory;
    private final IItemHandler itemHandler = new InvWrapper(inventory);

    public IItemHandler getItemHandler() { return itemHandler; }

    public RopeMasterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    public RopeMasterBlockEntity(BlockPos pos, BlockState state) {
        this(CreateFireFightingAdd.ROPE_MASTER_BE.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(FireHoseConnectorMode.class,
            Component.translatable("createfirefightingadd.fire_hose_connector.mode"), this, new DialTransform());
        behaviours.add(mode);
    }

    public BlockPos endpointPos() { return worldPosition.relative(getBlockState().getValue(RopeMasterBlock.FACING)); }
    UUID identity() { return identity; }
    UUID managedRope() { return managedRope; }
    boolean hasSelection() { return selected; }
    void select(UUID rope) { managedRope = rope; selected = true; setChanged(); }
    FireHoseConnectorMode mode() { return mode.get(); }

    public void sampleSignal() {
        if (!RopeMasterCompatibility.available()) return;
        if (!(level instanceof ServerLevel) || moving) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (getBlockState().getValue(RopeMasterBlock.POWERED) != powered)
            level.setBlock(worldPosition, getBlockState().setValue(RopeMasterBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        refreshClaim();
        boolean rising = powered && !lastSignal;
        lastSignal = powered;
        if (rising && pending == null) pending = RopeMasterTransactions.capture(this);
        setChanged();
    }

    @Override public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) sampleSignal();
    }

    @Override public void tick() {
        if (!RopeMasterCompatibility.available()) { pending = null; return; }
        super.tick();
        if (level == null) return;
        if (!level.isClientSide && !moving) {
            // Some redstone providers omit neighbour notifications; this checks only the six adjacent faces.
            if (level.hasNeighborSignal(worldPosition) != lastSignal
                    || getBlockState().getValue(RopeMasterBlock.POWERED) != lastSignal) sampleSignal();
            if (pending != null && RopeMasterTransactions.takeBudget((ServerLevel) level)) {
                var request = pending;
                pending = null;
                RopeMasterTransactions.execute(this, request);
            }
        }
    }

    void remember(BlockPos partner) {
        cachedPartner = partner.immutable();
        cachedStructure = SableStructureCompat.containingSubLevelId(level, partner);
        setChanged();
    }

    BlockPos cachedPartner() {
        if (cachedPartner == null || !level.isLoaded(cachedPartner)) return null;
        return java.util.Objects.equals(cachedStructure, SableStructureCompat.containingSubLevelId(level, cachedPartner))
            ? cachedPartner : null;
    }

    void fail(String reason) {
        failure = reason;
        highlightColor = FAILURE_RED;
        boolean audible = highlightUntil <= level.getGameTime() + 30;
        highlightUntil = level.getGameTime() + 40;
        highlight(false);
        if (audible) AllSoundEvents.DENY.playOnServer(level, worldPosition, 0.5f, 1);
        notifyUpdate();
    }

    void success() {
        failure = "";
        highlightColor = SUCCESS_LIME;
        highlightUntil = level.getGameTime() + 40;
        highlight(true);
        notifyUpdate();
    }

    void highlight(boolean successful) {
        if (!(SableStructureCompat.worldLevel(this) instanceof ServerLevel server)) return;
        Vec3 center = SableStructureCompat.transformPositionToWorld(this, endpointPos().getCenter());
        int color = successful ? SUCCESS_LIME : FAILURE_RED;
        // Show connector feedback around the local endpoint.
        DustParticleOptions particle = new DustParticleOptions(new net.createmod.catnip.theme.Color(color).asVectorF(), 1);
        for (int i = 0; i < 24; i++) {
            double ox = (server.random.nextDouble() - 0.5) * 0.9;
            double oy = (server.random.nextDouble() - 0.5) * 0.9;
            double oz = (server.random.nextDouble() - 0.5) * 0.9;
            server.sendParticles(particle, center.x + ox, center.y + oy, center.z + oz, 1, 0, 0, 0, 0);
        }
        var packet = new RopeMasterHighlightPacket(endpointPos(), color);
        var recipients = server.getPlayers(player -> player.distanceToSqr(center) < 64 * 64);
        for (var player : recipients)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }

    void refund(ItemStack stack) {
        ItemStack remainder = inventory.addItem(stack.copy());
        if (!remainder.isEmpty()) drop(remainder);
    }

    private void drop(ItemStack stack) {
        Vec3 world = SableStructureCompat.projectToWorld(level, worldPosition.getCenter());
        level.addFreshEntity(new ItemEntity(level, world.x, world.y, world.z, stack));
    }

    public void dropInventory() {
        if (level == null || level.isClientSide || moving) return;
        releaseClaim();
        ItemStack stack = inventory.removeItemNoUpdate(0);
        if (!stack.isEmpty()) drop(stack);
    }

    public void beginMove() { moving = true; releaseClaim(); pending = null; }
    public void finishMove() { moving = false; refreshClaim(); }

    void refreshClaim() {
        if (!RopeMasterCompatibility.available()) return;
        if (claimed != null && !claimed.equals(endpointPos())) {
            releaseClaim();
            selected = false;
            managedRope = null;
            cachedPartner = null;
            cachedStructure = null;
            pending = null;
            setChanged();
        }
        if (!level.isLoaded(endpointPos())) return;
        BlockEntity endpoint = level.getBlockEntity(endpointPos());
        if (endpoint == null || RopeMasterCompatibility.adapters().stream().noneMatch(a -> a.supports(endpoint))) return;
        CompoundTag tag = endpoint.getPersistentData().getCompound(RopeMasterTransactions.CLAIM);
        if (tag.hasUUID("Manager") && tag.getUUID("Manager").equals(identity)
                && tag.getLong("Position") == worldPosition.asLong()) {
            boolean restored = claimed == null;
            claimed = endpointPos();
            if (restored) {
                highlightColor = managedRope != null ? SUCCESS_LIME : 0x55aaff;
                highlightUntil = level.getGameTime() + 40;
                notifyUpdate();
            }
            return;
        }
        tag.putUUID("Manager", identity);
        tag.putLong("Position", worldPosition.asLong());
        endpoint.getPersistentData().put(RopeMasterTransactions.CLAIM, tag);
        endpoint.setChanged();
        claimed = endpointPos();
        highlightColor = 0x55aaff;
        highlightUntil = level.getGameTime() + 40;
        notifyUpdate();
    }

    private void releaseClaim() {
        if (claimed != null && level != null && level.isLoaded(claimed)) {
            BlockEntity endpoint = level.getBlockEntity(claimed);
            if (endpoint != null) {
                CompoundTag tag = endpoint.getPersistentData().getCompound(RopeMasterTransactions.CLAIM);
                if (tag.hasUUID("Manager") && tag.getUUID("Manager").equals(identity)) {
                    endpoint.getPersistentData().remove(RopeMasterTransactions.CLAIM);
                    endpoint.setChanged();
                }
            }
        }
        claimed = null;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("Identity", identity);
        tag.putBoolean("Selected", selected);
        if (managedRope != null) tag.putUUID("ManagedRope", managedRope);
        tag.put("Inventory", unresolvedInventory != null && inventory.isEmpty()
            ? unresolvedInventory.copy() : inventory.createTag(registries));
        tag.putBoolean("LastSignal", lastSignal);
        tag.putString("Failure", failure);
        tag.putLong("HighlightUntil", highlightUntil);
        tag.putInt("HighlightColor", highlightColor);
        if (claimed != null) tag.putLong("ClaimedEndpoint", claimed.asLong());
        if (cachedPartner != null)
            SableStructureCompat.writeLinkedBlock(tag, "Partner", "PartnerStructure", cachedPartner, cachedStructure);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("Identity")) identity = tag.getUUID("Identity");
        selected = tag.getBoolean("Selected");
        managedRope = tag.hasUUID("ManagedRope") ? tag.getUUID("ManagedRope") : null;
        inventory.removeAllItems();
        ListTag savedInventory = tag.getList("Inventory", 10);
        inventory.fromTag(savedInventory, registries);
        // Retain unresolved item data without restoring stacks already extracted.
        unresolvedInventory = !RopeMasterCompatibility.available() && inventory.isEmpty() && !savedInventory.isEmpty()
            ? savedInventory.copy() : null;
        lastSignal = tag.getBoolean("LastSignal");
        failure = tag.getString("Failure");
        highlightUntil = tag.getLong("HighlightUntil");
        highlightColor = tag.contains("HighlightColor") ? tag.getInt("HighlightColor") : FAILURE_RED;
        if (clientPacket) claimed = tag.contains("ClaimedEndpoint") ? BlockPos.of(tag.getLong("ClaimedEndpoint")) : null;
        var partner = SableStructureCompat.readLinkedBlock(tag, "Partner", "PartnerStructure");
        cachedPartner = partner.pos();
        cachedStructure = partner.subLevelId();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.createfirefightingadd.rope_connector_master"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RopeMasterMenu(id, inventory, this);
    }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        if (failure.isEmpty()) return false;
        tooltip.add(Component.translatable("createfirefightingadd.rope_master." + failure));
        return true;
    }

    private static final class DialTransform extends ValueBoxTransform.Sided {
        DialTransform() { scale = 0.375f; }
        @Override protected Vec3 getSouthLocation() { return Vec3.ZERO; }
        @Override protected boolean isSideActive(BlockState state, Direction side) {
            Direction facing = state.getValue(RopeMasterBlock.FACING);
            return side.getAxis() == (facing.getAxis().isVertical() ? Direction.Axis.Z : Direction.Axis.Y);
        }
        @Override public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.getValue(RopeMasterBlock.FACING);
            // The model's switch faces are centered at (8, 12.5, 10) and (8, 3.5, 10) when facing north.
            return new Vec3(0.5, 0.5, 0.5).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-2 / 16d))
                .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(4.5 / 16d + 0.002));
        }
    }
}
