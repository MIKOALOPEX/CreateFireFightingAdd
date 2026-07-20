# Create Firefighting Add Handoff

This document is a compact project handoff for future Codex tasks. It captures the current working context, important design decisions, known pitfalls, and suggested next steps so new chats can continue without rereading the full history.

## Project Goal

Create Firefighting Add is a NeoForge 1.21.1 addon for Create. The mod focuses on firefighting-themed Create-compatible equipment:

- fluid transport through fire hoses and cabinets
- fixed and handheld nozzles
- high-pressure pumps and fluid-driven kinetic devices
- fire extinguishing, fluid spray interactions, and recipe-like processing
- Create Ponder scenes and compatibility with other structure/fluid mods

Current version should remain `0.2.1-beta` unless the user explicitly asks to bump it.

## Development Rules From The User

- Do not clean backup, temp, or run files unless explicitly asked.
- Do not update backup folders unless explicitly asked.
- Do not push to GitHub unless explicitly asked.
- When editing, prefer small targeted changes. Avoid broad rewrites when only one behavior is requested.
- Preserve tested behavior. The user tests in-game frequently and reports exact visual or logic problems.
- The project has mixed human-written and AI-assisted code. README/license already reflect this.
- Code is MIT; art assets are All Rights Reserved.

## Current Repository State

- Root: `D:\CreateAdd\CreateFireFightingAdd`
- GitHub remote: `https://github.com/MIKOALOPEX/CreateFireFightingAdd.git`
- Current branch used recently: `main`
- Last known pushed commit before this handoff: `ea2efd4 Improve handheld nozzle entity handling`
- Minecraft: `1.21.1`
- NeoForge: `21.1.228`
- Create: `6.0.10-280`
- Current mod version: `0.2.1-beta`

Important sibling or external paths used during development:

- `D:\CreateAdd\assets` and `D:\CreateAdd\CreateAssets`: source art assets provided by the user.
- `D:\CreateAdd\CreateFireFightingAdd\tempbackup`: local milestone backups. Do not touch unless asked.
- `D:\CreateAdd\CreateFireFightingAdd\run`: main test run directory.
- `D:\CreateAdd\CreateFireFightingAdd\run-ved`: alternate VED test run directory.
- `C:\Users\ASUS\Desktop\Simulated-Project-main`: reference project used for simulated/spring behavior research.
- `C:\Users\ASUS\Desktop\burnt`: Burnt/Burnt Basic reference project for fire/smoldering compatibility.

## Directory Map

Main code:

- `src/main/java/com/mikoalopex/createfirefightingadd/CreateFireFightingAdd.java`: central registration, event hooks, Create compatibility registration, client setup.
- `src/main/java/com/mikoalopex/createfirefightingadd/Config.java`: server/common config.
- `src/main/java/com/mikoalopex/createfirefightingadd/ClientConfig.java`: client config.
- `src/main/java/com/mikoalopex/createfirefightingadd/PartialModels.java`: Create partial model references.
- `src/main/java/com/mikoalopex/createfirefightingadd/RemapManager.java`: old-name remap support.

Public APIs:

- `api/fire_hose`: hose connection API and state helpers.
- `api/nozzle`: spray hit/interact API for third-party blocks.
- `api/handheld`: handheld nozzle binding API.
- `api/backtank`: multipurpose backtank fluid/air consumption API.
- `api/kinetics`: pipeline turbine source/claiming interfaces.

Content packages:

- `content/blocks/fire_hose`: fire hose endpoint block, item placement, dynamic hose rendering, connector, mounted storage/movement behavior.
- `content/blocks/fire_pole`: fire pole movement/slow-fall behavior.
- `content/blocks/flow_meter`: old flow meter plus newer fluid flow meter behavior/rendering.
- `content/equipment/backtank`: multipurpose backtank item/block, air/fluid storage, pump-like behavior.
- `content/equipment/handheld`: fire hydrant cabinet, GUI, handheld nozzle controller item, dropped controller entity, hose rendering, spray handling.
- `content/fluids/nozzle`: fixed cone/flat nozzle and bucket controller spray logic, particles, effects, sounds, debug rendering.
- `content/fluids/water_intake`: water intake and bucket controller binding.
- `content/items`: pneumatic hammer and water-intake binding item.
- `content/kinetics/pump`: high-pressure pump.
- `content/kinetics/turbine`: pipeline turbine and deterministic source sharing.
- `content/ponder`: Create Ponder plugin, index, and scenes.

Compatibility packages:

