package com.agurim.robocraft.diag;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.act.ActuatorDriver;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.sense.SensorReader;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the YAML on enable and complains loudly about the mistakes that otherwise fail silently.
 *
 * <p>Every check here exists because the failure mode is invisible during play. A typo in a
 * sensor type reads zero forever; two parts sharing a block make every one of them ambiguous;
 * a board that runs off the edge of a plot writes tiles onto the neighbour's. None of those
 * throw, none of them look like bugs from inside the game, and all of them will happen the first
 * time content is edited by hand.
 */
public final class Validate {

    private Validate() {}

    public static void run(RoboCraftPlugin plugin) {
        List<String> problems = new ArrayList<>();

        world(plugin, problems);
        difficulty(plugin, problems);
        parts(plugin, problems);
        blocks(plugin, problems);
        board(plugin, problems);
        starterKit(plugin, problems);
        placements(plugin, problems);

        for (String problem : problems) plugin.getLogger().warning("config check: " + problem);
        if (problems.isEmpty()) {
            plugin.getLogger().info("config check: parts.yml and config.yml look consistent.");
        }
    }

    /** The configured world has to exist, or every plot silently lands in the wrong one. */
    private static void world(RoboCraftPlugin plugin, List<String> problems) {
        String name = plugin.getConfig().getString("world", "world");
        if (plugin.getServer().getWorld(name) == null) {
            problems.add("world '" + name + "' does not exist - plots will fall back to the default world.");
        }
    }

    /**
     * A teaching world on anything but PEACEFUL.
     *
     * <p>Unlike the rest of these checks this one is loud in game - you die - but it is loud in
     * the wrong place. A teacher watching students be shot by skeletons while they wire a robot
     * has no reason to suspect one line of server.properties, and every reason to suspect the
     * plugin. Found the first time anyone played: mobs made the workshop unusable long before any
     * mission could be attempted.
     *
     * <p>A warning, not a correction. Difficulty belongs to whoever runs the server, not to us.
     */
    private static void difficulty(RoboCraftPlugin plugin, List<String> problems) {
        World world = plugin.plots().world();
        if (world != null && world.getDifficulty() != Difficulty.PEACEFUL) {
            problems.add("world '" + world.getName() + "' is on difficulty " + world.getDifficulty()
                    + " - students get attacked while building. Set  difficulty=peaceful  in"
                    + " server.properties and restart. This is a server setting, not a plugin one.");
        }
    }

    /** A sensor or actuator type nothing implements is a part that quietly does nothing. */
    private static void parts(RoboCraftPlugin plugin, List<String> problems) {
        for (Part part : plugin.parts().all()) {
            if (part.isSensor()) {
                if (part.sensor().isEmpty()) {
                    problems.add("part '" + part.id() + "' is kind: sensor but has no sensor: type.");
                } else if (!SensorReader.TYPES.contains(part.sensor())) {
                    problems.add("part '" + part.id() + "' has sensor: " + part.sensor()
                            + " which nothing implements - it will read 0 forever. Known: "
                            + String.join(", ", sorted(SensorReader.TYPES)));
                }
            }
            if (part.isActuator()) {
                if (part.actuator().isEmpty()) {
                    problems.add("part '" + part.id() + "' is kind: actuator but has no actuator: type.");
                } else if (!ActuatorDriver.TYPES.contains(part.actuator())) {
                    problems.add("part '" + part.id() + "' has actuator: " + part.actuator()
                            + " which nothing drives - it will never move. Known: "
                            + String.join(", ", sorted(ActuatorDriver.TYPES)));
                }
            }
            if (part.isBattery() && part.capacity() <= 0) {
                problems.add("battery part '" + part.id() + "' has no capacity, so a robot using it "
                        + "can never run.");
            }
        }
        if (plugin.parts().all().stream().noneMatch(Part::isController)) {
            problems.add("no part has kind: controller - nothing can be built at all.");
        }
        if (plugin.parts().all().stream().noneMatch(Part::isBattery)) {
            problems.add("no part has kind: battery - no robot can ever run.");
        }
    }

