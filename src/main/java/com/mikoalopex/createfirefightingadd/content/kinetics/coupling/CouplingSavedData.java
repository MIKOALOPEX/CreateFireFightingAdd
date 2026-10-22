package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Keeps unloaded-partner notifications and constraint removals across server sessions. */
@EventBusSubscriber(modid = CreateFireFightingAdd.MODID)
public final class CouplingSavedData extends SavedData {
    static final ResourceLocation FEATURE = ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "ball_coupling");
    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int REMOVALS_PER_RETRY = 16;

    private record Removal(ResourceKey<Level> dimension, UUID pair) {}

    private final Set<UUID> disconnected = new HashSet<>();
    private final LinkedHashSet<Removal> removals = new LinkedHashSet<>();
    private boolean ready;
    private final ResourceLocation feature;

    public CouplingSavedData() { this(FEATURE); }

    private CouplingSavedData(ResourceLocation feature) { this.feature = feature; }

    private static final ResourceLocation[] FEATURES = {FEATURE,
        ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "fluid_coupling")};

    static CouplingSavedData get(MinecraftServer server) {
        return get(server, FEATURE);
    }

    public static CouplingSavedData get(MinecraftServer server, ResourceLocation feature) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> new CouplingSavedData(feature),
            (tag, registries) -> load(tag, feature)), FEATURE.equals(feature) ? "createfirefightingadd_couplings"
                : "createfirefightingadd_" + feature.getPath() + "_couplings");
    }
    public void invalidate(UUID pair) {
        if (pair != null && disconnected.add(pair)) setDirty();
    }

    public boolean consume(UUID pair) {
        if (pair != null && disconnected.remove(pair)) { setDirty(); return true; }
        return false;
    }

    public void removeConstraint(ServerLevel level, UUID pair) {
        if (pair == null) return;
        Removal removal = new Removal(level.dimension(), pair);
        if (removals.add(removal)) setDirty();
        if (ready && CouplingPhysics.remove(level, feature, pair)) {
            removals.remove(removal);
            setDirty();
        }
    }

    public boolean removalPending(ResourceKey<Level> dimension, UUID pair) {
        return removals.contains(new Removal(dimension, pair));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if (StressCouplingCompatibility.enabled())
            for (var feature : FEATURES) get(event.getServer(), feature).ready = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        if (StressCouplingCompatibility.enabled())
            for (var feature : FEATURES) get(event.getServer(), feature).ready = false;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % RETRY_INTERVAL_TICKS != 0 || !StressCouplingCompatibility.enabled()) return;
        for (var feature : FEATURES) {
            CouplingSavedData data = get(event.getServer(), feature);
            if (!data.ready) continue;
            data.retryRemovals((dimension, pair) -> {
                ServerLevel level = event.getServer().getLevel(dimension);
                return level != null && CouplingPhysics.remove(level, feature, pair);
            });
        }
    }

    void retryRemovals(BiPredicate<ResourceKey<Level>, UUID> remove) {
        // Retry only recorded keys, never scan bodies or world blocks. Failed entries rotate to the back.
        int attempts = Math.min(REMOVALS_PER_RETRY, removals.size());
        for (int i = 0; i < attempts; i++) {
            Removal removal = removals.removeFirst();
            if (remove.test(removal.dimension(), removal.pair())) setDirty();
            else removals.add(removal);
        }
    }

    private static CouplingSavedData load(CompoundTag tag) {
        return load(tag, FEATURE);
    }

    private static CouplingSavedData load(CompoundTag tag, ResourceLocation feature) {
        var data = new CouplingSavedData(feature);
        for (var entry : tag.getList("Disconnected", 8)) {
            try { data.disconnected.add(UUID.fromString(entry.getAsString())); }
            catch (IllegalArgumentException ignored) {}
        }
        for (var entry : tag.getList("PendingRemovals", 10)) {
            CompoundTag removal = (CompoundTag) entry;
            ResourceLocation dimension = ResourceLocation.tryParse(removal.getString("Dimension"));
            if (dimension != null && removal.hasUUID("Pair"))
                data.removals.add(new Removal(ResourceKey.create(Registries.DIMENSION, dimension), removal.getUUID("Pair")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        disconnected.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("Disconnected", list);
        ListTag pending = new ListTag();
        for (Removal removal : removals) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", removal.dimension().location().toString());
            entry.putUUID("Pair", removal.pair());
            pending.add(entry);
        }
        tag.put("PendingRemovals", pending);
        return tag;
    }
}
