# Firefighting compatibility

This document describes the split Firefighting working tree. The integrated
mod's historical compatibility table is available in Git history.

Firefighting requires Minecraft 1.21.1, NeoForge and Create. Its current loader
ranges remain Minecraft `[1.21.1,1.22)`, NeoForge `[21,)`, Create `[6.0.10,6.1.0)`.
Sable spatial support is optional and requires at least 1.2.2, with no upper
version limit. A missing or unavailable spatial adapter leaves ordinary-world
firefighting available. Existing Jade, JEI, blueprint and other device-specific
optional integration remains local to Firefighting.

Physical hoses, both coupling systems, rope devices, Rocker and copycat doors
belong to Powertrain. Firefighting no longer registers or manages their entities,
physics adapters, configuration, packets or optional-physics visibility system.
It has no compile-time or required runtime dependency on Powertrain. Each mod owns a copy of the optional hose-relay adapter; only Minecraft types and a versioned read-only pressure snapshot cross that boundary.

Ordinary hose fluid transport retains its own pairing, storage, direction and
pressure implementation. The optional split-mod relay preserves pressure, direction, custom range and external-fill source semantics; adjacent compatible ports use sided NeoForge fluid capabilities. Other devices continue through the existing Create integration.
Compilation and standalone checks do not establish mixed-network runtime behaviour.

The split builds are development artifacts. Old integrated-mod saves must not be
opened with them before the migration procedure and runtime validation are complete.
