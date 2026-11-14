package com.mikoalopex.createfirefightingadd.compatibility;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional, read-only bridge for the split mods' existing hose relay semantics.
 * Each mod owns its copy. Only Minecraft types cross this boundary. */
public final class HoseRelayCompatibility {
    private static final ClassValue<Optional<Method>> READERS = new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method method = type.getMethod("createAdditions$getHoseState", Direction.class, BlockEntity.class);
                return method.getReturnType() == CompoundTag.class ? Optional.of(method) : Optional.empty();
            } catch (NoSuchMethodException missing) { return Optional.empty(); }
        }
    };
    public record State(boolean relay, boolean pulling, float pressure, int range, boolean externalFill) {}
    public static boolean isEndpoint(Object endpoint) {
        return endpoint != null && READERS.get(endpoint.getClass()).isPresent();
    }
    public static State read(Object endpoint, Direction face, BlockEntity requester) {
        if (endpoint == null) return null;
        var method = READERS.get(endpoint.getClass());
        if (method.isEmpty()) return null;
        try {
            CompoundTag data = (CompoundTag) method.get().invoke(endpoint, face, requester);
            if (data == null || data.getInt("Version") != 1) return null;
            float pressure = data.getFloat("Pressure");
            int range = data.getInt("Range");
            if (!Float.isFinite(pressure) || pressure <= 0 || range <= 0) return null;
            return new State(data.getBoolean("Relay"), data.getBoolean("Pulling"), pressure,
                range, data.getBoolean("ExternalFill"));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Hose relay compatibility failed for " + endpoint.getClass().getName(), failure);
        }
    }
    /** Original firefighting pump API, optionally present when a pump is discovered. */
    public static Number pumpValue(Object pump, String methodName) {
        try {
            return (Number) pump.getClass().getMethod(methodName).invoke(pump);
        } catch (NoSuchMethodException absent) { return null; }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pump compatibility failed for " + pump.getClass().getName(), failure);
        }
    }
    /** Discovery must reach a remote pump before its exact range can be read. */
    public static int pumpSearchRange(int localRange) {
        if (RemoteSearch.method == null) return localRange;
        try {
            return Math.max(localRange, (int) RemoteSearch.method.invoke(null));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Hose pump-search compatibility failed", failure);
        }
    }
    public static int createAdditions$getPumpSearchRange() {
        int range = Math.round(com.simibubi.create.content.fluids.FluidPropagator.getPumpRange()
            * com.mikoalopex.createfirefightingadd.Config.highPressurePumpMultiplier);
        return Math.max(com.mikoalopex.createfirefightingadd.Config.hoseMaxLength, range);
    }
    private static final class RemoteSearch {
        static final Method method = find();
        private static Method find() {
            try {
                return Class.forName("com.mikoalopex.createpowertrain.compatibility.HoseRelayCompatibility", false,
                    HoseRelayCompatibility.class.getClassLoader()).getMethod("createAdditions$getPumpSearchRange");
            } catch (ClassNotFoundException | NoSuchMethodException absent) { return null; }
        }
    }
    /** Capability traffic is only a fallback, never a replacement for a discovered pump/relay. */
    public static int externalSourceSide(int backDistance, int partnerDistance, boolean backRecent, boolean partnerRecent) {
        if (backDistance != Integer.MAX_VALUE || partnerDistance != Integer.MAX_VALUE) return -1;
        return backRecent ? 0 : partnerRecent ? 1 : -1;
    }
    private HoseRelayCompatibility() {}
}