- `integration/sable`: optional Sable physical substructure compatibility. Must run without Sable present.
- `integration/sableschematic`: optional Sable schematic/blueprint save mapping, reflection-based.
- `integration/burnt`: optional Burnt/Burnt Basic fire and smoldering compatibility.
- `mixin`: defensive compatibility hooks. Avoid adding new mixins unless the behavior cannot be done with events/APIs.

Resources:

- `src/main/resources/assets/createfirefightingadd/models`: block/item models.
- `src/main/resources/assets/createfirefightingadd/textures`: textures, GUI images, item textures.
- `src/main/resources/assets/createfirefightingadd/ponder`: saved Ponder scene structures.
- `src/main/resources/assets/createfirefightingadd/lang`: `zh_cn.json`, `en_us.json`.
- `src/main/resources/assets/createfirefightingadd/sounds.json` and `sounds/*.ogg`: custom sounds.
- `src/main/resources/data/createfirefightingadd`: recipes, loot tables, remaps.

Docs:

- `docs/handheld_fire_hydrant_system_plan.md`: design plan for handheld hydrant/nozzle system.

## Implemented Feature Summary

### Fire Hose

- Fire hose has two independent endpoint blocks with connection state.
- Endpoints can be disconnected without destroying blocks.
- Players can reconnect endpoints by using the hose item on two compatible endpoints.
- Shears can sever a connected hose; this plays a cutting sound.
- Hose color can be changed using black/white dye.
- Hose is compatible with Create fluid networks and has support for non-Create external fluid input.
- Hose now has APIs intended for external reconnection and blueprint compatibility.
- Hose supports Sable schematic mapping and Sable physical substructure coordinate mapping.
- Hose dynamic structure behavior has been heavily tuned. Treat it carefully.

Key decision:

- Fire hose does not try to be a full custom pressure system anymore. It mostly cooperates with Create pressure and external flow observations.
- Create-native behavior and non-Create behavior are separated where possible.

Important pitfall:

- Fire hose facing and apparent visual orientation can be misleading. The current hose logic has been tested as correct in its own environment; do not "fix" it based only on intuition about facing.
- Connected hose state must avoid copied/duplicated contraption endpoints forming one-to-many links.
- Do not remove old fallback fields unless explicitly safe. New saves do not need old compatibility, but defensive loading should not crash.

### Nozzles And Spray

- Cone nozzle and flat nozzle are the active nozzle types.
- Old firefighting nozzle remains as legacy/remap-compatible content but should not be exposed as a normal player item.
- Spray supports water, lava, diesel-like ignited fluids, potion-like behavior, and Dragon's Breath when Create Dragons Plus is present.
- Spray extinguishes vanilla fire and Burnt smoldering/burning surfaces.
- Spray handles line-of-sight/occlusion to prevent water extinguishing through walls.
- Spray can push entities, apply effects, process Create-compatible item interactions, hydrate farmland, and convert concrete powder to concrete.
- Spray processing has a public API so other mods can observe spray hits or register interactions without interfering with built-in behavior.
- Spray projectile count is throttled by config with performance as a priority.

Important pitfall:

- The spray path has server-side effects, client particles, debug line rendering, and optional Sable/Create contraption transforms. Changing only one layer can desync visuals and behavior.
- Empty fluid stacks such as `minecraft:empty` with amount `0` caused packet encode crashes before. Keep broad empty-fluid guards in place.
- Burnt compatibility should extinguish only exposed/surface smoldering; do not make water penetrate burned blocks.

### High-Pressure Pump

- High-pressure pump is intended to behave like a Create pump with configurable multiplier.
- Default multiplier is `2x`, corresponding to the old intent of 32-block transfer distance compared with Create's 16.
- It was normalized to be easier for hose/turbine logic to identify.
- It should not appear in vanilla building-block tabs.

### Pipeline Turbine

- Pipeline turbine reads upstream pressure/source information and outputs kinetic stress.
- The source sharing coordinator was redesigned so multiple turbines on one fluid network cannot duplicate infinite stress.
- Stress is distributed by network/source group, not by each turbine independently.
- Source pump speed is used for output rotation. Source pump stress impact is used for total available stress.
- Updates should be event/coordinator driven where possible, not per-tick network scans.
- It does not need active behavior on Create contraptions; only safe assembly/movement is required unless the user asks otherwise.

Important pitfall:

- Re-entering a world previously caused turbine stress to double and then contaminate downstream distribution. If touching this system, test world reload plus adding/removing turbines on both sides of intermediate pipes.
- Multiple pumps can merge into one network; stress contribution should sum from all pressure-providing pumps, while speed attribution must remain deterministic.

