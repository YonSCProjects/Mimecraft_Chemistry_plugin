# tools/

Not part of the build - `robotics/src/main/java` is the only source set, so nothing here ships
in the jar.

## BenchCheck.java

Drives the real `Evaluator` (kept free of Bukkit types on purpose) through the scenarios in
`missions.yml`, to answer two questions that should not be assumed:

1. **Is the rule grammar expressive enough to solve each mission?**
2. **Does the naive solution actually fail?** A mission whose obvious wrong answer still passes
   teaches nothing.

It also runs the engine's power model, which is how `efficiency`'s `start-energy` was tuned:
the duty-cycled robot finishes with room to spare even with a spare sensor attached, while the
always-on lamp runs flat about two thirds of the way through.

Run it against the Java 21 target:

```
gradle :robotics:compileJava
javac -cp robotics/build/classes/java/main -d /tmp/bc robotics/tools/BenchCheck.java
java  -cp "robotics/build/classes/java/main;/tmp/bc" BenchCheck
```

Exit code is non-zero if any check fails. Scenarios are transcribed from `missions.yml` rather
than parsed, so re-check them by eye if you change the mission steps.

## smoke-test.ps1

Downloads nothing and assumes nothing: point it at a directory that already contains a `paper.jar`,
an accepted `eula.txt` and a `plugins/` folder holding the RoboCraft jar, and it will start the
server, wait for it to finish booting, run `/rc selftest` and `/rc progress` from the console, stop
the server cleanly so `onDisable` is exercised too, and leave the log for inspection.

```
powershell -ExecutionPolicy Bypass -File robotics/tools/smoke-test.ps1 `
    -ServerDir C:\path\to\testserver `
    -JavaExe   "C:\Program Files\Eclipse Adoptium\jdk-21...\bin\java.exe"
```

Two things it knows that cost a run each to learn: Windows PowerShell writes a UTF-8 BOM on the
first line it sends to a process, and the server reads it as part of the command - so the script
burns it on a deliberate blank line first. And both output pipes must be drained asynchronously,
or a full pipe deadlocks the server mid-boot.

Used against Paper 1.21.8 build 60 (Java 21) and Paper 26.2 build 116 (Java 25).

## missions-linear.yml

The ladder as it was before the fork-B split: six required missions in one sequence, with the night
light first. Kept in case the split turns out to be wrong in front of a class.

To go back, copy it over `src/main/resources/missions.yml` and rebuild. It has no `optional:` flags,
so every mission becomes required again and the warm-up handling simply never triggers. You would
also want to re-lock `buzzer`, `presence_sensor` and `gate` in `parts.yml`, since the linear ladder
hands those out as rewards.

It lives here rather than in `resources/` on purpose - anything in the source set gets bundled into
the jar, and a spare copy of the content is not something to ship.

## Driving the assistant loop from a Claude Code session

The plugin is only the capture-and-delivery half (DESIGN.md 4.7). To be the agent from a session
on the same machine:

1. Run a server with `enable-rcon=true` and `rcon.port=25575`, and set `rcon.password` to whatever
   the session's Minecraft MCP bridge is configured with — mismatched passwords fail as a bare
   "Authentication failed" with nothing to say which side is wrong.
2. Read pending questions with `robocraft questions` over RCON, or tail
   `plugins/RoboCraft/questions.jsonl` directly — the file carries far more context than the
   command's summary, including the student's rule table and live readings.
3. Reply with `robocraft whisper <player> [chat|actionbar|title] <text>`.

On a superflat world the top solid block is y=-61, so `plot.ground-y: -61` and `board.offset-y: -60`.
The defaults assume y=64 and would leave the board floating in the air.
