package com.agurim.robocraft.mission;

import com.agurim.robocraft.program.Evaluator;
import com.agurim.robocraft.program.Program;
import com.agurim.robocraft.program.Rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The test bench, run against a program in memory instead of a robot in the world.
 *
 * <p>Free of every Bukkit type on purpose, like {@link Evaluator}: it is what lets the content
 * validator run every mission's {@code solution:} on enable and refuse a mission its own answer
 * cannot pass, and what finds the checks an EMPTY program passes for free - "day: lamp off" holds
 * before anyone writes a rule - so progress can be counted over the checks that need one
 * (docs/DESIGN.md 4.12).
 *
 * <p>It is a line-for-line mirror of {@link com.agurim.robocraft.robot.RobotEngine#tick} and
 * {@link MissionService#advance}: sense from the injected environment (with feedback computed from
 * LAST tick's outputs), decide with the same {@code Evaluator}, latch the outputs, apply the same
 * power arithmetic, then advance the scenario - waits counting down by the tick interval, switches
 * counted for {@code steady}, a halted robot still advanced so a flat battery fails a check instead
 * of hanging. <b>If either of those changes, change this.</b> The self-test holds the two together
 * on the night light: the real bench and this must agree on the solution and on the naive program.
 *
 * <p>Two things the world supplies that this cannot: a sensor type the scenario never injects
 * reads 0 here (the engine would read the block), and solar gain uses the injected light or 0.
 * Every mission injects what it measures, so neither has mattered.
 */
public final class BenchSim {

    /** A part on the reference robot, without the {@code Material} a real part carries. */
    public record PartSpec(String id, String kind, String sensor, String actuator, int capacity, int drain) {
        boolean isBattery()  { return "battery".equals(kind); }
        boolean isSolar()    { return "solar".equals(kind); }
        boolean isSensor()   { return "sensor".equals(kind); }
        boolean isActuator() { return "actuator".equals(kind); }
    }

    /** The engine's knobs, as config.yml sets them. */
    public record Settings(int tickInterval, int memorySlots, boolean powerEnabled,
                           int baseDrain, int sensorDrain, int solarGain) {}

    /** One {@code expect} step and how it went. {@code detail} is English - this reaches a console. */
    public record CheckResult(int stepIndex, boolean ok, String detail) {}

    /** Every check, in order. Unlike the bench, the run continues past a failure, so a free-check
     *  scan sees all of them; {@link #passed()} is what the bench would have said. */
    public record Result(List<CheckResult> checks, boolean completed) {
        public boolean passed() {
            if (!completed) return false;
            for (CheckResult c : checks) if (!c.ok()) return false;
            return true;
        }
        public CheckResult firstFailure() {
            for (CheckResult c : checks) if (!c.ok()) return c;
            return null;
        }
        public int passedCount() {
            int n = 0;
            for (CheckResult c : checks) if (c.ok()) n++;
            return n;
        }
    }

    /**
     * Ports as a robot built from a part list would get them: sensors S1, S2... and actuators
     * A1, A2... in list order. This is the convention a {@code solution:} is written against -
     * the first sensor in {@code needs} is S1 - and what a demo has to remap onto the student's
     * actual robot by part type.
     */
    public static Map<String, PartSpec> ports(List<PartSpec> parts) {
        Map<String, PartSpec> out = new LinkedHashMap<>();
        int s = 0, a = 0;
        for (PartSpec p : parts) {
            if (p.isSensor()) out.put("S" + (++s), p);
            else if (p.isActuator()) out.put("A" + (++a), p);
        }
        return out;
    }

    private final Mission mission;
    private final Program program;
    private final List<PartSpec> parts;
    private final Map<String, PartSpec> ports;
    private final Settings settings;

    public BenchSim(Mission mission, Program program, List<PartSpec> parts, Settings settings) {
        this.mission = mission;
        this.program = program;
        this.parts = parts;
        this.ports = ports(parts);
        this.settings = settings;
    }

    // ------------------------------------------------------------------ state

    private final Map<String, Integer> inputs  = new LinkedHashMap<>();
    private final Map<String, Integer> outputs = new LinkedHashMap<>();
    private final Map<String, Integer> env = new LinkedHashMap<>();
    private final Map<String, Mission.Feedback> feedback = new LinkedHashMap<>();
    private final Map<String, Integer> lastOutputs = new LinkedHashMap<>();
    private final Map<String, Integer> switches = new LinkedHashMap<>();
    private int[] mem;
    private int energy;
    private boolean running;
    private String halt;
    private long now;
    private int index;
    private int waitLeft;

    public Result run() {
        int interval = Math.max(1, settings.tickInterval());
        mem = new int[Math.max(1, settings.memorySlots())];
        int capacity = 0;
        for (PartSpec p : parts) if (p.isBattery()) capacity += p.capacity();
        energy = mission.startEnergy() > 0 ? mission.startEnergy() : capacity;
        running = true;
        halt = null;
        now = 0;
        index = 0;
        waitLeft = 0;

        // Enough ticks for every wait plus every zero-wait step, with slack; never a hang.
        long budget = 0;
        for (Mission.Step s : mission.steps()) budget += s.waitTicks();
        int maxTicks = (int) (budget / interval) + mission.steps().size() + 32;

        List<CheckResult> checks = new ArrayList<>();
        for (int t = 0; t < maxTicks; t++) {
            now += interval;
            if (running) tick();
            if (advance(checks)) return new Result(checks, true);
        }
        return new Result(checks, false);
    }

    // ------------------------------------------------- RobotEngine.tick, mirrored

    private void tick() {
        // SENSE - from the bench's view, feedback included, using LAST tick's outputs.
        Map<String, Integer> view = view();
        Map<String, Integer> readings = new LinkedHashMap<>();
        for (Map.Entry<String, PartSpec> e : ports.entrySet()) {
            PartSpec p = e.getValue();
            if (!p.isSensor()) continue;
            Integer injected = view.get(p.sensor());
            readings.put(e.getKey(), injected != null ? injected : inputs.getOrDefault(e.getKey(), 0));
        }
        inputs.clear();
        inputs.putAll(readings);

        // DECIDE
        Evaluator.Result decision = Evaluator.run(program, inputs, mem, (int) Math.max(0, now / 20L));
        Map<String, Integer> commands = decision.commands();

        // ACT - outputs latch.
        for (Map.Entry<String, PartSpec> e : ports.entrySet()) {
            if (!e.getValue().isActuator()) continue;
            String port = e.getKey();
            int prev = outputs.getOrDefault(port, 0);
            outputs.put(port, commands.containsKey(port) ? commands.get(port) : prev);
        }

        // POWER
        if (!settings.powerEnabled()) return;
        int drain = settings.baseDrain();
        int gain = 0;
        int capacity = 0;
        int light = env.containsKey("light") ? Math.max(0, Math.min(15, env.get("light"))) : 0;
        for (Map.Entry<String, PartSpec> e : ports.entrySet()) {
            PartSpec p = e.getValue();
            if (p.isSensor()) drain += Math.max(p.drain(), settings.sensorDrain());
            else if (p.isActuator() && outputs.getOrDefault(e.getKey(), 0) != 0) drain += p.drain();
        }
        for (PartSpec p : parts) {
            if (p.isBattery()) capacity += p.capacity();
            else if (p.isSolar()) gain += settings.solarGain() * light / 15;
        }
        energy = energy - drain + gain;
        if (capacity > 0) energy = Math.min(energy, capacity);
        energy = Math.max(0, energy);
        if (energy <= 0) { running = false; halt = "battery flat"; }
    }

    /** {@link MissionService#envFor}: the injected readings, raised where a fed-back actuator is on. */
    private Map<String, Integer> view() {
        if (feedback.isEmpty()) return env;
        Map<String, Integer> view = new LinkedHashMap<>(env);
        for (Map.Entry<String, Mission.Feedback> e : feedback.entrySet()) {
            String sensorType = e.getKey();
            Mission.Feedback fb = e.getValue();
            if (!view.containsKey(sensorType)) continue;
            boolean anyOn = false;
            for (String port : portsOfType(fb.actuator())) {
                if (outputs.getOrDefault(port, 0) != 0) { anyOn = true; break; }
            }
            if (anyOn) view.put(sensorType, Math.min(fb.max(), view.get(sensorType) + fb.add()));
        }
        return view;
    }

    // --------------------------------------------- MissionService.advance, mirrored

    /** One advance of the scenario. Returns true when the scenario has run to its end. */
    private boolean advance(List<CheckResult> checks) {
        int interval = Math.max(1, settings.tickInterval());
        countSwitches();
        if (waitLeft > 0) { waitLeft -= interval; return false; }

        for (int guard = 0; guard < 32; guard++) {
            if (index >= mission.steps().size()) return true;
            Mission.Step step = mission.steps().get(index);
            if (step.hasEnv()) env.putAll(step.env());
            if (step.hasFeedback()) feedback.putAll(step.feedback());
            if (step.hasExpect()) {
                String failure = check(step);
                checks.add(new CheckResult(index, failure == null, failure == null ? "" : failure));
            }
            index++;
            if (step.waitTicks() > 0) {
                waitLeft = step.waitTicks();
                switches.clear();
                lastOutputs.clear();
                lastOutputs.putAll(outputs);
                return false;
            }
        }
        return false;
    }

    private void countSwitches() {
        for (Map.Entry<String, Integer> e : outputs.entrySet()) {
            boolean was = lastOutputs.getOrDefault(e.getKey(), 0) != 0;
            boolean is  = e.getValue() != 0;
            if (was != is) switches.merge(e.getKey(), 1, Integer::sum);
        }
        lastOutputs.clear();
        lastOutputs.putAll(outputs);
    }

    /** null when every expectation holds, else what went wrong - the bench's checks, in English. */
    private String check(Mission.Step step) {
        for (Map.Entry<String, String> e : step.expect().entrySet()) {
            String key = e.getKey(), want = e.getValue();
            if ("running".equalsIgnoreCase(key)) {
                boolean expected = Boolean.parseBoolean(want);
                if (running != expected) return running ? "robot still running" : "robot stopped: " + halt;
                continue;
            }
            if ("steady".equalsIgnoreCase(key)) {
                List<String> p = portsOfType(want);
                if (p.isEmpty()) return "no " + want + " on the robot";
                for (String port : p) {
                    int n = switches.getOrDefault(port, 0);
                    if (n > 1) return want + " (" + port + ") switched " + n + " times during the wait";
                }
                continue;
            }
            if (key.length() >= 2 && key.charAt(0) == 'M' && Character.isDigit(key.charAt(1))) {
                int slot = Integer.parseInt(key.substring(1)) - 1;
                int actual = (slot >= 0 && slot < mem.length) ? mem[slot] : 0;
                int expected = parseInt(want, 0);
                if (actual != expected) return key + " = " + actual + ", want " + expected;
                continue;
            }
            List<String> p = portsOfType(key);
            if (p.isEmpty()) return "no " + key + " on the robot";
            int expected = switch (want.toLowerCase()) {
                case "on", "true"   -> 1;
                case "off", "false" -> 0;
                default -> parseInt(want, Integer.MIN_VALUE);
            };
            if (expected == Integer.MIN_VALUE) continue;
            boolean numeric = "display".equals(key);
            for (String port : p) {
                int actual = outputs.getOrDefault(port, 0);
                boolean ok = numeric ? (actual == expected) : ((actual != 0 ? 1 : 0) == expected);
                if (!ok) {
                    return key + " (" + port + ") = " + (numeric ? actual : (actual != 0 ? "ON" : "OFF"))
                            + ", want " + (numeric ? expected : (expected != 0 ? "ON" : "OFF"));
                }
            }
        }
        return null;
    }

    private List<String> portsOfType(String actuatorType) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, PartSpec> e : ports.entrySet()) {
            if (e.getValue().isActuator() && actuatorType.equals(e.getValue().actuator())) out.add(e.getKey());
        }
        return out;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return fallback; }
    }

    // ---------------------------------------------------------------- helpers

    /** The sensor and actuator ports a program names, so a solution can be checked against the robot it assumes. */
    public static java.util.Set<String> portsNamed(Program program) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Rule r : program.rules()) {
            for (String name : new String[] { r.source(), r.target(), r.rhs().source(), r.arg().source() }) {
                if (name != null && name.length() >= 2 && (name.charAt(0) == 'S' || name.charAt(0) == 'A')
                        && Character.isDigit(name.charAt(1))) {
                    out.add(name);
                }
            }
        }
        return out;
    }
}
