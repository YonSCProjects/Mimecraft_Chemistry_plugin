# RoboCraft - Architecture

Class-level reference for safely modifying the code. See `../CLAUDE.md` for the high-level map and
`DESIGN.md` for why any of it is shaped this way.

## Bootstrap
`RoboCraftPlugin extends JavaPlugin`
- `onEnable`: `saveDefaultConfig()`, create 3 `NamespacedKey`s (`part`, `tile`, `kiosk`), then
  construct **in this order**: `PartRegistry, PlayerStore, PlotManager, PartStore, RobotStore,
  MissionService, RobotEngine, ComponentBoard, WorkshopKiosk, StatusBar`; register 5 listeners;
  set the command executor and tab completer; `engine.start()`; then `Validate.run` and
  `missions.registry().validate`.
  The order matters in one place only: `PartStore.controllers()` calls `plugin.parts()` at runtime,
  so the registry must exist first. Everything else reaches its collaborators lazily through
  getters on the plugin.
- `onDisable`: `engine.stop()`, then saves `RobotStore`, `PartStore`, `PlayerStore`.
- `batteryCapacity(robotKey)` is on the plugin because three unrelated callers need it.

## part/
- `Part` (record): id, name, kind, block, sensor, actuator, capacity, drain, reach, radius, range,
  hint, unlockedByDefault. `kind` is one of `controller | battery | solar | sensor | actuator`.
- `PartRegistry`: `get`, `has`, `all`, `size`, `byBlock`. Loads `parts.yml`; iteration order is the
  order of tiles on the Component Board. `byBlock` is why two parts may never share a `Material` -
  `Validate` enforces it.
- `Placed` (record): partId, facing, robot, port. `robot` is the location key of the controller
  (empty = orphan). `port` is `S1..Sn` / `A1..An`, empty for the controller and for battery/solar.
- `PartStore`: location -> `Placed`, persisted to **`placements.yml`** (deliberately not
  `parts.yml`, which is bundled content). `key(Location)` / `fromKey(String)` convert both ways;
  `fromKey` returns null for a malformed key or an unloaded world. `partsOf(robotKey)`,
  `controllers()`, `detachAll`, `nextPort(robotKey, sensor)`. Saves on every mutation.
- `PartItems`: builds a part item (its own block + name + lore + PDC `part`=id +
  `custom_model_data = parts.base-model-data + index`). `idOf` reads the PDC back.
- `PartLabels`: the floating readout over a part - **this is the debugger**. `refresh` reuses the
  existing `TextDisplay` and only spawns one if absent; the engine calls it every tick for every
  part, so remove-and-respawn would churn entities and visibly flicker.

## program/
Bukkit-free by rule, so `tools/BenchCheck.java` can drive it without a server.
- `Op`: `LT LE EQ NE GE GT`, each with `symbol()` and `test(a,b)`; `next()` cycles for the GUI.
- `Verb`: `ON OFF SHOW SET ADD`. `next(memoryTarget)` cycles only within the verbs legal for that
  target kind, so the GUI cannot build a nonsense rule.
- `Operand` (record): either a constant or a reference to a source. `encode`/`decode` as `C7`/`PS1`.
- `Rule` (record): source, op, rhs, target, verb, arg. Source `ALWAYS` = unconditional.
  `text()` renders "IF S1 < 7 THEN A1 ON"; `encode`/`decode` round-trip through a pipe-delimited
  string; `decode` returns a blank rule rather than throwing on garbage.
- `Program`: an ordered `List<Rule>` plus `encode`/`decode`.
- `Evaluator`: **one pass of the rule table.** Rules run top to bottom, later wins, and memory
  writes land immediately so a later rule sees them - that last property is what makes edge
  detection (`M2 SET S1` *after* the comparison) work. Returns the ports commanded this tick plus
  the index of the last rule that fired. It does **not** latch: a port absent from the result means
  "no rule spoke about it", and holding the previous value is the caller's job.

## robot/
- `Robot`: controller key, owner, energy, running flag, `Program`, `memory[]`, `inputs` (port ->
  last reading), `outputs` (port -> current state), `lastFired`, `halt` reason. Memory is
  **deliberately not persisted** - a real controller loses RAM on power-cycle.
- `RobotStore`: the persistent half (owner, energy, rules) in `robots.yml`.
- `RobotEngine`: the tick loop, every `robot.tick-interval` ticks.
  1. **Sense** - readings are built into a fresh map, so a detached port disappears; a part in an
     unloaded chunk holds its previous reading rather than reading 0, because 0 is a real value.
  2. **Decide** - `Evaluator.run`.
  3. **Act** - a port nobody commanded holds its previous value (outputs latch). Only changed
     values are written, except `ActuatorDriver.continuous` ones.
  4. **Power** - base + per-sensor + per-active-actuator drain, minus solar gain, capped at battery
     capacity. Reaching 0 halts the robot.
  Then labels, then `missions.advance`. `tickAll` skips robots whose owner is offline or whose
  controller chunk is unloaded - reading a block in an unloaded chunk loads it synchronously.
  `whyNotReady` is the shared precondition check for `/rc run` and the GUI's run button.

