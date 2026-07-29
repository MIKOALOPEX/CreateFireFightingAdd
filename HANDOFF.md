# Create Firefighting Add - Project Handoff

Last updated: 2026-07-29

This is the authoritative handoff for new Codex tasks working on this repository.
Read it before changing code. For exact behavior, the current source and Git
history take precedence over old chat history.

## Current Baseline

- Repository: `D:\CreateAdd\CreateFireFightingAdd`
- Remote: `https://github.com/MIKOALOPEX/CreateFireFightingAdd.git`
- Branch: `main`
- Current development branch: `main`.
- Check the local branch, working tree, and remote state before every task;
  this document intentionally does not embed a self-referential commit hash.
- Mod version: `0.2.1-beta`
- Minecraft: `1.21.1`
- NeoForge development version: `21.1.228`
- Create: `6.0.10-280`
- Ponder: `1.0.82`

Recent important commits:

- `f466d67` - prevent Firehose loop fluid duplication
- `d08f8f1` - spray performance scheduler and scan reduction
- `f4a8bb1` - guard multipurpose backtank tooltip before Create config load
- `9f9c8f8` - release `0.2.1-beta` updates
- `ea2efd4` - handheld nozzle entity handling

Do not change the version or push commits unless the user explicitly asks.

## User Rules

These rules apply to every task:

1. Do not delete or clean backups, temporary files, logs, or run files unless
   explicitly requested.
2. Do not update anything under `tempbackup` unless explicitly requested.
3. Do not push to GitHub unless explicitly requested.
4. Preserve tested behavior. Prefer the smallest change that fixes the reported
   problem.
5. Do not reinterpret a request for a visual or animation rollback as permission
   to remove unrelated systems.
6. New blocks need only tolerate Create contraption assembly and movement unless
   active contraption behavior is explicitly requested.
7. Sable physical substructures and Create contraptions are different systems.
8. The mod must start and function without Sable and Aeronautics installed.
9. Register mod content only in this mod's creative tab unless explicitly
   requested otherwise.
10. Code is MIT. Art and other visual assets are All Rights Reserved.

## Important Paths

- Source: `src/main/java/com/mikoalopex/createfirefightingadd`
- Resources: `src/main/resources`
- Main development runtime: `run`
- Alternate multiplayer runtime: `run-ved`
- Milestone backups: `tempbackup`
- User-provided source assets: `D:\CreateAdd\assets`
- Additional shared assets: `D:\CreateAdd\CreateAssets`
- Comparison builds and reports: `D:\CreateAdd\比对目录`
- Local-only Codex plans: `run/codex-plans`

The `run` and backup directories may contain large amounts of historical data.
Do not treat old logs as current evidence without checking timestamps.

## Project Structure

### Entry Points And Configuration

- `CreateFireFightingAdd.java`: registration and common event integration.
- `Config.java`: server/common gameplay and performance configuration.
- `ClientConfig.java`: client-only rendering configuration.
- `PartialModels.java`: Create partial model registration.
- `RemapManager.java`: legacy ID/remap support.

### Public APIs

- `api/fire_hose`: hose connection and endpoint state APIs.
- `api/nozzle`: third-party spray observation and block interaction API.
- `api/handheld`: handheld controller binding and cleanup API.
- `api/backtank`: multipurpose backtank air/fluid consumption API.
- `api/kinetics`: pipeline turbine source and network coordination APIs.

Third-party APIs should report or extend interactions without bypassing the
mod's own validation and scheduling.

### Main Content Packages

- `content/blocks/fire_hose`: hose endpoints, connection state, rendering,
  connector block, fluid transfer, Sable and contraption mapping.
- `content/blocks/fire_pole`: vertical pole placement and player descent.
- `content/blocks/flow_meter`: legacy meter and current fluid flow meter.
- `content/equipment/backtank`: multipurpose wearable/block backtank.
- `content/equipment/handheld`: cabinet, GUI, handheld controller, hose
  rendering, spray handling, and dropped controller entity.
- `content/fluids/nozzle`: fixed nozzles, bucket controller spray, effects,
  particles, processing, and spray scheduler.
