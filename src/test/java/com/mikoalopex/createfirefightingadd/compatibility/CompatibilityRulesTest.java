package com.mikoalopex.createfirefightingadd.compatibility;

public final class CompatibilityRulesTest {
    public static void main(String[] args) {
        expect(null, CapabilityStatus.State.MISSING);
        expect("1.2.1", CapabilityStatus.State.UNSUPPORTED);
        for (String version : new String[] {"1.2.2", "2.0.6", "3.0.0", "100.0.0"})
            expect(version, CapabilityStatus.State.AVAILABLE);
        System.out.println("Firefighting spatial compatibility rules passed");
    }
    private static void expect(String version, CapabilityStatus.State expected) {
        var actual = CompatibilityRules.evaluate(Capability.SABLE_SPATIAL, id -> version);
        if (actual.state() != expected) throw new AssertionError(version + ": " + actual);
    }
}
