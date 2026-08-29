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
Lives in the **`chemcraft/`** module of a Gradle multi-project (see Repo layout).

## Repo layout
This repo is a **Gradle multi-project holding two unrelated teaching plugins** that share build
config, conventions and docs - not a library and a consumer. They are **never loaded together**:
each runs on its own dedicated server, so plot grids, worlds and join flows can't collide.

```
build.gradle       shared config: Paper dep, toolchain, UTF-8, the -Pmc dual-target switch
settings.gradle    includes :chemcraft and :robotics
chemcraft/         ChemCraft - this document describes this module
robotics/          RoboCraft - the robotics workshop plugin (see robotics/CLAUDE.md)
docs/              shared design/architecture docs
```

**No shared `core` module yet, deliberately.** The generic classroom infrastructure
(`PlotManager`, `PlotShield`, `Whisper`, `Ranks`, `Credit`, `Guide`) is ~400 LOC of ChemCraft's
~4000 and couples only to the plugin main class, used purely as a `JavaPlugin`. Extracting a
platform from one example encodes ChemCraft's accidents as the interface, so **robotics copies what
it needs first**; the shared core gets extracted once a second implementation shows where the real
seam is. Because the coupling is a mechanical `ChemCraftPlugin` -> `JavaPlugin` swap, waiting is
cheap.

## The two dev servers
They are never loaded **into the same server** - separate worlds, plot grids and join flows - but
they are meant to run **at the same time**, so a class can be split across both. Separate folders,
separate ports, no clash.

| | ChemCraft | RoboCraft |
|---|---|---|
| Folder | `C:.2_ChemCraft` | `C:.2_RoboCraft` |
| Students join | `localhost` | `localhost:25566` |
| Game port | 25565 (the default) | 25566 |
| RCON port | 25576 | 25575 |
| Start | `start.bat` | `start.bat` |

Both are Paper 26.2 on Java 25, offline-mode, superflat, with `plot.ground-y: -61` - a flat world's
top solid block, not the shipped default of 64, which would leave the wall floating.

**RoboCraft holds RCON 25575 on purpose:** that is the fixed target of the Minecraft MCP bridge in
a Claude Code session, and RoboCraft is where the assistant loop is being developed. Anything on
the other port is reached with `robotics/tools/rcon.ps1 -Port 25576 "<command>"`, which works for
either server and either PowerShell edition.

ChemCraft needs `/cc region build` run once, by an op, on first join - the ten gathering zones are
not built automatically and the command is player-only, so it cannot be done over RCON.

