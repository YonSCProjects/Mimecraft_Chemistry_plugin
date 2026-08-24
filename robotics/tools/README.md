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