- `content/fluids/water_intake`: water intake and bucket-controller binding.
- `content/items`: pneumatic hammer and related tools.
- `content/kinetics/pump`: high-pressure pump.
- `content/kinetics/turbine`: pipeline turbine and shared source coordinator.
- `content/ponder`: Ponder registration and scenes.
- `integration/sable`: optional Sable physical-substructure compatibility.
- `integration/sableschematic`: optional Sable schematic compatibility.
- `integration/burnt`: optional Burnt and Burnt Basic fire compatibility.
- `mixin`: narrowly scoped defensive compatibility hooks.

## Current System Status

### Firehose

Implemented behavior:

- Two independent endpoint blocks can be placed, disconnected, and reconnected.
- Breaking range leaves endpoints intact but disables transfer.
- Hose item reconnects compatible empty endpoints.
- Shears disconnect the pair and play the cutting sound.
- Black and white dye switch hose and endpoint appearance.
- Supports Create pressure networks and non-Create fluid sources.
- Supports world, Sable, and substantial Create contraption endpoint mapping.
- Exposes reusable connection APIs for other blocks and blueprint integrations.
- Connector block supports idle, fixed, and free reconnection modes.

Current pressure model:

- Create-native pressure remains authoritative where available.
- Non-Create source handling uses external flow/capability observations and
  configurable default transmission distance.
- Do not replace this with a new custom pressure simulation without a separate
  design review.

Latest conservation fix:

- `FluidTransportBehaviour#getProvidedOutwardFluid()` is a descriptive Create
  pipe-flow snapshot, not drainable storage.
- `FireHoseBlockEntity.pullFromSourceEndpoint()` must never copy that snapshot
  directly into the hose tank.
- Commit `f466d67` keeps the snapshot for observation only and accepts fluid only
  through a real executable capability transfer.
- This fixed multi-hose/multi-pump loop fluid duplication. The user verified
  that fluid totals remain conserved even when individual tank amounts fluctuate.

Debug logging:

- `FireHoseDebugLog` remains in source but is disabled with `ENABLED = false`.
- Do not delete it. It can be re-enabled for future topology/transfer tests.

Local future plan:

- `run/codex-plans/firehose-transactional-hardening.md`
- This file is intentionally ignored by Git.
- It proposes defense against third-party handlers whose simulated drain differs
  from executed drain. It is not implemented.

Firehose pitfalls:

- Visual facing can look counterintuitive. Do not change it based on appearance
  alone; it has been tested in the current network logic.
- Never treat advertised pressure or observed flow as proof that fluid was
  actually drained.
- Copied contraption endpoints must not form one-to-many links.
- Test both direct hose-to-hose and pipe-bridged chains after transfer changes.

### Spray And Nozzles

Active devices:

- Cone nozzle
- Flat nozzle
- Handheld nozzle controller
- Bucket controller
- Mounted variants on supported structures

Supported effects include:

- vanilla and Burnt/Burnt Basic extinguishing
- exposed smoldering-surface extinguishing
- entity push, fluid effects, potion behavior, and damage
- water/lava/ignited-fluid processing
- Create item and depot processing
- farmland hydration
- concrete powder conversion
- optional Dragon's Breath particles/effects
- third-party block hit observation through the nozzle API

Performance architecture:

- Commit `d08f8f1` introduced `SprayAuxiliaryScheduler`,
  `SprayDepotScanner`, and `SprayPerformanceDebug`.
- Fixed nozzles no longer perform the previous large full-AABB block scan for
  every auxiliary interaction on every tick.
- Critical extinguishing remains prioritized.
- Entity, depot, recipe, Sable, and other auxiliary work is budgeted and spread
  across ticks.
- Server configuration is intended to let administrators tune spray work budgets
  for their hardware.

Measured result:

- User testing confirmed at least 25 simultaneous active sprays without material
  TPS loss.
- Around 30-40 sprays remained usable and did not immediately reduce TPS to
  single digits.
- This is the current accepted performance baseline.

Do not regress this architecture by restoring per-nozzle broad scans.

