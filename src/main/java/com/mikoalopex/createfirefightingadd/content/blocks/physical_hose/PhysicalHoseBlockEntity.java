package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.List;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Keeps endpoint and strand data readable when the optional physics dependency is absent. */
public class PhysicalHoseBlockEntity extends FireHoseBlockEntity {
    private CompoundTag dormantStrand = new CompoundTag();

    public PhysicalHoseBlockEntity(BlockPos pos, BlockState state) { super(PhysicalHoses.BLOCK_ENTITY.get(), pos, state); }
    public int width() { return getBlockState().getValue(PhysicalHoseBlock.SIZE).width; }
    public void cycleSize() {
        if (level == null || level.isClientSide || !PhysicalHoses.enabled()) return;
        var other = getPairedHose();
        if (attached() && !(other instanceof PhysicalHoseBlockEntity)) return;
        var size = getBlockState().getValue(PhysicalHoseBlock.SIZE).next();
        setSize(size);
        if (other instanceof PhysicalHoseBlockEntity hose) hose.setSize(size);
    }

    public void setSize(PhysicalHoseBlock.Size size) {
        if (level == null || level.isClientSide) return;
        level.setBlock(worldPosition, getBlockState().setValue(PhysicalHoseBlock.SIZE, size), 3);
        notifyUpdate();
    }

    public record EndFrame(Vec3 point, Vec3 normal, Vec3 up) {}
    public EndFrame renderEnd(boolean start) { return null; }
    public boolean connectPhysical(PhysicalHoseBlockEntity target) { return false; }
    public void disconnectPhysical() {}
    public boolean attached() { return dormantStrand.hasUUID("HasRopeAttached"); }
    public List<Vec3> renderPoints(float partial) { return List.of(); }
    protected boolean hasPhysicsBackend() { return false; }
    @Override public boolean allowsHoseConnection() { return false; }
    @Override protected boolean transportEnabled() { return false; }

    @Override protected boolean prepareTransportTick() {
        if (level == null || level.isClientSide) return false;
        if (transportEnabled()) return true;
        updateDisconnectedEndpoint();
        return false;
    }

    @Override public void remove() {
        // Native holder destruction handles both ends; ordinary hose routing is not involved.
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        if (!hasPhysicsBackend()) {
            dormantStrand = new CompoundTag();
            for (String key : new String[] {"OwnStrand", "HasRopeAttached", "Strand"})
                if (tag.contains(key)) dormantStrand.put(key, tag.get(key).copy());
        }
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        if (!hasPhysicsBackend())
            for (String key : dormantStrand.getAllKeys())
                if (!packet || !key.equals("Strand")) tag.put(key, dormantStrand.get(key).copy());
    }
}
