import com.agurim.robocraft.program.*;

import java.util.*;

/**
 * Offline check of the RoboCraft mission ladder.
 *
 * Not a unit test of the plugin - it drives the real Evaluator (which is Bukkit-free on purpose)
 * through the same scenarios missions.yml describes, to answer two questions I should not assume:
 *   1. is the rule grammar expressive enough to SOLVE each mission?
 *   2. does the NAIVE solution actually fail, so the mission teaches the thing it claims to?
 * Scenarios are transcribed from missions.yml.
 */
public class BenchCheck {

    static int failures = 0;
    static int checks = 0;

    // ---- a tiny robot simulator: latched outputs + the engine's power model ----
    static final class Sim {
        final Program program;
        final Map<String, Integer> inputs = new LinkedHashMap<>();
        final Map<String, Integer> outputs = new LinkedHashMap<>();
        final int[] memory = new int[4];
        int energy;
        boolean running = true;
        int sensors, lampDrain, baseDrain = 1;

        Sim(Program p, int energy, int sensors, int lampDrain) {
            this.program = p; this.energy = energy; this.sensors = sensors; this.lampDrain = lampDrain;
        }

        void tick(int seconds) {
            if (!running) return;
            Evaluator.Result r = Evaluator.run(program, inputs, memory, seconds);
            // Latch: a port nobody commanded holds its previous value.
            outputs.putAll(r.commands());

            int drain = baseDrain + sensors;
            for (Map.Entry<String, Integer> e : outputs.entrySet()) {
                if (e.getKey().startsWith("A") && e.getValue() != 0) drain += lampDrain;
            }
            energy -= drain;
            if (energy <= 0) { energy = 0; running = false; }
        }

        int out(String port) { return outputs.getOrDefault(port, 0); }
    }

    // ---- rule builders -----------------------------------------------------
    static Rule when(String src, Op op, int rhs, String target, Verb verb) {
        return new Rule(src, op, Operand.of(rhs), target, verb, Operand.of(0));
    }
    static Rule whenArg(String src, Op op, int rhs, String target, Verb verb, int arg) {
        return new Rule(src, op, Operand.of(rhs), target, verb, Operand.of(arg));
    }
    static Rule whenRef(String src, Op op, String rhs, String target, Verb verb, int arg) {
        return new Rule(src, op, Operand.ref(rhs), target, verb, Operand.of(arg));
    }
    static Rule always(String target, Verb verb, String argRef) {
        return new Rule(Rule.ALWAYS, Op.LT, Operand.of(0), target, verb, Operand.ref(argRef));
    }
    static Program prog(Rule... rules) {
        Program p = new Program();
        for (Rule r : rules) p.add(r);
        return p;
    }

    // ---- assertions --------------------------------------------------------
    static void expect(String mission, String what, int actual, int wanted) {
        checks++;
        boolean ok = actual == wanted;
        if (!ok) failures++;
        System.out.printf("   %s %-46s got %-4d want %d%n", ok ? "ok  " : "FAIL", what, actual, wanted);
    }

    static void expectFails(String label, boolean didFail) {
        checks++;
        if (!didFail) failures++;
        System.out.printf("   %s %-46s %s%n", didFail ? "ok  " : "FAIL", label,
                didFail ? "(naive solution correctly fails)" : "(naive solution WRONGLY passes)");
    }

    /** Apply an environment reading, then let the loop settle for `ticks` iterations. */
    static void step(Sim sim, String port, int value, int ticks, int startSecond) {
        sim.inputs.put(port, value);
        for (int i = 0; i < ticks; i++) sim.tick(startSecond + i);
    }

    public static void main(String[] args) {
        System.out.println("RoboCraft mission ladder - offline check\n");

        nightLight();
        alarm();
        autoDoor();
        counter();
        thermostat();
        efficiency();

        System.out.printf("%n%d checks, %d failures%n", checks, failures);
        if (failures > 0) System.exit(1);
    }

    // 1. night light: sensor -> threshold -> output, and the latching trap
    static void nightLight() {
        System.out.println("1. night_light");
        Program good = prog(
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 2);
        step(s, "S1", 14, 6, 0);  expect("night_light", "day -> lamp off", s.out("A1"), 0);
        step(s, "S1", 2, 6, 6);   expect("night_light", "night -> lamp on", s.out("A1"), 1);
        step(s, "S1", 13, 6, 12); expect("night_light", "day again -> lamp off", s.out("A1"), 0);

        // The naive version forgets that outputs latch - which is the whole lesson.
        Program naive = prog(when("S1", Op.LT, 7, "A1", Verb.ON));
        Sim n = new Sim(naive, 2400, 1, 2);
        step(n, "S1", 14, 6, 0);
        step(n, "S1", 2, 6, 6);
        step(n, "S1", 13, 6, 12);
        expectFails("one-rule version stays lit in daylight", n.out("A1") != 0);
    }

    // 2. alarm: two actuators from one condition
    static void alarm() {
        System.out.println("2. alarm");
        Program p = prog(
                when("S1", Op.LT, 5, "A1", Verb.ON),
                when("S1", Op.LT, 5, "A2", Verb.ON),
                when("S1", Op.GE, 5, "A1", Verb.OFF),
                when("S1", Op.GE, 5, "A2", Verb.OFF));
        Sim s = new Sim(p, 2400, 1, 2);
        step(s, "S1", 16, 6, 0); expect("alarm", "far -> buzzer off", s.out("A1"), 0);
        step(s, "S1", 3, 6, 6);  expect("alarm", "near -> buzzer on", s.out("A1"), 1);
                                 expect("alarm", "near -> lamp on", s.out("A2"), 1);
        step(s, "S1", 15, 6, 12);expect("alarm", "far again -> buzzer off", s.out("A1"), 0);
        System.out.printf("   (uses %d of 5 rules)%n", p.size());
    }

