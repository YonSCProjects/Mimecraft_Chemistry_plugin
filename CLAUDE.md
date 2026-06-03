# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# ChemCraft

Read this first. It's the map of the project for anyone (human or Claude Code) picking it up.
Deeper docs live in `docs/`. This file stays high-signal.

## What this is
A **PaperMC plugin that teaches chemistry** to middle-schoolers (~11-14) by turning a Minecraft
server into a game. The full loop:
**gather raw materials -> refine into element atoms -> fill a personal periodic-table wall ->
bond atoms into molecules -> react molecules / stack them into materials.**

Working title: **ChemCraft**. Repo: `Mimecraft_Chemistry_plugin`. Package: `com.agurim.chemcraft`.

## Tech stack & build
- **PaperMC 1.21.x**, **Java 21** (required by 1.21).
- **Gradle** (`build.gradle`, Groovy DSL). Build: `gradle build` -> jar in `build/libs/`.
  No wrapper committed yet; run `gradle wrapper` once or open in IntelliJ. `settings.gradle` adds the
  foojay toolchain resolver, so Gradle auto-downloads a JDK 21 if one isn't installed locally.
- **No automated test suite** (no `src/test/`, no `gradle test` target). The "data-level checks"
  mentioned under Status are manual/in-game; verification is by building the jar and smoke-testing
  on a 1.21.x server (see Run / smoke-test below).
- Paper API dep is pinned to `1.21.8-R0.1-SNAPSHOT`. Change it to match the target server.
- **Source is ASCII / UTF-8 and uses Adventure `Component`s for all messages** (no legacy `§`
  colour codes) - a deliberate choice to dodge encoding problems. Keep it that way.

> **Status: builds and enables against Paper 1.21.8.** First built and deployed on a 1.21.8 server on
> 2026-06-04. It compiles clean - the *only* error was a Java generics bug in `ReactionService`
> (`getOrDefault` with a typed default on a wildcard `Map<?,?>`); the **Paper API surface needed no
> changes**. On the live server the plugin enables with no exceptions, generates its config + content
> YAML, and the join flow + command registration work (`/chemcraft` from console correctly replies
> "Players only."). Data-level checks also pass (reactions balanced, no dangling YAML refs). Still
> unconfirmed end-to-end in-game: the full gameplay loop (2x2x2 cube -> material, reactor menu,
> wall discover, bond cycling).

## Run / smoke-test
1. Jar -> `plugins/`, start a 1.21.x server. It writes its config + content YAML into
   `plugins/ChemCraft/`.
2. In `config.yml` set `world` and `plot.origin-x/z` to a flat empty area with ground at
   `plot.ground-y`.
3. Join -> teleported to your plot with a ghost periodic-table wall.
4. As op, place the station blocks (see `config.yml > stations`). Then:
   ```
   /chemcraft give C 8            # place a 2x2x2 carbon cube -> diamond block
   /chemcraft givemol hydrogen_gas 2
   /chemcraft givemol oxygen_gas 1   # react at the FURNACE -> water
   ```
   Full commands: `/chemcraft tp | give | givemol | discover | reset | buildwall | reload` (`/cc`).

## Architecture at a glance
`ChemCraftPlugin#onEnable` builds every service and registers the listeners. Everything
gameplay-related is **driven by YAML** so content can change without recompiling.

Packages (`src/main/java/com/agurim/chemcraft/`):
- `element/` - `Element` (record), `ElementRegistry` (loads `elements.yml`), `Families`
  (family -> block colour), `AtomItems` (atoms as items: PDC tag + `custom_model_data`).
- `world/AtomStore` - location -> element symbol for placed atom blocks (persisted `atoms.yml`).
- `molecule/` - `BondStore` (edge -> bond order, `bonds.yml`), `Molecule`/`MoleculeRegistry`
  (`molecules.yml`, matched by composition), `MoleculeEngine` (the bond graph + valence check +
  recognition), `MoleculeItems` (molecules as sample items).
