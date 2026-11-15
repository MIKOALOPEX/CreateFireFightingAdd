# Create Firefighting Add

Create Firefighting Add is a NeoForge addon for Minecraft 1.21.1 built around Create-compatible firefighting, fluid transport, pumps, hoses, nozzles, and related Ponder scenes.

This working tree has been split into two independently built mods. Firefighting
keeps its existing `createfirefightingadd` ID and ordinary, non-physical hoses.
Powertrain (`createpowertrain`) owns physical hoses, fluid and stress couplings,
rope devices, Rocker and copycat doors. Neither mod depends on the other.

Build and run the static and pure-logic checks with `gradlew check jar`.
These checks do not launch Minecraft; gameplay validation is a separate step.
For world compatibility and optional offline conversion, see
[the migration guide](Old_World_Cleanner/docs/split-migration.md). Back up existing worlds before changing the installed mod set.

Both hose brackets use Create's wooden or metal bracket items. Ordinary hose
selection and placement use right-click; physical hose selection and placement
use sneak-right-click. Changing slots, swapping hands or dropping the bracket
cancels that bracket's selection.

## Development Status

This project is currently in beta. Some systems may continue to change as compatibility and gameplay behavior are refined.

Development is carried out with a combination of manually written code and AI-assisted programming. AI-assisted changes are reviewed, adjusted, and integrated by the maintainer as part of the normal development process.

## License

This project uses a split license:

- Source code is licensed under the MIT License unless a file states otherwise.
- Art assets are All Rights Reserved unless a file states otherwise. This includes textures, models, Ponder scene structure files, and other visual or media assets distributed with the mod.

See `LICENSE` for the full license terms.

## Notices And Contact

If you believe any part of this repository contains material that infringes your rights, is missing required attribution, or should be distributed under different license terms, please contact the maintainer with the relevant file path, asset or code reference, and a description of the concern so it can be reviewed and resolved.

## World cleaner and transition release

The permanent [world conversion archive](https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/Old_World_Cleanner) contains World cleaner, the 0.2.9-beta transition build and instructions.