## Tech stack & build
- **Dual target.** `gradle build` -> Paper **1.21.8** / Java 21 (legacy class server).
  `gradle build "-Pmc=26.2"` -> Paper **26.2 stable** / Java 25, jar `ChemCraft-x-mc26.2.jar`.
  Minecraft switched to year-based versions after 1.21.11; 26.2 needed **zero source changes**.
  The 26.2 build needs Gradle 9.2+ (Gradle 8.9 can't drive a 25 toolchain); the JDK 25 itself is
  auto-provisioned by the foojay resolver, so no `JAVA_HOME` juggling.
  `plugin.yml`'s `api-version` is templated from the same property.
  **The 26.2 server is the primary dev target.**
- **Gradle multi-project** (Groovy DSL). All shared config - Paper dep, toolchain, UTF-8, the
  dual-target switch - lives in the **root `build.gradle`**; `chemcraft/build.gradle` holds only the
  version and the jar name. Build from the repo root; jar lands in **`chemcraft/build/libs/`**.
  `gradle :chemcraft:build` builds just this plugin. No wrapper committed yet; run
  `gradle wrapper` once or open in IntelliJ. `settings.gradle` adds the foojay toolchain
  resolver, so Gradle auto-downloads the JDK 21/25 toolchain if it isn't installed locally - it
  must be **1.0.0+**, since 0.8.0 crashes on Gradle 9 (`JvmVendorSpec.IBM_SEMERU`).
- **No automated test suite** (no `chemcraft/src/test/`, no `gradle test` target). The
  "data-level checks" mentioned under Status are manual/in-game; verification is by building the
  jar and smoke-testing on a 1.21.x server (see Run / smoke-test below).
- Paper API dep is pinned to `1.21.8-R0.1-SNAPSHOT` in the **root** `build.gradle`. Change it there
  to match the target server.
- **All messages use Adventure `Component`s** (no legacy `§` colour codes) - a deliberate choice to
  dodge encoding problems. **Player-facing text is Hebrew (UTF-8);** keep it that way. The root `build.gradle`
  pins the compiler and resource encoding to UTF-8 (`options.encoding`, `filteringCharset`), so
  Hebrew in `.java` and `.yml` builds correctly. Keep non-displayed tokens in Latin/ASCII: element
  symbols (H, O), chemical formulas (H2O), command keywords (`/cc give`), YAML keys/ids, and Bukkit
  `Material`/enum names.

> **Status: builds and enables against Paper 1.21.8.** First built and deployed on a 1.21.8 server on
> 2026-06-04. It compiles clean - the *only* error was a Java generics bug in `ReactionService`
> (`getOrDefault` with a typed default on a wildcard `Map<?,?>`); the **Paper API surface needed no
> changes**. On the live server the plugin enables with no exceptions, generates its config + content
> YAML, and the join flow + command registration work (`/chemcraft` from console correctly declines
> with "לשחקנים בלבד."). Data-level checks also pass (reactions balanced, no dangling YAML refs).
>
> **Also verified on Paper 26.2 build 116 / Java 25 on 2026-08-24** - the primary dev target, which
> until now had only ever been *built* for, never run. Enables clean (25 elements, 11 molecules,
> 5 reactions, 10 regions), writes all ten YAML files, declines console commands correctly, and
> disables cleanly. Zero exceptions, no warnings. Driven by `robotics/tools/smoke-test.ps1`, which
> works for either plugin.
>
> Still unconfirmed end-to-end in-game: the full gameplay loop (2x2x2 cube -> material, reactor
> menu, wall discover, bond cycling).

## Run / smoke-test
1. Jar -> `plugins/`, start a 1.21.x server. It writes its config + content YAML into
   `plugins/ChemCraft/`.
2. In `config.yml` set `world` and `plot.origin-x/z` to a flat empty area with ground at
   `plot.ground-y`.
3. Join -> teleported to your plot. On first join you get a welcome title, a `/cc guide`
   walkthrough, a starter kit of raw materials, and a labeled row of station blocks built next to
   your ghost periodic-table wall (`StationKiosk`) - no op setup needed.
4. Walk to the wall, use the kiosk stations to extract atoms, and play the loop. For quick admin
   testing you can still shortcut with:
   ```
   /chemcraft give C 8            # place a 2x2x2 carbon cube -> diamond block
   /chemcraft givemol hydrogen_gas 2
   /chemcraft givemol oxygen_gas 1   # react at the FURNACE -> water
   ```
   Full commands: `/chemcraft guide | kit | tp | give | givemol | discover | reset | buildwall | reload` (`/cc`).

## Architecture at a glance
`ChemCraftPlugin#onEnable` builds every service and registers the listeners. Everything
gameplay-related is **driven by YAML** so content can change without recompiling.

Packages (`chemcraft/src/main/java/com/agurim/chemcraft/`):
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
Read once at enable (bundled in `chemcraft/src/main/resources/`, also written to
`plugins/ChemCraft/` on first run):
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
4. **Messages = Adventure `Component`**; player-facing text in Hebrew (UTF-8), with symbols /
   formulas / command keywords / YAML ids / `Material` names kept in Latin.
5. **Listeners that must run after protection use `EventPriority.HIGH` + `ignoreCancelled`.**

## Known issues / caveats
- **Never `/cc region build` with `regions.flatten: true` on a server whose zones were
  hand-sculpted** - it re-flattens the terrain. The dev server ships `flatten: false`.
- Integer-valence bonding model: an **ionic lattice (salt) flashes "over-bonded"** while building
  up before the 2x2x2 completes; resonance molecules (e.g. ozone) can't be represented. Accepted.
- Molecule recognition is by **composition only** (formula), so isomers aren't distinguished.
- Completing a molecule grants a sample item while leaving the blocks - a minor "matter
  duplication" leak; conservation is taught in the *reaction* step instead.
- `atoms.yml` / `bonds.yml` are saved on every change (fine at classroom scale).
- `setCustomModelData(int)` is deprecated in 1.21.4 but still works.

## Roadmap
### Next up (campaign Phase 3, designed - see docs/DESIGN.md)
- **Class silo:** a shared cumulative goal (bossbar, e.g. "20 ammonia for the fertilizer silo"),
  capped at 50% per student, deposits are PDC-tagged molecule samples only, rewards are
  flavour/speed - **never elements** (a collective gate on individual progress breaks absence
  resilience). Blocked on a decision: the molecule dup leak must close first, or samples become
  farmable silo currency.
- **Co-op reactions:** optional `min-operators: 2` on *new bonus* reactions only (the glucose
  capstone `6 CO2 + 6 H2O -> glucose + 6 O2`), escrow per reactor block, outputs split
  proportionally to what each student contributed (no flat payout - that's a currency printer).

### Parked ideas
- **In-game AI lab assistant (עוזר/ת מעבדה)** available to every student: a log-tailing agent
  answers chat questions per-student, using their real state (position, inventory,
  `players.yml` progress). **NOT BUILT.** Reads are feasible today over RCON/MCP, but delivery
  is not: on 26.2 `tell`/`tellraw`/`msg` execute silently over RCON and deliver nothing (only
  `say` and `title`/`actionbar` work), so private replies need a plugin-side command
  (e.g. `/cc whisper`) rather than a console command. Open questions: Socratic hints vs.
  answers, rate limiting, and surfacing the questions to the teacher as formative assessment.
- **`/cc progress`** teacher overview (per-student tiles lit / assists / seen count) - there is
  currently no way to see the class without walking plot to plot.
- **Resource pack** mapping `custom_model_data` (atoms `7000 + atomicNumber`, molecules
  `8000 + hash`) to real icons - pure visual, no code. Biggest visual win available.
- More content via YAML; region-filtered recipes that force cross-region supply chains.
- True 3D geometry reveal on molecule completion (show the real bent/tetrahedral shape).

## Where to read more
- `docs/DESIGN.md` - the concept and the **decision log (what we chose and why)**.
- `docs/ARCHITECTURE.md` - class-by-class detail and data formats.
- `docs/EXTENDING.md` - how to add content.
- `CHANGELOG.md` - how the project was built, slice by slice.
- `README.md` - player/teacher-facing overview and gameplay.
