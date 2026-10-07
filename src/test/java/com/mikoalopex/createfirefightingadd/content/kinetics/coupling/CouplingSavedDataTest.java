package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Exercises persisted removals without starting a world or loading the optional physics backend. */
public final class CouplingSavedDataTest {
    public static void main(String[] args) throws Exception {
        ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
        CompoundTag tag = new CompoundTag();
        ListTag pending = new ListTag();
        Set<UUID> pairs = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            UUID pair = UUID.randomUUID();
            pairs.add(pair);
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", "minecraft:overworld");
            entry.putUUID("Pair", pair);
            pending.add(entry);
        }
        pending.add(pending.getCompound(0).copy());
        tag.put("PendingRemovals", pending);
        CouplingSavedData data = load(tag);
        Set<UUID> attempted = new HashSet<>();
        data.retryRemovals((dimension, pair) -> {
            check(dimension.equals(overworld), "Removal must retain its dimension");
            attempted.add(pair);
            return false;
        });
        check(attempted.size() == 16, "Retry budget must be bounded");
        check(data.save(new CompoundTag(), null).getList("PendingRemovals", 10).size() == 20,
            "Failed removals must remain saved and duplicates must be collapsed");
        data.retryRemovals((dimension, pair) -> { attempted.add(pair); return false; });
        check(attempted.equals(pairs), "Failed entries must not starve later entries");
        data = load(data.save(new CompoundTag(), null));
        for (UUID pair : pairs) check(data.removalPending(overworld, pair), "Reload lost a pending removal");
        data.retryRemovals((dimension, pair) -> true);
        check(data.save(new CompoundTag(), null).getList("PendingRemovals", 10).size() == 4,
            "Only acknowledged removals should be discarded");
        data.retryRemovals((dimension, pair) -> true);
        check(data.save(new CompoundTag(), null).getList("PendingRemovals", 10).isEmpty(), "Queue must drain");
        UUID unloadedPartner = UUID.randomUUID();
        data.invalidate(unloadedPartner);
        data = load(data.save(new CompoundTag(), null));
        check(data.consume(unloadedPartner) && !data.consume(unloadedPartner), "Partner tombstone must survive reload");
        System.out.println("CouplingSavedData: persistence, deduplication and bounded retries passed");
    }

    private static CouplingSavedData load(CompoundTag tag) throws Exception {
        var load = CouplingSavedData.class.getDeclaredMethod("load", CompoundTag.class);
        load.setAccessible(true);
        return (CouplingSavedData) load.invoke(null, tag);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
