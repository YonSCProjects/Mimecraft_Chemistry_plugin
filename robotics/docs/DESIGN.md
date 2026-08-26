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

### 2.1 The alternative that isn't: Minecraft Education

The strongest argument against building this at all was never redstone. It was **Minecraft
Education Edition**, whose Code Builder already gives you a programmable Agent driven by MakeCode
blocks or Python - a more mature version of the "program a robot" half than this will ever be.

**Settled 2026-08-25: it has no Hebrew.** Yon checked. For a Hebrew-speaking classroom it is not
an option, so the comparison never arises.

Two consequences, and they are not the same size:

- **It does not help the redstone argument.** Redstone is entirely language-free - a Hebrew-speaking
  twelve-year-old can learn it from a silent video. §2 still stands or falls on wiring versus
  program, exactly as before.
- **It does argue against a text-based authoring surface.** The click-only rule table was justified
  partly by "no typing means no English barrier", which read as a nice-to-have. With the only
  off-the-shelf alternative ruled out on precisely that ground, it stops being a nice-to-have:
  signs, books or a text syntax would reintroduce the barrier that just eliminated the competition.
  This is the argument against fork C.

It also means there is no off-the-shelf Hebrew-language option for teaching this at all, which
gives the project a reason to exist beyond one classroom.

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

### 4.7 The assistant: the plugin captures, it does not answer

Every student gets an assistant they can ask, in Hebrew, from inside the game: `/rc ask למה הנורה
לא נדלקת?`

**The plugin never talks to an AI.** It owns two things - capture and delivery - and nothing else.
`/rc ask` appends the question, with the state needed to answer it, to `questions.jsonl`. An
external agent tails that file and replies with `/rc whisper <player> [channel] <text>`.

Three reasons that split is right, and they are all practical:

- **No keys, no cost, no model choice inside the plugin.** The assistant can be swapped, upgraded
  or switched off without a rebuild, and a server with no agent running loses nothing but answers.
- **Delivery has to be plugin-side anyway.** On 26.2 the console `tell`, `tellraw` and `msg`
  execute silently over RCON and deliver nothing - no error, no log line. Only `say`, which
  broadcasts to the whole class, and `title`/`actionbar` work. A private answer needs `/rc whisper`.
- **The questions are a teaching record on their own.** `/rc questions` shows what the class is
  stuck on even when nobody is answering. That is formative assessment for free.

**What makes it worth doing here is the context.** A chemistry question can be answered from
position and inventory. A robotics question is almost always *"why did it do that?"*, which is
unanswerable in the abstract - so the capture includes the student's **actual rule table**, what
each sensor is reading at that moment, which rule fired last, where the outputs ended up, and the
bench check they most recently failed. That is the difference between "check your threshold" and
*"rule 2 only fires at S1 >= 7, and S1 is reading 4 right now."*

> **OPEN #5.** Should the agent answer, or ask back? The plugin deliberately does not decide - the
> prompt belongs to whoever runs the agent. For a workshop whose whole point is that the student
> finds the bug by reading values, a Socratic hint is probably right, but that is Yon's call and it
> can change without touching code.

---

## 5. Mission ladder - warm-ups, then the work redstone cannot do

**Decision (2026-08-25, fork B): the ladder splits in two.** Yon has no redstone experience, so the
comparison was settled with worked examples instead of a race - and doing that exposed the real
shape of it. Missions 1-3 of the original ladder are all things redstone does *as well or better*:
a night light is an inverted daylight sensor next to a lamp (two blocks), an alarm is a tripwire,
and a pressure-plate door is a beginner's first build. Only from mission 4 does redstone start to
lose.

So those three become **optional warm-ups**. They are not deleted, because a student who has never
programmed still needs an easy first success with the tools - and they are honest about what they
are. They grant no parts and gate nothing.

### Warm-ups (optional)

| Mission | Teaches | In redstone |
|---|---|---|
| **נורת לילה** night light | sensor, threshold, and that outputs latch | 2 blocks - easier |
| **אזעקה** alarm | a second sensor, two actions from one condition | tripwire - easier |
| **דלת אוטומטית** automatic door | mechanical actuators | plate + piston - easier |

### The ladder (required)

| # | Mission | Teaches | Why redstone struggles |
|---|---|---|---|
| 1 | **אור דמדומים** twilight lamp | a band between two thresholds; **later-rules-win as a tool** | two comparator threshold circuits plus combining logic |
| 2 | **דלת שנשארת פתוחה** the door that stays open | memory holding a **deadline**, not a flag; `TIME` | needs a monostable circuit |
| 3 | **מונה** counter | `ADD`, edge detection, rule order | hopper counter or a flip-flop chain |
| 4 | **תרמוסטט** thermostat | **feedback and hysteresis** | latch + two comparators - and there is no temperature to sense at all |
| 5 | **חיסכון** efficiency | energy budget, duty cycling | the concept does not exist |

Rung 1 is where "later rules win" stops being a gotcha and becomes the answer:

```
1  WHEN  S1  >=  10   THEN  A1  OFF     day: off
2  WHEN  S1  <   10   THEN  A1  ON      dim: on
3  WHEN  S1  <    4   THEN  A1  OFF     full dark: off again, overriding rule 2
```

Rung 2 is the warm-up door done properly - the reflex version shuts the instant you step away;
this one holds a deadline in memory:

```
1  WHEN  S1    =  1    THEN  M1  SET TIME
2  WHEN  S1    =  1    THEN  M1  ADD  3
3  WHEN  TIME  <  M1   THEN  A1  ON
4  WHEN  TIME  >= M1   THEN  A1  OFF
```

Rung 4 remains the intellectual peak: the bench asks for the heater's state at 45 degrees *twice*
and expects different answers, which a single threshold cannot produce.

### The parts follow the same line

Seven parts are available from the start - exactly what the warm-ups need. The eight the ladder
unlocks are exactly the ones **redstone has no equivalent for**: distance, heat, colour, rain, a
redstone bridge, a display, a solar panel, a marker. Nothing had to be said about it; the
progression is the argument.

**A warm-up must never be the only source of a part**, or skipping one strands the student.
`MissionRegistry.validate` enforces that on every enable, and its check was verified by seeding the
fault deliberately.

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
   *(§2.1 is settled and no longer open: Minecraft Education has no Hebrew.)*
6. **Co-op.** ChemCraft learned that collective gates break absence resilience. The safe shape here
   is a shared *exhibition hall* of working mechanisms - a showcase, not a shared goal.
