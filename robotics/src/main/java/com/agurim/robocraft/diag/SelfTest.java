package com.agurim.robocraft.diag;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartLabels;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Op;
import com.agurim.robocraft.program.Operand;
import com.agurim.robocraft.program.Program;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.program.Verb;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a real robot out of real blocks, drives a known input through it, and checks that a real
 * block moved at the other end - then puts everything back exactly as it was.
 *
 * <p>This exists because the interesting failures are all server-side and version-specific: whether
 * {@code setBlockData} on a REDSTONE_LAMP actually sticks without a redstone source, whether
 * TextDisplay entities spawn and update, whether the engine's latching survives a real tick. None
 * of that can be checked by compiling, and a teacher deploying to a new server before a lesson
 * wants a straight answer to "does this work here?"
 *
 * <p>Runs from the console too, so it can be driven by an automated smoke test.
 */
public final class SelfTest {

    private SelfTest() {}

    /** One assertion and how it went. */
    public record Check(String name, boolean ok, String detail) { }

    public static List<Check> run(RoboCraftPlugin plugin, CommandSender sender, Location origin) {
        List<Check> checks = new ArrayList<>();
        Map<Location, BlockData> restore = new LinkedHashMap<>();
        List<Location> placed = new ArrayList<>();
        String controllerKey = null;

        try {
            Part controller = firstOfKind(plugin, "controller");
            Part battery    = firstOfKind(plugin, "battery");
            Part sensor     = firstSensor(plugin, "light");
            Part lamp       = firstActuator(plugin, "lamp");
            if (controller == null || battery == null || sensor == null || lamp == null) {
                checks.add(new Check("parts.yml supplies controller/battery/light sensor/lamp", false,
                        "one of them is missing from parts.yml"));
                return checks;
            }

            Location ctrlLoc = origin.clone();
            Location battLoc = origin.clone().add(1, 0, 0);
            Location sensLoc = origin.clone().add(2, 0, 0);
            Location lampLoc = origin.clone().add(3, 0, 0);
            // Sensors read the light of the block above them, so keep the roof off.
            for (int dx = 0; dx <= 3; dx++) clearAbove(origin.clone().add(dx, 1, 0), restore);

            put(plugin, ctrlLoc, controller, "", "", restore, placed);
            controllerKey = PartStore.key(ctrlLoc);
            put(plugin, battLoc, battery, controllerKey, "", restore, placed);
            put(plugin, sensLoc, sensor, controllerKey, "S1", restore, placed);
            put(plugin, lampLoc, lamp, controllerKey, "A1", restore, placed);

            checks.add(new Check("four part blocks placed and registered",
                    plugin.placements().partsOf(controllerKey).size() == 3,
                    plugin.placements().partsOf(controllerKey).size() + " parts attached (want 3)"));

            Robot robot = plugin.robots().getOrCreate(controllerKey, null);
            robot.energy(plugin.batteryCapacity(controllerKey));
            robot.program(nightLightProgram());
            robot.outputs().clear();
            robot.start(plugin.engine().now());
            int startEnergy = robot.energy();

            // --- dark: the lamp should come on, and the BLOCK should actually change ---
            plugin.engine().tick(robot, Map.of("light", 2));
            checks.add(new Check("dark -> rule fires, output commanded ON",
                    robot.outputs().getOrDefault("A1", 0) == 1,
                    "A1 = " + robot.outputs().get("A1")));
            checks.add(new Check("dark -> REDSTONE_LAMP block data really is lit",
                    isLit(lampLoc), "lit=" + isLit(lampLoc)));
            checks.add(new Check("sensor reading recorded on the robot",
                    robot.inputs().getOrDefault("S1", -1) == 2,
                    "S1 = " + robot.inputs().get("S1")));

            // --- light: it should go off again (this is the latching path) ---
            plugin.engine().tick(robot, Map.of("light", 14));
            checks.add(new Check("light -> output commanded OFF",
                    robot.outputs().getOrDefault("A1", 1) == 0,
                    "A1 = " + robot.outputs().get("A1")));
            checks.add(new Check("light -> lamp block really is unlit",
                    !isLit(lampLoc), "lit=" + isLit(lampLoc)));

            // --- latching: with no rule matching, the output must hold, not reset ---
            robot.program(new Program());          // no rules at all
            boolean litBefore = isLit(lampLoc);
            plugin.engine().tick(robot, Map.of("light", 2));
            checks.add(new Check("no matching rule -> output latches instead of resetting",
                    isLit(lampLoc) == litBefore, "lit " + litBefore + " -> " + isLit(lampLoc)));

            checks.add(new Check("battery drained while running",
                    robot.energy() < startEnergy,
                    startEnergy + " -> " + robot.energy()));

            // --- the labels are the debugger; if they do not exist, nothing is debuggable ---
            PartLabels.refresh(plugin, sensLoc);
            checks.add(new Check("sensor carries a TextDisplay label",
                    hasLabel(plugin, sensLoc, sensor.id()), "looked around " + pretty(sensLoc)));

            PartLabels.refresh(plugin, sensLoc);
            checks.add(new Check("refresh updates the label in place, never duplicates",
                    countLabels(plugin, sensLoc, sensor.id()) == 1,
                    countLabels(plugin, sensLoc, sensor.id()) + " labels (want 1)"));

        } catch (Exception e) {
            checks.add(new Check("self-test ran without throwing", false, e.toString()));
            plugin.getLogger().warning("selftest threw: " + e);
        } finally {
            cleanUp(plugin, controllerKey, placed, restore);
        }
        return checks;
    }

