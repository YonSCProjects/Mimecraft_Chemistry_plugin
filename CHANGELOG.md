# Changelog

Built in slices. Versions are pre-release working milestones.

## v0.8.0 - The teaching layer (campaign Phase 2) + dual-target build
- **"Seen" tile tier (גילוי בהדגמה):** watching a classmate extract an element you lack (within
  `demo.radius`) turns your wall tile yellow with a where-to-reproduce hint and records the
  demonstrator. When you later perform your OWN first extraction of it, the demonstrator earns
  the assist - teaching pays exactly like gifting, but witnessing alone grants nothing.
- **Helper ranks (דרגות):** assists earn Hebrew tab-list prefixes (עוזר/ת מעבדה → לבורנט/ית →
  כימאי/ת → פרופסור, config `ranks.tiers`) - status only, never material perks. Rank 2 unlocks
  `/cc visit <name>`: teleport to a classmate's plot to look and learn. `Credit` is now the
  single payout path for both gift and demo assists.
- **Visit-proofing (from the adversarial review):** bond editing is owner-only (visitors could
  previously cycle bonds and steal molecule completion rewards - a pre-existing hole), plot
  containers are owner-only, and wall labels no longer duplicate when updated while the plot
  chunk is unloaded (remote witnessing/discovery).
- **Dual-target build:** `gradle build` targets Paper 1.21.8 (Java 21); `gradle build -Pmc=26.2`
  targets Paper 26.2 stable (Java 25, Minecraft's new year-based versioning) with a templated
  plugin.yml api-version. Zero source changes were needed for 26.2.

## v0.7.0 - Shared regions & cooperation (campaign Phase 1)
- **Shared gathering regions** (`regions.yml`, new `region/` package): ten themed zones matching
  the (previously dead) `region:` field in `elements.yml`. Anyone may harvest the marked gather
  node beds (custom drop, placeholder, timed regrowth, self-healing on restart); everything else
  in a zone is protected. Stations move out of the plot kiosk into their regions
  (`kiosk.stations: [reactor]` keeps only the reactor home); outside regions/kiosks the old
  material hijack is gone - vanilla blocks behave normally again. Ops: `/cc region
  list|tp|build|buildall` (build modes: flat platform or stations-only into hand-sculpted
  terrain; build/buildall also work from console). With no `regions.yml`, everything behaves
  exactly as v0.6 (legacy mode; upgraded servers stay legacy until an admin opts in).
- **Wall registration** (`WallRegisterListener`): right-click your own gray periodic-table tile
  holding a matching atom - the atom is consumed, the tile lights, the fact prints. A second,
  additive discovery path, so gifted/traded atoms finally count.
- **Cooperation credit**: extraction-minted atoms carry the extractor's identity (PDC stamp +
  "הופק על ידי" lore). When a classmate registers your atom for their first-ever discovery of
  that element you earn an assist: server broadcast, level-up chime (banked offline), a permanent
  "התגלה יחד עם" line on their wall tile, and a place on the shared "עוזרים מובילים" sidebar
  (`AssistBoard`). Structurally farm-proof: one credit per (receiver, element) ever; unstamped
  mints (admin give, block re-drops, reaction outputs) and self-credit pay nothing.
- **Hardening** (from an adversarial review pass): `chemcraft.admin` permission gates
  give/givemol/discover/reset/reload; starter kit is once per player and now includes tools;
  kiosk blocks and node beds are protected from farming (harvest is scoped to the exact bed
  coordinates); explosions never damage blocks; buckets/fire blocked outside your plot;
  extraction inputs are counted and consumed by the same Material predicate (closes a
  renamed-item exploit); zero-input recipes retuned (sulfur=gunpowder, silver=gravel,
  air gases=redstone - the energy currency).

## v0.6.0 - Hebrew localization
- All in-game player-facing text is now **Hebrew**: chat messages, titles, menu/station names,
  item names and lore, the `/cc guide`, and all educational content (element/molecule/material
  facts, recipe/reaction labels) in the YAML.
- Kept in Latin where translating would be wrong: element symbols, chemical formulas, command
  keywords, YAML keys/ids, and Bukkit `Material` names.
- No code-structure change; `build.gradle` already compiles sources and resources as UTF-8.
- Note: Minecraft renders Hebrew right-to-left; lines mixing Hebrew with Latin symbols/numbers may
  visually reorder in-game and can need small wording tweaks after a live pass. Dev/teacher docs
  (`README.md`, `docs/`) stay in English.

## v0.5.0 - Onboarding & self-serve start
- **Auto-built station kiosk:** every plot gets a labeled row of station blocks next to its wall
  (`StationKiosk`), so a player can start without an op placing anything. Names/hints and layout
  are config-driven (`station-info`, `kiosk`); `/cc buildwall` rebuilds it too.
- **Starter kit:** first-time players (and `/cc kit`) receive raw materials to bootstrap the
  stations (`StarterKit`, config `starter-kit`).
- **In-game guide:** a welcome title plus a step-by-step "how to play" walkthrough on first join and
  any time via `/cc guide` (`Guide`), including a "X / Y elements discovered" progress line.

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
