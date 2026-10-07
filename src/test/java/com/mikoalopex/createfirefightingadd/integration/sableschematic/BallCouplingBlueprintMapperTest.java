package com.mikoalopex.createfirefightingadd.integration.sableschematic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Checks that copied pairs retain ownership while remaining independent of the source and other copies. */
public final class BallCouplingBlueprintMapperTest {
    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID(), pair = UUID.randomUUID();
            CompoundTag first = endpoint(a, b, pair), second = endpoint(b, a, pair);
            Map<UUID, UUID> session = new HashMap<>();
            BallCouplingBlueprintMapper.remapIdentities(first, session);
            BallCouplingBlueprintMapper.remapIdentities(second, session);
            check(first.getUUID("CouplingEndpoint").equals(second.getUUID("CouplingPartner")), "First endpoint mapping");
            check(second.getUUID("CouplingEndpoint").equals(first.getUUID("CouplingPartner")), "Second endpoint mapping");
            check(first.getUUID("CouplingPair").equals(second.getUUID("CouplingPair")), "Pair must be shared");
            check(!pair.equals(first.getUUID("CouplingPair")), "Copy must not reuse source constraint identity");
            check(Integer.signum(a.compareTo(b)) == Integer.signum(first.getUUID("CouplingEndpoint")
                .compareTo(second.getUUID("CouplingEndpoint"))), "Free-mode controller must stay on the same end");
            CompoundTag copy = endpoint(a, b, pair);
            BallCouplingBlueprintMapper.remapIdentities(copy, new HashMap<>());
            check(!copy.getUUID("CouplingPair").equals(first.getUUID("CouplingPair")), "Repeated placement must be isolated");
            check(copy.getBoolean("CouplingEstablished"), "Established passive connection must survive copying");
            check(copy.getCompound("CouplingReference").getDouble("V0") == 0.25, "Reference pose must survive copying");
        }
        System.out.println("BallCouplingBlueprintMapper: 100 pair remapping checks passed");
    }

    private static CompoundTag endpoint(UUID endpoint, UUID partner, UUID pair) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CouplingEndpoint", endpoint);
        tag.putUUID("CouplingPartner", partner);
        tag.putUUID("CouplingPair", pair);
        tag.putBoolean("CouplingEstablished", true);
        CompoundTag reference = new CompoundTag();
        reference.putDouble("V0", 0.25);
        tag.put("CouplingReference", reference);
        return tag;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
