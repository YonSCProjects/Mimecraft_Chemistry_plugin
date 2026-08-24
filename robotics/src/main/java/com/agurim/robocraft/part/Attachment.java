package com.agurim.robocraft.part;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which controller a part belongs to, and what happens when one is added or taken away.
 *
 * <p>Lifted out of the listener on purpose. The listener needs a real player holding a real item,
 * so none of this could be exercised without a human at a keyboard - and this is the logic a
 * student's whole build depends on. Here it is reachable by {@code /rc selftest}, and the listener
 * is left with only the event plumbing and the messages.
 */
public final class Attachment {

    private Attachment() {}

    public enum Outcome {
        /** Attached to a controller and given a port. */
        ATTACHED,
        /** Placed, but no controller was in range - it stands there doing nothing. */
        NO_CONTROLLER,
        /** Placed, but that controller already has as many parts as it is allowed. */
        FULL,
        /** This part IS a controller; it owns a robot rather than joining one. */
        IS_CONTROLLER
    }

    public record Result(Outcome outcome, String controllerKey, String port) {
        public boolean attached() { return outcome == Outcome.ATTACHED; }
    }

    /** The nearest controller within {@code robot.attach-radius}, or null. */
    public static String nearestController(RoboCraftPlugin plugin, Location loc) {
        int radius = plugin.getConfig().getInt("robot.attach-radius", 4);
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (String key : plugin.placements().controllers()) {
            Location c = PartStore.fromKey(key);
            if (c == null || c.getWorld() == null || !c.getWorld().equals(loc.getWorld())) continue;
            double d = c.distance(loc);
            if (d <= radius && d < bestDist) { bestDist = d; best = key; }
        }
        return best;
    }

    /**
     * Register a part that has just been placed, joining it to a controller if one is in range.
     * Always records the part - an unattached part is still a part, it simply does nothing yet.
     */
    public static Result place(RoboCraftPlugin plugin, Location loc, Part part, BlockFace facing) {
        String key = PartStore.key(loc);

        if (part.isController()) {
            plugin.placements().put(loc, new Placed(part.id(), facing, "", ""));
            return new Result(Outcome.IS_CONTROLLER, key, "");
        }

        String controller = nearestController(plugin, loc);
        if (controller == null) {
            plugin.placements().put(loc, new Placed(part.id(), facing, "", ""));
            return new Result(Outcome.NO_CONTROLLER, null, "");
        }

        int max = plugin.getConfig().getInt("robot.max-parts", 16);
        if (plugin.placements().partsOf(controller).size() >= max) {
            plugin.placements().put(loc, new Placed(part.id(), facing, "", ""));
            return new Result(Outcome.FULL, controller, "");
        }

        // Only sensors and actuators get a port; a battery has nothing a rule could refer to.
        boolean ported = part.isSensor() || part.isActuator();
        String port = ported ? plugin.placements().nextPort(controller, part.isSensor()) : "";
        plugin.placements().put(loc, new Placed(part.id(), facing, controller, port));
        return new Result(Outcome.ATTACHED, controller, port);
    }

    /**
     * Take a part out. Breaking a controller orphans everything that was attached to it rather
     * than deleting it - the student's build stays standing, it just stops being a robot until a
     * controller comes back.
     *
     * @return the location keys whose labels now need refreshing
     */
    public static java.util.Set<String> remove(RoboCraftPlugin plugin, Location loc, Part part) {
        String key = PartStore.key(loc);
        Placed placed = plugin.placements().get(loc);
        String controller = (placed == null) ? "" : placed.robot();

        plugin.placements().remove(loc);

        if (part.isController()) {
            Map<String, Placed> orphans = new LinkedHashMap<>(plugin.placements().partsOf(key));
            plugin.placements().detachAll(key);
            plugin.robots().remove(key);
            plugin.missions().abort(key);
            return orphans.keySet();
        }
        return (controller == null || controller.isEmpty())
                ? java.util.Set.of()
                : java.util.Set.of(controller);
    }

    /**
     * Parts already standing in range with no controller join the one that just arrived.
     *
     * @return the location keys that were adopted
     */
    public static java.util.Set<String> adoptOrphans(RoboCraftPlugin plugin, String controllerKey,
                                                     Location controllerLoc) {
        int radius = plugin.getConfig().getInt("robot.attach-radius", 4);
        int max = plugin.getConfig().getInt("robot.max-parts", 16);
        java.util.Set<String> adopted = new java.util.LinkedHashSet<>();

        for (Map.Entry<String, Placed> e : new LinkedHashMap<>(plugin.placements().partsOf("")).entrySet()) {
            if (plugin.placements().partsOf(controllerKey).size() >= max) break;
            Location loc = PartStore.fromKey(e.getKey());
            if (loc == null || loc.getWorld() == null
                    || !loc.getWorld().equals(controllerLoc.getWorld())) continue;
            if (loc.distance(controllerLoc) > radius) continue;

            Part part = plugin.parts().get(e.getValue().partId());
            if (part == null || part.isController()) continue;

            boolean ported = part.isSensor() || part.isActuator();
            String port = ported ? plugin.placements().nextPort(controllerKey, part.isSensor()) : "";
            plugin.placements().put(loc, e.getValue().withRobot(controllerKey, port));
            adopted.add(e.getKey());
        }
        return adopted;
    }
}
