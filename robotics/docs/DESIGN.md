# RoboCraft - Design & Decision Log

The robotics-workshop sibling of ChemCraft. Same bet: **you learn it by playing it**, the content
lives in YAML, and the teaching happens in the world rather than in a wall of text.

Status: **design + first vertical slice**, written 2026-08-23/24. Everything marked **OPEN** is a
decision Yon has not made yet - flagged deliberately rather than silently resolved.

---

## 1. What we are actually teaching

A robotic mechanism is a **loop**, not a machine:

```
        +---------------------------------------------+
        |                                             |
   [ SENSE ] -> [ DECIDE ] -> [ ACT ] -> (world changes)
        ^                                             |
        +---------------------------------------------+
```

Everything a middle-schooler needs from an intro robotics course falls out of that loop:

| Concept | Where it shows up here |
|---|---|
| Sensors turn the physical world into **numbers** | every sensor reads to an int, shown live on its label |
| A **threshold** is a boundary you measure, not guess | `WHEN S1 < 7` - the 7 is the student's problem |
| **Actuators latch** until told otherwise | outputs hold; forgetting to turn one off is the first bug everyone hits |
| **Feedback**: your action changes the next reading | the thermostat oscillates if you use one threshold |
| **Hysteresis** fixes oscillation | two thresholds + a memory bit - mission 5 |
| **State** is what makes sequencing possible | `M1..M4`, with `SET` and `ADD` |
| **Energy is finite**; efficiency is a goal | the battery drains per active actuator; duty-cycling is the fix |
| **Debugging** = watching values, not staring at wiring | live labels + which-rule-fired highlight |

The prize is **transfer**. When this student later meets an Arduino, a micro:bit or a LEGO brick,
the rule table they wrote here is recognisably `loop() { if (analogRead(A0) < 7) ... }`.

---

## 2. The decision that defines the project: why not just redstone?

**Minecraft already ships sensor -> logic -> actuator. That is what redstone is.** If RoboCraft
doesn't clearly beat a well-made redstone map, it should not exist. This has to be answered first.

**Decision: redstone teaches *wiring*. RoboCraft teaches *the program*.**

Wiring is not what a robotics workshop is about, and it is exactly where redstone spends all of a
student's attention - routing, repeater delays, torch burnout, and a spaghetti whose behaviour
cannot be read off the page. RoboCraft moves the difficulty to where the learning is:

| | Redstone | RoboCraft |
|---|---|---|
| The program is | invisible; inferred from wire layout | **a readable list of rules**, visible at once |
| Sensor values are | binary, mostly | **analog ints** - light 0-15, distance 0-16, colour, heat |
| Thresholds | comparator hacks | `WHEN S1 < 7` - a number you measure and tune |
| Debugging | trace the wire | **watch live values**; see which rule fired this tick |
| Energy | free and infinite | **a battery that runs out** |
| Failure feedback | "it didn't work" | **a bench that names the check that failed** |
| Transfer to real robotics | barely | direct - the rule table *is* pseudo-code |

Redstone is not banned. There is a `redstone` input part on purpose, so a student who loves it can
feed a redstone contraption into a RoboCraft program. The bridge is a feature, not a concession.

> **OPEN #1.** Yon has not signed off on this framing. It is load-bearing - everything in section 4
> follows from it. If he disagrees, most of this document changes.

---

## 3. Decision: stationary mechanisms first, rovers later

Driving a multi-block build around is expensive and fragile in a plugin: rebuild the structure every
move, drag the display entities along, cross chunk borders, fight plot protection. It is also **not
how a real robotics workshop starts.**

Real workshops open with mechatronics that stay put - a thermostat, an automatic door, a night
light, an alarm, a sorting gate. Those teach the *entire* sense-decide-act loop, thresholds,
latching, feedback and hysteresis, with zero movement code.

**Decision: Phase 1 is stationary mechanisms; Phase 2 adds rovers.** That is sequencing, not
scope-cutting - it front-loads every concept and defers the one genuinely hard piece of engineering.

When rovers land, the intent is a config-capped chassis (e.g. 3x3x3) that shifts one block per move
step with its parts and labels carried along - **the student's own build moving**, not a decorative
model standing next to a moving entity.

> **OPEN #2.** To a 12-year-old, "robot" may simply mean "it moves". If that framing matters more
> than the concept ladder, rovers have to be in the first release and this decision flips.

---

## 4. The game

### 4.1 A robot is a physical build

| Piece | What it is | Role |
|---|---|---|
| **Controller** (בקר) | Tier-1 block + label | the brain. Right-click opens the program; its label is the robot's screen |
| **Battery** (סוללה) | Tier-1 block | capacity and drain. Empty = the robot halts |
| **Sensors** (חיישנים) | Tier-1 blocks | read the world to an int. Ports `S1, S2, ...` |
| **Actuators** (מפעילים) | Tier-1 blocks | change the world. Ports `A1, A2, ...` |
| **Solar panel** | Tier-1 block | recharges in daylight - makes light a *resource*, not just an input |

A part attaches to the nearest unclaimed controller within `robot.attach-radius` (default 4) when
placed, and the assigned port is printed on its floating label. **Ports are pin numbers** - that is
the entire point of them.

All of this is ChemCraft's proven Tier-1 pattern: vanilla block + `TextDisplay` + identity in a
location map (`PartStore`). No new block IDs; a plugin cannot register those.

### 4.2 The program is a rule table

```
  1  WHEN  S1  <   7   THEN  A1  ON
  2  WHEN  S1  >=  9   THEN  A1  OFF
  3  WHEN  S2  =   1   THEN  A2  ON
  4  ALWAYS            THEN  A3  SHOW S1
```

- Authored in a **chest GUI, one rule per row**, entirely by clicking. No typing means **no syntax
  errors and no English barrier** - which matters enormously in a Hebrew-speaking classroom.
