import com.agurim.robocraft.program.*;

import java.util.*;

/**
 * Offline check of the RoboCraft mission ladder.
 *
 * Not a unit test of the plugin - it drives the real Evaluator (which is Bukkit-free on purpose)
 * through the same scenarios missions.yml describes, to answer two questions I should not assume:
 *   1. is the rule grammar expressive enough to SOLVE each mission?
 *   2. does the NAIVE solution actually fail, so the mission teaches the thing it claims to?
 * Scenarios are transcribed from missions.yml: three warm-ups, six rungs, seven bonus jobs.
 *
 * The simulator models the three things the bench adds on top of the Evaluator: latched outputs,
 * the engine's power arithmetic, and - since the flicker rung - FEEDBACK, the world talking back:
 * while any output is on, a named input reads higher on the NEXT tick. That one-tick lag is what
 * makes a single-threshold program oscillate, and exactly what a lamp beside its own sensor does.
 */
public class BenchCheck {

    static int failures = 0;
    static int checks = 0;

    // ---- a tiny robot simulator: latched outputs + feedback + the engine's power model ----
    static final class Sim {
        final Program program;
        final Map<String, Integer> base    = new LinkedHashMap<>();  // what the bench injects
        final Map<String, Integer> inputs  = new LinkedHashMap<>();  // what the program sees
        final Map<String, Integer> outputs = new LinkedHashMap<>();
        final int[] memory = new int[4];
        int energy;
        boolean running = true;
        int sensors, defaultDrain, baseDrain = 1;
        final Map<String, Integer> portDrain = new LinkedHashMap<>();
        int panels = 0, solarGain = 2;
        String fbInput = null; int fbAdd = 0, fbMax = 15;
        int switches = 0;

        Sim(Program p, int energy, int sensors, int defaultDrain) {
            this.program = p; this.energy = energy; this.sensors = sensors; this.defaultDrain = defaultDrain;
        }

        /** While any output is on, {@code input} reads base + add (capped) on the following tick. */
        Sim feedback(String input, int add, int max) { fbInput = input; fbAdd = add; fbMax = max; return this; }
        Sim drain(String port, int n) { portDrain.put(port, n); return this; }
        Sim solar(int panels) { this.panels = panels; return this; }

