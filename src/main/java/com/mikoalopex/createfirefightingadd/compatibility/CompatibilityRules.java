package com.mikoalopex.createfirefightingadd.compatibility;

import java.util.List;
import java.util.function.Function;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/** Version rules shared by runtime checks and early Mixin loading. */
public final class CompatibilityRules {
    public record Requirement(String mod, String minimum, String upperExclusive) {
        public String range() { return "[" + minimum + "," + upperExclusive + ")"; }
        public boolean accepts(String version) {
            var actual = new DefaultArtifactVersion(version);
            return actual.compareTo(new DefaultArtifactVersion(minimum)) >= 0
                && actual.compareTo(new DefaultArtifactVersion(upperExclusive)) < 0;
        }
    }

    private static final Requirement SPATIAL = new Requirement("sable", "1.2.2", "3.0.0");
    private static final Requirement PHYSICS = new Requirement("sable", "2.0.0", "3.0.0");
    private static final Requirement ROPE = new Requirement("simulated", "1.2.1", "1.4.0");

    public static List<Requirement> requirements(Capability capability) {
        return switch (capability) {
            case SABLE_SPATIAL -> List.of(SPATIAL);
            case PHYSICAL_HOSE -> List.of(PHYSICS, new Requirement("simulated", "1.3.0", "1.4.0"));
            case ROPE_CONNECT -> List.of(SPATIAL, ROPE);
            case ROPE_CAPTURE, ROPE_MANAGE -> List.of(PHYSICS, ROPE);
            case COUPLING, FLUID_COUPLING -> List.of(PHYSICS,
                new Requirement("aeronautics", "1.3.0", "1.4.0"),
                new Requirement("simulated", "1.3.0", "1.4.0"),
                new Requirement("synaxis", "1.4.0", "1.6.0"),
                new Requirement("sable_schematic_api", "0.4.1", "2.0.0"),
                new Requirement("ldlib2", "2.2.17", "3.0.0"));
        };
    }

    public static CapabilityStatus evaluate(Capability capability, Function<String, String> versions) {
        for (Requirement requirement : requirements(capability)) {
            String version = versions.apply(requirement.mod());
            if (version == null)
                return new CapabilityStatus(CapabilityStatus.State.MISSING,
                    "Missing " + requirement.mod() + " " + requirement.range());
            if (!requirement.accepts(version))
                return new CapabilityStatus(CapabilityStatus.State.UNSUPPORTED,
                    requirement.mod() + " " + version + "; supported " + requirement.range());
        }
        return CapabilityStatus.ready();
    }

    private CompatibilityRules() {}
}
