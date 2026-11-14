# Split release preparation

## Status

Release version: `0.3.0-release`. Local build checks and packaging inspection are required before synchronization. Checksums remain local and are not repository assets.

## Packaging

- Run `gradlew check jar` with Java 21 in each project.
- Publish only the distributable mod JAR from `build/libs`, with its SHA-256 checksum.
- Do not publish run directories, worlds, logs, local notes, validation output, extracted
  dependency code, temporary scripts, development JARs or machine-specific paths.
- Keep the existing registry IDs and save formats. This cleanup does not migrate worlds.
- Preserve the source and asset licensing notices. Dependency JARs are not release assets.

## Validation

Automated checks cover compilation and the repository's pure-logic regression suite.
The maintainer has reported the latest combined-environment gameplay changes as generally
passing. This is not a full fresh-install, dedicated-server or all-mod-version certification.
Before publication, confirm single-mod and combined installations with the declared dependencies,
plus old integrated-version coexistence where advertised. Use copies of worlds for upgrade tests.

## Recent changes

- Independent firefighting content and optional Powertrain hose interoperability.
- Pump/relay sources take priority over transient capability traffic, preserving pump range.
- Corrected nozzle placement orientation and sneak reversal.
- Deferred fluid capability, pipe shape and network refresh on nozzle placement, rotation and load.

Repository: https://github.com/MIKOALOPEX/Create-FireFightingAdd
