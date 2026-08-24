package com.agurim.robocraft.robot;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.act.ActuatorDriver;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartLabels;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Evaluator;
import com.agurim.robocraft.sense.SensorReader;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

/**
 * The loop: sense -> decide -> act -> power, every {@code robot.tick-interval} ticks.
 *
 * <p>This is the whole idea of the course made mechanical. Rules run top to bottom and later rules
 * win, exactly like {@code loop()} with a chain of ifs; outputs latch, so nothing turns itself off.
 * That last one is the classic first bug and we want students to hit it.
 */
public class RobotEngine {

    private final RoboCraftPlugin plugin;
    private BukkitTask task;
    private long ticks;

    public RobotEngine(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public long now() { return ticks; }

    public void start() {
        int interval = Math.max(1, plugin.getConfig().getInt("robot.tick-interval", 10));
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks += interval;
            tickAll();
        }, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tickAll() {
        for (String key : plugin.placements().controllers()) {
            Robot robot = plugin.robots().get(key);
            if (robot == null || !robot.running()) continue;
            if (plugin.getConfig().getBoolean("robot.require-owner-online", true)
                    && (robot.owner() == null || plugin.getServer().getPlayer(robot.owner()) == null)) {
                continue;
            }
            try {
                tick(robot);
            } catch (Exception e) {
                robot.stop("שגיאה");
                plugin.getLogger().warning("Robot " + key + " tick failed: " + e);
            }
        }
        // A robot that halted mid-mission is no longer ticked above, so its bench would hang
        // forever. This lets a flat battery surface as a failed check instead of a frozen run.
        plugin.missions().tickStalled();
    }

    /** One pass of the loop for one robot. */
    public void tick(Robot robot) {
        Map<String, Placed> parts = plugin.placements().partsOf(robot.key());

        // ---- SENSE ----
        Map<String, Integer> env = plugin.missions().envFor(robot.key());
        robot.inputs().clear();
        for (Map.Entry<String, Placed> e : parts.entrySet()) {
            Part part = plugin.parts().get(e.getValue().partId());
            if (part == null || !part.isSensor()) continue;
            Location loc = PartStore.fromKey(e.getKey());
            if (loc == null) continue;
            robot.inputs().put(e.getValue().port(), SensorReader.read(loc, e.getValue(), part, env));
        }

        // ---- DECIDE ----
        Evaluator.Result decision = Evaluator.run(
                robot.program(), robot.inputs(), robot.memory(), robot.seconds(ticks));
        Map<String, Integer> commands = decision.commands();
        robot.lastFired(decision.lastFired());

        // ---- ACT ----
        for (Map.Entry<String, Placed> e : parts.entrySet()) {
            Part part = plugin.parts().get(e.getValue().partId());
            if (part == null || !part.isActuator()) continue;
            String port = e.getValue().port();
            int prev = robot.outputs().getOrDefault(port, 0);
            // Not commanded this tick? Hold the last value. Outputs latch, like a real output pin.
            int desired = commands.containsKey(port) ? commands.get(port) : prev;
            if (desired != prev || ActuatorDriver.continuous(part)) {
                Location loc = PartStore.fromKey(e.getKey());
                if (loc != null) ActuatorDriver.apply(loc, part, desired, prev);
            }
            robot.outputs().put(port, desired);
        }

        // ---- POWER ----
        power(robot, parts);

        // ---- SHOW ---- (the labels are the debugger; they update in place, never respawn)
        for (String key : parts.keySet()) {
            Location loc = PartStore.fromKey(key);
            if (loc != null && loc.isChunkLoaded()) PartLabels.refresh(plugin, loc);
        }
        Location ctrl = PartStore.fromKey(robot.key());
        if (ctrl != null && ctrl.isChunkLoaded()) PartLabels.refresh(plugin, ctrl);

        plugin.missions().advance(robot);
    }

    /**
     * Energy is what turns "it works" into "it works within a budget" - the whole point of
     * mission 6. Active actuators cost, sensors cost a little, solar panels pay some back.
     */
    private void power(Robot robot, Map<String, Placed> parts) {
        if (!plugin.getConfig().getBoolean("power.enabled", true)) return;

        int drain = plugin.getConfig().getInt("power.base-drain", 1);
        int gain = 0;
        int capacity = 0;

        for (Map.Entry<String, Placed> e : parts.entrySet()) {
            Part part = plugin.parts().get(e.getValue().partId());
            if (part == null) continue;
            if (part.isBattery()) {
                capacity += part.capacity();
            } else if (part.isSensor()) {
                drain += Math.max(part.drain(), plugin.getConfig().getInt("power.sensor-drain", 1));
            } else if (part.isActuator()) {
                if (robot.outputs().getOrDefault(e.getValue().port(), 0) != 0) drain += part.drain();
            } else if (part.isSolar()) {
                Location loc = PartStore.fromKey(e.getKey());
                if (loc != null) {
                    int light = loc.clone().add(0, 1, 0).getBlock().getLightLevel();
                    gain += plugin.getConfig().getInt("power.solar-gain", 2) * light / 15;
                }
            }
        }

        int energy = robot.energy() - drain + gain;
        if (capacity > 0) energy = Math.min(energy, capacity);
        robot.energy(energy);

        if (robot.energy() <= 0) robot.stop("סוללה ריקה");
    }

    /** Can this robot run? Returns a Hebrew reason if not, or null if it is good to go. */
    public String whyNotReady(Robot robot) {
        Map<String, Placed> parts = plugin.placements().partsOf(robot.key());
        boolean battery = false;
        for (Placed p : parts.values()) {
            Part part = plugin.parts().get(p.partId());
            if (part != null && part.isBattery()) battery = true;
        }
        if (!battery) return "אין סוללה מחוברת";
        if (robot.program().isEmpty()) return "אין כללים בתוכנית";
        if (plugin.getConfig().getBoolean("power.enabled", true) && robot.energy() <= 0) return "הסוללה ריקה";
        return null;
    }
}
