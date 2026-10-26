package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.HashMap;
import java.util.Map;

public final class CompatibilityRulesTest {
    public static void main(String[] args) {
        Map<String, String> versions = new HashMap<>();
        for (Capability capability : Capability.values()) expect(versions, capability, CapabilityStatus.State.MISSING);
        versions.put("sable", "2.0.4");
        expect(versions, Capability.SABLE_SPATIAL, CapabilityStatus.State.AVAILABLE);
        expect(versions, Capability.COUPLING, CapabilityStatus.State.MISSING);
        versions.put("simulated", "1.3.0");
        expect(versions, Capability.ROPE_CAPTURE, CapabilityStatus.State.AVAILABLE);
        expect(versions, Capability.ROPE_MANAGE, CapabilityStatus.State.AVAILABLE);
        expect(versions, Capability.PHYSICAL_HOSE, CapabilityStatus.State.AVAILABLE);
        versions.put("simulated", "1.2.1");
        expect(versions, Capability.PHYSICAL_HOSE, CapabilityStatus.State.UNSUPPORTED);
        versions.put("sable", "1.2.2");
        versions.put("simulated", "1.2.1");
        versions.put("synaxis", "1.3.4");
        expect(versions, Capability.ROPE_CONNECT, CapabilityStatus.State.AVAILABLE);
        expect(versions, Capability.ROPE_CAPTURE, CapabilityStatus.State.UNSUPPORTED);
        expect(versions, Capability.COUPLING, CapabilityStatus.State.UNSUPPORTED);
        versions.putAll(Map.of("sable", "2.0.4", "simulated", "1.3.0", "aeronautics", "1.3.0",
            "synaxis", "1.5.0", "sable_schematic_api", "1.0.1", "ldlib2", "2.2.17"));
        for (Capability capability : Capability.values()) expect(versions, capability, CapabilityStatus.State.AVAILABLE);
        versions.put("synaxis", "1.2.0");
        expect(versions, Capability.PHYSICAL_HOSE, CapabilityStatus.State.AVAILABLE);
        expect(versions, Capability.COUPLING, CapabilityStatus.State.UNSUPPORTED);
        expect(versions, Capability.ROPE_CAPTURE, CapabilityStatus.State.AVAILABLE);
        versions.put("synaxis", "1.4.0");
        expect(versions, Capability.COUPLING, CapabilityStatus.State.AVAILABLE);
        versions.put("sable", "3.0.0");
        for (Capability capability : Capability.values()) expect(versions, capability, CapabilityStatus.State.UNSUPPORTED);
        versions.put("sable", "1.1.3");
        expect(versions, Capability.SABLE_SPATIAL, CapabilityStatus.State.UNSUPPORTED);
        if (!new CompatibilityRules.Requirement("test", "1.4.0", "1.6.0").accepts("1.5.0"))
            throw new AssertionError("Supported version rejected");
        if (new CompatibilityRules.Requirement("test", "1.4.0", "1.6.0").accepts("1.6.0"))
            throw new AssertionError("Unknown upper version accepted");
        System.out.println("Compatibility version matrix passed");
    }

    private static void expect(Map<String, String> versions, Capability capability, CapabilityStatus.State expected) {
        var actual = CompatibilityRules.evaluate(capability, versions::get);
        if (actual.state() != expected) throw new AssertionError(capability + ": " + actual + ", expected " + expected);
    }
}
