package com.agurim.robocraft.diag;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Attachment;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartLabels;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.mission.MissionService;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Op;
import com.agurim.robocraft.program.Operand;
import com.agurim.robocraft.program.Program;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.program.RuleEdit;
import com.agurim.robocraft.program.Verb;
import com.agurim.robocraft.robot.Robot;
import com.agurim.robocraft.sense.SensorReader;
import com.agurim.robocraft.command.RoboCraftCommand;
import com.agurim.robocraft.listener.InteractListener;
import com.agurim.robocraft.ui.Guide;
import com.agurim.robocraft.ui.ProgramMenu;
import com.agurim.robocraft.ui.StatusBar;
import com.agurim.robocraft.ui.TrophyShelf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds real robots out of real blocks, drives known inputs through them, checks that real blocks
 * moved at the other end - then puts everything back exactly as it was.
 *
 * <p>This exists because the interesting failures are all server-side and version-specific:
 * whether {@code setBlockData} on a REDSTONE_LAMP sticks with no redstone source, whether an
 * IRON_TRAPDOOR opens without redstone, whether TextDisplay entities spawn and update in place.
 * None of that can be checked by compiling, and a teacher deploying to a new server before a
 * lesson wants a straight answer to "does this work here?"
 *
 * <p>Runs from the console too, which is what makes it automatable.
 *
 * <p>It deliberately does NOT build a Component Board or a kiosk: those write to a student's plot,
 * and a diagnostic must never damage real progress. Their geometry is checked arithmetically instead.
 */
public final class SelfTest {

    private SelfTest() {}

    /** One assertion and how it went. */
    public record Check(String name, boolean ok, String detail) { }

    public static List<Check> run(RoboCraftPlugin plugin, CommandSender sender, Location origin) {
        List<Check> checks = new ArrayList<>();
        Scratch scratch = new Scratch();
        try {
            loop(plugin, sender, origin, scratch, checks);
            actuators(plugin, origin.clone().add(0, 0, 3), scratch, checks);
            sensors(plugin, origin.clone().add(0, 0, 6), scratch, checks);
            portsAndStore(plugin, origin.clone().add(0, 0, 9), scratch, checks);
            bench(plugin, sender, origin.clone().add(0, 0, 12), scratch, checks);
            attachment(plugin, origin.clone().add(0, 0, 15), scratch, checks);
            gui(plugin, origin.clone().add(0, 0, 19), scratch, checks);
            load(plugin, sender, origin.clone().add(0, 0, 24), scratch, checks);
            assistant(plugin, origin.clone().add(0, 0, 30), scratch, checks);
            editing(plugin, checks);
            vocabulary(plugin, checks);
            geometry(plugin, checks);
            progression(plugin, checks);
            legibility(plugin, checks);
        } catch (Exception e) {
            checks.add(new Check("self-test ran without throwing", false, e.toString()));
            plugin.getLogger().warning("selftest threw: " + e);
        } finally {
            try { scratch.restore(plugin); }
            catch (Exception e) { checks.add(new Check("scratch area restored", false, e.toString())); }
        }
        return checks;
    }

    // ------------------------------------------------------------- A. the loop

    private static void loop(RoboCraftPlugin plugin, CommandSender sender, Location origin,
                             Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part battery    = kind(plugin, "battery");
        Part sensor     = sensor(plugin, "light");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || battery == null || sensor == null || lamp == null) {
            checks.add(new Check("parts.yml supplies controller/battery/light sensor/lamp", false,
                    "one of them is missing"));
            return;
        }

        Location ctrl = origin.clone();
        Location lampLoc = origin.clone().add(3, 0, 0);
        Location sensLoc = origin.clone().add(2, 0, 0);
        for (int dx = 0; dx <= 3; dx++) scratch.clear(origin.clone().add(dx, 1, 0));

        String key = PartStore.key(ctrl);
        scratch.part(plugin, ctrl, controller, "", "");
        scratch.part(plugin, origin.clone().add(1, 0, 0), battery, key, "");
        scratch.part(plugin, sensLoc, sensor, key, "S1");
        scratch.part(plugin, lampLoc, lamp, key, "A1");
        scratch.robot(key);

        checks.add(new Check("three parts attach to the controller",
                plugin.placements().partsOf(key).size() == 3,
                plugin.placements().partsOf(key).size() + " attached (want 3)"));

        Robot robot = plugin.robots().getOrCreate(key, null);
        robot.energy(plugin.batteryCapacity(key));
        robot.program(nightLight());
        robot.outputs().clear();
        robot.start(plugin.engine().now());
        int startEnergy = robot.energy();

        plugin.engine().tick(robot, Map.of("light", 2));
        checks.add(new Check("dark -> rule fires, output commanded ON",
                robot.outputs().getOrDefault("A1", 0) == 1, "A1 = " + robot.outputs().get("A1")));
        checks.add(new Check("dark -> REDSTONE_LAMP block data really is lit",
                lit(lampLoc), "lit=" + lit(lampLoc)));
        checks.add(new Check("sensor reading recorded on the robot",
                robot.inputs().getOrDefault("S1", -1) == 2, "S1 = " + robot.inputs().get("S1")));

        plugin.engine().tick(robot, Map.of("light", 14));
        checks.add(new Check("light -> output commanded OFF",
                robot.outputs().getOrDefault("A1", 1) == 0, "A1 = " + robot.outputs().get("A1")));
        checks.add(new Check("light -> lamp block really is unlit", !lit(lampLoc), "lit=" + lit(lampLoc)));

        robot.program(new Program());                       // no rules at all
        boolean before = lit(lampLoc);
        plugin.engine().tick(robot, Map.of("light", 2));
        checks.add(new Check("no matching rule -> output latches instead of resetting",
                lit(lampLoc) == before, "lit " + before + " -> " + lit(lampLoc)));

        checks.add(new Check("battery drained while running", robot.energy() < startEnergy,
                startEnergy + " -> " + robot.energy()));

        // Memory: the counter mission depends on writes landing immediately and order mattering.
        Program counter = new Program();
        counter.add(new Rule("S1", Op.GT, Operand.ref("M2"), "M1", Verb.ADD, Operand.of(1)));
        counter.add(new Rule(Rule.ALWAYS, Op.LT, Operand.of(0), "M2", Verb.SET, Operand.ref("S1")));
        robot.program(counter);
        java.util.Arrays.fill(robot.memory(), 0);
        plugin.engine().tick(robot, Map.of("light", 0));
        plugin.engine().tick(robot, Map.of("light", 1));    // rising edge -> M1 = 1
        plugin.engine().tick(robot, Map.of("light", 1));    // no edge -> still 1
        checks.add(new Check("memory counts a rising edge exactly once",
                robot.mem(0) == 1, "M1 = " + robot.mem(0) + " (want 1)"));

        PartLabels.refresh(plugin, sensLoc);
        checks.add(new Check("sensor carries a TextDisplay label",
                labels(plugin, sensLoc, sensor.id()) > 0, "none found"));
        PartLabels.refresh(plugin, sensLoc);
        checks.add(new Check("refresh updates the label in place, never duplicates",
                labels(plugin, sensLoc, sensor.id()) == 1,
                labels(plugin, sensLoc, sensor.id()) + " labels (want 1)"));

        // A part just outside the radius is placed, inert, and looks identical to a working one.
        // The first person to hit it missed it completely and had to be told by hand, so the label
        // now carries the measurement rather than only the verdict. Far enough out that the check
        // is about the number, not about rounding.
        Location farOff = sensLoc.clone().add(0, 0, 40);
        String hint = PartLabels.attachHint(plugin, farOff);
        int radius = plugin.getConfig().getInt("robot.attach-radius", 4);
        checks.add(new Check("a detached part's label states the distance and the limit",
                hint.matches(".*\\d.*") && hint.contains(String.valueOf(radius)), hint));

