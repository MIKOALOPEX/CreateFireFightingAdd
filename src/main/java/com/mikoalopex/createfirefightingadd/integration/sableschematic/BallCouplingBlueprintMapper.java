package com.mikoalopex.createfirefightingadd.integration.sableschematic;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.BallCouplingBlockEntity;
import net.minecraft.nbt.CompoundTag;

/** Remaps pair identities per placement session; copying one endpoint produces an open joint. */
final class BallCouplingBlueprintMapper implements InvocationHandler {
    private static final Map<Object, Map<UUID, UUID>> SESSIONS = new WeakHashMap<>();
    private final FireHoseBlueprintMapper.Api api;
    private BallCouplingBlueprintMapper(FireHoseBlueprintMapper.Api api) { this.api = api; }
    static Object create(FireHoseBlueprintMapper.Api api) {
        return Proxy.newProxyInstance(BallCouplingBlueprintMapper.class.getClassLoader(),
            new Class<?>[] {api.blockMapperClass()}, new BallCouplingBlueprintMapper(api));
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws ReflectiveOperationException {
        return switch (method.getName()) {
            case "save" -> {
                CompoundTag tag = args[1] == null ? null : ((CompoundTag)args[1]).copy();
                if (tag != null && api.saveBlockEntity().invoke(args[0]) instanceof BallCouplingBlockEntity be) {
                    BallCouplingBlockEntity other = be.partner();
                    Object ref = other == null ? null : api.blockRef().invoke(args[0], other.getBlockPos());
                    if (!(ref instanceof Optional<?> value) || value.isEmpty()) clearPair(tag);
                    clearKinetics(tag);
                }
                yield tag;
            }
            case "beforeLoadBlockEntity" -> {
                CompoundTag tag = (CompoundTag)args[1];
                if (tag != null) {
                    Object session = api.session().invoke(args[0]);
                    Map<UUID, UUID> ids = SESSIONS.computeIfAbsent(session, ignored -> new HashMap<>());
                    for (String name : new String[] {"CouplingEndpoint", "CouplingPartner", "CouplingPair"})
                        if (tag.hasUUID(name)) tag.putUUID(name, ids.computeIfAbsent(tag.getUUID(name), ignored -> UUID.randomUUID()));
                    tag.putBoolean("CouplingSearch", false);
                    clearKinetics(tag);
                }
                yield null;
            }
            case "afterLoadBlockEntity" -> null;
            case "toString" -> "CreateFireFightingAdd BallCouplingBlueprintMapper";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private static void clearPair(CompoundTag tag) {
        tag.remove("CouplingPartner"); tag.remove("CouplingPair"); tag.putBoolean("CouplingSearch", false);
    }
    private static void clearKinetics(CompoundTag tag) {
        tag.remove("Network"); tag.remove("Source"); tag.putFloat("Speed",0);
    }
}
