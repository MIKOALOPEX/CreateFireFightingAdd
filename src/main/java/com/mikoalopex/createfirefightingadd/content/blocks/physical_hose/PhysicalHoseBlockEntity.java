package com.mikoalopex.createfirefightingadd.content.blocks.physical_hose;

import java.util.List;
import com.mikoalopex.createfirefightingadd.content.blocks.fire_hose.FireHoseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Stores physical hose endpoints and bracket bindings independently of the
 * optional physics backend.
 */
public class PhysicalHoseBlockEntity extends FireHoseBlockEntity {
    public final java.util.List<PhysicalHoseAnchors.Binding> anchors = new java.util.ArrayList<>();
    public java.util.UUID bracketStrand;
    public double bracketFirstLength = 1;
    protected boolean anchorsDirty;
    public PhysicalHoseAnchors.Snapshot anchorSnapshot() { return null; }
    public boolean rebuildAnchors() { return false; }
    public void releaseAnchor(java.util.UUID node) {
        if (anchors.removeIf(binding -> binding.node().equals(node))) {
            anchorsDirty = !rebuildAnchors(); notifyUpdate();
        }
    }
    public void clearAnchors() {
        var old = java.util.List.copyOf(anchors);
        anchors.clear();
        for (var binding : old) {
            var bracket = binding.resolve(level);
            if (bracket != null) level.destroyBlock(binding.pos(), true);
        }
        notifyUpdate();
    }
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
        bracketStrand = tag.hasUUID("BracketStrand") ? tag.getUUID("BracketStrand") : null;
        bracketFirstLength = tag.contains("BracketFirstLength") ? tag.getDouble("BracketFirstLength") : 1;
        anchors.clear();
        for (var entry : tag.getList("PhysicalAnchors", net.minecraft.nbt.Tag.TAG_COMPOUND))
            anchors.add(PhysicalHoseAnchors.Binding.read((CompoundTag) entry));
        if (!hasPhysicsBackend()) {
            dormantStrand = new CompoundTag();
            for (String key : new String[] {"OwnStrand", "HasRopeAttached", "Strand"})
                if (tag.contains(key)) dormantStrand.put(key, tag.get(key).copy());
        }
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        if (bracketStrand != null) tag.putUUID("BracketStrand", bracketStrand);
        tag.putDouble("BracketFirstLength", bracketFirstLength);
        var list = new net.minecraft.nbt.ListTag();
        for (var binding : anchors) list.add(binding.write(level));
        tag.put("PhysicalAnchors", list);
        if (!hasPhysicsBackend())
            for (String key : dormantStrand.getAllKeys())
                if (!packet || !key.equals("Strand")) tag.put(key, dormantStrand.get(key).copy());
    }
}