- **Rules run top to bottom, every tick** - literally `loop()` with a chain of `if`s.
- **Later rules win.** Memory writes are visible to later rules in the same tick, so sequencing works.
- **Outputs latch.** Nothing switches off by itself. This is the classic first bug and we want them
  to hit it.
- `M1..M4` are readable as conditions and writable with `SET` / `ADD`. `ADD` turns memory into a
  counter, and counters are what make timing and sequencing possible - a lot of expressive power
  for two verbs.

Grammar, deliberately tiny:

```
RULE    := WHEN <cond> THEN <action>  |  ALWAYS THEN <action>
cond    := <source> <op> <int>
source  := S1..Sn | M1..M4 | TIME
op      := <  |  <=  |  =  |  !=  |  >=  |  >
action  := A1..An ON|OFF  |  A1..An SHOW <source>  |  M1..M4 SET|ADD <int>
```

### 4.3 The tick

Every `robot.tick-interval` ticks (default 10 = 0.5 s), for each running robot:

1. **Sense** - read every attached sensor into a value map.
2. **Decide** - evaluate rules in order; memory applies immediately, actuator commands accumulate.
3. **Act** - write only the actuator states that actually changed, never spam block updates.
4. **Power** - drain base + per-active-actuator cost, add solar gain. At 0 the robot halts.

The controller label shows live state, so **the loop is visible while it runs**. That is the one
thing redstone structurally cannot give you.

### 4.4 Missions run on a test bench, not in the weather

A mission is a stated goal, a scripted scenario, and pass/fail checks (`missions.yml`).

**Decision: a mission injects a *simulated environment* instead of changing the world.** A scenario
step sets `env.light = 2`, and the light sensor reads that instead of block light.

Three reasons, each sufficient on its own:

- **The world is shared.** Forcing night to test one student's night light would black out the whole
  class. `setPlayerTime` is client-side only, so server block light would not move anyway and the
  sensor would read the wrong value - the obvious approach is also the broken one.
- **Determinism.** Same scenario, same result, every run. A test that flickers teaches nothing.
- **Speed.** A day/night cycle is ten real minutes; the bench does it in fifteen seconds.

It is also better pedagogy: injecting inputs and asserting outputs is exactly how real embedded code
is tested. The bench narrates what it is simulating ("בודקים: לילה, עוצמת אור = 2") so the
conditions are visible, and on failure it names **which check failed**, not just "failed".

Rewards are **parts**, which light tiles on the Component Board.

> **OPEN #4.** Is a bench too much like a test? It gives the objective feedback a robotics student
> desperately needs, but a red FAIL can land badly in a classroom. Current plan: unlimited retries,
> never a score, always name the failing check, and frame it as "הרצת ניסוי" rather than a grade.

### 4.5 The Component Board

ChemCraft's periodic-table wall, transplanted: a per-plot grid of ghost tiles, one per part type,
lit as you unlock them; click a lit tile to draw that part. It answers "what can I build with?" and
"what am I working towards?" in one glance, and it is the same satisfaction as filling the table.

### 4.6 The loop, end to end

```
  pick a mission  ->  draw parts from the board  ->  build the mechanism
        ^                                                    |
        |                                                    v
   unlock parts  <-  bench says PASS  <-  run the bench  <-  write the rules
                            |                                     ^
                            +--- names the check that failed -----+
```

---

## 5. Mission ladder (first arc, all stationary)

| # | Mission | Teaches | Unlocks |
|---|---|---|---|
| 1 | **נורת לילה** - lamp on when dark | sensor -> threshold -> output | buzzer |
| 2 | **אזעקה** - buzzer and lamp when something is close | a second sensor; two actions | distance sensor |
| 3 | **דלת אוטומטית** - gate opens when a player is near | actuators that move; latching | memory |
| 4 | **מונה** - count the openings | `ADD`, edge detection | display |
| 5 | **תרמוסטט** - hold heat in a band | **feedback + hysteresis** | solar panel |
| 6 | **חיסכון** - pass mission 1 on half the battery | energy budget, duty cycling | - |

Mission 5 is the intellectual peak: with one threshold the output chatters, and the only fix is two
thresholds plus a memory bit. A genuinely deep idea, reachable by a 12-year-old who has just watched
their own lamp flicker.

---

## 6. What we inherit from ChemCraft

Copied, not shared - see the root `CLAUDE.md` for why there is no `core` module yet.

- `PlotManager`, the plot grid, `PlotProtection`, the plot shield
- The Tier-1 custom-block pattern (vanilla block + `TextDisplay` + location map)
- Chest-GUI menus with PDC-tagged icons
- YAML-driven content, written to the data folder on first run
- Adventure `Component`s; Hebrew UTF-8, Latin for symbols / ids / `Material` names
- `EventPriority.HIGH` + `ignoreCancelled` for anything that must run after protection
- Zero-op-setup onboarding: join -> plot -> kiosk -> starter parts
- The **absence-resilience** rule: nothing individual may sit behind a group gate

---

## 7. Open questions for Yon

1. **The redstone framing (§2)** - is "redstone teaches wiring, we teach the program" right?
   Everything else follows from it.
2. **Stationary-first (§3)** - agreed, or must rovers ship in v1 because "robot" means "it moves"?
3. **Rule count.** Five rules fit one GUI page and force economical thinking. Too few?
4. **Is the bench too much like a test?** (§4.4 carries the current mitigation.)
5. **Naming.** `RoboCraft`, package `com.agurim.robocraft`. Cheap to change now, annoying later.
6. **Co-op.** ChemCraft learned that collective gates break absence resilience. The safe shape here
   is a shared *exhibition hall* of working mechanisms - a showcase, not a shared goal.
