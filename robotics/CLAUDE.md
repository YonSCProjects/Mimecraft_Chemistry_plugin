# RoboCraft

The robotics-workshop plugin. Read this first; the concept and the **decision log** are in
`docs/DESIGN.md`. The root `CLAUDE.md` covers the multi-project build and the conventions both
plugins share.

## What this is
A **PaperMC plugin that teaches robotics** to middle-schoolers (~11-14) by making them build
mechanisms that **sense, decide and act**, and then proving the mechanism works on a test bench.
The full loop:
**pick a mission -> draw parts from the board -> build the mechanism -> write the rule table ->
run the bench -> unlock parts.**

Package: `com.agurim.robocraft`. Jar: `robotics/build/libs/RoboCraft-<version>[-mc26.2].jar`.

> **Status: runs clean on 26.2, and a person has now played it.** Verified on Paper 26.2 build 116
> (Java 25): enables, writes its content YAML, registers commands, saves its runtime files on
> disable, zero exceptions, and **`/rc selftest` passes 149/149**. The mission content is
> separately checked offline (`tools/BenchCheck.java`, 85 checks over all sixteen missions - run
> it with the **JDK 25** `javac`/`java`, the build classes are version 69). Yon walked the first
> warm-up end to end on the Tair class server in September 2026; everything he could not
> perceive got fixed (`docs/DESIGN.md` §4.9).
>
> **Seven per-class servers are live** (`C:\26.2_RoboCraft_<Class>`, ports 25567-25573) and
> three hold real student work. A bundled YAML is copied to a server **only if absent**, so a
> content change ships by copying `missions.yml`/`parts.yml` explicitly, never `config.yml`
> (theirs carry `plot.ground-y: -61`, and the bundled 64 builds the plot in the sky).

## The two decisions everything rests on
1. **Redstone teaches wiring; we teach the program.** Minecraft already ships sensor -> logic ->
   actuator, so the only justification for this plugin is that the *program* becomes a readable,
   debuggable object, sensor values become real analog numbers, and energy is finite.
   `docs/DESIGN.md` §2 argues this properly. **Not yet signed off by Yon.**
2. **Stationary mechanisms first, rovers later.** A thermostat teaches the whole loop with zero
   movement code, and moving a multi-block build is the one genuinely hard piece of engineering
   here. `docs/DESIGN.md` §3.

## Architecture at a glance
`RoboCraftPlugin#onEnable` builds every service, registers the listeners, starts the engine, and
validates the mission ladder. All gameplay content is **YAML-driven**.

Packages (`robotics/src/main/java/com/agurim/robocraft/`):
- `part/` - `Part` (record), `PartRegistry` (`parts.yml`), `Attachment` (which controller a part
  joins, and what happens when one is removed), `PartStore` (location -> `Placed`,
  persisted `placements.yml`), `PartItems` (parts as PDC-tagged items), `PartLabels` (the live
  floating readouts - **this is the debugger**).
- `program/` - `Rule`, `Program`, `Op`, `Verb`, `Operand`, plus **`Evaluator`** (one pass of the
  rule table) and **`RuleEdit`** (what a click on a cell does). Both deliberately **free of every
  Bukkit type**, so the two things hardest to get right can be tested without a player.
- `robot/` - `Robot` (runtime state), `RobotStore` (`robots.yml`: owner, energy, rules),
  `RobotEngine` (the tick loop: sense -> decide -> act -> power).
- `sense/SensorReader` - world (or simulated bench input) -> an int.
- `act/ActuatorDriver` - a decision -> a block change.
- `mission/` - `Mission`, `MissionRegistry` (`missions.yml` + ladder validation),
  `MissionService` (the **test bench**). Three tiers: three `optional: true` warm-ups covering
  what redstone already does well, **six required rungs** where it does not, and seven
  `bonus: true` jobs after the ladder that grant and gate nothing. Progress is counted over the
  required ladder only; `skippable()` is warm-up-or-bonus, and `validate` rejects either as the
  sole source of a part - skipping one must strand nobody. A bench step may carry `feedback:`
  (an actuator that is ON raises an injected reading next tick - the lamp lighting its own
  sensor) and an expect may carry `steady:` (at most one switch during the wait) - that pair is
  how the flicker rung fails a single threshold in fifteen seconds. See docs/DESIGN.md 4.4, 5.
- `plot/` - `PlotManager` (copied from ChemCraft), `WorkshopKiosk` (the charging pad).
- `data/PlayerStore` - per-UUID progress (`players.yml`): plot, unlocked parts, completed missions.
- `ui/` - `ProgramMenu` (the rule-table GUI), `ComponentBoard` (the ghost->lit parts wall),
  `Guide`, `StatusBar`, `ProgressReport` (the teacher view).
- `assistant/AskService` + `ui/Whisper` - the assistant layer. **The plugin never talks to an AI:**
  it captures a question plus the state needed to answer it (the student's rule table, live
  readings, last rule fired, last failed bench check) into `questions.jsonl`, and an external agent
  replies via `/rc whisper`. See docs/DESIGN.md 4.7.
- `diag/` - `SelfTest` + `Scratch`: builds real robots out of real blocks, drives known inputs
  through them, checks real blocks moved, and restores the world exactly. **This is the test
  suite**, and it is also the answer to "does this work on this server?" before a lesson.
- `listener/` - `JoinListener`, `PlotProtection`, `PartBlockListener` (attach/detach + ports),
  `InteractListener`, `MenuListener`.

### Data files
Bundled in `resources/`, copied to `plugins/RoboCraft/` on first run **only if absent**:
`config.yml`, `parts.yml`, `missions.yml`. Runtime state: `players.yml`, `placements.yml`,
`robots.yml`, and `questions.jsonl` (append-only, one JSON object per line).