    // 3. auto door
    static void autoDoor() {
        System.out.println("3. auto_door");
        Program p = prog(
                when("S1", Op.EQ, 1, "A1", Verb.ON),
                when("S1", Op.EQ, 0, "A1", Verb.OFF));
        Sim s = new Sim(p, 2400, 1, 4);
        step(s, "S1", 0, 6, 0);  expect("auto_door", "nobody -> gate shut", s.out("A1"), 0);
        step(s, "S1", 1, 6, 6);  expect("auto_door", "someone -> gate open", s.out("A1"), 1);
        step(s, "S1", 0, 6, 12); expect("auto_door", "gone -> gate shut", s.out("A1"), 0);
    }

    // 4. counter: edge detection needs memory AND the right rule order
    static void counter() {
        System.out.println("4. counter");
        Program good = prog(
                whenRef("S1", Op.GT, "M2", "M1", Verb.ADD, 1),   // rising edge: now > previous
                always("M2", Verb.SET, "S1"));                   // remember for next tick
        Sim s = new Sim(good, 2400, 1, 1);
        step(s, "S1", 0, 4, 0);
        step(s, "S1", 1, 4, 4);
        step(s, "S1", 0, 4, 8);
        step(s, "S1", 1, 4, 12);
        step(s, "S1", 0, 4, 16);
        expect("counter", "two entries -> M1 == 2", s.memory[0], 2);

        // Order matters: remembering before comparing means the edge is never seen.
        Program wrongOrder = prog(
                always("M2", Verb.SET, "S1"),
                whenRef("S1", Op.GT, "M2", "M1", Verb.ADD, 1));
        Sim w = new Sim(wrongOrder, 2400, 1, 1);
        step(w, "S1", 0, 4, 0);
        step(w, "S1", 1, 4, 4);
        step(w, "S1", 0, 4, 8);
        expectFails("remembering before comparing counts nothing", w.memory[0] == 0);

        // No memory at all: counts every tick the door is open, not every opening.
        Program noEdge = prog(whenArg("S1", Op.EQ, 1, "M1", Verb.ADD, 1));
        Sim ne = new Sim(noEdge, 2400, 1, 1);
        step(ne, "S1", 1, 4, 0);
        expectFails("counting without edge detection overcounts", ne.memory[0] > 1);
    }

    // 5. thermostat: the hysteresis signature - the same input, two different correct answers
    static void thermostat() {
        System.out.println("5. thermostat");
        Program good = prog(
                when("S1", Op.LT, 30, "A1", Verb.ON),
                when("S1", Op.GT, 60, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 2);
        step(s, "S1", 20, 6, 0);  expect("thermostat", "cold -> heater on", s.out("A1"), 1);
        step(s, "S1", 45, 6, 6);  expect("thermostat", "midband rising -> stays on", s.out("A1"), 1);
        step(s, "S1", 70, 6, 12); expect("thermostat", "hot -> heater off", s.out("A1"), 0);
        step(s, "S1", 45, 6, 18); expect("thermostat", "midband falling -> stays off", s.out("A1"), 0);

        // One threshold cannot give two different answers for 45 - that IS the lesson.
        Program single = prog(
                when("S1", Op.LT, 45, "A1", Verb.ON),
                when("S1", Op.GE, 45, "A1", Verb.OFF));
        Sim n = new Sim(single, 2400, 1, 2);
        step(n, "S1", 20, 6, 0);
        step(n, "S1", 45, 6, 6);
        expectFails("single threshold cannot hold through midband", n.out("A1") == 0);
    }

    // 6. efficiency: does the energy budget actually bite?
    static void efficiency() {
        System.out.println("6. efficiency");
        int lampDrain = 6, start = 150;

        Program good = prog(
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF));
        Sim s = new Sim(good, start, 1, lampDrain);
        step(s, "S1", 14, 7, 0);
        step(s, "S1", 2, 7, 7);
        step(s, "S1", 14, 7, 14);
        System.out.printf("   correct solution: %d energy left, running=%s%n", s.energy, s.running);
        expect("efficiency", "duty-cycled robot survives", s.running ? 1 : 0, 1);

        Sim s2 = new Sim(prog(
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF)), start, 2, lampDrain);
        step(s2, "S1", 14, 7, 0);
        step(s2, "S1", 2, 7, 7);
        step(s2, "S1", 14, 7, 14);
        System.out.printf("   correct + spare sensor: %d energy left, running=%s%n", s2.energy, s2.running);
        expect("efficiency", "still survives with an extra sensor", s2.running ? 1 : 0, 1);

        Program wasteful = prog(always("A1", Verb.ON, "S1"));
        Sim w = new Sim(wasteful, start, 1, lampDrain);
        step(w, "S1", 14, 7, 0);
        step(w, "S1", 2, 7, 7);
        step(w, "S1", 14, 7, 14);
        System.out.printf("   always-on solution: %d energy left, running=%s%n", w.energy, w.running);
        expectFails("always-on robot runs flat", !w.running);
    }
}
