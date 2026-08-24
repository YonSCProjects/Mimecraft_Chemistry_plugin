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
