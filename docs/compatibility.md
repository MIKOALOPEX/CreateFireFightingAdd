# Optional Physics Compatibility

Compatibility is evaluated per capability, not per creative-tab section. Firefighting
features, Rocker and copycat doors remain available without optional physics mods.
NeoForge still enforces the dependencies declared by those optional mods themselves.

## Component Policy

| Capability | Components / behavior |
| --- | --- |
| `SABLE_SPATIAL` | Spatial queries and transforms for supported Sable structures; ordinary-world behavior remains available without it. |
| `ROPE_CONNECT` | Smart Rope Connector. |
| `ROPE_CAPTURE` | Rope Hook, including tail-hook mode. |
| `ROPE_MANAGE` | Rope Connector Master. |
| `COUPLING` | Stress Coupling and the shared coupling backend. |
| `FLUID_COUPLING` | Fluid Coupling; requires the shared backend and its own endpoint adapter. |
| `PHYSICAL_HOSE` | Independent 4/6/8px physical fire hoses; native rope simulation with existing hose transport. |

Unavailable components are hidden from creative listings and JEI; their conditional
recipes are omitted. Placement, interaction and server-side operations are guarded.
Registrations remain stable for saved worlds. Disabled connector/hook block entities
retain raw NBT without running physics. The disabled manager permits inventory
recovery but does not create or destroy ropes; unresolved optional items retain their
serialized data while the block remains in the world.

Removing optional mods is not a lossless world-conversion mechanism. Third-party
ropes, structures and persisted constraints belong to their respective mods and may
not be recoverable or removable without compatible APIs. Back up worlds before
changing or removing physics dependencies.

## Version Policy

`CompatibilityRules` is the single source of version ranges. Upper bounds are
exclusive and intentionally conservative until a new API series is verified.

| Capability | Required versions |
| --- | --- |
| Spatial | Sable `[1.2.2,3.0.0)` |
| Connect | Spatial requirements and Simulated `[1.2.1,1.4.0)` |
| Physical hose | Sable `[2.0.0,3.0.0)` and Simulated `[1.3.0,1.4.0)`; no Synaxis requirement. |
| Capture / Manage | Sable `[2.0.0,3.0.0)` and Simulated `[1.2.1,1.4.0)` |
| Stress / Fluid Coupling | Sable `[2.0.0,3.0.0)`, Aeronautics and Simulated `[1.3.0,1.4.0)`, Synaxis `[1.4.0,1.6.0)`, Sable Schematic API `[0.4.1,2.0.0)`, LDLib2 `[2.2.17,3.0.0)` |

The presence of a weld feature alone is insufficient: the current coupling backend
also requires the angular-limit APIs. Synaxis 1.3.4 therefore does not enable this
backend. Runtime API probes can disable a capability even when its versions match.

## Maintenance

- `CompatibilityRules`: version requirements, also safe during early Mixin loading.
- `CompatibilityManager`: cached capability results, API probes and startup report.
- `ComponentPolicies`: block/item membership; add new gated components here.
- `CompatibilityMixinPlugin`: skip optional injections before missing or unsupported
  classes are loaded. Update this when adding optional mixins.
- `CapabilityCondition`: recipe conditions; new gated recipes need this condition.
- `CompatibilityEvents` and component guards: authoritative server enforcement.
- `CompatibilitySnapshot`: capabilities sent on login and after a runtime backend
  failure, intersected with local capabilities for client presentation. A revision
  check schedules updates only when state changes; there are no per-tick probes.
- `CompatibilityCreativeContents`: remove entries through the event API; the
  parent and search getters return immutable views.
- `CompatibilityCreativeTabsMixin`: invalidate cached tab parameters after a
  visibility change. Vanilla rebuilds the open tab and search trees.
- `CompatibilityJeiPlugin`: owns compatibility hiding and restores only entries
  it previously hid. The legacy rope-latch item remains hidden.

Endpoint construction failures disable the affected capability for the process.
A fluid adapter failure leaves stress coupling available; a shared backend failure
disables both. Recipe conditions are evaluated at data reload, while placement and
operation guards apply immediately. Capability bits are append-only; an older
server that does not send the fluid bit leaves that component hidden on the client.

Do not import optional API types into the neutral compatibility layer. Load backend
implementations only after their prerequisites pass. A UI check alone is not a
security boundary: packets, redstone and automated entry points also need guards.

Each server start reports `[CFFA compatibility]`, the installed versions, environment,
capability results and disabled components. Missing optional mods use INFO; installed
unsupported versions or failed API probes use WARN, with diagnostic detail at DEBUG.

## Verification

`gradlew check build` includes the pure version-policy regression tests. To verify
real class loading, put the desired optional jars in
`build/compatibility-smoke-<profile>/mods`, then run
`gradlew runCompatibilitySmoke -PcompatibilitySmoke -PcompatibilityProfile=<profile>`.

Profiles: `base` (Create only), `sable` (only Sable), `old` / `old120` (Sable 1 and
old Synaxis), `ropes` (supported rope stack without Synaxis), `full` (full stack).
These isolated GameTest directories do not replace the developer's `run/mods`.
Check the GameTest result, not just Gradle's exit status. These smoke tests cover
startup, capability selection and inactive data retention; they do not substitute
for client rendering, physical connection or cross-version world-migration tests.

The smoke tests also exercise the real creative event mutation API. The `full`
profile injects a failing fluid endpoint constructor to check fallback and isolation.
Run `gradlew runCompatibilityClientSmoke -PcompatibilitySmoke -PcompatibilityProfile=<profile>`
for an isolated client check. It creates a fresh flat world under the profile's
`saves` directory so datapack registries and server configuration are loaded.
It verifies transformed tab caching and capability restoration, then tests JEI
lifecycle calls against a recording runtime. It exits
after logging `Compatibility client smoke: PASSED` or `FAILED`; inspect that result
instead of relying on the process exit code. This does not replace an in-world
check of the visible menu, a real JEI runtime or multiplayer packet timing.