### Fire Hose Connector

- English ID is `fire_hose_connector`.
- It is a pipe-like full block with modes: idle, fixed, free.
- It can cache and reconnect hose endpoints.
- Free mode scans for a valid empty endpoint if the cached target is gone or out of range.
- Sable/world endpoint combinations are supported, while Create contraptions are intentionally not part of this connector logic.
- Core connection logic was extracted enough to be reused by future blocks.

Important pitfall:

- Cache should update only when a new valid connection is established, not merely when a disconnect notification happens.

### Water Intake And Bucket Controller

- Water intake searches nearby infinite water sources, including physically lower ground-level sources.
- Water intake can bind to bucket controller.
- Bucket controller can receive water and spray downward.
- Binding is saved and blueprint-compatible.
- Both have Ponder scenes.

### Fire Pole

- Vertical `fire_pole` block, shaft-like placement/extension behavior.
- Slows players only while they are moving downward near the pole.
- Does not block upward jumping/climbing behavior.
- Fall damage mitigation around the pole.
- Sable compatibility was adjusted to avoid heavy coordinate math and to work with tilted/inverted substructures.
- End caps/rims appear when not connected to another pole.
- Allows Create contraption assembly/movement at a minimum.

### Multipurpose Backtank

- Wearable backtank that stores Create-style compressed air plus 2B fluid.
- As a block, it acts like an internal tank plus logical pump when powered from the top.
- Input/output modes controlled by a Create-style value setting/dial.
- When unpowered, bottom face is open as a normal fluid container.
- It can interact with fire hose after composite external input detection fixes.
- Full creative item should show full air value `900/900`.
- Worn attributes should match intended backtank-like protection/toughness/knockback resistance.
- Fill-complete sound uses the same sound as pneumatic hammer charge completion.

Important pitfall:

- Fire hose logic was confirmed correct; many later fluid issues were actually backtank/external pump behavior conflicts.
- When powered, the backtank's internal logical pump has priority and external fill/drain should not bypass it.

### Pneumatic Hammer

- Handheld tool with base and cog partial models.
- Normal mode uses stone-tier mining speed but keeps intended mining level behavior.
- Charge mode takes backtank air, plays steam sound, and enables 3x3 mining/area attack behavior.
- No durability after later design change.
- Cog rotates slowly when held, faster while charging, and spins quickly on use.
- Tooltip is Create-style shift-to-view summary.

### Flow Meter

- Old test `Flow Meter` was hidden from creative tab but retained.
- New normalized fluid flow meter exists with panel and two sliding pointers.
- Pointers animate smoothly from previous value instead of resetting to zero every update.
- Collision box was pulled inward by 1px while model stayed unchanged.

### Fire Hydrant Cabinet And Handheld Nozzle Controller

This is the newest and most volatile feature area.

Implemented pieces:

- `fire_hydrant_cabinet` block with inventory GUI.
- Cabinet stores hose/nozzle/bucket-related slots.
- Cabinet door has open/close states and sound.
- Cabinet can be bound by a handheld nozzle controller using Shift + right click.
- Normal right click opens GUI.
- Any player can interact with cabinet; high freedom is preserved.
- Removing hose/nozzle from GUI or automation should break active binding. Bucket slot input/output should not break binding.
- Hopper/Create item IO support is expected/partly implemented for cabinet inventory automation.
- Handheld nozzle controller can render in hand, on back, and as a dropped world entity.
- Bound controller spray is intended to reuse fixed nozzle behavior and network particles/debug lines as much as possible.
- Client config can disable handheld hose rendering in first person or third person to avoid camera mod issues.
- Dropped bound controller converts to a custom neutral entity instead of an ordinary item entity.
- Dropped controller entity can be picked up by right click.
- Entity has a 30-second lifetime, then clears binding and becomes an unbound dropped item.
- Dropped controller entity has a translated entity name.
- Cabinet in Sable substructures now transforms both hose anchor position and normal through Sable client render pose.
- Cabinet on Create contraptions should only be assembled/moved; no interaction/runtime behavior.

Important current pitfalls:

- First-person handheld hose rendering is hard. The stable visual approach has used a "fake hose" near the held item plus a remote continuation to hide impossible first-person attachment seams.
- Third-person and first-person transforms are different; do not assume a coordinate fix for one applies to the other.
- The user explicitly rejected deleting the dropped entity implementation when only action animation rollback was requested. Be precise and minimal when reverting.
- `LastYaw`/yaw persistence for handheld item/controller existed to fix rotation while the player turns. Do not remove this behavior casually.
- Dropped controller entity orientation must be horizontal, never vertical.
- Sable moving substructure support for the dropped controller entity may still need more investigation. The user compared it to Create's `cardboard_package` entity and expects similar moving-on-substructure behavior.
- Current custom dropped entity had a recent severe bug where bound item toss disappeared entirely. If that resurfaces, inspect `HandheldNozzleControllerEntity.tryConvertDroppedItem`, `tryConvertTossedItem`, entity registration, and `defineSynchedData`.

### Ponder

Implemented Ponder scenes include:

- high-pressure pump range and pressure
- nozzle basic spray, fire/processing, fluid variants
- water intake and bucket controller
- fire hose connection and relay/basic usage

Ponder issues already solved:

- Text localization keys needed to be real translated strings.
- Structure templates should use provided NBT scenes, not temporary generated scenes.
- Scene bounding boxes, camera, and highlighted positions were tuned by user screenshots.
- Pig movement was replaced with Ponder bird movement for smoother demonstration.

## Compatibility Summary

### Sable

- Sable is optional at runtime. The mod must launch without Sable or Aeronautics.
- Sable compatibility was moved toward reflection/backend checks to avoid hard runtime dependency.
- Sable schematic compatibility is separate from Sable physical substructure compatibility.
- Do not confuse Sable substructures with Create contraptions.
- For Sable render positions, use the existing `SableStructureClientCompat` and backend abstractions, not guessed manual transforms.
- For Sable physical behavior, inspect `dev/ryanhcode/sable` references included in the project before changing behavior.

### Create Contraptions

- "Dynamic structure" means Create contraptions such as minecart assembly, not Sable physical structures.
- Default policy set by the user: unless explicitly requested, new blocks only need minimum safe Create contraption compatibility: assemble/move without crashing, not necessarily function while mounted.
- Exceptions already implemented:
  - fire hose has significant contraption mapping/connection logic
  - fixed nozzles and bucket controller have mounted spray/fluid storage behavior
  - multipurpose backtank has some mounted storage/fluid interaction behavior
  - fire hydrant cabinet should now only move/assemble and not interact

### Burnt/Burnt Basic

- Spray extinguishes active fire and smoldering surface state.
- The desired behavior is no penetration through burned/flammable blocks; only exposed contacted surfaces are extinguished.

### Other Fluid Mods

- Fluid Logistics and other non-Create pumps can write pressure/flow in ways that differ from Create.
- Fire hose external source detection uses composite checks to avoid breaking multi-pump/source logic.
- Do not assume every pump inherits Create pump classes.

## Configuration

Existing config work includes:

- high-pressure pump multiplier relative to Create pump
- non-Create pump default hose push distance
- spray projectile/performance throttles
- nozzle buildup/range/fire chance behavior
- pipeline turbine output bounds/source-sharing settings
- handheld hose first-person and third-person render toggles

When adding config:

- Client-only visual options go in `ClientConfig`.
- Server/gameplay behavior belongs in common/server `Config`.
- Preserve current defaults unless the user asks to rebalance.

## Assets And Models

Assets have often been provided under `D:\CreateAdd\assets` with Chinese folder names, then imported/renamed into standard resource paths.

Important model conventions:

- Pipeline turbine has separate `x` and `y` block models.
- Fire hose connector has separate `x` and `y` block models.
- Handheld nozzle controller has split partials: base, handle, cog, cone, flat, and show/item variants.
- Cabinet has separate block partials: base/box, door, hose, cone, flat.
- GUI cabinet slots are hand-aligned; do not infer slot positions from icons. The user explicitly defined:
  - left slot: nozzle
  - upper-right slot: hose
  - lower-right slot: bucket
- For GUI alignment, "slot" means actual interactive slot; "UI texture" means the overlaid item/slot art.

## Known Technical Pitfalls

- The project contains many old run logs and temp files. Ignore them unless asked.
- Some generated or historical language/resource files may look odd in terminal previews; validate with JSON parsing before assuming corruption.
- Gradle may fail if a stale local proxy is configured. User's proxy is not fixed.
- `sable-schematic-api` should not be bundled as a hard runtime dependency unless explicitly decided. Prefer optional/reflection compatibility.
- Sable API versions may vary. Use API presence checks rather than hard version checks where possible.
- Packet encoding crashes often came from invalid/empty fluid stacks. Guard broadly against empty fluid data.
- Client/server split is critical:
  - visual hose/particle/debug rendering is client-side
  - spray effects, inventory, binding validity, and entity spawning are server-side
  - multiplayer needs explicit state sync for handheld/back-mounted/spraying states