Spray pitfalls:

- Keep client particles separate from authoritative server effects.
- Preserve broad guards against empty `FluidStack` values such as
  `minecraft:empty` with amount `0`; invalid stacks previously caused packet
  encoding disconnects.
- Sable and contraption transforms must query only relevant/intersecting spaces.
- Burnt extinguishing applies to exposed surfaces and must not penetrate blocked
  combustible material.
- F3+B/debug geometry should be calculated only when needed.

### Handheld Firefighting Equipment

Implemented:

- Fire hydrant cabinet with GUI and automated item IO.
- Hose, nozzle, and bucket slots with slot-specific binding invalidation.
- Normal right click opens the cabinet.
- Shift + right click binds the controller.
- Cabinet door opens for GUI use or active binding and has custom sounds.
- Handheld controller renders in first person, third person, and on the back.
- Client options can disable first-person or third-person hose rendering.
- Bound controller spray reuses the fixed spray system.
- Bound controllers dropped into the world convert to a custom neutral entity.
- The entity can be picked up, times out after 30 seconds, clears binding, and
  becomes an unbound item.
- Cabinet Sable anchors use the existing Sable transform compatibility.
- On Create contraptions the cabinet is intentionally passive: assemble/move only.

Current known visual issue:

- The first-person handheld hose's cabinet-side continuation/virtual anchor can
  point toward an incorrect remote location or ground position in some camera
  states.
- Third-person and other cabinet rendering are generally correct.
- Treat first-person and third-person transforms as separate rendering systems.
- Do not solve this by deleting the remote continuation, dropped entity, yaw
  persistence, binding logic, or action suppression.

Historical requirements that must remain:

- `LastYaw`/equivalent orientation persistence prevents the held controller from
  jumping when the player rotates the camera.
- Bound first-person use suppresses incompatible vanilla arm actions.
- The dropped entity must lie horizontally.
- Other players must see held/back-mounted/bound/spraying state.
- Removing the hose or nozzle through GUI or automation breaks binding.
- Bucket-slot changes do not break binding.

### Multipurpose Backtank

- Stores Create-style compressed air and 2B of fluid.
- Wearable and usable as a placed block.
- Powered mode gives the internal logical pump exclusive transfer control.
- Unpowered mode exposes the bottom side as a normal fluid container.
- Input/output modes use a Create-style value setting.
- Creative-tab stack should contain full air (`900/900`).
- Tooltip access is guarded until Create configuration has loaded
  (`f4a8bb1`), preventing startup crashes with equipment-slot mods.
- Fill completion and pneumatic hammer charge completion share the intended sound.

### Pipeline Turbine

- Reads source/network information without modifying fluid transfer.
- Source pumps contribute available stress to a shared network group.
- Turbines share that capacity instead of duplicating source stress.
- Source speed determines turbine rotation; source stress impact determines
  total available stress.
- Updates are coordinator/event driven rather than continuous independent scans.
- Active Create contraption behavior is not required.

Regression tests:

- Reloading a world must not multiply output stress.
- Adding a turbine after reload must redistribute all turbines in the network.
- Pipes between turbine groups must not prevent redistribution.
- Multiple upstream pumps must aggregate deterministically.

### Other Stable Systems

- High-pressure pump: configurable multiplier, Create-compatible behavior.
- Fire pole: vertical extension, downward-only slowdown, Sable-compatible local
  gravity handling, endpoint rims.
- Water intake and bucket controller: infinite-source search, binding,
  blueprint persistence, and Ponder scenes.
- Fluid flow meter: two smooth sliding indicators, pipe-like item/drop behavior.
- Pneumatic hammer: air charging, charge persistence, 3x3 use, split model
  animation, Create-style tooltip.
- Ponder: pump, hose, nozzles, water intake, and bucket-controller scenes using
  saved structure templates and localized text.

## Compatibility Policy

### Sable

- Sable is optional at runtime.
- Do not introduce direct class loading that makes Sable or Aeronautics mandatory.
- Sable schematic compatibility and Sable physical-substructure compatibility
  are separate layers.
