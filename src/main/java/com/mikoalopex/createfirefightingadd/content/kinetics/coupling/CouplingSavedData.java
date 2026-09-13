package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers a disconnect until an unloaded partner has observed it. */
final class CouplingSavedData extends SavedData {
    private final Set<UUID> disconnected = new HashSet<>();
    static CouplingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CouplingSavedData::new,
            (tag, registries) -> load(tag)), "createfirefightingadd_couplings");
    }
    void invalidate(UUID pair) { if (pair != null && disconnected.add(pair)) setDirty(); }
    boolean consume(UUID pair) {
        if (pair != null && disconnected.remove(pair)) { setDirty(); return true; }
        return false;
    }
    private static CouplingSavedData load(CompoundTag tag) {
        var data = new CouplingSavedData();
        for (var entry : tag.getList("Disconnected", 8)) {
            try { data.disconnected.add(UUID.fromString(entry.getAsString())); }
            catch (IllegalArgumentException ignored) {}
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag(); disconnected.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("Disconnected", list); return tag;
    }
}