        void tick(int seconds) {
            if (!running) return;
            inputs.clear();
            inputs.putAll(base);
            if (fbInput != null && base.containsKey(fbInput)) {
                boolean anyOn = false;
                for (Map.Entry<String, Integer> e : outputs.entrySet()) {
                    if (e.getKey().startsWith("A") && e.getValue() != 0) { anyOn = true; break; }
                }
                if (anyOn) inputs.put(fbInput, Math.min(fbMax, base.get(fbInput) + fbAdd));
            }

            Evaluator.Result r = Evaluator.run(program, inputs, memory, seconds);
            Map<String, Integer> before = new LinkedHashMap<>(outputs);
            // Latch: a port nobody commanded holds its previous value.
            outputs.putAll(r.commands());
            for (Map.Entry<String, Integer> e : outputs.entrySet()) {
                boolean was = before.getOrDefault(e.getKey(), 0) != 0, is = e.getValue() != 0;
                if (was != is) switches++;
            }

            int drain = baseDrain + sensors;
            for (Map.Entry<String, Integer> e : outputs.entrySet()) {
                if (e.getKey().startsWith("A") && e.getValue() != 0) {
                    drain += portDrain.getOrDefault(e.getKey(), defaultDrain);
                }
            }
            // The engine reads the bench's injected light for solar, never the real clock.
            int light = base.getOrDefault("S1", 15);
            int gain = panels * solarGain * light / 15;
            energy = energy - drain + gain;
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
    static Rule whenRefArg(String src, Op op, int rhs, String target, Verb verb, String argRef) {
        return new Rule(src, op, Operand.of(rhs), target, verb, Operand.ref(argRef));
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
        System.out.printf("   %s %-52s got %-4d want %d%n", ok ? "ok  " : "FAIL", what, actual, wanted);
    }

    static void expectTrue(String what, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        System.out.printf("   %s %-52s %s%n", ok ? "ok  " : "FAIL", what, detail);
    }

    static void expectFails(String label, boolean didFail) {
        checks++;
        if (!didFail) failures++;
        System.out.printf("   %s %-52s %s%n", didFail ? "ok  " : "FAIL", label,
                didFail ? "(naive solution correctly fails)" : "(naive solution WRONGLY passes)");
    }

    /** Apply an environment reading, then let the loop settle for `ticks` iterations. */
    static void step(Sim sim, String port, int value, int ticks, int startSecond) {
        sim.base.put(port, value);
        for (int i = 0; i < ticks; i++) sim.tick(startSecond + i);
    }

    public static void main(String[] args) {
        System.out.println("RoboCraft mission ladder - offline check\n");

        nightLight();
        alarm();
        autoDoor();
        twilight();
        timedDoor();
        counter();
        flicker();
        thermostat();
        efficiency();
        fireAlarm();
        tunnelGauge();
        penGuard();
        rainVent();
        solarStation();
        colorLock();
        manualOverride();

        System.out.printf("%n%d checks, %d failures%n", checks, failures);
        if (failures > 0) System.exit(1);
    }

    // W1 night light: sensor -> threshold -> output, and the latching trap
    static void nightLight() {
        System.out.println("W1. night_light");
        Program good = prog(
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 6);
        step(s, "S1", 14, 6, 0);  expect("night_light", "day -> lamp off", s.out("A1"), 0);
        step(s, "S1", 2, 6, 6);   expect("night_light", "night -> lamp on", s.out("A1"), 1);
        step(s, "S1", 13, 6, 12); expect("night_light", "day again -> lamp off", s.out("A1"), 0);

        // The naive version forgets that outputs latch - which is the whole lesson.
        Program naive = prog(when("S1", Op.LT, 7, "A1", Verb.ON));
        Sim n = new Sim(naive, 2400, 1, 6);
        step(n, "S1", 14, 6, 0);
        step(n, "S1", 2, 6, 6);
        step(n, "S1", 13, 6, 12);
        expectFails("one-rule version stays lit in daylight", n.out("A1") != 0);
    }

    // W2 alarm: two actuators from one condition
    static void alarm() {
        System.out.println("W2. alarm");
        Program p = prog(
                when("S1", Op.EQ, 1, "A1", Verb.ON),
                when("S1", Op.EQ, 1, "A2", Verb.ON),
                when("S1", Op.EQ, 0, "A1", Verb.OFF),
                when("S1", Op.EQ, 0, "A2", Verb.OFF));
        Sim s = new Sim(p, 2400, 1, 4).drain("A2", 6);
        step(s, "S1", 0, 6, 0); expect("alarm", "nobody -> buzzer off", s.out("A1"), 0);
        step(s, "S1", 1, 6, 6); expect("alarm", "someone -> buzzer on", s.out("A1"), 1);
                                expect("alarm", "someone -> lamp on", s.out("A2"), 1);
        step(s, "S1", 0, 6, 12);expect("alarm", "gone -> buzzer off", s.out("A1"), 0);
        System.out.printf("   (uses %d of 5 rules)%n", p.size());
    }

    // W3 auto door
    static void autoDoor() {
        System.out.println("W3. auto_door");
        Program p = prog(
                when("S1", Op.EQ, 1, "A1", Verb.ON),
                when("S1", Op.EQ, 0, "A1", Verb.OFF));
        Sim s = new Sim(p, 2400, 1, 10);
        step(s, "S1", 0, 6, 0);  expect("auto_door", "nobody -> gate shut", s.out("A1"), 0);
        step(s, "S1", 1, 6, 6);  expect("auto_door", "someone -> gate open", s.out("A1"), 1);
        step(s, "S1", 0, 6, 12); expect("auto_door", "gone -> gate shut", s.out("A1"), 0);
    }

    // R1 twilight: a band between two thresholds, where the ORDER of the rules is the answer
    static void twilight() {
        System.out.println("R1. twilight");
        Program good = prog(
                when("S1", Op.GE, 10, "A1", Verb.OFF),   // day: off
                when("S1", Op.LT, 10, "A1", Verb.ON),    // dim: on
                when("S1", Op.LT,  4, "A1", Verb.OFF));  // full dark: off again, overriding above
        Sim s = new Sim(good, 2400, 1, 6);
        step(s, "S1", 14, 6, 0);  expect("twilight", "full day -> off", s.out("A1"), 0);
        step(s, "S1",  7, 6, 6);  expect("twilight", "dusk -> on", s.out("A1"), 1);
        step(s, "S1",  1, 6, 12); expect("twilight", "full dark -> off again", s.out("A1"), 0);
        step(s, "S1",  5, 6, 18); expect("twilight", "back into the band -> on", s.out("A1"), 1);
        step(s, "S1", 12, 6, 24); expect("twilight", "morning -> off", s.out("A1"), 0);
        System.out.printf("   (uses %d of 5 rules)%n", good.size());

        // The night-light answer: one threshold, so it cannot switch off again at the bottom.
        Program naive = prog(
                when("S1", Op.LT, 10, "A1", Verb.ON),
                when("S1", Op.GE, 10, "A1", Verb.OFF));
        Sim n = new Sim(naive, 2400, 1, 6);
        step(n, "S1", 14, 6, 0);
        step(n, "S1",  1, 6, 6);
        expectFails("a single threshold stays lit in full dark", n.out("A1") != 0);
    }

    // R2 timed door: memory holding a DEADLINE, not a flag
    static void timedDoor() {
        System.out.println("R2. timed_door");
        Program good = prog(
                whenRefArg("S1", Op.EQ, 1, "M1", Verb.SET, "TIME"),  // remember when last seen
                whenArg("S1", Op.EQ, 1, "M1", Verb.ADD, 3),          // ...plus the timeout
                whenRef("TIME", Op.LT, "M1", "A1", Verb.ON,  0),
                whenRef("TIME", Op.GE, "M1", "A1", Verb.OFF, 0));
        Sim s = new Sim(good, 2400, 1, 10);
        step(s, "S1", 0, 4, 0);   expect("timed_door", "nobody -> shut", s.out("A1"), 0);
        step(s, "S1", 1, 4, 4);   expect("timed_door", "arrival -> open", s.out("A1"), 1);
        step(s, "S1", 0, 2, 8);   expect("timed_door", "one second after leaving -> STILL open", s.out("A1"), 1);
        step(s, "S1", 0, 4, 10);  expect("timed_door", "past the deadline -> shut on its own", s.out("A1"), 0);
        System.out.printf("   (uses %d of 5 rules)%n", good.size());

        // The warm-up door: closes the instant they step away, which is the thing being improved on.
        Program reflex = prog(
                when("S1", Op.EQ, 1, "A1", Verb.ON),
                when("S1", Op.EQ, 0, "A1", Verb.OFF));
        Sim r = new Sim(reflex, 2400, 1, 10);
        step(r, "S1", 1, 4, 0);
        step(r, "S1", 0, 2, 4);
        expectFails("the reflex door shuts the moment they leave", r.out("A1") == 0);
    }

    // R3 counter: edge detection needs memory AND the right rule order - and the display shows it
    static void counter() {
        System.out.println("R3. counter");
        Program good = prog(
                whenRef("S1", Op.GT, "M2", "M1", Verb.ADD, 1),   // rising edge: now > previous
                always("M2", Verb.SET, "S1"),                    // remember for next tick
                always("A1", Verb.SHOW, "M1"));                  // the display shows the count
        Sim s = new Sim(good, 2400, 1, 1);
        step(s, "S1", 0, 4, 0);
        step(s, "S1", 1, 4, 4);
        step(s, "S1", 0, 4, 8);
        step(s, "S1", 1, 4, 12);
        step(s, "S1", 0, 4, 16);
        expect("counter", "two entries -> M1 == 2", s.memory[0], 2);
        expect("counter", "and the display shows 2", s.out("A1"), 2);

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

    // R4 flicker: the lamp lights its own sensor. Hysteresis on light, met before heat.
    static void flicker() {
        System.out.println("R4. flicker");
        // env light 4 at night; a lit lamp adds 9 -> 13 at the sensor, capped at 15
        Program good = prog(
                when("S1", Op.LT,  7, "A1", Verb.ON),
                when("S1", Op.GT, 13, "A1", Verb.OFF));     // off only ABOVE what the lamp itself gives
        Sim s = new Sim(good, 2400, 1, 6).feedback("S1", 9, 15);
        step(s, "S1", 14, 3, 0);  expect("flicker", "day -> off", s.out("A1"), 0);
        s.switches = 0;
        step(s, "S1", 4, 8, 3);   expect("flicker", "night -> on", s.out("A1"), 1);
        expectTrue("...and steady through its own light (<= 1 switch)", s.switches <= 1, s.switches + " switches");
        step(s, "S1", 14, 3, 11); expect("flicker", "morning -> off", s.out("A1"), 0);
        s.switches = 0;
        step(s, "S1", 4, 8, 14);  expect("flicker", "night again -> on", s.out("A1"), 1);
        expectTrue("...steady all night", s.switches <= 1, s.switches + " switches");

        // The ordinary night light: one threshold, and the lamp's own glow pushes the reading over it.
        Program naive = prog(
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF));
        Sim n = new Sim(naive, 2400, 1, 6).feedback("S1", 9, 15);
        step(n, "S1", 14, 3, 0);
        n.switches = 0;
        step(n, "S1", 4, 8, 3);
        expectFails("the night-light program flickers under feedback (" + n.switches + " switches)", n.switches > 1);

        // An OFF threshold at exactly the fed-back reading still flickers - it must be strictly above.
        Program edge = prog(
                when("S1", Op.LT,  7, "A1", Verb.ON),
                when("S1", Op.GE, 13, "A1", Verb.OFF));
        Sim e = new Sim(edge, 2400, 1, 6).feedback("S1", 9, 15);
        step(e, "S1", 14, 3, 0);
        e.switches = 0;
        step(e, "S1", 4, 8, 3);
        expectFails("OFF at >= 13 (the lamp's own reading) still flickers", e.switches > 1);
    }

    // R5 thermostat: the hysteresis signature again, on heat - the same input, two correct answers
    static void thermostat() {
        System.out.println("R5. thermostat");
        Program good = prog(
                when("S1", Op.LT, 30, "A1", Verb.ON),
                when("S1", Op.GT, 60, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 6);
        step(s, "S1", 20, 6, 0);  expect("thermostat", "cold -> heater on", s.out("A1"), 1);
        step(s, "S1", 45, 6, 6);  expect("thermostat", "midband rising -> stays on", s.out("A1"), 1);
        step(s, "S1", 70, 6, 12); expect("thermostat", "hot -> heater off", s.out("A1"), 0);
        step(s, "S1", 45, 6, 18); expect("thermostat", "midband falling -> stays off", s.out("A1"), 0);

        // One threshold cannot give two different answers for 45 - that IS the lesson.
        Program single = prog(
                when("S1", Op.LT, 45, "A1", Verb.ON),
                when("S1", Op.GE, 45, "A1", Verb.OFF));
        Sim n = new Sim(single, 2400, 1, 6);
        step(n, "S1", 20, 6, 0);
        step(n, "S1", 45, 6, 6);
        expectFails("single threshold cannot hold through midband", n.out("A1") == 0);
    }

    // R6 efficiency: does the energy budget actually bite?
    static void efficiency() {
        System.out.println("R6. efficiency");
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

    // B1 fire alarm: a threshold chosen from real magnitudes (torch 52, magma 56), two outputs
    static void fireAlarm() {
        System.out.println("B1. fire_alarm");
        // A1 buzzer, A2 display
        Program good = prog(
                always("A2", Verb.SHOW, "S1"),
                when("S1", Op.GT, 54, "A1", Verb.ON),
                when("S1", Op.LE, 54, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 4).drain("A2", 1);
        step(s, "S1", 50, 3, 0);  expect("fire_alarm", "calm -> display 50", s.out("A2"), 50);
                                  expect("fire_alarm", "calm -> buzzer off", s.out("A1"), 0);
        step(s, "S1", 52, 3, 3);  expect("fire_alarm", "a torch (52) does NOT ring", s.out("A1"), 0);
        step(s, "S1", 56, 3, 6);  expect("fire_alarm", "magma (56) rings", s.out("A1"), 1);
                                  expect("fire_alarm", "...and the display shows 56", s.out("A2"), 56);
        step(s, "S1", 50, 3, 9);  expect("fire_alarm", "cooled -> quiet", s.out("A1"), 0);
        step(s, "S1", 62, 3, 12); expect("fire_alarm", "hot again -> rings again", s.out("A1"), 1);

        Program low = prog(
                always("A2", Verb.SHOW, "S1"),
                when("S1", Op.GT, 50, "A1", Verb.ON),
                when("S1", Op.LE, 50, "A1", Verb.OFF));
        Sim n = new Sim(low, 2400, 1, 4).drain("A2", 1);
        step(n, "S1", 50, 3, 0);
        step(n, "S1", 52, 3, 3);
        expectFails("a threshold at 50 rings on a torch", n.out("A1") != 0);
    }

    // B2 tunnel gauge: a directional sensor; 16 means "sees nothing"
    static void tunnelGauge() {
        System.out.println("B2. tunnel_gauge");
        Program good = prog(
                always("A2", Verb.SHOW, "S1"),
                when("S1", Op.LT, 3, "A1", Verb.ON),
                when("S1", Op.GE, 3, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 4).drain("A2", 1);
        step(s, "S1", 11, 3, 0);  expect("tunnel_gauge", "clear to the wall -> display 11", s.out("A2"), 11);
                                  expect("tunnel_gauge", "clear -> quiet", s.out("A1"), 0);
        step(s, "S1", 2, 3, 3);   expect("tunnel_gauge", "cave-in at 2 -> buzzer", s.out("A1"), 1);
        step(s, "S1", 11, 3, 6);  expect("tunnel_gauge", "cleared -> quiet", s.out("A1"), 0);
        step(s, "S1", 16, 3, 9);  expect("tunnel_gauge", "nothing in range reads 16, and is quiet", s.out("A1"), 0);
                                  expect("tunnel_gauge", "display shows 16", s.out("A2"), 16);
    }

    // B3 pen guard: the counter on an animal sensor, plus a lamp, in exactly five rules
    static void penGuard() {
        System.out.println("B3. pen_guard");
        Program good = prog(
                whenRef("S1", Op.GT, "M2", "M1", Verb.ADD, 1),
                when("S1", Op.EQ, 1, "A1", Verb.ON),
                when("S1", Op.EQ, 0, "A1", Verb.OFF),
                always("M2", Verb.SET, "S1"),
                always("A2", Verb.SHOW, "M1"));
        Sim s = new Sim(good, 2400, 1, 6).drain("A2", 1);
        step(s, "S1", 0, 2, 0);   expect("pen_guard", "all in -> lamp off", s.out("A1"), 0);
                                  expect("pen_guard", "all in -> count 0", s.out("A2"), 0);
        step(s, "S1", 1, 2, 2);   expect("pen_guard", "one out -> lamp on", s.out("A1"), 1);
        step(s, "S1", 0, 2, 4);   expect("pen_guard", "back -> lamp off", s.out("A1"), 0);
                                  expect("pen_guard", "one escape -> display 1", s.out("A2"), 1);
        step(s, "S1", 1, 2, 6);
        step(s, "S1", 0, 2, 8);   expect("pen_guard", "two escapes -> display 2", s.out("A2"), 2);
                                  expect("pen_guard", "two escapes -> M1 == 2", s.memory[0], 2);
        expectTrue("fits in exactly five rules", good.size() == 5, good.size() + " rules");
    }

    // B4 rain vent: two sensors; "and" written as a later rule that wins
    static void rainVent() {
        System.out.println("B4. rain_vent");
        // S1 light, S2 rain, A1 the roof gate (ON = open)
        Program good = prog(
                when("S1", Op.GE, 7, "A1", Verb.ON),     // light: open
                when("S1", Op.LT, 7, "A1", Verb.OFF),    // dark: close
                when("S2", Op.EQ, 1, "A1", Verb.OFF));   // rain: close - LAST, so it wins
        Sim s = new Sim(good, 2400, 2, 10);
        s.base.put("S2", 0);
        step(s, "S1", 14, 3, 0);  expect("rain_vent", "dry day -> vent open", s.out("A1"), 1);
        step(s, "S2", 1, 3, 3);   expect("rain_vent", "rain starts -> vent shut despite light", s.out("A1"), 0);
        s.base.put("S2", 0);
        step(s, "S1", 3, 3, 6);   expect("rain_vent", "dry but night -> shut", s.out("A1"), 0);
        step(s, "S1", 14, 3, 9);  expect("rain_vent", "dry morning -> open again", s.out("A1"), 1);

        Program rainFirst = prog(
                when("S2", Op.EQ, 1, "A1", Verb.OFF),
                when("S1", Op.GE, 7, "A1", Verb.ON),
                when("S1", Op.LT, 7, "A1", Verb.OFF));
        Sim n = new Sim(rainFirst, 2400, 2, 10);
        n.base.put("S2", 0);
        step(n, "S1", 14, 3, 0);
        step(n, "S2", 1, 3, 3);
        expectFails("rain rule first is overridden by the light rule", n.out("A1") != 0);
    }

    // B5 solar station: energy BALANCE - gain vs drain, sizing the array
    static void solarStation() {
        System.out.println("B5. solar_station");
        // base 1 + light sensor 1 + display 1 (on, since SHOW writes a value) = 3 out; 2 in per panel
        Program p = prog(always("A1", Verb.SHOW, "S1"));

        Sim one = new Sim(p, 40, 1, 1).solar(1);
        step(one, "S1", 15, 60, 0);
        System.out.printf("   one panel:  %d energy after the day, running=%s%n", one.energy, one.running);
        expectFails("one panel loses 1 a tick and dies by night", !one.running);

        Sim two = new Sim(p, 40, 1, 1).solar(2);
        step(two, "S1", 15, 60, 0);
        expect("solar_station", "two panels: still running at dusk", two.running ? 1 : 0, 1);
        expect("solar_station", "...and the display shows the light", two.out("A1"), 15);
        int banked = two.energy;
        step(two, "S1", 2, 20, 60);
        System.out.printf("   two panels: banked %d by dusk, %d left after the night%n", banked, two.energy);
        expect("solar_station", "two panels survive the night on what they banked", two.running ? 1 : 0, 1);

        Sim spare = new Sim(p, 40, 2, 1).solar(2);   // a spare sensor left attached burns the margin
        step(spare, "S1", 15, 60, 0);
        step(spare, "S1", 2, 20, 60);
        expectFails("a spare sensor on two panels does not make the night", !spare.running);
    }

    // B6 colour lock: equality on a code - 14 is not "more than" 11
    static void colorLock() {
        System.out.println("B6. color_lock");
        Program good = prog(
                when("S1", Op.EQ, 13, "A1", Verb.ON),
                when("S1", Op.NE, 13, "A1", Verb.OFF));
        Sim s = new Sim(good, 2400, 1, 10);
        step(s, "S1", 16, 3, 0);  expect("color_lock", "no block -> shut", s.out("A1"), 0);
        step(s, "S1", 14, 3, 3);  expect("color_lock", "red -> shut", s.out("A1"), 0);
        step(s, "S1", 11, 3, 6);  expect("color_lock", "blue -> shut", s.out("A1"), 0);
        step(s, "S1", 13, 3, 9);  expect("color_lock", "green -> open", s.out("A1"), 1);
        step(s, "S1", 16, 3, 12); expect("color_lock", "key removed -> shut", s.out("A1"), 0);

        Program lessThan = prog(
                when("S1", Op.LE, 13, "A1", Verb.ON),
                when("S1", Op.GT, 13, "A1", Verb.OFF));
        Sim n = new Sim(lessThan, 2400, 1, 10);
        step(n, "S1", 11, 3, 0);
        expectFails("'<=' instead of '=' opens on blue", n.out("A1") != 0);
    }

    // B7 manual override: "or" - two reasons for one output, and the lever's rule must come last
    static void manualOverride() {
        System.out.println("B7. manual_override");
        // S1 light, S2 lever
        Program good = prog(
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF),
                when("S2", Op.GT, 0, "A1", Verb.ON));    // the lever wins, so it comes last
        Sim s = new Sim(good, 2400, 2, 6);
        s.base.put("S2", 0);
        step(s, "S1", 14, 3, 0);  expect("manual_override", "day, lever down -> off", s.out("A1"), 0);
        step(s, "S2", 15, 3, 3);  expect("manual_override", "lever up -> on despite daylight", s.out("A1"), 1);
        s.base.put("S2", 0);
        step(s, "S1", 2, 3, 6);   expect("manual_override", "lever down, night -> on", s.out("A1"), 1);
        step(s, "S1", 14, 3, 9);  expect("manual_override", "morning -> off", s.out("A1"), 0);

        Program leverFirst = prog(
                when("S2", Op.GT, 0, "A1", Verb.ON),
                when("S1", Op.LT, 7, "A1", Verb.ON),
                when("S1", Op.GE, 7, "A1", Verb.OFF));
        Sim n = new Sim(leverFirst, 2400, 2, 6);
        n.base.put("S2", 0);
        step(n, "S1", 14, 3, 0);
        step(n, "S2", 15, 3, 3);
        expectFails("lever rule first is switched off again by the day rule", n.out("A1") == 0);
    }
}
