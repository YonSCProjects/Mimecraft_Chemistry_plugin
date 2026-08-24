package com.agurim.robocraft.diag;

import com.agurim.robocraft.RoboCraftPlugin;
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
import com.agurim.robocraft.program.Verb;
import com.agurim.robocraft.robot.Robot;
import com.agurim.robocraft.sense.SensorReader;
import com.agurim.robocraft.ui.ProgramMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
            loop(plugin, origin, scratch, checks);
            actuators(plugin, origin.clone().add(0, 0, 3), scratch, checks);
            sensors(plugin, origin.clone().add(0, 0, 6), scratch, checks);
            portsAndStore(plugin, origin.clone().add(0, 0, 9), scratch, checks);
            bench(plugin, sender, origin.clone().add(0, 0, 12), scratch, checks);
            gui(plugin, origin.clone().add(0, 0, 15), scratch, checks);
            vocabulary(plugin, checks);
            geometry(plugin, checks);
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

    private static void loop(RoboCraftPlugin plugin, Location origin, Scratch scratch, List<Check> checks) {
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
        checks.add(new Check("the rule table paints a full 54-slot window",
                inv != null && inv.getSize() == 54, inv == null ? "null" : inv.getSize() + " slots"));
        if (inv == null) return;

        checks.add(new Check("row 1 column 0 marks rule 1",
                inv.getItem(0) != null && inv.getItem(0).getType() == Material.PAPER,
                String.valueOf(inv.getItem(0) == null ? null : inv.getItem(0).getType())));
        checks.add(new Check("row 1 columns 2 and 3 carry the comparison a WHEN rule needs",
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
    }

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
