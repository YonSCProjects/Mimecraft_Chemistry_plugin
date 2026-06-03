# Changelog

Built in four slices. Versions are pre-release working milestones.

## v0.4.0 - Reactions & materials (Act 3)
- Reactor station (`FURNACE`) with a reaction GUI; 5 balanced reactions (`reactions.yml`).
- Molecules can be reacted into products; the balanced equation and conservation of mass are shown;
  effects are particle-only (no real explosions).
- Completing a molecule now also grants a bottled **molecule sample** (`MoleculeItems`) for reactions.
- **Materials:** a 2x2x2 atom cube crystallises into a real block (`materials.yml`, `MaterialEngine`);
  uniform cubes (carbon->diamond, metals->blocks) and a Na/Cl checkerboard->salt crystal.

## v0.3.0 - The molecule engine (Act 2)
- Adjacent atom blocks bond; `MoleculeEngine` reads the connected cluster as one molecule.
- Valence satisfaction detects completion; recognition via composition (`molecules.yml`, 11 molecules).
- **Bond order** by sneak + right-click a face (single/double/triple); over-bonding is flagged.
- Completion -> fact + reward + particles; `BondStore` persists bond orders.

## v0.2.0 - Atoms & extraction (part of Act 1)
- Atoms as resource-pack-ready items (`AtomItems`); placeable as Tier-1 blocks (`AtomStore` + labels).
- Four extraction stations on the reactivity ladder (`extraction.yml`, station GUI): panning,
  air-separation, smelter (ore + carbon), electrolysis (needs redstone).
- Extracting an element discovers it, lights its wall tile, and shows a fact.

## v0.1.0 - Plots, wall, progress (foundation of Act 1)
- Per-student plots in one shared world (`PlotManager`); plot protection.
- Personal ghost->lit periodic-table wall (`PeriodicWall`, Display entities).
- Per-UUID progress (`PlayerStore`); 25 elements defined (`elements.yml`).
- `/chemcraft` admin/debug commands.
