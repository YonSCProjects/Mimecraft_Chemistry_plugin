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

> **Status: runs clean on both targets.** Verified on Paper 1.21.8 build 60 (Java 21) and Paper
> 26.2 build 116 (Java 25): enables, writes its content YAML, registers commands, saves its runtime
> files on disable, zero exceptions, and **`/rc selftest` passes 82/82 on both**. The mission ladder
> is separately checked offline (`tools/BenchCheck.java`, 22 checks).
>
> **Still unverified: anything that needs a real player at a keyboard** - `JoinListener`,
> `PlotProtection`, `StatusBar`, and the event plumbing in the two block listeners. The *logic*
> those listeners used to hold has been pulled out into `Attachment` and `RuleEdit`, which the
> self-test drives directly; what is left in them is the plumbing and the Hebrew messages.
> No human has ever clicked the rule table.

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
  `MissionService` (the **test bench**).
- `plot/` - `PlotManager` (copied from ChemCraft), `WorkshopKiosk` (the charging pad).
- `data/PlayerStore` - per-UUID progress (`players.yml`): plot, unlocked parts, completed missions.
- `ui/` - `ProgramMenu` (the rule-table GUI), `ComponentBoard` (the ghost->lit parts wall),
  `Guide`, `StatusBar`, `ProgressReport` (the teacher view).
- `diag/` - `SelfTest` + `Scratch`: builds real robots out of real blocks, drives known inputs
  through them, checks real blocks moved, and restores the world exactly. **This is the test
  suite**, and it is also the answer to "does this work on this server?" before a lesson.
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
- **No human has ever played it.** The click model's layout and semantics are both checked; what
  is not checked is whether it is *learnable*, and no test can tell us that.
- `SensorReader` implements `mob` and `random`, but no part in `parts.yml` uses them - dead
  branches until a part is added, kept because both are cheap and obviously useful.
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
`give | unlock | reset | reload | selftest | progress` for admins. **`selftest` and `progress` also
run from the console** - the first is what you want before a lesson, the second during one.

## Roadmap
- **Watch a real student use it.** Everything mechanical is now checked; what is not checked is
  whether the rule table is *learnable*, and that needs a person, not a test.
- **Rovers** (Phase 2): a config-capped chassis that shifts a block per move step, carrying its
  parts and labels.
- Resource pack mapping `custom_model_data` (`parts.base-model-data` + index) to real part icons.
- A shared **exhibition hall** of working mechanisms - a showcase, never a shared goal, because a
  collective gate on individual progress breaks absence resilience.