- Create's PFI-style fluid behavior aggregates mounted fluid storages. Do not reinvent it unless necessary.
- Create's stress APIs can report impact differently depending on block and generated stress. Validate with actual in-game goggles/Jade numbers.

## Testing Checklist

Before publishing or pushing behavior-heavy changes, test:

- `.\gradlew.bat compileJava`
- Launch main dev client and enter existing test world.
- Launch VED client if multiplayer/client sync is involved.
- Fire hose:
  - Create pump injects into hose
  - Create pump extracts from hose
  - non-Create pump injects/extracts
  - hose-to-hose direct and pipe-bridged chains
  - world to Sable, Sable to world, Sable to Sable if relevant
  - Create contraption assembly/disassembly if touched
- Nozzles:
  - fixed world spray
  - mounted/contraption spray if touched
  - Sable spray if touched
  - water/lava/potion/dragon breath if touched
  - Burnt active fire and smoldering
  - item processing rate is not instant unless intended
- Handheld cabinet/controller:
  - normal right click opens GUI
  - Shift + right click binds/unbinds
  - first-person held render
  - third-person other-player render
  - back-mounted render
  - dropped entity render, pickup, timeout, and binding cleanup
  - cabinet inventory automation changes break binding only when hose/nozzle are removed
  - cabinet on Sable substructure hose anchor follows the rendered cabinet
  - cabinet on Create contraption only moves/assembles
- Pipeline turbine:
  - world reload does not duplicate stress
  - adding/removing turbines updates old and new turbines
  - multiple turbines on one network share source capacity
  - multiple source pumps aggregate correctly

## Unfinished Or Risky Areas

High priority:

- Dropped handheld nozzle controller entity on fast-moving Sable substructures still needs a robust strategy. User expects behavior closer to Create's entity-form cardboard package.
- Verify that the dropped controller entity no longer disappears instantly when tossed. If it does, inspect entity conversion and registration before touching rendering.
- Multiplayer handheld controller state: all players should see another player's bound hose, back-mounted state, held state, and active spray.
- Cabinet automation: ensure all item IO paths consistently trigger binding validation when hose/nozzle slots change.

Medium priority:

- First-person handheld hose visuals are acceptable only if seams are hidden. If refining, isolate first-person transforms from third-person transforms.
- Cabinet/cabinet GUI model alignment may still need screenshot-guided tuning.
- Sable + handheld cabinet + hose rendering should be retested after the recent render-normal fix.
- Create contraption support should stay intentionally minimal for new blocks unless the user requests active behavior.

Lower priority:

- Additional Ponder scenes for newer equipment may be needed later.
- More tooltip polish and localization cleanup.
- More public API examples for third-party spray interaction.

## Suggested Next Steps

1. If continuing the handheld entity issue, start by reading:
   - `content/equipment/handheld/HandheldNozzleControllerEntity.java`
   - `content/equipment/handheld/HandheldNozzleControllerEntityRenderer.java`
   - `content/equipment/handheld/HandheldNozzleHoseRenderer.java`
   - `content/equipment/handheld/HandheldNozzleSprayHandler.java`
   - `api/handheld/HandheldNozzleBindingApi.java`

2. Reproduce the latest reported issue in game before changing code:
   - toss a bound handheld controller
   - verify custom entity appears
   - verify orientation is horizontal
   - verify right-click pickup
   - verify hose remains connected until timeout/range break

3. If solving Sable moving-surface behavior for the dropped entity:
   - first inspect how Sable updates ordinary entities on sublevels
   - compare with Create's entity-form cardboard package
   - avoid hard Sable runtime dependencies
   - prefer optional backend/reflection style if API calls are needed

4. For any new block/item:
   - register only in the mod's own creative tab
   - add Create-style tooltip if it is a tool/equipment item
   - decide explicitly whether it runs on Create contraptions or only assembles/moves

5. Before committing:
   - run `git status --short`
   - run `.\gradlew.bat compileJava`
   - avoid staging backups, run logs, temp files, or built jars unless explicitly requested

## Useful Commands

```powershell
cd D:\CreateAdd\CreateFireFightingAdd
git status --short
.\gradlew.bat compileJava
.\gradlew.bat build
git log --oneline -5
```

Use `rg` for searches. Avoid broad file rewrites when one target file is enough.