        // Render the state trace over a robot that has readings, memory and a program, so the log
        // also shows what a student sees when they ask why their robot did something.
        try {
            robot.program(nightLight());
            plugin.engine().tick(robot, Map.of("light", 3));
            com.agurim.robocraft.ui.Trace.send(plugin, sender, robot);
            checks.add(new Check("the state trace renders", true, ""));
        } catch (Exception e) {
            checks.add(new Check("the state trace renders", false, e.toString()));
        }
    }

    // ---------------------------------------------------------- B. actuators

    private static void actuators(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part battery    = kind(plugin, "battery");
        if (controller == null || battery == null) return;

        Location ctrl = origin.clone();
        String key = PartStore.key(ctrl);
        scratch.part(plugin, ctrl, controller, "", "");
        scratch.part(plugin, origin.clone().add(1, 0, 0), battery, key, "");
        scratch.robot(key);

        Robot robot = plugin.robots().getOrCreate(key, null);
        robot.energy(plugin.batteryCapacity(key));
        robot.outputs().clear();

        int slot = 2;
        for (String type : List.of("gate", "buzzer", "marker", "display")) {
            Part part = actuator(plugin, type);
            if (part == null) continue;
            Location loc = origin.clone().add(slot, 0, 0);
            String port = "A" + (slot - 1);
            scratch.clear(origin.clone().add(slot, 1, 0));
            scratch.part(plugin, loc, part, key, port);
            slot++;

            Program p = new Program();
            p.add(new Rule(Rule.ALWAYS, Op.LT, Operand.of(0), port, Verb.ON, Operand.of(0)));
            robot.program(p);
            robot.start(plugin.engine().now());
            plugin.engine().tick(robot, Map.of());

            boolean commanded = robot.outputs().getOrDefault(port, 0) != 0;
            checks.add(new Check(type + ": ON is commanded without throwing", commanded,
                    port + " = " + robot.outputs().get(port)));

            if ("gate".equals(type)) {
                checks.add(new Check("gate: IRON_TRAPDOOR really opened (no redstone involved)",
                        open(loc), "open=" + open(loc)));
                Program off = new Program();
                off.add(new Rule(Rule.ALWAYS, Op.LT, Operand.of(0), port, Verb.OFF, Operand.of(0)));
                robot.program(off);
                plugin.engine().tick(robot, Map.of());
                checks.add(new Check("gate: and really closed again", !open(loc), "open=" + open(loc)));
            }
            if ("display".equals(type)) {
                Program show = new Program();
                show.add(new Rule(Rule.ALWAYS, Op.LT, Operand.of(0), port, Verb.SHOW, Operand.of(42)));
                robot.program(show);
                plugin.engine().tick(robot, Map.of());
                checks.add(new Check("display: SHOW carries an arbitrary value through",
                        robot.outputs().getOrDefault(port, 0) == 42,
                        port + " = " + robot.outputs().get(port) + " (want 42)"));
            }
        }
    }

    // ------------------------------------------------------------ C. sensors

    private static void sensors(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
        // distance: put a wall three blocks north and see if the raycast finds it
        Part distance = sensor(plugin, "distance");
        if (distance != null) {
            Location loc = origin.clone();
            scratch.part(plugin, loc, distance, "", "");
            for (int i = 1; i <= 4; i++) scratch.clear(loc.clone().add(0, 0, -i));
            scratch.set(loc.clone().add(0, 0, -3), Material.STONE);
            int read = SensorReader.read(loc, plugin.placements().get(loc), distance, null);
            checks.add(new Check("distance sensor measures a wall 3 blocks away",
                    read == 3, "read " + read + " (want 3)"));
        }

        // colour: the index of the block underneath, in vanilla dye order
        Part colour = sensor(plugin, "color");
        if (colour != null) {
            Location loc = origin.clone().add(2, 0, 0);
            scratch.part(plugin, loc, colour, "", "");
            scratch.set(loc.clone().add(0, -1, 0), Material.RED_WOOL);
            int read = SensorReader.read(loc, plugin.placements().get(loc), colour, null);
            checks.add(new Check("colour sensor reads RED under it",
                    read == DyeColor.RED.ordinal(), "read " + read + " (want " + DyeColor.RED.ordinal() + ")"));
        }

        // heat: a magma block nearby must push it above neutral
        Part heat = sensor(plugin, "heat");
        if (heat != null) {
            Location loc = origin.clone().add(4, 0, 0);
            scratch.part(plugin, loc, heat, "", "");
            int cold = SensorReader.read(loc, plugin.placements().get(loc), heat, null);
            scratch.set(loc.clone().add(1, 0, 0), Material.MAGMA_BLOCK);
            int warm = SensorReader.read(loc, plugin.placements().get(loc), heat, null);
            checks.add(new Check("heat sensor rises near a heat source",
                    warm > cold, cold + " -> " + warm));
        }

        // redstone: a redstone block beside it must register
        Part redstone = sensor(plugin, "redstone");
        if (redstone != null) {
            Location loc = origin.clone().add(6, 0, 0);
            scratch.part(plugin, loc, redstone, "", "");
            scratch.set(loc.clone().add(1, 0, 0), Material.REDSTONE_BLOCK);
            int read = SensorReader.read(loc, plugin.placements().get(loc), redstone, null);
            checks.add(new Check("redstone sensor sees an adjacent redstone block",
                    read > 0, "power " + read));
        }

        // everything else: must return a value in its documented range and never throw
        for (String type : List.of("light", "player", "mob", "rain", "random")) {
            Part part = sensor(plugin, type);
            if (part == null) continue;
            Location loc = origin.clone().add(0, 0, 2);
            scratch.part(plugin, loc, part, "", "");
            try {
                int read = SensorReader.read(loc, plugin.placements().get(loc), part, null);
                boolean sane = switch (type) {
                    case "light"           -> read >= 0 && read <= 15;
                    case "player", "mob", "rain" -> read == 0 || read == 1;
                    case "random"          -> read >= 0 && read < 100;
                    default                -> true;
                };
                checks.add(new Check(type + " sensor returns a value in range", sane, "read " + read));
            } catch (Exception e) {
                checks.add(new Check(type + " sensor returns a value in range", false, e.toString()));
            }
            plugin.placements().remove(loc);
        }

        // the bench must win over the world, or missions test nothing
        Part light = sensor(plugin, "light");
        if (light != null) {
            Location loc = origin.clone().add(0, 0, 2);
            scratch.part(plugin, loc, light, "", "");
            int forced = SensorReader.read(loc, plugin.placements().get(loc), light, Map.of("light", 9));
            checks.add(new Check("a simulated reading overrides the world",
                    forced == 9, "read " + forced + " (want 9)"));
        }
    }

    // --------------------------------------------------- D. ports and storage

    private static void portsAndStore(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part light      = sensor(plugin, "light");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || light == null || lamp == null) return;

        Location ctrl = origin.clone();
        String key = PartStore.key(ctrl);
        scratch.part(plugin, ctrl, controller, "", "");
        scratch.robot(key);

        String s1 = plugin.placements().nextPort(key, true);
        scratch.part(plugin, origin.clone().add(1, 0, 0), light, key, s1);
        String s2 = plugin.placements().nextPort(key, true);
        scratch.part(plugin, origin.clone().add(2, 0, 0), light, key, s2);
        String a1 = plugin.placements().nextPort(key, false);
        scratch.part(plugin, origin.clone().add(3, 0, 0), lamp, key, a1);

        checks.add(new Check("ports are handed out in order: S1, S2, A1",
                "S1".equals(s1) && "S2".equals(s2) && "A1".equals(a1),
                s1 + ", " + s2 + ", " + a1));

        // placements.yml must survive a reload, or a restart loses every student's build
        plugin.placements().save();
        PartStore reloaded = new PartStore(plugin);
        Placed roundTrip = reloaded.byKey(PartStore.key(origin.clone().add(1, 0, 0)));
        checks.add(new Check("placements survive a save/load round trip",
                roundTrip != null && light.id().equals(roundTrip.partId())
                        && key.equals(roundTrip.robot()) && "S1".equals(roundTrip.port()),
                roundTrip == null ? "entry missing after reload" : roundTrip.toString()));

        checks.add(new Check("a controller is discoverable in the store",
                reloaded.controllers().contains(key), "controllers: " + reloaded.controllers().size()));

        checks.add(new Check("a location key round-trips",
                PartStore.fromKey(PartStore.key(ctrl)) != null
                        && PartStore.fromKey(PartStore.key(ctrl)).getBlockX() == ctrl.getBlockX(),
                "key " + PartStore.key(ctrl)));
        checks.add(new Check("a malformed key is rejected rather than throwing",
                PartStore.fromKey("nonsense") == null, "expected null"));
    }

    // ------------------------------------------------------- D2. the bench

    /**
     * Runs a real mission end to end, then runs the obvious wrong answer and checks it FAILS.
     *
     * <p>The second half matters as much as the first. A bench that passes everything teaches
     * nothing, and a mission whose naive solution slips through is a mission with no lesson in it.
     *
     * <p>Driven synchronously by ticking the engine directly, so a scenario that takes fifteen
     * seconds of wall clock in play finishes here in milliseconds. Progress is passed as a null
     * owner, so a self-test can never award a student a mission they did not do.
     */
    private static void bench(RoboCraftPlugin plugin, CommandSender sender, Location origin,
                              Scratch scratch, List<Check> checks) {
        Mission mission = plugin.missions().registry().byId("night_light");
        if (mission == null) {
            checks.add(new Check("missions.yml still defines night_light", false, "not found"));
            return;
        }

        Part controller = kind(plugin, "controller");
        Part battery    = kind(plugin, "battery");
        Part light      = sensor(plugin, "light");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || battery == null || light == null || lamp == null) return;

        Location ctrl = origin.clone();
        String key = PartStore.key(ctrl);
        scratch.part(plugin, ctrl, controller, "", "");
        scratch.part(plugin, origin.clone().add(1, 0, 0), battery, key, "");
        scratch.robot(key);

        Robot robot = plugin.robots().getOrCreate(key, null);

        // A robot missing the parts the mission calls for must be refused, with a reason.
        robot.program(nightLight());
        String refused = plugin.missions().start(sender, null, robot, mission);
        checks.add(new Check("bench refuses a robot that is missing parts, and says which",
                refused != null && refused.contains("חסר"), String.valueOf(refused)));

        scratch.clear(origin.clone().add(2, 1, 0));
        scratch.clear(origin.clone().add(3, 1, 0));
        scratch.part(plugin, origin.clone().add(2, 0, 0), light, key, "S1");
        scratch.part(plugin, origin.clone().add(3, 0, 0), lamp, key, "A1");

        // The correct two-rule program must pass.
        robot.program(nightLight());
        String why = plugin.missions().start(sender, null, robot, mission);
        checks.add(new Check("bench accepts a correctly built robot", why == null, String.valueOf(why)));
        if (why == null) {
            drive(plugin, robot, key);
            checks.add(new Check("a correct night-light program passes the bench",
                    plugin.missions().lastOutcome(key) == MissionService.Outcome.PASS,
                    "outcome " + plugin.missions().lastOutcome(key)));
        }

        // The naive one-rule program must fail: it never turns the lamp off again.
        Program naive = new Program();
        naive.add(new Rule("S1", Op.LT, Operand.of(7), "A1", Verb.ON, Operand.of(0)));
        robot.program(naive);
        if (plugin.missions().start(sender, null, robot, mission) == null) {
            drive(plugin, robot, key);
            checks.add(new Check("the naive one-rule program is caught by the bench",
                    plugin.missions().lastOutcome(key) == MissionService.Outcome.FAIL,
                    "outcome " + plugin.missions().lastOutcome(key)));
        }

        // --- rung 4, the flicker fix: the bench reproduces the playtest's feedback loop ---
        // A student's lamp lit its own light sensor and the robot oscillated every tick. `feedback`
        // raises the injected reading while the lamp is on; `steady` counts the switches. The
        // ordinary night-light program must now FAIL here, and two thresholds must pass.
        Mission flicker = plugin.missions().registry().byId("flicker");
        if (flicker == null) {
            checks.add(new Check("missions.yml defines the flicker rung", false, "not found"));
        } else {
            robot.program(nightLight());
            if (plugin.missions().start(sender, null, robot, flicker) == null) {
                drive(plugin, robot, key);
                checks.add(new Check("under feedback the night-light program flickers, and the bench says so",
                        plugin.missions().lastOutcome(key) == MissionService.Outcome.FAIL,
                        "outcome " + plugin.missions().lastOutcome(key)));
            }
            Program hysteresis = new Program();
            hysteresis.add(new Rule("S1", Op.LT, Operand.of(7),  "A1", Verb.ON,  Operand.of(0)));
            hysteresis.add(new Rule("S1", Op.GT, Operand.of(13), "A1", Verb.OFF, Operand.of(0)));
            robot.program(hysteresis);
            if (plugin.missions().start(sender, null, robot, flicker) == null) {
                drive(plugin, robot, key);
                checks.add(new Check("two thresholds hold the lamp steady through its own light",
                        plugin.missions().lastOutcome(key) == MissionService.Outcome.PASS,
                        "outcome " + plugin.missions().lastOutcome(key)));
            }
        }

        // --- solar gain follows the bench's sun, never the real clock ---
        // Otherwise a solar mission tested at midnight would gain nothing and tell the student
        // their sizing was wrong - the first mission ever to depend on the time of day.
        Part solar = kind(plugin, "solar");
        if (solar != null && !plugin.missions().isRunning(key)) {
            scratch.part(plugin, origin.clone().add(4, 0, 0), solar, key, "");
            robot.program(nightLight());
            robot.energy(1000);
            robot.start(plugin.engine().now());
            plugin.engine().tick(robot, Map.of("light", 15));
            int sunny = robot.energy() - 1000;
            robot.energy(1000);
            plugin.engine().tick(robot, Map.of("light", 0));
            int dark = robot.energy() - 1000;
            checks.add(new Check("a solar panel charges from the injected light, not the world's",
                    sunny > dark, "delta at light 15 = " + sunny + ", at light 0 = " + dark));
            robot.stop("selftest");
        }

        checks.add(new Check("a self-test run never records progress",
                plugin.store().completedMissions(java.util.UUID.nameUUIDFromBytes("selftest".getBytes()))
                        .isEmpty(), "expected no missions recorded"));
    }

    /** Tick the engine until the bench finishes, with a bound so a stuck run cannot hang the server. */
    private static void drive(RoboCraftPlugin plugin, Robot robot, String key) {
        for (int i = 0; i < 200 && plugin.missions().isRunning(key); i++) {
            plugin.engine().tick(robot);
        }
        plugin.missions().abort(key);
    }

    // --------------------------------------------------------- D4. attachment

    /**
     * Building and un-building a robot: the code every student's build depends on, and the code
     * a test can otherwise never reach, because the listener around it needs a real player holding
     * a real item. Hence {@link Attachment} existing separately at all.
     */
    private static void attachment(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part light      = sensor(plugin, "light");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || light == null || lamp == null) return;

        Location ctrl = origin.clone();
        Location sens = origin.clone().add(2, 0, 0);
        Location act  = origin.clone().add(0, 0, 2);
        Location far  = origin.clone().add(12, 0, 0);

        // --- a controller owns a robot rather than joining one ---
        scratch.set(ctrl, controller.block());
        scratch.track(ctrl);
        Attachment.Result made = Attachment.place(plugin, ctrl, controller, BlockFace.NORTH);
        String key = made.controllerKey();
        scratch.robot(key);
        checks.add(new Check("placing a controller creates a robot, not an attachment",
                made.outcome() == Attachment.Outcome.IS_CONTROLLER, String.valueOf(made.outcome())));

        // --- a part in range joins it and is given a port ---
        scratch.set(sens, light.block());
        scratch.track(sens);
        Attachment.Result s = Attachment.place(plugin, sens, light, BlockFace.NORTH);
        checks.add(new Check("a sensor in range attaches and is named S1",
                s.attached() && "S1".equals(s.port()) && key.equals(s.controllerKey()),
                s.outcome() + " port=" + s.port()));

        scratch.set(act, lamp.block());
        scratch.track(act);
        Attachment.Result a = Attachment.place(plugin, act, lamp, BlockFace.NORTH);
        checks.add(new Check("an actuator gets its own numbering, A1 not S2",
                a.attached() && "A1".equals(a.port()), a.outcome() + " port=" + a.port()));

        // --- out of range is still placed, just inert ---
        scratch.set(far, light.block());
        scratch.track(far);
        Attachment.Result orphan = Attachment.place(plugin, far, light, BlockFace.NORTH);
        checks.add(new Check("a part out of range is recorded but left unattached",
                orphan.outcome() == Attachment.Outcome.NO_CONTROLLER
                        && plugin.placements().get(far) != null,
                String.valueOf(orphan.outcome())));

        // --- breaking the controller orphans the build instead of deleting it ---
        java.util.Set<String> touched = Attachment.remove(plugin, ctrl, controller);
        Placed sensAfter = plugin.placements().get(sens);
        checks.add(new Check("breaking a controller leaves its parts standing, just detached",
                sensAfter != null && !sensAfter.attached(),
                sensAfter == null ? "the part vanished" : "robot='" + sensAfter.robot() + "'"));
        checks.add(new Check("the orphaned parts are reported so their labels can be redrawn",
                touched.size() == 2, touched.size() + " reported (want 2)"));
        checks.add(new Check("the robot itself is forgotten when its controller goes",
                plugin.robots().get(key) == null, "robot still present"));

        // --- and a new controller adopts whatever is standing around it ---
        scratch.set(ctrl, controller.block());
        Attachment.Result again = Attachment.place(plugin, ctrl, controller, BlockFace.NORTH);
        scratch.robot(again.controllerKey());
        java.util.Set<String> adopted = Attachment.adoptOrphans(plugin, again.controllerKey(), ctrl);
        checks.add(new Check("a new controller adopts the parts around it, but not the far one",
                adopted.size() == 2, adopted.size() + " adopted (want 2)"));
        Placed readopted = plugin.placements().get(sens);
        checks.add(new Check("an adopted part is given a port again",
                readopted != null && readopted.attached() && !readopted.port().isEmpty(),
                readopted == null ? "missing" : "port='" + readopted.port() + "'"));
    }

    // ------------------------------------------------------------ D3. the GUI

    /**
     * Paints the rule table headless and checks the cells landed where the click handler expects.
     *
     * <p>{@link com.agurim.robocraft.listener.MenuListener} decodes a click purely from its slot
     * number - row for the rule, column for the field. If the painter and the decoder ever disagree
     * about that layout, every click silently edits the wrong thing, which is close to the worst
     * possible bug in a tool whose entire point is that clicking is unambiguous.
     */
    private static void gui(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || lamp == null) return;

        Location ctrl = origin.clone();
        String key = PartStore.key(ctrl);
        scratch.part(plugin, ctrl, controller, "", "");
        scratch.part(plugin, origin.clone().add(1, 0, 0), lamp, key, "A1");
        scratch.robot(key);

        Robot robot = plugin.robots().getOrCreate(key, null);
        Program program = new Program();
        program.add(new Rule("S1", Op.LT, Operand.of(7), "A1", Verb.ON, Operand.of(0)));
        program.add(new Rule(Rule.ALWAYS, Op.LT, Operand.of(0), "A1", Verb.OFF, Operand.of(0)));
        robot.program(program);

        org.bukkit.inventory.Inventory inv = new ProgramMenu(key).build(plugin);
        // The readable copy of the program. A chest GUI shows an item's name only on hover, so the
        // rows are eight anonymous items until you mouse over them one at a time - which is exactly
        // what the first person to open it reported. These check the text that fixes that.
        List<Rule> sample = nightLight().rules();
        List<String> lines = ProgramMenu.programLines(sample);
        checks.add(new Check("every rule has a readable line, numbered from 1",
                lines.size() == sample.size() && lines.get(0).startsWith("1. "),
                String.join(" / ", lines)));
        // IF, not WHEN - the keyword every language they meet next actually uses. See Rule.
        checks.add(new Check("a rule line actually spells out the sentence",
                lines.get(0).contains("IF ") && lines.get(0).contains("THEN"), lines.get(0)));
        String widestRule = "";
        for (String l : lines) if (l.length() > widestRule.length()) widestRule = l;
        checks.add(new Check("a rule line fits the chat without wrapping",
                widestRule.length() <= Guide.CHAT_WIDTH,
                widestRule.length() + " chars: " + widestRule));
        checks.add(new Check("a full program still fits the chat window",
                ProgramMenu.programLines(sample).size() + 1 <= Guide.CHAT_LINES,
                sample.size() + " rules + header"));

        checks.add(new Check("the rule table paints a full 54-slot window",
                inv != null && inv.getSize() == 54, inv == null ? "null" : inv.getSize() + " slots"));
        if (inv == null) return;

        checks.add(new Check("row 1 column 0 marks rule 1",
                inv.getItem(0) != null && inv.getItem(0).getType() == Material.PAPER,
                String.valueOf(inv.getItem(0) == null ? null : inv.getItem(0).getType())));
        checks.add(new Check("row 1 columns 2 and 3 carry the comparison a conditional rule needs",
                inv.getItem(2) != null && inv.getItem(2).getType() == Material.COMPARATOR
                        && inv.getItem(3) != null && inv.getItem(3).getType() != Material.BLACK_STAINED_GLASS_PANE,
                "op=" + type(inv, 2) + " value=" + type(inv, 3)));
        checks.add(new Check("row 2 is an ALWAYS rule, so its comparison cells are blanked",
                type(inv, 11) == Material.BLACK_STAINED_GLASS_PANE
                        && type(inv, 12) == Material.BLACK_STAINED_GLASS_PANE,
                "slots 11,12 = " + type(inv, 11) + "," + type(inv, 12)));
        checks.add(new Check("every rule row ends with its delete cell",
                type(inv, 8) == Material.BARRIER && type(inv, 17) == Material.BARRIER,
                type(inv, 8) + ", " + type(inv, 17)));
        checks.add(new Check("unused rows are blank, so a click there edits nothing",
                type(inv, 27) == Material.BLACK_STAINED_GLASS_PANE, String.valueOf(type(inv, 27))));
        checks.add(new Check("the control row sits where the handler looks for it",
                type(inv, ProgramMenu.SLOT_ADD) == Material.EMERALD
                        && type(inv, ProgramMenu.SLOT_STOP) == Material.RED_CONCRETE
                        && type(inv, ProgramMenu.SLOT_HELP) == Material.BOOK,
                "add=" + type(inv, ProgramMenu.SLOT_ADD) + " stop=" + type(inv, ProgramMenu.SLOT_STOP)));

        // A rule row must be nine slots wide, or row N's cells bleed into row N+1's.
        checks.add(new Check("a rule occupies exactly one nine-slot row",
                type(inv, 9) == Material.PAPER, "slot 9 = " + type(inv, 9)));
    }

    private static Material type(org.bukkit.inventory.Inventory inv, int slot) {
        return (inv.getItem(slot) == null) ? Material.AIR : inv.getItem(slot).getType();
    }

    // ------------------------------------------------------- D5. click editing

    /**
     * Every cell of the rule table, clicked every way it can be clicked.
     *
     * <p>A bug here does not crash - it edits a different part of the rule than the one the
     * student clicked, in a tool whose whole premise is that clicking is unambiguous. That is the
     * kind of bug a person would blame on themselves rather than report.
     */
    private static void editing(RoboCraftPlugin plugin, List<Check> checks) {
        List<String> sources = List.of(Rule.ALWAYS, "S1", "S2", "M1", "TIME");
        List<String> targets = List.of("A1", "A2", "M1");
        List<String> values  = List.of("S1", "S2", "M1", "TIME");

        Rule base = new Rule("S1", Op.LT, Operand.of(7), "A1", Verb.ON, Operand.of(0));

        // --- token cells cycle both ways and wrap ---
        Rule fwd = RuleEdit.apply(base, RuleEdit.SOURCE, RuleEdit.Click.LEFT, sources, targets, values);
        Rule back = RuleEdit.apply(base, RuleEdit.SOURCE, RuleEdit.Click.RIGHT, sources, targets, values);
        checks.add(new Check("left cycles a token cell forward, right cycles it back",
                "S2".equals(fwd.source()) && Rule.ALWAYS.equals(back.source()),
                fwd.source() + " / " + back.source()));

        Rule wrapped = RuleEdit.apply(base.withSource("TIME"), RuleEdit.SOURCE,
                RuleEdit.Click.LEFT, sources, targets, values);
        checks.add(new Check("cycling past the end wraps to the start",
                Rule.ALWAYS.equals(wrapped.source()), wrapped.source()));

        // --- value cells step, and clamp at both ends ---
        Rule up = RuleEdit.apply(base, RuleEdit.RHS, RuleEdit.Click.LEFT, sources, targets, values);
        Rule big = RuleEdit.apply(base, RuleEdit.RHS, RuleEdit.Click.SHIFT, sources, targets, values);
        checks.add(new Check("a value cell steps by 1, or by 10 with shift",
                up.rhs().value() == 8 && big.rhs().value() == 17,
                up.rhs().value() + " / " + big.rhs().value()));

        Rule floor = RuleEdit.apply(base.withRhs(Operand.of(0)), RuleEdit.RHS,
                RuleEdit.Click.SHIFT_RT, sources, targets, values);
        Rule ceil = RuleEdit.apply(base.withRhs(Operand.of(RuleEdit.MAX_CONST)), RuleEdit.RHS,
                RuleEdit.Click.LEFT, sources, targets, values);
        checks.add(new Check("a value cell cannot go below 0 or past its ceiling",
                floor.rhs().value() == 0 && ceil.rhs().value() == RuleEdit.MAX_CONST,
                floor.rhs().value() + " / " + ceil.rhs().value()));

        // --- the alternate click swaps a literal for a port and back ---
        Rule asPort = RuleEdit.apply(base, RuleEdit.RHS, RuleEdit.Click.ALTERNATE, sources, targets, values);
        Rule asNumber = RuleEdit.apply(asPort, RuleEdit.RHS, RuleEdit.Click.ALTERNATE, sources, targets, values);
        checks.add(new Check("the alternate click swaps a number for a port, and back again",
                !asPort.rhs().constant() && asNumber.rhs().constant(),
                asPort.rhs().label() + " -> " + asNumber.rhs().label()));

        // --- retargeting must fix up a verb it just made illegal ---
        Rule toMemory = RuleEdit.apply(base.withTarget("A2"), RuleEdit.TARGET,
                RuleEdit.Click.LEFT, sources, targets, values);
        checks.add(new Check("pointing a rule at memory turns ON into SET",
                "M1".equals(toMemory.target()) && toMemory.verb() == Verb.SET,
                toMemory.target() + " " + toMemory.verb()));

        Rule memRule = new Rule("S1", Op.LT, Operand.of(7), "M1", Verb.ADD, Operand.of(1));
        Rule toActuator = RuleEdit.apply(memRule, RuleEdit.TARGET,
                RuleEdit.Click.LEFT, sources, targets, values);
        checks.add(new Check("pointing it back at an actuator turns ADD into ON",
                "A1".equals(toActuator.target()) && toActuator.verb() == Verb.ON,
                toActuator.target() + " " + toActuator.verb()));

        // --- hidden cells must be inert ---
        Rule always = base.withSource(Rule.ALWAYS);
        checks.add(new Check("an ALWAYS rule's hidden comparison cells are not editable",
                !RuleEdit.editable(always, RuleEdit.OP) && !RuleEdit.editable(always, RuleEdit.RHS),
                "op/value reported editable"));
        checks.add(new Check("a verb with no argument has no editable value cell",
                !RuleEdit.editable(base, RuleEdit.ARG)
                        && RuleEdit.editable(base.withVerb(Verb.SHOW), RuleEdit.ARG),
                "ON=" + RuleEdit.editable(base, RuleEdit.ARG)
                        + " SHOW=" + RuleEdit.editable(base.withVerb(Verb.SHOW), RuleEdit.ARG)));
        checks.add(new Check("clicking a hidden cell returns the rule untouched",
                RuleEdit.apply(always, RuleEdit.RHS, RuleEdit.Click.LEFT, sources, targets, values)
                        .equals(always), "the rule changed"));

        // --- an empty vocabulary must not throw or corrupt the rule ---
        checks.add(new Check("cycling with nothing to cycle to leaves the value alone",
                "A1".equals(RuleEdit.apply(base, RuleEdit.TARGET, RuleEdit.Click.LEFT,
                        sources, List.of(), values).target()),
                "target changed with an empty list"));

        // --- every field, every click, must survive ---
        boolean survived = true;
        String broke = "";
        for (int field = 0; field <= 8 && survived; field++) {
            for (RuleEdit.Click c : List.of(RuleEdit.Click.LEFT, RuleEdit.Click.RIGHT,
                    RuleEdit.Click.SHIFT, RuleEdit.Click.SHIFT_RT, RuleEdit.Click.ALTERNATE)) {
                try {
                    Rule out = RuleEdit.apply(base, field, c, sources, targets, values);
                    if (out == null) { survived = false; broke = "field " + field + " returned null"; break; }
                } catch (Exception e) {
                    survived = false;
                    broke = "field " + field + " threw " + e;
                    break;
                }
            }
        }
        checks.add(new Check("no field and click combination throws or nulls the rule", survived, broke));

        // The first rule a student adds should already read like the sentence they are writing.
        Rule starter = RuleEdit.starter(sources, targets);
        checks.add(new Check("a new rule starts as a complete example on the student's own ports",
                "S1".equals(starter.source()) && "A1".equals(starter.target())
                        && !starter.always() && starter.verb() == Verb.ON,
                starter.text()));
        checks.add(new Check("with no sensor attached it falls back to something still valid",
                RuleEdit.starter(List.of(Rule.ALWAYS, "M1"), targets).always(),
                RuleEdit.starter(List.of(Rule.ALWAYS, "M1"), targets).text()));
        checks.add(new Check("with nothing attached at all it does not throw",
                RuleEdit.starter(List.of(), List.of()) != null, "returned null"));
    }

    // ---------------------------------------------------------- D6. class load

    /** How many robots a class actually produces. Thirty students, most with one working machine. */
    private static final int CLASS_SIZE = 20;

    /**
     * Ticks a class-sized fleet and reports how long it took.
     *
     * <p>Everything else here checks that one robot is correct. This checks the thing that only
     * shows up with thirty of them: each tick reads every sensor, writes changed actuators and
     * repaints every label, twice a second, for every running robot at once. If that does not fit
     * inside a server tick the whole server stutters, and the symptom - a laggy server during a
     * lesson - looks nothing like its cause.
     */
    private static void load(RoboCraftPlugin plugin, CommandSender sender, Location origin,
                             Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part battery    = kind(plugin, "battery");
        Part light      = sensor(plugin, "light");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || battery == null || light == null || lamp == null) return;

        List<Robot> fleet = new ArrayList<>();
        for (int i = 0; i < CLASS_SIZE; i++) {
            Location base = origin.clone().add((i % 5) * 6, 0, (i / 5) * 6);
            String key = PartStore.key(base);
            for (int dx = 0; dx <= 3; dx++) scratch.clear(base.clone().add(dx, 1, 0));
            scratch.part(plugin, base, controller, "", "");
            scratch.part(plugin, base.clone().add(1, 0, 0), battery, key, "");
            scratch.part(plugin, base.clone().add(2, 0, 0), light, key, "S1");
            scratch.part(plugin, base.clone().add(3, 0, 0), lamp, key, "A1");
            scratch.robot(key);

            Robot robot = plugin.robots().getOrCreate(key, null);
            robot.energy(plugin.batteryCapacity(key));
            robot.program(nightLight());
            robot.start(plugin.engine().now());
            fleet.add(robot);
        }

        checks.add(new Check("a class-sized fleet builds", fleet.size() == CLASS_SIZE,
                fleet.size() + " robots"));

        // Two measurements, because they separate two very different costs. Alternating the
        // reading flips every lamp on every robot every tick - the pathological case, and the one
        // that exercises the block-write path. A steady reading changes nothing, so what it still
        // costs is the fixed overhead of scanning, reading and deciding.
        double churn  = worstTickMs(plugin, fleet, true);
        double steady = worstTickMs(plugin, fleet, false);

        sender.sendMessage(Component.text(String.format(
                "   %d robots: %.1f ms worst case, %.1f ms steady (a server tick is 50 ms)",
                CLASS_SIZE, churn, steady), NamedTextColor.GRAY));

        // Steady state is what a room full of robots actually does most of the time - a night
        // light flips twice a day, not twice a second - so this one has to be nearly free.
        checks.add(new Check("a class-sized fleet costs almost nothing when nothing is changing",
                steady < 5.0, String.format("%.1f ms for %d robots", steady, CLASS_SIZE)));

        // The pathological case still has to fit in a tick. It is reachable: a student whose
        // thermostat oscillates flips their lamp every single tick, which is exactly the bug
        // mission 5 is about. The engine only runs every tenth server tick, so there is headroom -
        // but not enough to be careless with.
        checks.add(new Check("and still fits inside one server tick when everything flips at once",
                churn < 40.0, String.format("%.1f ms for %d robots", churn, CLASS_SIZE)));

        for (Robot robot : fleet) robot.stop("selftest");
    }

    /** Worst of several passes, after a warm-up - the first pass through cold code is all JIT. */
    private static double worstTickMs(RoboCraftPlugin plugin, List<Robot> fleet, boolean alternate) {
        for (int warm = 0; warm < 4; warm++) {
            Map<String, Integer> env = Map.of("light", (alternate && warm % 2 == 0) ? 2 : 14);
            for (Robot robot : fleet) plugin.engine().tick(robot, env);
        }
        long worst = 0;
        for (int pass = 0; pass < 6; pass++) {
            Map<String, Integer> env = Map.of("light", (alternate && pass % 2 == 0) ? 2 : 14);
            long started = System.nanoTime();
            for (Robot robot : fleet) plugin.engine().tick(robot, env);
            worst = Math.max(worst, System.nanoTime() - started);
        }
        return worst / 1_000_000.0;
    }

    // ------------------------------------------------------- D7. the assistant

    /**
     * What an answering agent is handed when a student asks a question.
     *
     * <p>This is the half that goes stale silently. If the program model changes and the context
     * builder does not, the assistant keeps answering confidently about a rule table the student
     * is not looking at - which is worse than having no assistant, because it is wrong with
     * authority. Nothing else in the plugin would notice.
     */
    private static void assistant(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
        Part controller = kind(plugin, "controller");
        Part battery    = kind(plugin, "battery");
        Part light      = sensor(plugin, "light");
        Part lamp       = actuator(plugin, "lamp");
        if (controller == null || battery == null || light == null || lamp == null) return;

        Location ctrl = origin.clone();
        String key = PartStore.key(ctrl);
        for (int dx = 0; dx <= 3; dx++) scratch.clear(origin.clone().add(dx, 1, 0));
        scratch.part(plugin, ctrl, controller, "", "");
        scratch.part(plugin, origin.clone().add(1, 0, 0), battery, key, "");
        scratch.part(plugin, origin.clone().add(2, 0, 0), light, key, "S1");
        scratch.part(plugin, origin.clone().add(3, 0, 0), lamp, key, "A1");
        scratch.robot(key);

        Robot robot = plugin.robots().getOrCreate(key, null);
        robot.energy(plugin.batteryCapacity(key));
        robot.program(nightLight());
        robot.start(plugin.engine().now());
        plugin.engine().tick(robot, Map.of("light", 4));

        Map<String, String> ctx = plugin.ask().robotContext(robot);

        checks.add(new Check("the assistant is handed the student's actual rule table",
                ctx.getOrDefault("program", "").contains("IF S1 < 7 THEN A1 ON"),
                "program = " + ctx.get("program")));
        checks.add(new Check("...and what the sensors are reading right now",
                "S1=4".equals(ctx.get("readings")), "readings = " + ctx.get("readings")));
        checks.add(new Check("...and which port is which part",
                ctx.getOrDefault("parts", "").contains("S1=" + light.id())
                        && ctx.getOrDefault("parts", "").contains("A1=" + lamp.id()),
                "parts = " + ctx.get("parts")));
        checks.add(new Check("...and where the outputs ended up",
                "A1=ON".equals(ctx.get("outputs")), "outputs = " + ctx.get("outputs")));
        checks.add(new Check("...and which rule decided it",
                "1".equals(ctx.get("last_rule_fired")), "last_rule_fired = " + ctx.get("last_rule_fired")));
        checks.add(new Check("...and whether the robot is even running",
                "running".equals(ctx.get("robot")), "robot = " + ctx.get("robot")));

        Map<String, String> none = plugin.ask().robotContext(null);
        checks.add(new Check("a student with no robot yields context rather than an exception",
                "none".equals(none.get("robot")), String.valueOf(none.get("robot"))));

        // questions.jsonl is UTF-8 JSON-per-line; Hebrew and quotes must survive the trip.
        String quoted = com.agurim.robocraft.assistant.AskService.q("למה \"הנורה\" לא נדלקת?\nשורה");
        checks.add(new Check("Hebrew, quotes and newlines survive JSON encoding",
                quoted.startsWith("\"") && quoted.endsWith("\"")
                        && quoted.contains("\\\"הנורה\\\"") && quoted.contains("\\n")
                        && !quoted.contains("\n"),
                quoted));
    }

    // ------------------------------------------------------- E. the vocabulary

    private static void vocabulary(RoboCraftPlugin plugin, List<Check> checks) {
        Rule rule = new Rule("S1", Op.GE, Operand.of(7), "A1", Verb.SHOW, Operand.ref("M2"));
        Rule back = Rule.decode(rule.encode());
        checks.add(new Check("a rule survives encode/decode",
                back.source().equals(rule.source()) && back.op() == rule.op()
                        && back.rhs().value() == 7 && back.target().equals("A1")
                        && back.verb() == Verb.SHOW && "M2".equals(back.arg().source()),
                rule.encode() + " -> " + back.encode()));

        checks.add(new Check("a corrupt rule decodes to a blank instead of throwing",
                Rule.decode("garbage") != null, "expected a blank rule"));

        List<String> sources = ProgramMenu.sources(plugin, "no-such-robot");
        checks.add(new Check("the condition vocabulary always offers ALWAYS, memory and TIME",
                sources.contains(Rule.ALWAYS) && sources.contains("M1") && sources.contains("TIME"),
                String.join(",", sources)));

        List<String> targets = ProgramMenu.targets(plugin, "no-such-robot");
        checks.add(new Check("the action vocabulary offers memory even with no actuators attached",
                targets.contains("M1"), String.join(",", targets)));

        checks.add(new Check("ON/OFF cycling never lands on a memory verb",
                !Verb.ON.next(false).isMemory() && !Verb.OFF.next(false).isMemory(),
                Verb.ON.next(false) + ", " + Verb.OFF.next(false)));
        checks.add(new Check("a memory target only ever offers SET/ADD",
                Verb.SET.next(true).isMemory() && Verb.ADD.next(true).isMemory(),
                Verb.SET.next(true) + ", " + Verb.ADD.next(true)));
    }

    // ------------------------------------------------------- E2. legibility

    /**
     * That the guide actually fits in the chat it is printed to.
     *
     * <p>The only check here that came from a person rather than from reasoning. /rc guide sent
     * seventeen lines into a ten-line chat, so it appeared to start at item 6, and its two longest
     * lines wrapped and cost a line each on top. Nothing threw, every string was correct, and the
     * feature was unusable. Arithmetic can hold the line where a Bukkit test cannot: a guide that
     * does not fit on screen is a guide nobody reads.
     */
    private static void legibility(RoboCraftPlugin plugin, List<Check> checks) {
        List<String> body = Guide.bodyText();

        // Two lines are reserved for the progress footer send() appends.
        int budget = Guide.CHAT_LINES - 2;
        checks.add(new Check("the guide body fits the chat window",
                body.size() <= budget, body.size() + " lines, budget " + budget));

        String widest = "";
        for (String l : body) if (l.length() > widest.length()) widest = l;
        checks.add(new Check("no guide line is wide enough to wrap",
                widest.length() <= Guide.CHAT_WIDTH,
                widest.length() + " chars: " + widest));

        // A wrapped mission name would cost the same line the footer needs.
        String longest = "";
        for (Mission m : plugin.missions().registry().all()) {
            if (m.name().length() > longest.length()) longest = m.name();
        }
        checks.add(new Check("the longest mission name still fits the progress footer",
                ("הבאה בתור: " + longest).length() <= Guide.CHAT_WIDTH,
                longest));

        // The numbered steps must read 1..n with nothing missing, or the list looks truncated -
        // which is exactly how the old one looked when its top had scrolled away.
        int expected = 1;
        boolean sequential = true;
        for (String l : body) {
            if (!l.isEmpty() && Character.isDigit(l.charAt(0))) {
                if (!l.startsWith(expected + ". ")) { sequential = false; break; }
                expected++;
            }
        }
        checks.add(new Check("the guide steps are numbered from 1 with no gaps",
                sequential && expected > 1, "next expected " + expected));
    }

    // ---------------------------------------------------------- F. geometry

    /**
     * Board and kiosk placement is checked arithmetically, never by building: building would
     * overwrite a real student's board, and a diagnostic must not damage progress.
     */
    private static void geometry(RoboCraftPlugin plugin, List<Check> checks) {
        int plot = 0;
        boolean allMatch = true;
        String detail = "";
        int i = 0;
        for (Part part : plugin.parts().all()) {
            Location tile = plugin.board().tileLocation(plot, i);
            Part found = plugin.board().partAt(plot, tile);
            if (found == null || !found.id().equals(part.id())) {
                allMatch = false;
                detail = "tile " + i + " resolves to " + (found == null ? "nothing" : found.id())
                        + " instead of " + part.id();
                break;
            }
            i++;
        }
        checks.add(new Check("every board tile maps back to its own part", allMatch, detail));

        Location off = plugin.board().tileLocation(plot, 0).clone().add(0, 0, 40);
        checks.add(new Check("a block away from the board is not a board tile",
                plugin.board().partAt(plot, off) == null, "expected null"));

        Location charger = plugin.kiosk().chargerLocation(plot);
        checks.add(new Check("the charging pad recognises its own location",
                plugin.kiosk().isKioskBlock(plot, charger), "at " + charger.getBlockX() + "," + charger.getBlockZ()));

        checks.add(new Check("plot lookup agrees with the plot's own corner",
                plugin.plots().plotIndexAt(plugin.plots().plotCorner(3)) == 3,
                "got " + plugin.plots().plotIndexAt(plugin.plots().plotCorner(3))));

        // A first-timer must land on their own plot, close enough to the board to read it, and
        // looking at it - the welcome text says the wall is in front of them.
        Location arrival = plugin.board().arrivalSpot(3);
        checks.add(new Check("the arrival spot is on the student's own plot",
                plugin.plots().plotIndexAt(arrival) == 3,
                "resolves to plot " + plugin.plots().plotIndexAt(arrival)));

        Location middleTile = plugin.board().tileLocation(3, Math.min(2, plugin.parts().size() - 1));
        double away = Math.hypot(arrival.getX() - (middleTile.getBlockX() + 0.5),
                                 arrival.getZ() - (middleTile.getBlockZ() + 0.5));
        checks.add(new Check("the arrival spot is within sight of the board, not across the field",
                away < 12, String.format("%.1f blocks away", away)));
        checks.add(new Check("and it faces the board rather than away from it",
                arrival.getZ() > middleTile.getBlockZ() && Math.abs(arrival.getYaw() - 180f) < 1f,
                "yaw " + arrival.getYaw()));

        trophies(plugin, checks);
        charger(plugin, checks);
    }

    /**
     * The charging pad must be reachable from where a student actually builds, and must say so
     * when it is not.
     *
     * <p>The pad is at the plot corner; the arrival spot is in front of the board. With the old
     * radius of 6 those were eleven blocks apart, so a robot built where the game put you could
     * never reach its own charger - and the click was silent about it. Both halves are checked
     * here because fixing only one still leaves a student stuck.
     */
    private static void charger(RoboCraftPlugin plugin, List<Check> checks) {
        int plot = 3;
        int radius = plugin.getConfig().getInt("power.charger-radius", 14);
        Location pad = plugin.kiosk().chargerLocation(plot);
        Location arrival = plugin.board().arrivalSpot(plot);

        double away = Math.hypot(arrival.getX() - (pad.getBlockX() + 0.5),
                                 arrival.getZ() - (pad.getBlockZ() + 0.5));
        checks.add(new Check("the charging pad reaches where a student is actually put down",
                away <= radius, String.format("%.1f blocks from the arrival spot, radius %d", away, radius)));

        // And every branch of the message says something a student can act on.
        String tooFar = plain(InteractListener.chargeResult(0, 8.9, false, radius));
        checks.add(new Check("a click out of range reports the distance and the reach",
                tooFar.contains("8.9") && tooFar.contains(String.valueOf(radius)), tooFar));
        String noBattery = plain(InteractListener.chargeResult(0, -1, true, radius));
        checks.add(new Check("a robot with no battery is told so, not told it is out of range",
                !noBattery.contains(String.valueOf(radius)) && !noBattery.isEmpty(), noBattery));
        String ok = plain(InteractListener.chargeResult(2, -1, false, radius));
        checks.add(new Check("a successful charge reports how many robots were topped up",
                ok.contains("2"), ok));
    }

    private static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(c);
    }

    /**
     * The trophy shelf, checked arithmetically for the same reason the board is: building one
     * would overwrite a real student's shelf.
     *
     * <p>The collision check is the one that matters. The shelf derives its height from the board
     * so it adapts to whatever {@code board.offset-y} a server actually uses - which is right, and
     * which also means a bad {@code above-board} or a board that grows a row would silently park
     * trophies on top of the part tiles and eat the board.
     */
    private static void trophies(RoboCraftPlugin plugin, List<Check> checks) {
        int plot = 3;
        List<Mission> missions = new ArrayList<>(plugin.missions().registry().all());

        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean distinct = true;
        for (int i = 0; i < missions.size(); i++) {
            Location slot = plugin.trophies().slotLocation(plot, i);
            if (!seen.add(slot.getBlockX() + "," + slot.getBlockY() + "," + slot.getBlockZ())) {
                distinct = false;
                break;
            }
        }
        checks.add(new Check("every mission gets its own trophy slot",
                distinct && seen.size() == missions.size(),
                seen.size() + " slots for " + missions.size() + " missions"));

        boolean onPlot = true;
        String where = "";
        for (int i = 0; i < missions.size(); i++) {
            Location slot = plugin.trophies().slotLocation(plot, i);
            if (plugin.plots().plotIndexAt(slot) != plot) {
                onPlot = false;
                where = "slot " + i + " resolves to plot " + plugin.plots().plotIndexAt(slot);
                break;
            }
        }
        checks.add(new Check("the whole shelf sits on the student's own plot", onPlot, where));

        java.util.Set<String> boardTiles = new java.util.HashSet<>();
        for (int i = 0; i < plugin.parts().size(); i++) {
            Location t = plugin.board().tileLocation(plot, i);
            boardTiles.add(t.getBlockX() + "," + t.getBlockY() + "," + t.getBlockZ());
        }
        boolean clear = true;
        String clash = "";
        for (int i = 0; i < missions.size(); i++) {
            Location slot = plugin.trophies().slotLocation(plot, i);
            if (boardTiles.contains(slot.getBlockX() + "," + slot.getBlockY() + "," + slot.getBlockZ())) {
                clear = false;
                clash = "trophy " + i + " is standing on a board tile";
                break;
            }
        }
        checks.add(new Check("no trophy overwrites a Component Board tile", clear, clash));

        checks.add(new Check("an unearned trophy still looks different from an earned one",
                TrophyShelf.trophyBlock(false, false, false) != TrophyShelf.trophyBlock(true, false, false),
                TrophyShelf.trophyBlock(false, false, false) + " vs " + TrophyShelf.trophyBlock(true, false, false)));

        // The one a person had to find for us. An empty trophy used to be GRAY_STAINED_GLASS,
        // which is exactly what a locked board tile is - so the shelf did not read as a separate
        // object at all: "they look like part of the wall". Placement was provably correct the
        // whole time. Being in the right place and being legible are different properties, and
        // only this second one was ever in doubt.
        boolean sharesWallBlock = false;
        for (Part part : plugin.parts().all()) {
            if (part.block() == TrophyShelf.trophyBlock(false, false, false)
                    || part.block() == TrophyShelf.trophyBlock(true, true, false)
                    || part.block() == TrophyShelf.trophyBlock(true, false, false)
                    || part.block() == TrophyShelf.trophyBlock(true, false, true)) {
                sharesWallBlock = true;
                break;
            }
        }
        checks.add(new Check("no trophy block is also a part block, so the shelf is never mistaken for the wall",
                !sharesWallBlock && TrophyShelf.trophyBlock(false, false, false) != Material.GRAY_STAINED_GLASS,
                "ghost is " + TrophyShelf.trophyBlock(false, false, false)));

        // Beside the board, not on top of it: every trophy must be clear of the board's columns.
        int boardMaxX = Integer.MIN_VALUE;
        for (int i = 0; i < plugin.parts().size(); i++) {
            boardMaxX = Math.max(boardMaxX, plugin.board().tileLocation(plot, i).getBlockX());
        }
        int shelfMinX = Integer.MAX_VALUE;
        for (int i = 0; i < missions.size(); i++) {
            shelfMinX = Math.min(shelfMinX, plugin.trophies().slotLocation(plot, i).getBlockX());
        }
        checks.add(new Check("the shelf stands clear to the side of the board, not over it",
                shelfMinX > boardMaxX, "board ends at x=" + boardMaxX + ", shelf starts at x=" + shelfMinX));

        // Above eye level is where the first one went unseen. Keep it level with the board.
        int boardBottom = plugin.board().tileLocation(plot, plugin.parts().size() - 1).getBlockY();
        int shelfTop = Integer.MIN_VALUE;
        for (int i = 0; i < missions.size(); i++) {
            shelfTop = Math.max(shelfTop, plugin.trophies().slotLocation(plot, i).getBlockY());
        }
        int boardTop = Integer.MIN_VALUE;
        for (int i = 0; i < plugin.parts().size(); i++) {
            boardTop = Math.max(boardTop, plugin.board().tileLocation(plot, i).getBlockY());
        }
        checks.add(new Check("the shelf is within the board's own height, not floating above it",
                shelfTop <= boardTop && shelfTop >= boardBottom,
                "shelf top y=" + shelfTop + ", board spans " + boardBottom + ".." + boardTop));

        checks.add(new Check("a warm-up trophy is visibly different from a ladder trophy",
                TrophyShelf.trophyBlock(true, true, false) != TrophyShelf.trophyBlock(true, false, false),
                TrophyShelf.trophyBlock(true, true, false) + " vs " + TrophyShelf.trophyBlock(true, false, false)));
        checks.add(new Check("a bonus trophy is a third metal, distinct from both",
                TrophyShelf.trophyBlock(true, false, true) != TrophyShelf.trophyBlock(true, false, false)
                        && TrophyShelf.trophyBlock(true, false, true) != TrophyShelf.trophyBlock(true, true, false),
                String.valueOf(TrophyShelf.trophyBlock(true, false, true))));

        // Sixteen missions on a shelf sized for eight would stack four rows high and climb out of
        // eye level - the exact mistake the first shelf made.
        int rows = 0;
        java.util.Set<Integer> ys = new java.util.HashSet<>();
        for (int i = 0; i < missions.size(); i++) ys.add(plugin.trophies().slotLocation(plot, i).getBlockY());
        rows = ys.size();
        checks.add(new Check("the shelf holds every mission in at most two rows",
                rows <= 2 && missions.size() >= 16, rows + " rows for " + missions.size() + " missions"));

        // A wall built for eight missions stays eight slots wide for ever unless something notices
        // the content grew. The join path notices through this flag; make sure it can.
        java.util.UUID ghost = java.util.UUID.nameUUIDFromBytes("selftest-layout".getBytes());
        int p = plugin.parts().size(), m = missions.size();
        boolean staleBeforeRecorded = !plugin.store().isLayoutCurrent(ghost, p, m);
        plugin.store().setLayout(ghost, p, m);
        boolean currentAfter = plugin.store().isLayoutCurrent(ghost, p, m);
        boolean staleWhenAPartIsAppended = !plugin.store().isLayoutCurrent(ghost, p + 1, m);
        boolean staleWhenAMissionIsAdded = !plugin.store().isLayoutCurrent(ghost, p, m + 1);
        plugin.store().forget(ghost);
        checks.add(new Check("a wall built before the count was tracked reads as stale once, then current",
                staleBeforeRecorded && currentAfter, "stale=" + staleBeforeRecorded + " current=" + currentAfter));
        checks.add(new Check("appending a part or adding a mission makes every built wall stale",
                staleWhenAPartIsAppended && staleWhenAMissionIsAdded,
                "part=" + staleWhenAPartIsAppended + " mission=" + staleWhenAMissionIsAdded));
    }

    // ------------------------------------------------------- F2. progression

    /**
     * What a student is told they have done, and what to do next.
     *
     * <p>This is the arithmetic behind the complaint that started it: finishing a warm-up moved
     * nothing on screen. Progress deliberately counts the required ladder only, so the bar sat at
     * "משימה 1/5" before and after a student's first success, and "הבאה בתור" pointed past the two
     * remaining warm-ups at a rung they were not ready for. Both are pure functions, so both can
     * be held to account here without a player.
     */
    private static void progression(RoboCraftPlugin plugin, List<Check> checks) {
        var registry = plugin.missions().registry();
        List<Mission> warmUps = registry.warmUps();
        List<Mission> required = registry.required();

        if (warmUps.isEmpty() || required.isEmpty()) {
            checks.add(new Check("the ladder has both warm-ups and required missions", false,
                    warmUps.size() + " warm-ups, " + required.size() + " required"));
            return;
        }

        java.util.Set<String> none = java.util.Set.of();
        checks.add(new Check("a brand new student is pointed at the first warm-up",
                warmUps.get(0).id().equals(id(registry.nextSuggested(none))),
                id(registry.nextSuggested(none))));

        java.util.Set<String> firstWarmDone = java.util.Set.of(warmUps.get(0).id());
        checks.add(new Check("after warm-up 1 they are pointed at warm-up 2, not past them",
                warmUps.get(1).id().equals(id(registry.nextSuggested(firstWarmDone))),
                id(registry.nextSuggested(firstWarmDone))));

        java.util.Set<String> allWarm = new java.util.HashSet<>();
        for (Mission m : warmUps) allWarm.add(m.id());
        checks.add(new Check("once the warm-ups are done the required ladder takes over",
                required.get(0).id().equals(id(registry.nextSuggested(allWarm))),
                id(registry.nextSuggested(allWarm))));

        // Starting the real ladder must stop the warm-up nagging, or optional work is not optional.
        java.util.Set<String> onLadder = java.util.Set.of(required.get(0).id());
        checks.add(new Check("a student on the required ladder is never sent back to a warm-up",
                !registry.nextSuggested(onLadder).warmUp(),
                id(registry.nextSuggested(onLadder))));

        checks.add(new Check("nextFor still ignores warm-ups, so progress maths is unchanged",
                required.get(0).id().equals(id(registry.nextFor(none))),
                id(registry.nextFor(none))));

        List<Mission> bonus = registry.bonus();

        // --- six rungs, and the flicker fix sits where hysteresis is first met ---
        // Yon promoted the playtest's accidental feedback loop to a required rung, placed after the
        // counter and before the thermostat: two thresholds first on a lamp you can WATCH flicker,
        // then again on heat. A student at 5/5 on a live server is now at 5/6 with rung 6 reachable.
        checks.add(new Check("the required ladder is six rungs",
                required.size() == 6, required.size() + " rungs"));
        int counterAt = -1, flickerAt = -1, thermoAt = -1;
        for (int i = 0; i < required.size(); i++) {
            switch (required.get(i).id()) {
                case "counter"    -> counterAt = i;
                case "flicker"    -> flickerAt = i;
                case "thermostat" -> thermoAt  = i;
                default -> { }
            }
        }
        checks.add(new Check("the flicker fix is the rung between the counter and the thermostat",
                counterAt >= 0 && flickerAt == counterAt + 1 && thermoAt == flickerAt + 1,
                "counter@" + counterAt + " flicker@" + flickerAt + " thermostat@" + thermoAt));
        Mission flickerRung = registry.byId("flicker");
        checks.add(new Check("flicker is required and grants nothing - no reward moved between rungs",
                flickerRung != null && !flickerRung.skippable() && flickerRung.reward().isEmpty(),
                flickerRung == null ? "missing" : "skippable=" + flickerRung.skippable() + " reward=" + flickerRung.reward()));

        // --- bonus: after the ladder, never before, never a gate ---
        checks.add(new Check("there are bonus missions, and every one grants nothing",
                !bonus.isEmpty() && bonus.stream().allMatch(m -> m.reward().isEmpty() && m.skippable() && !m.warmUp()),
                bonus.size() + " bonus"));
        java.util.Set<String> fiveOfSix = new java.util.HashSet<>();
        for (int i = 0; i < required.size() - 1; i++) fiveOfSix.add(required.get(i).id());
        checks.add(new Check("a student at 5/6 is pointed at rung 6, never at a bonus",
                required.get(required.size() - 1).id().equals(id(registry.nextSuggested(fiveOfSix))),
                id(registry.nextSuggested(fiveOfSix))));
        java.util.Set<String> ladderDone = new java.util.HashSet<>();
        for (Mission m : required) ladderDone.add(m.id());
        checks.add(new Check("once the ladder is done the first bonus is suggested",
                !bonus.isEmpty() && bonus.get(0).id().equals(id(registry.nextSuggested(ladderDone))),
                id(registry.nextSuggested(ladderDone))));
        checks.add(new Check("nextFor never points at a bonus, so n/6 stays honest",
                registry.nextFor(ladderDone) == null && registry.requiredDone(ladderDone) == required.size(),
                "nextFor=" + id(registry.nextFor(ladderDone))));

        // Skipping every extra must never strand a student: every locked part comes from a rung.
        boolean skippableGrants = false;
        for (Mission m : registry.all()) if (m.skippable() && !m.reward().isEmpty()) skippableGrants = true;
        checks.add(new Check("no skippable mission grants a part", !skippableGrants, ""));
        java.util.Set<String> granted = new java.util.HashSet<>();
        for (Mission m : required) granted.addAll(m.reward());
        String unreachable = "";
        for (Part p : plugin.parts().all()) {
            if (!p.unlockedByDefault() && !granted.contains(p.id())) unreachable += p.id() + " ";
        }
        checks.add(new Check("every locked part is unlocked by a required rung",
                unreachable.isEmpty(), unreachable.isEmpty() ? "" : "unreachable: " + unreachable));

        // A student reads a number and types it. "/rc missions #1" used to drop the argument in
        // silence - nothing ran, and they believed they had finished until the trophy stayed grey.
        //
        // The numbering then has to match the STATUS BAR, which counts three separate tracks. The
        // first attempt numbered the required five 1-5 and continued 6-8 into the warm-ups, so
        // night_light was "6" in the list, "חימום 1/3" on the bar, and order: 1 in missions.yml.
        // Three names for one mission, and the person testing it asked why.
        checks.add(new Check("required mission 1 is what the bar calls משימה 1",
                required.get(0).equals(RoboCraftCommand.resolve("1", required, warmUps, bonus)),
                String.valueOf(id(RoboCraftCommand.resolve("1", required, warmUps, bonus)))));
        checks.add(new Check("warm-up W1 is what the bar calls חימום 1, not a continued number",
                warmUps.get(0).equals(RoboCraftCommand.resolve("W1", required, warmUps, bonus)),
                String.valueOf(id(RoboCraftCommand.resolve("W1", required, warmUps, bonus)))));
        checks.add(new Check("the Hebrew ח1 works too, since the bar says חימום in Hebrew",
                warmUps.get(0).equals(RoboCraftCommand.resolve("ח1", required, warmUps, bonus)),
                String.valueOf(id(RoboCraftCommand.resolve("ח1", required, warmUps, bonus)))));
        checks.add(new Check("bonus B1 is what the bar calls בונוס 1",
                !bonus.isEmpty() && bonus.get(0).equals(RoboCraftCommand.resolve("B1", required, warmUps, bonus)),
                String.valueOf(id(RoboCraftCommand.resolve("B1", required, warmUps, bonus)))));
        checks.add(new Check("the Hebrew ב1 works too",
                !bonus.isEmpty() && bonus.get(0).equals(RoboCraftCommand.resolve("ב1", required, warmUps, bonus)),
                String.valueOf(id(RoboCraftCommand.resolve("ב1", required, warmUps, bonus)))));
        checks.add(new Check("a copied '#' is stripped rather than rejected",
                required.get(0).equals(RoboCraftCommand.resolve("#1", required, warmUps, bonus)),
                String.valueOf(id(RoboCraftCommand.resolve("#1", required, warmUps, bonus)))));
        checks.add(new Check("an id still resolves, so docs and mission messages keep working",
                warmUps.get(0).equals(RoboCraftCommand.resolve(warmUps.get(0).id(), required, warmUps, bonus)),
                warmUps.get(0).id()));
        checks.add(new Check("nonsense resolves to nothing rather than the wrong mission",
                RoboCraftCommand.resolve("banana", required, warmUps, bonus) == null
                        && RoboCraftCommand.resolve("99", required, warmUps, bonus) == null
                        && RoboCraftCommand.resolve("B99", required, warmUps, bonus) == null
                        && RoboCraftCommand.resolve("", required, warmUps, bonus) == null,
                "expected null for banana / 99 / B99 / empty"));

        // --- and what the bar actually reads ---
        int w = warmUps.size(), r = required.size(), b = bonus.size();
        StatusBar.Status fresh = StatusBar.compute(warmUps.get(0).name(), true, false, 0, w, 0, r, 0, b);
        StatusBar.Status afterOne = StatusBar.compute(warmUps.get(1).name(), true, false, 1, w, 0, r, 0, b);
        checks.add(new Check("finishing a warm-up actually moves the status bar",
                afterOne.progress() > fresh.progress(),
                fresh.progress() + " -> " + afterOne.progress()));
        checks.add(new Check("the warm-up bar counts warm-ups, not the required ladder",
                fresh.text().startsWith("חימום 1/" + w) && fresh.warmUp(),
                fresh.text()));

        StatusBar.Status ladder = StatusBar.compute(required.get(1).name(), false, false, w, w, 1, r, 0, b);
        checks.add(new Check("the required bar reads as a mission out of six",
                ladder.text().startsWith("משימה 2/6") && !ladder.warmUp() && !ladder.bonus(),
                ladder.text()));

        StatusBar.Status extra = StatusBar.compute(bonus.isEmpty() ? "x" : bonus.get(0).name(),
                false, true, w, w, r, r, 0, b);
        checks.add(new Check("after the ladder the bar switches to a green bonus track",
                extra.bonus() && extra.text().startsWith("בונוס 1/" + b), extra.text()));

        StatusBar.Status done = StatusBar.compute(null, false, false, w, w, r, r, b, b);
        checks.add(new Check("everything done fills the bar rather than dividing by zero",
                done.progress() == 1f, String.valueOf(done.progress())));
    }

    private static String id(Mission m) { return m == null ? "null" : m.id(); }

    // ------------------------------------------------------------- helpers

    private static Program nightLight() {
        Program program = new Program();
        program.add(new Rule("S1", Op.LT, Operand.of(7), "A1", Verb.ON,  Operand.of(0)));
        program.add(new Rule("S1", Op.GE, Operand.of(7), "A1", Verb.OFF, Operand.of(0)));
        return program;
    }

    private static boolean lit(Location loc) {
        BlockData data = loc.getBlock().getBlockData();
        return data instanceof Lightable lightable && lightable.isLit();
    }

    private static boolean open(Location loc) {
        BlockData data = loc.getBlock().getBlockData();
        return data instanceof Openable openable && openable.isOpen();
    }

    private static int labels(RoboCraftPlugin plugin, Location loc, String partId) {
        int n = 0;
        for (Entity ent : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 1.1, 0.5), 0.6, 0.8, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.partKey(), PersistentDataType.STRING);
                if (partId.equals(tag)) n++;
            }
        }
        return n;
    }

    private static Part kind(RoboCraftPlugin plugin, String kind) {
        for (Part p : plugin.parts().all()) if (kind.equals(p.kind())) return p;
        return null;
    }

    private static Part sensor(RoboCraftPlugin plugin, String type) {
        for (Part p : plugin.parts().all()) if (p.isSensor() && type.equals(p.sensor())) return p;
        return null;
    }

    private static Part actuator(RoboCraftPlugin plugin, String type) {
        for (Part p : plugin.parts().all()) if (p.isActuator() && type.equals(p.actuator())) return p;
        return null;
    }

    /** Print the outcome to whoever asked, and return true if everything passed. */
    public static boolean report(CommandSender sender, List<Check> checks) {
        int passed = 0;
        for (Check c : checks) {
            if (c.ok()) passed++;
            sender.sendMessage(Component.text((c.ok() ? "✔ " : "✖ ") + c.name(),
                    c.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (!c.ok() && c.detail() != null && !c.detail().isEmpty()) {
                sender.sendMessage(Component.text("    " + c.detail(), NamedTextColor.YELLOW));
            }
        }
        boolean allOk = passed == checks.size();
        sender.sendMessage(Component.text(
                "selftest: " + passed + "/" + checks.size() + (allOk ? " - הכול תקין" : " - יש בעיה"),
                allOk ? NamedTextColor.GREEN : NamedTextColor.RED));
        return allOk;
    }
}
