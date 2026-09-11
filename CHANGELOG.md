# Changelog

Built in slices. Versions are pre-release working milestones. ChemCraft entries carry its
version; RoboCraft entries (jar `RoboCraft-0.1.0`) are dated, and its decision log is
`robotics/docs/DESIGN.md`.

## RoboCraft 2026-09-11 - The teacher can stop the room
Yon asked for it before the next lesson: "pause the game for one player or for all and send a
big text that will appear on his screen." New `classroom/` package.
- **`/rc pause [player] [text]`** freezes every student (never a teacher) or one named player
  (anyone - naming yourself is how you see what a student sees): no walking, building, clicking,
  inventories or non-admin commands; looking around and chat still work. A yellow "⏸ הפסקה" title
  is pinned to the screen with the teacher's text under it, re-sent before it can fade, paged if
  long, and the rule table is closed if it was open. A student who joins into a paused room is
  frozen and told. **`/rc resume [player]`** lifts it with "▶ ממשיכים". A student released by
  name stays released until the room is resumed.
- **`/rc say [player] <text>`** is the big text without the freeze. Twenty characters or fewer
  becomes the big line itself; longer text pages through the subtitle forty characters at a time,
  because a title does not wrap and a long one runs off the screen with nothing telling the
  sender. Every big text is echoed to chat as `[המורה] ...` so a student who looked away can
  still read it. `classroom.teacher-name` and `classroom.page-seconds` in config.yml.
- All three run from the **console** too, so a laptop at the front can stop the room over RCON
  without being in the game. Replies are Hebrew to a player, English to a console.
- **The world is never paused.** Robots tick and a bench run finishes. A robot frozen mid-bench
  would fail its checks for a reason the student never caused.
- `PauseListener` is the one listener at `LOWEST` priority: a paused player's event is dead
  before plot protection or the part listeners - all `ignoreCancelled` - ever see it.
- `/rc selftest` 149 -> **161/161**: the word-wrap is held to its widths (and cuts a word no line
  can hold), and the pause state to its one rule - "everyone" never catches a teacher, a name
  catches anyone. What no test can tell us: whether the title reads on a real screen. Yon walks
  it on Tair first.

## RoboCraft 2026-09-11 - Six rungs, seven bonus jobs, and a bench that flickers on purpose
Step 1 of "The Yard" (`robotics/docs/DESIGN.md` §5): every mission now names the place on the
student's plot where it will be met, the content ships in full, and the world does not change yet.
- **The ladder is six.** Yon's call, mid-term, on a live class: the playtest's accidental feedback
  loop - a lamp lighting its own light sensor, oscillating twice a second - was mission 5's lesson
  arriving on mission 1, so it became rung 4, **נורה שלא מרצדת**, between the counter and the
  thermostat. Hysteresis is met first on a lamp you can watch flicker, then again on heat. The rung
  grants nothing, so nobody's earned parts moved; every 5/5 became 5/6.
- **The bench can feed an output back into an input.** A step may carry
  `feedback: { light: { actuator: lamp, add: 9, max: 15 } }` - while a lamp is on, the injected
  light reads 9 higher next tick - and an expect may carry `steady: lamp` - at most one switch
  during the wait. A single-threshold night light switches eight times in eighty ticks and the
  failure names it: `החליף מצב 8 פעמים בזמן ההמתנה - ריצוד`. Deterministic, fifteen seconds.
- **Seven bonus jobs after the ladder** (`bonus: true`): fire alarm, tunnel gauge, sheep-pen
  guard, rain vent, solar station, colour lock, manual override - one per part the ladder unlocks
  but never asked for. They grant nothing, gate nothing, are suggested only once the ladder is
  done, and get their own bossbar track (`בונוס 2/7`), their own gold trophy, and `B1..B7` in
  `/rc missions` (`ב1` works too). `nextFor` still ignores them, so progress maths is unchanged.
- **Animal sensor** (`mob_sensor`, unlocked by the counter): counts mobs, not players, and ignores
  armour stands so a part's own label cannot trip it. Appended to `parts.yml` - the board is
  indexed by position, and inserting a part shifts every tile after it on every live plot.
- **Every mission carries `site`, a five-line `quest` and a one-line `value`**, ready for the job
  posts of step 3. `validate` now refuses a quest line the chat would wrap (`Guide.CHAT_WIDTH`)
  and an expect that could never fail - both fired during authoring.