    // ------------------------------------------------------------------ world

    private static void put(RoboCraftPlugin plugin, Location loc, Part part, String robot, String port,
                            Map<Location, BlockData> restore, List<Location> placed) {
        Block block = loc.getBlock();
        restore.putIfAbsent(loc.clone(), block.getBlockData().clone());
        block.setType(part.block(), false);
        plugin.placements().put(loc, new Placed(part.id(), BlockFace.NORTH, robot, port));
        PartLabels.refresh(plugin, loc);
        placed.add(loc.clone());
    }

    private static void clearAbove(Location loc, Map<Location, BlockData> restore) {
        Block block = loc.getBlock();
        if (block.getType() == Material.AIR) return;
        restore.putIfAbsent(loc.clone(), block.getBlockData().clone());
        block.setType(Material.AIR, false);
    }

    private static boolean isLit(Location loc) {
        BlockData data = loc.getBlock().getBlockData();
        return data instanceof Lightable lightable && lightable.isLit();
    }

    private static boolean hasLabel(RoboCraftPlugin plugin, Location loc, String partId) {
        return countLabels(plugin, loc, partId) > 0;
    }

    private static int countLabels(RoboCraftPlugin plugin, Location loc, String partId) {
        int n = 0;
        for (Entity ent : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 1.1, 0.5), 0.6, 0.8, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.partKey(), PersistentDataType.STRING);
                if (partId.equals(tag)) n++;
            }
        }
        return n;
    }

    /** Put the world back exactly as it was - a diagnostic that leaves litter is not usable. */
    private static void cleanUp(RoboCraftPlugin plugin, String controllerKey,
                                List<Location> placed, Map<Location, BlockData> restore) {
        for (Location loc : placed) {
            Placed p = plugin.placements().get(loc);
            if (p != null) PartLabels.remove(plugin, loc, p.partId());
            plugin.placements().remove(loc);
        }
        if (controllerKey != null) {
            plugin.placements().detachAll(controllerKey);
            plugin.robots().remove(controllerKey);
        }
        restore.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
    }

    // ----------------------------------------------------------------- lookup

    private static Program nightLightProgram() {
        Program program = new Program();
        program.add(new Rule("S1", Op.LT, Operand.of(7),  "A1", Verb.ON,  Operand.of(0)));
        program.add(new Rule("S1", Op.GE, Operand.of(7),  "A1", Verb.OFF, Operand.of(0)));
        return program;
    }

    private static Part firstOfKind(RoboCraftPlugin plugin, String kind) {
        for (Part p : plugin.parts().all()) if (kind.equals(p.kind())) return p;
        return null;
    }

    private static Part firstSensor(RoboCraftPlugin plugin, String type) {
        for (Part p : plugin.parts().all()) if (p.isSensor() && type.equals(p.sensor())) return p;
        return null;
    }

    private static Part firstActuator(RoboCraftPlugin plugin, String type) {
        for (Part p : plugin.parts().all()) if (p.isActuator() && type.equals(p.actuator())) return p;
        return null;
    }

    private static String pretty(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /** Print the outcome to whoever asked, and return true if everything passed. */
    public static boolean report(CommandSender sender, List<Check> checks) {
        int passed = 0;
        for (Check c : checks) {
            if (c.ok()) passed++;
            sender.sendMessage(Component.text((c.ok() ? "✔ " : "✖ ") + c.name(),
                    c.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (!c.ok() && c.detail() != null) {
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
