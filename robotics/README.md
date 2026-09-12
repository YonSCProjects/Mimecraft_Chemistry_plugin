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
   IF    S1  <   7   THEN  A1  ON
   IF    S1  >=  9   THEN  A1  OFF
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

Three **optional warm-ups** first — a night light, an alarm, an automatic door. These are things
redstone already does well, and they say so; they exist so a student who has never programmed gets
an easy first success with the tools. They grant no parts and gate nothing.

Then the ladder proper, which starts where redstone stops being adequate:

| # | Mission | Teaches |
|---|---|---|
| 1 | אור דמדומים — twilight lamp | a band between two thresholds; **later rules win, as a tool** |
| 2 | דלת שנשארת פתוחה — the door that stays open | memory holding a deadline, not a flag |
| 3 | מונה — counter | edge detection, and that rule order matters |
| 4 | נורה שלא מרצדת — the flicker fix | **feedback and hysteresis**, on a lamp that lights its own sensor |
| 5 | תרמוסטט — thermostat | hysteresis again, on heat you cannot see |
| 6 | חיסכון — efficiency | an energy budget; duty-cycling |

Missions 4 and 5 are the peak. The bench asks for the output at the *same* reading twice and
expects different answers — which a single threshold cannot produce. The only way through is two
thresholds. A genuinely deep idea, reachable by a twelve-year-old who has just watched their own
lamp flicker — and in mission 4 the bench makes the lamp flicker on purpose, by feeding its light
back into the sensor, so the lesson arrives on a lamp before it is asked for on a thermostat.

After the ladder come seven **bonus** jobs — a fire alarm, a tunnel gauge, a sheep-pen guard, a
rain vent, a solar station, a colour lock and a manual override. They use the parts the ladder
earned, grant nothing, and gate nothing.

Seven parts are available from the start. **The nine you earn are exactly the ones redstone has no
equivalent for** — distance, heat, colour, rain and animal sensors, a display, a solar panel, a
marker, and a redstone bridge for students who want to feed a contraption in.

## Reading a mission

Every mission is a card, the same shape every time, short enough to be on screen whole:

```
▶ 4. נורה שלא מרצדת                            number and name
הנורה מאירה את החיישן שלה - תקנו כדי שלא תרצד.   the goal, one line
בונים: ✔ בקר · ✔ סוללה · ✔ חיישן אור · ○ נורה      what to build, ticked against your robot
✓ אור 14: נורה כבויה                              what the bench will check
✓ לילה, והנורה מאירה את החיישן: נורה דולקת, בלי ריצוד
✓ אור 14: נורה כבויה
✓ ושוב לילה: נורה דולקת, בלי ריצוד
[▶ הרצה]  [רמז]  [כל המשימות]                     click; nothing to type
```

`/rc missions` is the overview — three one-line tracks with a mark per mission, then the next
one and its buttons. Clicking any name opens its card. The lectern button at the bottom of the
rule table runs the mission you last opened (right-click shows its card), so the loop is: read
the card, place the parts, write the rules, press the lectern, watch the bench.

The checks on the card are the bench's own steps, so they cannot disagree with what is tested.
The rules that pass them are not on the card; `[רמז]` is a nudge, and the failure text names
the check that failed, why, and what the sensors were reading at that moment.

## The assistant

Students can ask, in Hebrew, from inside the game:

```
/rc ask למה הנורה לא נדלקת?
```

**The plugin never talks to an AI.** It captures the question together with the state needed to
answer it — the student's actual rule table, what each sensor is reading at that moment, which rule
fired last, and the bench check they most recently failed — and appends it to `questions.jsonl`. An
external agent tails that file and replies with `/rc whisper <player> [chat|actionbar|title] <text>`,
which is a plugin command because on 26.2 the console `tell`/`tellraw`/`msg` deliver nothing at all.

So the agent is external, swappable and optional: no keys or model choice inside the plugin, and a
server running no agent simply gets no answers. `/rc questions` shows what the class is stuck on —
which is useful as formative assessment even when nobody is answering.

Whether the agent should answer or ask back is set in the agent's prompt, not here. For a workshop
whose point is that students find bugs by reading values, a Socratic nudge is probably right.

## Commands

**Students** — `/rc guide`, `kit`, `tp`, `board`, `missions [n]` (overview, or one card),
`mission <n>` (run it), `hint [n]`, `run`, `stop`, `trace`, `charge`, `ask <question>`. In
practice students click; the commands are what the clicks run.

**Teachers** (op) — `/rc progress` for the class view, `/rc questions` for what the class is stuck
on, `/rc selftest` to check a server before a lesson, plus `give`, `unlock`, `reset`, `reload`.
`/rc whisper` is the assistant's delivery channel.

**Stopping the room.** A workshop does not stop because someone at the front says "stop"; it
stops when the screens stop.

```
/rc pause                         freeze every student; "⏸ הפסקה" pinned on every screen
/rc pause הסתכלו ללוח              the same, with your words under it
/rc pause kofiko בוא/י אליי        freeze one student, with a private line
/rc resume                        lift it (or /rc resume kofiko for one)
/rc say מי זוכר מה אומר כלל 2?    big text on every screen, nobody frozen
/rc say kofiko יפה מאוד            big text for one
```

A paused student cannot walk, build, click, open anything or run commands until you resume
them. They can still look around and read chat. The world keeps going — robots tick, a bench
run finishes — because a robot "paused" mid-bench would fail its checks for a reason the
student never caused. Teachers are never caught by `/rc pause`; naming yourself
(`/rc pause <your name>`) is, on purpose, the way to see what a student sees.

Short text becomes the big line itself; longer text pages through the subtitle a line at a
time and is echoed to chat, so a student who looked away can still read it.

`progress`, `selftest`, `pause`, `resume` and `say` also run from the **server console**, which
is where the class view's columns line up, where you want the check to run before anyone has
joined, and where a laptop at the front can stop the room without being in the game.

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
  exceptions, 93/93 self-test checks — and the click model's layout and semantics are both verified.
  Whether the rule table is *learnable* is not something a test can answer.
- **Two design decisions are not settled.** Why this exists rather than a redstone map, and whether
  robots should move. Both are argued, with the counter-case, in `docs/DESIGN.md`. A third is now
  settled: Minecraft Education would have been the stronger alternative, but it has no Hebrew.
- **Robot memory is not persisted.** A restart zeroes `M1..M4` — deliberately, because real
  controllers lose their RAM when the power goes.
- **All player-facing text is Hebrew and was not written by a native speaker.** `docs/HEBREW.md`
  lists every string for proofreading before it meets a class.
- No rovers, no co-op.

## Where to read more

- `docs/DESIGN.md` — the concept and the decision log, including what is still open.
- `docs/ARCHITECTURE.md` — class-by-class detail and the data formats.
- `CLAUDE.md` — the developer map.
- `tools/` — the offline mission checker and the server smoke-test driver.
