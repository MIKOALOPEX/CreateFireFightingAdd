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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Remaps pair identities per placement session; copying one endpoint produces an open joint. */
final class BallCouplingBlueprintMapper implements InvocationHandler {
    private static final String PARTNER_REF = "CouplingBlueprintPartner";
    private static final Map<Object, Map<UUID, UUID>> SESSIONS = new WeakHashMap<>();
    private final FireHoseBlueprintMapper.Api api;

    private BallCouplingBlueprintMapper(FireHoseBlueprintMapper.Api api) { this.api = api; }

    static Object create(FireHoseBlueprintMapper.Api api) {
        return Proxy.newProxyInstance(BallCouplingBlueprintMapper.class.getClassLoader(),
            new Class<?>[] {api.blockMapperClass()}, new BallCouplingBlueprintMapper(api));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws ReflectiveOperationException {
        return switch (method.getName()) {
            case "save" -> {
                CompoundTag tag = args[1] == null ? null : ((CompoundTag) args[1]).copy();
                if (tag != null && api.saveBlockEntity().invoke(args[0]) instanceof BallCouplingBlockEntity be) {
                    BallCouplingBlockEntity other = be.partner();
                    Object ref = other == null ? null : api.blockRef().invoke(args[0], other.getBlockPos());
                    if (ref instanceof Optional<?> value && value.isPresent()) {
                        CompoundTag partner = new CompoundTag();
                        partner.putInt("SubLevel", (int) api.blockRefSubLevelId().invoke(value.get()));
                        partner.putLong("Pos", ((BlockPos) api.blockRefLocalPos().invoke(value.get())).asLong());
                        tag.put(PARTNER_REF, partner);
                    } else clearPair(tag);
                    clearKinetics(tag);
                }
                yield tag;
            }
            case "beforeLoadBlockEntity" -> {
                CompoundTag tag = (CompoundTag) args[1];
                if (tag != null) {
                    Object session = api.session().invoke(args[0]);
                    Map<UUID, UUID> ids = SESSIONS.computeIfAbsent(session, ignored -> new HashMap<>());
                    remapIdentities(tag, ids);
                    // Preserve the established connection and limit reference, but not transient UI state.
                    tag.putBoolean("CouplingSearch", false);
                    tag.remove("CouplingGuiPairCooldown");
                    tag.remove("RenderLink");
                    clearKinetics(tag);
                }
                yield null;
            }
            case "afterLoadBlockEntity" -> {
                CompoundTag tag = (CompoundTag) args[2];
                if (args[1] instanceof BallCouplingBlockEntity be && tag != null && tag.contains(PARTNER_REF, 10)) {
                    CompoundTag ref = tag.getCompound(PARTNER_REF);
                    Object mapped = api.mapBlock().invoke(args[0],
                        api.newBlockRef(ref.getInt("SubLevel"), BlockPos.of(ref.getLong("Pos"))));
                    // This callback runs after all copied block entities have received their remapped data.
                    if (!(mapped instanceof BlockPos pos) || be.getLevel() == null
                            || !(be.getLevel().getBlockEntity(pos) instanceof BallCouplingBlockEntity other)
                            || !be.endpointId().equals(other.partnerId())
                            || !other.endpointId().equals(be.partnerId())
                            || be.pairId() == null || !be.pairId().equals(other.pairId()))
                        be.disconnect();
                    be.setChanged();
                }
                yield null;
            }
            case "toString" -> "CreateFireFightingAdd BallCouplingBlueprintMapper";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    static void remapIdentities(CompoundTag tag, Map<UUID, UUID> ids) {
        if (tag.hasUUID("CouplingEndpoint") && tag.hasUUID("CouplingPartner")) {
            UUID self = tag.getUUID("CouplingEndpoint"), other = tag.getUUID("CouplingPartner");
            if (!self.equals(other) && !ids.containsKey(self) && !ids.containsKey(other)) {
                UUID a = UUID.randomUUID(), b = UUID.randomUUID();
                // Keep the free-mode controller on the same end so saved limit frames retain their order.
                if (Integer.signum(self.compareTo(other)) != Integer.signum(a.compareTo(b))) {
                    UUID swap = a;
                    a = b;
                    b = swap;
                }
                ids.put(self, a);
                ids.put(other, b);
            }
        }
        for (String name : new String[] {"CouplingEndpoint", "CouplingPartner", "CouplingPair"})
            if (tag.hasUUID(name)) tag.putUUID(name, ids.computeIfAbsent(tag.getUUID(name), ignored -> UUID.randomUUID()));
    }

    private static void clearPair(CompoundTag tag) {
        tag.remove("CouplingPartner");
        tag.remove("CouplingPair");
        tag.putBoolean("CouplingSearch", false);
        tag.remove("CouplingEstablished");
        tag.remove("CouplingReference");
        tag.remove(PARTNER_REF);
    }

    private static void clearKinetics(CompoundTag tag) {
        tag.remove("Network");
        tag.remove("Source");
        tag.putFloat("Speed", 0);
    }
}
