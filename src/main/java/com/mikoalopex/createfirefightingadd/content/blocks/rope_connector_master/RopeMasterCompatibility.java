package com.mikoalopex.createfirefightingadd.content.blocks.rope_connector_master;

import java.util.ArrayList;
import java.util.List;
import com.mikoalopex.createfirefightingadd.api.rope.ManagedRopeAdapter;
import com.mikoalopex.createfirefightingadd.compatibility.Capability;
import com.mikoalopex.createfirefightingadd.compatibility.CompatibilityManager;
import net.minecraft.world.item.ItemStack;

/** Optional adapters are resolved lazily, so Create-only installations never load rope physics classes. */
public final class RopeMasterCompatibility {
    private static final List<ManagedRopeAdapter> ADAPTERS = new ArrayList<>();
    private static boolean initialized;

    private RopeMasterCompatibility() {}

    public static boolean available() { return CompatibilityManager.available(Capability.ROPE_MANAGE); }

    public static RopeMasterBlock createBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return available() ? Loaded.block(properties) : new RopeMasterBlock(properties);
    }

    public static synchronized void register(ManagedRopeAdapter adapter) {
        if (ADAPTERS.stream().anyMatch(existing -> existing.id().equals(adapter.id())))
            throw new IllegalArgumentException("Duplicate managed rope adapter: " + adapter.id());
        ADAPTERS.add(adapter);
    }

    public static synchronized List<ManagedRopeAdapter> adapters() {
        if (!available()) return List.of();
        if (!initialized) {
            initialized = true;
            if (available()) {
                ManagedRopeAdapter nativeAdapter = Loaded.adapter();
                if (nativeAdapter != null) register(nativeAdapter);
            }
        }
        return List.copyOf(ADAPTERS);
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && adapters().stream().anyMatch(adapter -> adapter.accepts(stack));
    }

    private static final class Loaded {
        static RopeMasterBlock block(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
            return new SableRopeMasterBlock(properties);
        }
        static ManagedRopeAdapter adapter() { return NativeRopeAdapter.supported() ? new NativeRopeAdapter() : null; }
    }
}