- `extraction/` - `Recipe` + `ExtractionService` (`extraction.yml`): raw materials -> atoms.
- `reaction/` - `Reaction` + `ReactionService` (`reactions.yml`): molecules -> products.
- `material/` - `MaterialDef` + `MaterialEngine` (`materials.yml`): a 2x2x2 atom cube -> a block.
- `table/PeriodicWall` - per-plot ghost->lit periodic table (Display entities).
- `plot/PlotManager` - grid of per-student plots in one shared world.
- `data/PlayerStore` - per-UUID progress (`players.yml`): plot index, discovered elements.
- `ui/` - `StationMenu`, `ReactionMenu` (chest GUIs).
- `listener/` - `JoinListener` (assign plot + build wall), `PlotProtection` (own-plot-only),
  `StationListener` (open station/reactor GUIs + handle clicks), `AtomBlockListener`
  (place/break atoms -> store, label, engines), `MoleculeListener` (sneak-click bond orders).
- `command/ChemCraftCommand` - admin/debug commands.

### Data files
Read once at enable (bundled in `resources/`, also written to `plugins/ChemCraft/` on first run):
`config.yml`, `elements.yml`, `extraction.yml`, `molecules.yml`, `reactions.yml`, `materials.yml`.
Runtime state (written by the plugin): `players.yml`, `atoms.yml`, `bonds.yml`.

> Note: a bundled YAML is only copied to `plugins/ChemCraft/` **if absent**. To ship new defaults
> to a running server, edit the server copy directly or delete it and restart.

### Key event flows
- **Place atom:** `BlockPlaceEvent` -> `PlotProtection` (NORMAL) decides -> `AtomBlockListener`
  (HIGH, ignoreCancelled) records in `AtomStore`, spawns a label, then
  `MaterialEngine.check` (lattice?) **else** `MoleculeEngine.evaluate` (molecule?).
- **Bond order:** sneak + right-click an atom face -> `MoleculeListener` -> `MoleculeEngine.cycleBond`
  (updates `BondStore`, re-evaluates).
- **Extract / react:** right-click a station/reactor block -> `StationListener` opens
  `StationMenu`/`ReactionMenu` -> click -> `ExtractionService.extract` / `ReactionService.react`.

### NamespacedKeys (PDC)
`tile` (wall labels), `atom` (atom items + atom-block labels), `recipe` (extraction icons),
`molecule` (molecule sample items), `reaction` (reaction icons).

## Conventions - follow these
1. **Content goes in YAML, not code.** New elements/molecules/reactions/materials/stations are
   data edits. See `docs/EXTENDING.md`.
2. **Custom "blocks" are Tier 1:** a vanilla block (family-coloured glass) + a `TextDisplay` label,
   with identity in a location map (`AtomStore`) and a `custom_model_data` value for a future
   resource pack. **You cannot register new block IDs in a server plugin** - don't try.
3. **Per-block identity lives in location maps**, never in block state.
4. **Messages = Adventure `Component`**, ASCII source only.
5. **Listeners that must run after protection use `EventPriority.HIGH` + `ignoreCancelled`.**

## Known issues / caveats
- Not yet built against Paper (see Status above).
- Integer-valence bonding model: an **ionic lattice (salt) flashes "over-bonded"** while building
  up before the 2x2x2 completes; resonance molecules (e.g. ozone) can't be represented. Accepted.
- Molecule recognition is by **composition only** (formula), so isomers aren't distinguished.
- Completing a molecule grants a sample item while leaving the blocks - a minor "matter
  duplication" leak; conservation is taught in the *reaction* step instead.
- `atoms.yml` / `bonds.yml` are saved on every change (fine at classroom scale).
- `setCustomModelData(int)` is deprecated in 1.21.4 but still works.

## Roadmap (all optional)
- **Resource pack** mapping `custom_model_data` (atoms `7000 + atomicNumber`, molecules
  `8000 + hash`) to real icons - pure visual, no code.
- More content via YAML.
- **Classroom layer:** quests, a build-off competition, scoreboards.
- True 3D geometry reveal on molecule completion (show the real bent/tetrahedral shape).

## Where to read more
- `docs/DESIGN.md` - the concept and the **decision log (what we chose and why)**.
- `docs/ARCHITECTURE.md` - class-by-class detail and data formats.
- `docs/EXTENDING.md` - how to add content.
- `CHANGELOG.md` - how the project was built, slice by slice.
- `README.md` - player/teacher-facing overview and gameplay.
