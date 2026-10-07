package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Retains the attachment flag while a hook is unloaded; no hook positions or spatial queries are stored. */
public final class RopeHookAttachments extends SavedData {
    private final Set<UUID> attached = new HashSet<>();

    public static RopeHookAttachments get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(RopeHookAttachments::new,
            (tag, registries) -> load(tag)), "createfirefightingadd_rope_hook_attachments");
    }

    public boolean contains(UUID rope) { return attached.contains(rope); }
    void attach(UUID rope) { if (rope != null && attached.add(rope)) setDirty(); }
    void release(UUID rope) { if (rope != null && attached.remove(rope)) setDirty(); }

    private static RopeHookAttachments load(CompoundTag tag) {
        var data = new RopeHookAttachments();
        for (var value : tag.getList("Ropes", 8)) {
            try { data.attached.add(UUID.fromString(value.getAsString())); }
            catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        attached.forEach(id -> entries.add(StringTag.valueOf(id.toString())));
        tag.put("Ropes", entries);
        return tag;
    }
}