    /**
     * Parts are identified by exact Material, so two parts cannot share one. The charger cannot
     * share with a part either: the interact handler checks parts first and the charger would
     * become unreachable.
     */
    private static void blocks(RoboCraftPlugin plugin, List<String> problems) {
        Map<Material, String> seen = new HashMap<>();
        for (Part part : plugin.parts().all()) {
            String other = seen.put(part.block(), part.id());
            if (other != null) {
                problems.add("parts '" + other + "' and '" + part.id() + "' both use "
                        + part.block() + " - a placed block cannot be told apart, so one of them "
                        + "will never be recognised.");
            }
        }
        String chargerName = plugin.getConfig().getString("power.charger-block", "");
        Material charger = Material.matchMaterial(chargerName);
        if (charger == null) {
            problems.add("power.charger-block '" + chargerName + "' is not a real block - "
                    + "the charging pad will not be built.");
        } else if (seen.containsKey(charger)) {
            problems.add("power.charger-block " + charger + " is also part '" + seen.get(charger)
                    + "' - right-clicking it will open the part, never the charger.");
        }
    }

    /**
     * The board is laid out from the plot corner, so a wide catalogue can run off the edge of the
     * plot and start writing tiles onto the neighbour's - which looks like griefing, not a config
     * mistake.
     */
    private static void board(RoboCraftPlugin plugin, List<String> problems) {
        int size    = plugin.getConfig().getInt("plot.size", 96);
        int perRow  = Math.max(1, plugin.getConfig().getInt("board.per-row", 6));
        int spacing = Math.max(1, plugin.getConfig().getInt("board.tile-spacing", 2));
        int offsetX = plugin.getConfig().getInt("board.offset-x", 8);
        int offsetZ = plugin.getConfig().getInt("board.offset-z", 4);

        int widest = offsetX + (perRow - 1) * spacing;
        if (widest >= size) {
            problems.add("the component board is " + widest + " blocks from the plot corner but a "
                    + "plot is only " + size + " wide - tiles will land on the next student's plot.");
        }
        if (offsetZ >= size) {
            problems.add("board.offset-z (" + offsetZ + ") is outside the plot (" + size + ").");
        }

        // The board and the charging pad must not want the same block.
        Location charger = plugin.kiosk().chargerLocation(0);
        for (int i = 0; i < plugin.parts().size(); i++) {
            Location tile = plugin.board().tileLocation(0, i);
            if (tile.getBlockX() == charger.getBlockX()
                    && tile.getBlockY() == charger.getBlockY()
                    && tile.getBlockZ() == charger.getBlockZ()) {
                problems.add("the charging pad sits on board tile " + i
                        + " - one of them will overwrite the other.");
                break;
            }
        }
    }

    /**
     * Renaming or deleting a part in parts.yml silently breaks every build that already used it.
     * Nothing throws: the store still holds the placement, the registry no longer resolves it, so
     * the engine skips it, the label never updates again, and a controller simply stops being a
     * controller. From inside the game the robot just quietly stops working.
     */
    private static void placements(RoboCraftPlugin plugin, List<String> problems) {
        java.util.Map<String, Integer> unknown = new HashMap<>();
        for (String key : plugin.placements().allKeys()) {
            String id = plugin.placements().byKey(key).partId();
            if (!plugin.parts().has(id)) unknown.merge(id, 1, Integer::sum);
        }
        unknown.forEach((id, count) -> problems.add(count + " placed block" + (count == 1 ? "" : "s")
                + " reference part '" + id + "', which parts.yml no longer defines - those builds "
                + "have quietly stopped working. Restore the id, or clear them out."));

        int orphanRobots = 0;
        for (String key : plugin.robots().all().keySet()) {
            if (plugin.placements().byKey(key) == null) orphanRobots++;
        }
        if (orphanRobots > 0) {
            problems.add(orphanRobots + " saved robot" + (orphanRobots == 1 ? " in" : "s in")
                    + " robots.yml " + (orphanRobots == 1 ? "has" : "have")
                    + " no controller block any more - harmless, but "
                    + (orphanRobots == 1 ? "its program is" : "their programs are") + " unreachable.");
        }
    }

    private static void starterKit(RoboCraftPlugin plugin, List<String> problems) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("starter-kit.parts");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            if (!plugin.parts().has(id)) {
                problems.add("starter-kit lists '" + id + "', which is not a part in parts.yml.");
            } else if (!plugin.parts().get(id).unlockedByDefault()) {
                problems.add("starter-kit hands out '" + id + "' but parts.yml has it locked - "
                        + "students get the item with the board tile still greyed out.");
            }
        }
    }

    private static List<String> sorted(java.util.Set<String> set) {
        List<String> out = new ArrayList<>(set);
        java.util.Collections.sort(out);
        return out;
    }
}