### Key flows
- **Attach:** `BlockPlaceEvent` -> `PlotProtection` (NORMAL) -> `PartBlockListener` (HIGH,
  ignoreCancelled) finds the nearest controller within `robot.attach-radius`, assigns the next
  port (`S1`/`A1`), and says so. **A port is a pin number** - that is the vocabulary the program
  is written in.
- **Tick:** `RobotEngine` every `robot.tick-interval` - read sensors -> `Evaluator.run` -> write
  only changed actuators -> drain/charge energy -> update labels -> advance any mission.
- **Bench:** `/rc mission <id>` -> `MissionService` injects `env` readings by sensor *type* and
  asserts actuator states; failure names the check and the reason, never a score.

## Conventions - follow these
1. **Content goes in YAML, not code.** New parts/missions are data edits.
2. **Custom "blocks" are Tier 1:** a vanilla block + a `TextDisplay` + identity in `PartStore`.
   You cannot register new block IDs from a plugin.
3. **Never use a block that changes state on its own.** Parts are matched by exact `Material`, so
   plain copper would oxidize and silently stop being a part. Waxed only. (ChemCraft lost a
   station to this in playtesting.)
4. **Messages = Adventure `Component`**, Hebrew UTF-8; Latin for ports, ids, `Material` names and
   rule tokens (`S1`, `A1`, `<`, `ON`) - a rule row must read identically in any client.
5. **`Evaluator` stays Bukkit-free**, so `tools/BenchCheck.java` keeps working.
6. **Listeners that must run after protection use `EventPriority.HIGH` + `ignoreCancelled`.**

## Known issues / caveats
- **No human has ever played it.** The click model's layout and semantics are both checked; what
  is not checked is whether it is *learnable*, and no test can tell us that.
- `SensorReader` implements `random`, but no part in `parts.yml` uses it - a dead branch until a
  part is added, kept because it is cheap and obviously useful. (`mob` got its part in September
  2026: `mob_sensor`, the animal sensor, which ignores armour stands so a part label cannot trip
  it.)
- **`parts.yml` is append-only.** Board and shelf tiles are indexed by position in the file, so
  inserting a part in the middle shifts every tile after it on every live plot. `clearLabels`
  makes a rebuild honest whatever moved, but the *blocks* still move; append.
- **`/rc selftest` cannot be driven over RCON.** It builds a twenty-robot fleet and outruns RCON's
  packet timeout, and worse, a command's output goes back to the *sender* - so over RCON the result
  vanishes with the timed-out connection instead of reaching the log. Run it from the server console
  (stdin), which is what `tools/smoke-test.ps1` does. `/rc questions` and `/rc whisper` are both
  fast and work over RCON fine - which matters, because whisper is how an external agent replies.
- **The label refresh is the hot path.** It runs for every part of every running robot twice a
  second. It short-circuits on a cheap signature and caches the display entity by UUID; a naive
  version cost 35 ms of a 50 ms tick with twenty robots. If you change what a label shows, change
  `PartLabels.signature` to match or the label goes stale.
- **26.2's block-write path is about twice as expensive as 1.21.8's.** Twenty robots all flipping
  a lamp every tick: 15 ms on 1.21.8, 32 ms on 26.2. Steady state is ~2 ms on both, and that is
  what a room normally does - but it is worth knowing on the primary target.
- **Robot memory is not persisted** - a restart zeroes `M1..M4`. Deliberate: real controllers lose
  RAM on power-cycle, and it is worth teaching.
- Robots only tick while their owner is online (`robot.require-owner-online`).
- Right-clicking a part while **holding a block** is treated as building, not using, so the
  controller menu needs an empty hand. Necessary - otherwise you cannot build against a part.
- A mission's `expect` targets an actuator **type**, so every lamp on the robot must match. Fine
  for the current ladder; would need per-port expectations for anything subtler.
- No rovers and no co-op yet.

## Commands
`/rc guide | kit | tp | board | missions | mission <id> | run | stop | charge` for students;
`ask <question>` too; `give | unlock | reset | reload | selftest | progress | questions | whisper`
for admins. **`whisper` is the assistant's delivery channel and must work from the console.** **`selftest` and `progress` also
run from the console** - the first is what you want before a lesson, the second during one.

## Roadmap
- **The Yard** (designed, skeptic-reviewed, step 1 of 3 shipped): every mission becomes a job at
  a site on the student's own plot - a house, a gatehouse, a pen, a tunnel, a station - with a
  job post as the mission's front door. Step 1 (this content, the bench keys, the animal sensor)
  ships with zero world change. Step 2 builds the sites; step 3 wires the posts. Design and the
  five decisions Yon took are in `docs/DESIGN.md` §5 and
  `C:\Users\Admin\.claude\plans\yard-design-draft\`.
- **Explore world and boss arena** (approved, unbuilt): `C:\Users\Admin\.claude\plans\lets-plan-a-way-pure-quasar.md`
  layers 3-4. `pvp=false` on every class server before any gear ships.
- **Watch a real student use it.** Yon has; a class has not. What is not checked is whether the
  rule table is *learnable* by a twelve-year-old, and that needs thirty of them, not a test.
- **Rovers** (Phase 2): a config-capped chassis that shifts a block per move step, carrying its
  parts and labels.
- Resource pack mapping `custom_model_data` (`parts.base-model-data` + index) to real part icons.
- A shared **exhibition hall** of working mechanisms - a showcase, never a shared goal, because a
  collective gate on individual progress breaks absence resilience.
