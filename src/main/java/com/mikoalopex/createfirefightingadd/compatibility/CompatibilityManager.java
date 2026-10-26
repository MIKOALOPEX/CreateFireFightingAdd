package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.concurrent.atomic.AtomicReferenceArray;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.StressCouplingCompatibility;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;

/** Resolves optional adapters once, before their factories or runtime entry points are used. */
public final class CompatibilityManager {
    private static final AtomicReferenceArray<CapabilityStatus> STATUS = new AtomicReferenceArray<>(Capability.values().length);
    private static volatile long revision;

    public static long revision() { return revision; }
    public static boolean failed(Capability capability) {
        CapabilityStatus status = STATUS.get(capability.ordinal());
        return status != null && status.state() == CapabilityStatus.State.API_UNAVAILABLE;
    }

    public static synchronized void disable(Capability capability, String reason) {
        CapabilityStatus previous = STATUS.get(capability.ordinal());
        if (previous != null && !previous.available()) return;
        STATUS.set(capability.ordinal(), new CapabilityStatus(CapabilityStatus.State.API_UNAVAILABLE, reason));
        revision++;
        LogUtils.getLogger().warn("[CFFA compatibility] {} disabled: {}. {}", capability, reason, ComponentPolicies.impact(capability));
        if (capability == Capability.COUPLING)
            disable(Capability.FLUID_COUPLING, "Shared coupling backend unavailable");
    }

    public static int mask() {
        int mask = 0;
        for (Capability capability : Capability.values())
            if (available(capability)) mask |= 1 << capability.ordinal();
        return mask;
    }

    public static String version(String id) {
        return ModList.get().getModContainerById(id)
            .map(mod -> mod.getModInfo().getVersion().toString()).orElse(null);
    }

    public static CapabilityStatus versions(Capability capability) {
        return CompatibilityRules.evaluate(capability, CompatibilityManager::version);
    }

    public static boolean available(Capability capability) { return status(capability).available(); }

    public static CapabilityStatus status(Capability capability) {
        CapabilityStatus cached = STATUS.get(capability.ordinal());
        return cached != null ? cached : resolve(capability);
    }

    private static synchronized CapabilityStatus resolve(Capability capability) {
        CapabilityStatus cached = STATUS.get(capability.ordinal());
        if (cached != null) return cached;
        CapabilityStatus result = versions(capability);
        if (result.available()) {
            try {
                if (!probe(capability)) result = new CapabilityStatus(CapabilityStatus.State.API_UNAVAILABLE,
                    "Required adapter/API probe failed; use a supported dependency set (details in DEBUG log)");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                result = new CapabilityStatus(CapabilityStatus.State.API_UNAVAILABLE, error.toString());
                LogUtils.getLogger().debug("[CFFA compatibility] {} probe failed", capability, error);
            }
        }
        STATUS.set(capability.ordinal(), result);
        return result;
    }

    private static boolean probe(Capability capability) throws ReflectiveOperationException {
        if (capability == Capability.SABLE_SPATIAL) return SableStructureCompat.available();
        if (!available(Capability.SABLE_SPATIAL)) return false;
        if (capability == Capability.COUPLING) return StressCouplingCompatibility.enabled();
        if (capability == Capability.FLUID_COUPLING)
            return available(Capability.COUPLING)
                && com.mikoalopex.createfirefightingadd.content.fluids.coupling.FluidCouplings.adapterAvailable();
        ClassLoader loader = CompatibilityManager.class.getClassLoader();
        String rope = "dev.simulated_team.simulated.content.blocks.rope.";
        Class<?> holder = Class.forName(rope + "RopeStrandHolderBehavior", false, loader);
        holder.getMethod("getAttachedStrand");
        holder.getMethod("isAttached");
        Class.forName(rope + "rope_connector.RopeConnectorBlockEntity", false, loader);
        Class<?> strand = Class.forName(rope + "strand.server.ServerRopeStrand", false, loader);
        strand.getMethod("getUUID");
        Class.forName("dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener", false, loader);
        Class.forName("dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape", false, loader);
        if (capability != Capability.ROPE_CONNECT) {
            if (!available(Capability.ROPE_CONNECT)) return false;
            Class<?> physics = Class.forName("dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject", false, loader);
            Class<?> system = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", false, loader);
            physics.getMethod("onAddition", system);
            physics.getDeclaredMethod("remove");
            physics.getDeclaredField("handle");
            strand.getMethod("prePhysicsTick", system, net.minecraft.server.level.ServerLevel.class, double.class);
            Class.forName("dev.ryanhcode.sable.api.physics.object.rope.RopeHandle", false, loader);
        }
        if (capability == Capability.PHYSICAL_HOSE)
            return com.mikoalopex.createfirefightingadd.content.blocks.physical_hose.PhysicalHoses.adapterAvailable();
        if (capability == Capability.ROPE_MANAGE) {
            if (!available(Capability.ROPE_CAPTURE)) return false;
            holder.getMethod("destroyRope", net.minecraft.server.level.ServerPlayer.class,
                net.minecraft.world.phys.Vec3.class, boolean.class);
        }
        return true;
    }

    public static void logSummary() {
        boolean coupling = available(Capability.COUPLING);
        String environment = version("sable") == null ? "NO_SABLE"
            : version("synaxis") == null ? "SABLE_WITHOUT_SYNAXIS"
            : coupling ? "FULL_COUPLING" : "PHYSICS_WITHOUT_COUPLING";
        var log = LogUtils.getLogger();
        log.info("[CFFA compatibility] Environment: {}. Base/Create features remain enabled.", environment);
        for (String id : new String[] {"sable", "aeronautics", "simulated", "synaxis", "sable_schematic_api", "ldlib2"})
            log.info("[CFFA compatibility] {}={}", id, version(id) == null ? "not installed" : version(id));
        for (Capability capability : Capability.values()) {
            CapabilityStatus status = status(capability);
            String impact = ComponentPolicies.impact(capability);
            if (status.state() == CapabilityStatus.State.UNSUPPORTED || status.state() == CapabilityStatus.State.API_UNAVAILABLE)
                log.warn("[CFFA compatibility] {}: {} ({}). {}", capability, status.state(), status.reason(), impact);
            else log.info("[CFFA compatibility] {}: {} ({}). {}", capability, status.state(), status.reason(),
                status.available() ? "Enabled" : impact);
        }
        if (!coupling) log.info("[CFFA compatibility] Existing disabled blocks retain data. Previously saved third-party constraints cannot be guaranteed removable without a compatible physics API; back up worlds before downgrading.");
    }

    private CompatibilityManager() {}
}