- Use existing compatibility backends and `SableStructureClientCompat`; do not
  invent coordinate transforms from visual intuition.

### Create Contraptions

- A Create contraption is not a Sable substructure.
- Default for new blocks: safe assembly and movement only.
- Existing active exceptions include Firehose, supported spray devices, and
  selected mounted storage behavior.

### External Fluid Mods

- Do not assume external pumps inherit Create pump classes.
- Pressure, advertised flow, and executable fluid capability are separate signals.
- Real tank mutation requires a successful executable fluid transfer.

### Burnt

- Extinguish active fire and contacted exposed smoldering surfaces.
- Do not extinguish through blocked faces.

## Configuration Policy

Use `Config.java` for server/gameplay/performance settings and
`ClientConfig.java` for purely visual settings.

Current important configuration areas:

- high-pressure pump multiplier
- non-Create hose transmission distance
- hose range/connection behavior
- spray server budgets and auxiliary intervals
- projectile/particle degradation thresholds
- nozzle behavior and range
- pipeline turbine limits
- first-person and third-person handheld hose rendering

Preserve defaults unless the user requests balancing changes.

## Verification

Minimum build checks:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat build
git status --short
```

Use the main `run` environment unless the user explicitly names another runtime.

Module-specific checks:

### Firehose

- Create pump injection and extraction
- external pump injection and extraction
- multiple pumps feeding one network
- multiple hoses in chains and loops
- direct hose-to-hose and pipe-bridged connections
- empty/full destination behavior and topology updates
- world/Sable/contraption mapping if touched
- total fluid conservation

### Spray

- 1, 5, 20, and 40 simultaneous sprays
- fixed, handheld, Sable, and mounted sources
- extinguishing remains immediate enough
- auxiliary processing may be delayed but must complete
- multiplayer particle and debug synchronization

### Handheld

- GUI and automated slot changes
- bind, force rebind, disconnect, and range disconnect
- first-person held render
- third-person other-player render
- back-mounted render
- drop conversion, entity pickup, timeout, and binding cleanup
- Sable cabinet anchor

### Turbine

- world reload
- add/remove turbines after reload
- multiple pumps and multiple network branches

## Open Work

Confirmed open or deferred work:

1. First-person handheld hose virtual/cabinet anchor still needs isolated rendering
   correction.
2. Firehose third-party transactional hardening is documented locally but not
   implemented.
3. New "chute slide" content has only been discussed; no implementation should
   be assumed.
4. Additional Ponder scenes and API examples may be added later.

Do not revive older issue lists without reproducing them against the current
baseline.

## Multi-Task And Worktree Protocol

Use one Codex task per module when work can proceed independently.

Recommended task branches:

- `codex/firehose`
- `codex/spray-system`
- `codex/handheld-tools`
- `codex/structure-compat`
- `codex/rendering-assets`
- `codex/release`

Rules:

1. Each task uses its own Git worktree and branch.
2. Start from a known clean `main` commit.
3. A module task must state its owned files and non-goals.
4. Avoid simultaneous edits to shared registration, config, or language files.
5. Module tasks build and create local commits but do not push unless explicitly
   instructed.
6. The coordinating task reviews and integrates commits into `main`.
7. Update this document when a module changes project-wide behavior.

Suggested task opening prompt:

```text
Work only on the <module> module in this task's isolated worktree.
Start from main commit <hash> and use branch codex/<module>.
Read HANDOFF.md before editing.
Do not update backups, change the version, or push GitHub.
Preserve all behavior outside the stated module.
```

Suggested task completion prompt:

```text
Run the relevant build/tests, summarize changed behavior and remaining risks,
update the module handoff information, and create a local commit. Do not push.
```

## Final Notes For New Tasks

- Search with `rg`.
- Read the full call path before changing Firehose or spray behavior.
- Use logs to confirm transfer direction and executed amounts.
- Keep rendering fixes isolated from server state and binding logic.
- Prefer structured APIs over guessed coordinate or pressure behavior.
- If a requested change conflicts with a tested invariant, stop and explain the
  conflict before rewriting the subsystem.