## sense/SensorReader
`read(loc, placed, part, env)` -> an int. `env` maps sensor **type** to a simulated reading and
wins over the world; that single line is what the whole mission design rests on. `TYPES` publishes
the implemented names so `Validate` can catch a typo in `parts.yml`.
Types: `light` (block light above the sensor, since its own block is opaque), `distance` (raycast
along `facing`, `reach` means nothing found), `player`, `mob`, `heat` (50 neutral, fire/lava up,
ice/snow down), `color` (vanilla dye index of the block below, 16 = not coloured), `rain`,
`redstone`, `random`.

## act/ActuatorDriver
`apply(loc, part, value, previous)`. `lamp` sets `Lightable`, `gate` sets `Openable`, `buzzer`
plays a note **on the rising edge only** (a buzzer re-firing every tick is unbearable in a class of
thirty), `marker` spawns particles and is the only `continuous` type, `display` is drawn by
`PartLabels` and writes nothing to the world.

## mission/
- `Mission` (record) + nested `Step` (say, env, waitTicks, expect, because). `waitTicks` is named
  that because `wait` is an illegal record component - it collides with `Object.wait()`.
- `MissionRegistry`: loads `missions.yml` sorted by `order`; `nextFor(done)`; `validate` walks the
  ladder and warns if a mission needs a part nothing earlier unlocks. That deadlock is invisible in
  play - the student is told a part is missing with no way to get it - and it happened on the first
  draft.
- `MissionService`: **the test bench.** A run holds the mission, a `CommandSender`, a nullable
  progress owner, the accumulated `env`, a step index and a wait counter. `advance` is called once
  per robot tick; `tickStalled` covers robots that halted mid-run and are no longer being ticked, so
  a flat battery reads as a failed check instead of a frozen run. `expect` matches actuators by
  **type**, memory slots by name, or `running`. A null owner records no progress, which is what lets
  the self-test drive a real mission without awarding it.

## assistant/AskService
`ask(player, question)` appends one JSON object per line to `questions.jsonl` (UTF-8). The plugin
never calls an AI - see DESIGN.md 4.7. `robotContext(robot)` is deliberately free of `Player` so
the self-test can exercise it: it carries the program as text, live readings, ports, memory,
outputs and the last rule that fired, and it is the half that silently goes stale when the program
model changes. `q()` is minimal JSON string quoting - enough for Hebrew, quotes and newlines.
`recent(n)` reads the tail back for `/rc questions`.

## ui/Whisper
Plugin-side private delivery: `chat`, `actionbar`, `title`, plus `send(channel, ...)` and `find()`
by exact online name. Exists because 26.2's console `tell`/`tellraw`/`msg` deliver nothing at all.

## plot/
- `PlotManager`: the plot grid. Copied from ChemCraft; the only change was the plugin type.
- `WorkshopKiosk`: the charging pad and its label.

## data/PlayerStore
`players.yml`: `plot`, `board-built`, `unlocked` (part ids), `missions` (completed ids),
`kit-claimed`, `name`, and `_meta.next-plot`. `unlocked(id)` returns default-unlocked parts plus
earned ones. `allPlayers()` is the roster `/rc progress` is built from.

## ui/
- `ProgramMenu`: the rule table. `build` paints without showing (used headless by the self-test);
  `open` shows it; `render` repaints the open window. **Never re-open from inside a click handler** -
  it desyncs the client mid-click. Layout is one rule per row:
  `[#] [source] [op] [value] [->] [target] [verb] [arg] [x]`, controls on row 6.
  `sources`/`targets`/`valueSources` are the click vocabulary, shared with `MenuListener`.
- `ComponentBoard`: the ghost -> lit parts wall. Unlocked tiles show the part's real block.
- `Guide`, `StatusBar`, `ProgressReport` (the teacher view; roster sorted least-progress-first).

## diag/
- `SelfTest` + `Scratch`: **this is the test suite.** Builds real robots from real blocks, drives
  known inputs, asserts real blocks moved, and restores every block it overwrote. Never builds a
  board or kiosk - that would overwrite a student's plot - so their geometry is checked
  arithmetically instead.
- `Validate`: checks `parts.yml` and `config.yml` on enable for the mistakes that fail silently.

## listener/
- `JoinListener` - plot, board, kiosk, kit, guide, status bar.
- `PlotProtection` (NORMAL) - own plot only; also protects board tiles and the charging pad.
- `PartBlockListener` (HIGH, ignoreCancelled) - attach on place, orphan on break, adopt orphans
  when a controller arrives, hand the part back rather than the vanilla block.
- `InteractListener` - controller opens the program, board tile hands out a part, charger charges.
  Holding a block means building, not using, so part interaction is skipped then.
- `MenuListener` - decodes a click from its slot number alone (row = rule, column = field).

## Persistence formats (all YAML, in `plugins/RoboCraft/`)
```
placements.yml   world;x;y;z:  {part, facing, robot, port}
robots.yml       world;x;y;z:  {owner, energy, rules: ["S1|LT|C7|A1|ON|C0", ...]}
players.yml      <uuid>: {plot, board-built, unlocked[], missions[], kit-claimed, name}
                 _meta.next-plot
questions.jsonl  append-only, one JSON object per line, UTF-8:
                 {ts, player, uuid, question, x, y, z, where, missions_done, parts_unlocked,
                  current_mission*, last_bench_failure?, robot, energy, parts, readings,
                  memory, program, rules_used, last_rule_fired?, outputs}
```
Bundled content (`config.yml`, `parts.yml`, `missions.yml`) is copied on first run **only if
absent**, so shipping new defaults to a live server means editing or deleting the server's copy.
