# 0.2.9-beta — World Conversion Edition

**This is a dedicated transition build for preparing existing integrated Create Firefighting Add worlds for the upcoming split into Firefighting and Powertrain. Back up your world before using either conversion method.**

**[Downloads, World cleaner and full instructions on GitHub](https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/world-conversion/0.2.9-beta)**
[GitHub repository](https://github.com/MIKOALOPEX/CreateFireFightingAdd)

### In-game preparation

1. Install this version in your existing integrated environment with its dependencies. Do not install it alongside the split Powertrain mod.
2. An administrator runs `/cffa prepare_split`, then begins saving and quitting within **30 seconds**. On a multiplayer server, an administrator must stop the whole server with `/stop`.
3. Wait for shutdown and processing to finish. The command only queues the request; cleanup starts after the world has closed. If the 30-second window expires, the request is cancelled without scanning, backing up or cleaning the world. `/cffa cancel_split` also cancels it.
4. After shutdown, the mod verifies a backup and creates a separate world named `CFFA-029-CLEANED-...`. The original is not overwritten. Install both split mods and their dependencies before testing this new copy. Do not reopen it with the integrated mod, which may recreate connections.

This method removes the mod's connections, ropes, constraints and related references while retaining blocks and substructures. It does not remap moved registry IDs; legacy Powertrain components may be lost after switching mods.

### World cleaner

The **[separate GitHub directory](https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/world-conversion/0.2.9-beta)** also contains **World cleaner.zip**, a standalone fallback for large worlds or cases that connection cleanup does not resolve. It requires Python 3.10+ and can be used without installing this transition build.

Close the game/server, run `Clean_Old_World.bat`, select an export directory, then select your source world. All processing is local and creates a separate copy. This fallback removes known legacy components and identifiable related constraint-provider blocks; it does not delete whole Sable substructures. Reserve enough disk space and read its guide first.

Built-in processing is limited to 512 MiB per world, 64 MiB per file and 100,000 files. Unsupported formats stop processing instead of publishing an incomplete result.

Entering a world displays an English conversion notice with a red backup reminder and a GitHub link. Cleanup success means a copy was generated, not that every modpack has passed runtime testing. Keep the original world and old environment for recovery.
