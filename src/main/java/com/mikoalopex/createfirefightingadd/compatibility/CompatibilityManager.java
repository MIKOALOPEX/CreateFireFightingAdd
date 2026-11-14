package com.mikoalopex.createfirefightingadd.compatibility;

import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import net.neoforged.fml.ModList;

/** Optional spatial support; base firefighting remains available without Sable. */
public final class CompatibilityManager {
    public static String version(String id) {
        return ModList.get().getModContainerById(id)
            .map(mod -> mod.getModInfo().getVersion().toString()).orElse(null);
    }

    public static CapabilityStatus versions(Capability capability) {
        return CompatibilityRules.evaluate(capability, CompatibilityManager::version);
    }

    public static CapabilityStatus status(Capability capability) {
        CapabilityStatus result = versions(capability);
        if (!result.available()) return result;
        return SableStructureCompat.available() ? result
            : new CapabilityStatus(CapabilityStatus.State.API_UNAVAILABLE, "Spatial adapter unavailable");
    }

    public static boolean available(Capability capability) { return status(capability).available(); }

    private CompatibilityManager() {}
}