- Solar panels charge from the *injected* light when a bench supplies one, so the solar station
  can be tested at noon. A mission with `start-energy` refills the battery on success. A charge
  attempt mid-run is refused and says so, because the efficiency rung is an energy budget.
- Board and shelf clear every label in their box before a rebuild, so a rebuild is honest whatever
  moved, and **a wall rebuilds itself on join when the content grew** - `players.yml` now records
  how many parts and missions each wall was built for, so a student whose shelf has eight slots
  gets sixteen the next time they log in, on every server, with nobody editing `board-built` by
  hand. `reconcileUnlocks` on join grants the rewards of already-passed missions, for when a
  reward list grows after the fact - as the counter's just did.
- `/rc selftest` 93 -> **149/149**; `tools/BenchCheck.java` 33 -> **85** over all sixteen
  missions (run it with the JDK 25 tools - the build classes are version 69).
- Also since v0.8.2, from Yon's September playtest on the Tair server: the trophy shelf and a
  celebration on every pass (`aaf62a6`, `1c47a22`), a detached part that says why (`02fca61`),
  `IF` instead of `WHEN` and a charging pad that reaches the arrival spot (`0cb8c91`), and mission
  numbers that agree with the status bar (`b55d67c`, `9cd1990`). Every one was a thing that was
  provably correct and impossible to perceive.

## v0.8.2 - Multi-project build: room for a second plugin
- **Repo is now a Gradle multi-project** holding two unrelated teaching plugins: `chemcraft/` and
  `robotics/` (a robotics workshop plugin, skeleton only). They are *not* a library and a consumer -
  each runs on its own dedicated server and they are never loaded together, so plot grids, worlds
  and join flows can't collide. All 52 ChemCraft files moved with `git mv`, so history follows them.
- **Shared build config hoisted to the root `build.gradle`:** the Paper dependency, the toolchain,
  the UTF-8 pins and the whole `-Pmc=26.2` dual-target switch are now written once for every module.
  `chemcraft/build.gradle` is down to a version and a jar name. Jar moved to
  `chemcraft/build/libs/`; `base.archivesName` keeps it `ChemCraft-<version>[-mc26.2].jar`.
- **No shared `core` module, deliberately.** The generic classroom infrastructure (`PlotManager`,
  `PlotShield`, `Whisper`, `Ranks`, `Credit`, `Guide`) is ~400 LOC of ~4000 and couples only to the
  plugin main class, used purely as a `JavaPlugin`. Extracting a platform from a single example
  encodes ChemCraft's accidents as the interface - robotics copies what it needs first, and the
  seam gets cut once a second implementation shows where it actually is. The eventual extraction is
  a mechanical `ChemCraftPlugin` -> `JavaPlugin` swap, so waiting costs nothing.
- **Fixed a latent build bug:** the foojay toolchain resolver was pinned at `0.8.0`, which crashes
  on Gradle 9 (`JvmVendorSpec.IBM_SEMERU` was removed). It never surfaced because JDK 21 was
  installed locally so the resolver never ran - but the documented `-Pmc=26.2` target needs JDK 25
  and tripped it immediately. Now `1.0.0`, and Gradle auto-provisions the JDK 25; `JAVA_HOME`
  juggling is no longer needed for the 26.2 build.
- Both targets verified with clean builds. No behaviour change to ChemCraft: not one source file
  was edited.

## v0.8.1 - Plot shield, and a station block that rusted shut
- **Station blocks must never weather.** `panning` was `COPPER_BLOCK`, which oxidizes - fast, in
  26.2's copper age. Because stations are matched by exact material, an oxidized block silently
  stops being a station, which would have made gold, silver and sulfur unobtainable mid-campaign
  with no error message, and made `/cc region tp` refuse those zones. Now `WAXED_COPPER_BLOCK`:
  identical appearance, never weathers. Found by playtesting, not by review.
- **Plot shield (מגן החלקה).** Touching another student's plot already failed silently; now it
  fails *legibly*. Every blocked attempt gets a barrier flash and a thunk so protection feels
  solid, and repeated attempts inside a window quietly teleport the student home - deliberately
  boring, because a dramatic punishment turns griefing into a toy for this age group. Nothing is
  ever damaged, dropped, or lost. Covers breaking, building, buckets, ignition, containers, and
  bond editing. Config: `plot-shield`.
- **`/cc report`** (admin): who has been testing plot boundaries and how often - the lever that
  actually changes behaviour in a classroom is the teacher seeing it. `/cc report clear` resets.

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
