# RoboCraft

A PaperMC plugin that teaches **robotics** to middle-schoolers by making them build machines that
**sense, decide and act** — and then proving the machine works.

Sibling of ChemCraft in the same repo. They never run together; each has its own server.

---

## What a student does

1. **Place a controller.** Everything you put near it joins that robot and is given a name:
   `S1`, `S2` for sensors, `A1`, `A2` for actuators. Those names are pin numbers, and they are the
   vocabulary the program is written in.
2. **Give it a battery.** No power, no robot. Energy runs out while it works.
3. **Right-click the controller** and write the program by clicking — one rule per row:

   ```
   WHEN  S1  <   7   THEN  A1  ON
   WHEN  S1  >=  9   THEN  A1  OFF
   ```

   No typing, so no syntax errors and no English barrier.
4. **Run it.** Rules run top to bottom, twice a second, forever. Later rules win. **Outputs latch** —
   a lamp that switched on stays on until a rule switches it off. That is the first bug everybody
   hits, and it is meant to be.
5. **Run the mission.** The bench feeds your robot a scripted set of readings and checks what it
   does. It never gives a score. It names the check that failed, tells you why, shows you what the
   sensors were reading at that moment, and lets you retry as often as you like.

Stuck? `/rc trace` prints what every sensor is reading, what is in memory, which rules match right
now, and **which rule actually decided each output**.

## The mission ladder

| # | Mission | Teaches |
|---|---|---|
| 1 | נורת לילה — night light | sensor → threshold → output, and that outputs latch |
| 2 | אזעקה — alarm | a second sensor; two actions from one condition |
| 3 | דלת אוטומטית — automatic door | actuators that move |
| 4 | מונה — counter | memory, edge detection, and that rule order matters |
| 5 | תרמוסטט — thermostat | **feedback and hysteresis** — one threshold makes it chatter |
| 6 | חיסכון — efficiency | an energy budget; duty-cycling |

Mission 5 is the peak. The bench asks for the heater's state at the *same* temperature twice and
expects different answers — which a single threshold cannot produce. The only way through is two
thresholds. A genuinely deep idea, reachable by a twelve-year-old who has just watched their own
lamp flicker.

## Commands

**Students** — `/rc guide`, `kit`, `tp`, `board`, `missions`, `mission <id>`, `run`, `stop`,
`trace`, `charge`.

**Teachers** (op) — `/rc progress` for the class view, `/rc selftest` to check a server before a
lesson, plus `give`, `unlock`, `reset`, `reload`.

`progress` and `selftest` also run from the **server console**, which is where the class view's
columns line up and where you want the check to run before anyone has joined.

## Build & run

From the repo root, JDK 21 and Gradle:

```
gradle :robotics:build                 # Paper 1.21.8, Java 21
gradle :robotics:build "-Pmc=26.2"     # Paper 26.2,   Java 25
```

The jar lands in `robotics/build/libs/`. Drop it into `plugins/`, start the server, then set
`world` and `plot.origin-x/z` in `plugins/RoboCraft/config.yml` to a flat empty area with ground at
`plot.ground-y`. Join, and you are teleported to your workshop with a starter kit and a component
board already built — no op setup needed.

Before a lesson on a new server, run `/rc selftest` from the console. It builds a real robot out of
real blocks, drives a known reading through it, checks a real block moved at the other end, and puts
the world back exactly as it was.

## Honest caveats

- **No human has ever played it.** It runs clean on real Paper servers — both targets, zero
  exceptions, 85/85 self-test checks — and the click model's layout and semantics are both verified.
  Whether the rule table is *learnable* is not something a test can answer.
- **Two design decisions are not settled.** Why this exists rather than a redstone map, and whether
  robots should move. Both are argued, with the counter-case, in `docs/DESIGN.md`.
- **Robot memory is not persisted.** A restart zeroes `M1..M4` — deliberately, because real
  controllers lose their RAM when the power goes.
- **All player-facing text is Hebrew and was not written by a native speaker.** It should be
  proofread before it meets a class.
- No rovers, no co-op.

## Where to read more

- `docs/DESIGN.md` — the concept and the decision log, including what is still open.
- `docs/ARCHITECTURE.md` — class-by-class detail and the data formats.
- `CLAUDE.md` — the developer map.
- `tools/` — the offline mission checker and the server smoke-test driver.
