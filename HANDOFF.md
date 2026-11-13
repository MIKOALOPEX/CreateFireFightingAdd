# Current handoff — 0.2.9-beta transition

Project: D:/CreateAdd/CreateFireFightingTransition029. Branch: codex/legacy-world-cleanup.

- Current player documentation: release/README.md; full local-tool scope: docs/legacy-world-cleanup.md.
- Current artifacts: release/createfirefightingadd-0.2.9-beta.jar, release/World cleaner.zip and release/0.2.9-beta-SHA256.txt.
- World cleaner prompts are English: red risk/copy/space warning, yellow local-only notice, export selection before source selection, close-window instruction on completion. Explicit Source/Output/DestinationRoot and DryRun remain supported.
- In-game command and 30-second expiry are implemented; the login message is fixed English with a red backup warning. Runtime world loading is for the user to test. Do not launch Minecraft or process actual saves without further authorization.
- Never delete whole Sable substructures. Java cleanup retains blocks; the offline tool may remove identified components/providers in a new copy only.
- Java checks and JAR build passed previously; latest launcher and cleanup suite: 30 synthetic tests passed. No game code changed in the packaging/launcher revision.
- Old handoff and release notes: docs/history/. Historical binaries: release/history/. Archived local logs: D:/CreateAdd/.local-archive/2026-11-13-transition-release/manifest.json.
- World folders and environment backups retained: names alone do not establish that they are disposable. No world data was merged or deleted.
- Build caches, dependencies and runnable test environments retained. GitHub publication uses the fixed world-conversion/0.2.9-beta/ directory with the JAR, World cleaner.zip, checksums and bilingual instructions. Modrinth description: docs/modrinth-0.2.9-beta.md. Actual Modrinth upload remains with the maintainer.
