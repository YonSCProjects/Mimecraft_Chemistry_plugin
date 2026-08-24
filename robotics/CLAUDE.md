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

Package: `com.agurim.robocraft`. Jar: `robotics/build/libs/robotics-<version>[-mc26.2].jar`.

> **Status: compiles against both targets; never yet run on a server.** The mission ladder has
> been checked offline (`tools/BenchCheck.java`, 22 checks) but nothing here has been smoke-tested
> in-game. Everything below the engine is unverified against a live Paper server.

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
- `part/` - `Part` (record), `PartRegistry` (`parts.yml`), `PartStore` (location -> `Placed`,
  persisted `placements.yml`), `PartItems` (parts as PDC-tagged items), `PartLabels` (the live
  floating readouts - **this is the debugger**).
- `program/` - `Rule`, `Program`, `Op`, `Verb`, `Operand`, and **`Evaluator`**: one pass of the
  rule table, deliberately **free of every Bukkit type** so the semantics can be tested offline.
- `robot/` - `Robot` (runtime state), `RobotStore` (`robots.yml`: owner, energy, rules),
  `RobotEngine` (the tick loop: sense -> decide -> act -> power).
- `sense/SensorReader` - world (or simulated bench input) -> an int.
- `act/ActuatorDriver` - a decision -> a block change.
- `mission/` - `Mission`, `MissionRegistry` (`missions.yml` + ladder validation),
  `MissionService` (the **test bench**).
- `plot/` - `PlotManager` (copied from ChemCraft), `WorkshopKiosk` (the charging pad).
- `data/PlayerStore` - per-UUID progress (`players.yml`): plot, unlocked parts, completed missions.
- `ui/` - `ProgramMenu` (the rule-table GUI), `ComponentBoard` (the ghost->lit parts wall),
  `Guide`, `StatusBar`.
- `listener/` - `JoinListener`, `PlotProtection`, `PartBlockListener` (attach/detach + ports),
  `InteractListener`, `MenuListener`.

### Data files
Bundled in `resources/`, copied to `plugins/RoboCraft/` on first run **only if absent**:
`config.yml`, `parts.yml`, `missions.yml`. Runtime state: `players.yml`, `placements.yml`,
`robots.yml`.

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
- **Never smoke-tested in game.** Chiefly unverified: the `ProgramMenu` click model, display
  entity churn, and whether `IRON_TRAPDOOR`/`REDSTONE_LAMP` block-data writes behave as expected.
- **Robot memory is not persisted** - a restart zeroes `M1..M4`. Deliberate: real controllers lose
  RAM on power-cycle, and it is worth teaching.
- Robots only tick while their owner is online (`robot.require-owner-online`).
- Right-clicking a part while **holding a block** is treated as building, not using, so the
  controller menu needs an empty hand. Necessary - otherwise you cannot build against a part.
- A mission's `expect` targets an actuator **type**, so every lamp on the robot must match. Fine
  for the current ladder; would need per-port expectations for anything subtler.
- No rovers, no co-op, no teacher overview yet.

## Roadmap
- **Smoke-test on the 26.2 server** - the only thing that matters next.
- **Rovers** (Phase 2): a config-capped chassis that shifts a block per move step, carrying its
  parts and labels.
- **`/rc progress`** teacher overview - which missions the class is stuck on is the single most
  useful thing a teacher can see.
- Resource pack mapping `custom_model_data` (`parts.base-model-data` + index) to real part icons.
- A shared **exhibition hall** of working mechanisms - a showcase, never a shared goal, because a
  collective gate on individual progress breaks absence resilience.
