package com.agurim.robocraft.mission;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The test bench.
 *
 * <p>A mission injects simulated sensor readings rather than changing the world. Three reasons,
 * each sufficient: the world is shared and forcing night would black out the whole class;
 * {@code setPlayerTime} is client-side only so block light would not move anyway and the sensor
 * would read the wrong value; and a real day/night cycle is ten minutes against fifteen seconds
 * here. It is also how embedded code is really tested - inject inputs, assert outputs.
 *
 * <p>Failure never produces a score. It names the check that failed and why, and you may retry as
 * often as you like.
 *
 * <p>A run is driven by any {@link CommandSender}, not just a player, so the console can run one -
 * useful for a demo on the projector, and it is what lets {@code /rc selftest} verify the bench
 * end to end. Progress is only recorded when there is a real student behind it.
 */
public class MissionService {

    /** How a finished run ended - kept so a caller can inspect the result after the fact. */
    public enum Outcome { PASS, FAIL }

    private final RoboCraftPlugin plugin;
    private final MissionRegistry registry;
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Map<String, Outcome> lastOutcome = new LinkedHashMap<>();

    public MissionService(RoboCraftPlugin plugin) {
        this.plugin = plugin;
        this.registry = new MissionRegistry(plugin);
    }

    public MissionRegistry registry() { return registry; }

    private static final class Run {
        final Mission mission;
        final CommandSender sender;
        final UUID owner;                 // null when nobody's progress should change
        final Map<String, Integer> env = new LinkedHashMap<>();
        int index;
        int waitLeft;

        Run(Mission mission, CommandSender sender, UUID owner) {
            this.mission = mission;
            this.sender = sender;
            this.owner = owner;
        }
    }

    /** Simulated readings for this robot, or null when it is not on the bench. */
    public Map<String, Integer> envFor(String robotKey) {
        Run run = runs.get(robotKey);
        return (run == null) ? null : run.env;
    }

    public boolean isRunning(String robotKey)      { return runs.containsKey(robotKey); }
    public Outcome lastOutcome(String robotKey)    { return lastOutcome.get(robotKey); }
    public void abort(String robotKey)             { runs.remove(robotKey); }

    public String start(Player player, Robot robot, Mission mission) {
        return start(player, player.getUniqueId(), robot, mission);
    }

    /** Start a mission. Returns a Hebrew reason it cannot start, or null on success. */
    public String start(CommandSender sender, UUID owner, Robot robot, Mission mission) {
        String missing = missingParts(robot, mission);
        if (missing != null) return missing;
        if (robot.program().isEmpty()) return "אין כללים בתוכנית. לחצו על הבקר וכתבו כלל.";

        int capacity = batteryCapacity(robot);
        robot.energy(mission.startEnergy() > 0 ? mission.startEnergy() : capacity);
        robot.outputs().clear();
        robot.start(plugin.engine().now());

        runs.put(robot.key(), new Run(mission, sender, owner));
        lastOutcome.remove(robot.key());

        if (sender instanceof Player player) {
            player.showTitle(Title.title(
                    Component.text(mission.name(), NamedTextColor.AQUA),
                    Component.text("הרצת ניסוי", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        sender.sendMessage(Component.text("▶ " + mission.name() + " - " + mission.brief(), NamedTextColor.AQUA));
        return null;
    }

    /** Advance the scenario. Called by the engine once per robot tick. */
    public void advance(Robot robot) {
        Run run = runs.get(robot.key());
        if (run == null) return;

        // A student who logs out mid-run should not leave a bench ticking; the console cannot leave.
        if (run.sender instanceof Player player && !player.isOnline()) {
            runs.remove(robot.key());
            return;
        }

        int interval = Math.max(1, plugin.getConfig().getInt("robot.tick-interval", 10));
        if (run.waitLeft > 0) { run.waitLeft -= interval; return; }

        // Several zero-wait steps in a row are normal (env then expect); the bound is a guard
        // against a hand-edited missions.yml with no waits at all.
        for (int guard = 0; guard < 32; guard++) {
            if (run.index >= run.mission.steps().size()) { pass(run, robot); return; }

            Mission.Step step = run.mission.steps().get(run.index);
            if (!step.say().isEmpty() && plugin.getConfig().getBoolean("missions.bench-narrate", true)) {
                if (run.sender instanceof Player player) {
                    player.sendActionBar(Component.text(step.say(), NamedTextColor.YELLOW));
                }
                run.sender.sendMessage(Component.text("· " + step.say(), NamedTextColor.GRAY));
            }
            if (step.hasEnv()) run.env.putAll(step.env());
            if (step.hasExpect()) {
                String failure = check(robot, step);
                if (failure != null) { fail(run, robot, step, failure); return; }
            }
            run.index++;
            if (step.waitTicks() > 0) { run.waitLeft = step.waitTicks(); return; }
        }
    }

    /**
     * Robots that halted mid-mission stop being ticked by the engine, so their bench would hang.
     * The engine calls this so a flat battery reads as a failed check instead of a frozen run.
     */
    public void tickStalled() {
        for (String key : new ArrayList<>(runs.keySet())) {
            Robot robot = plugin.robots().get(key);
            if (robot != null && !robot.running()) advance(robot);
        }
    }

    // ---------------------------------------------------------------- checks

    /** null when every expectation holds, otherwise the Hebrew detail of the first that did not. */
    private String check(Robot robot, Mission.Step step) {
        for (Map.Entry<String, String> e : step.expect().entrySet()) {
            String key = e.getKey();
            String want = e.getValue();

            if ("running".equalsIgnoreCase(key)) {
                boolean expected = Boolean.parseBoolean(want);
                if (robot.running() != expected) {
                    return robot.running() ? "הרובוט עדיין פועל" : "הרובוט נעצר: " + nz(robot.halt());
                }
                continue;
            }
            if (key.length() >= 2 && key.charAt(0) == 'M' && Character.isDigit(key.charAt(1))) {
                int slot = Integer.parseInt(key.substring(1)) - 1;
                int expected = parseInt(want, 0);
                if (robot.mem(slot) != expected) return key + " = " + robot.mem(slot) + " במקום " + expected;
                continue;
            }
            String detail = checkActuator(robot, key, want);
            if (detail != null) return detail;
        }
        return null;
    }

    private String checkActuator(Robot robot, String type, String want) {
        List<String> ports = portsOfType(robot, type);
        String label = actuatorName(type);
        if (ports.isEmpty()) return "אין " + label + " מחובר לרובוט";

        int expected = switch (want.toLowerCase()) {
            case "on", "true"   -> 1;
            case "off", "false" -> 0;
            default -> parseInt(want, Integer.MIN_VALUE);
        };
        if (expected == Integer.MIN_VALUE) return null;

        boolean numeric = "display".equals(type);
        for (String port : ports) {
            int actual = robot.outputs().getOrDefault(port, 0);
            boolean ok = numeric ? (actual == expected) : ((actual != 0 ? 1 : 0) == expected);
            if (!ok) {
                String shown  = numeric ? String.valueOf(actual)   : (actual != 0 ? "ON" : "OFF");
                String wanted = numeric ? String.valueOf(expected) : (expected != 0 ? "ON" : "OFF");
                return label + " (" + port + ") = " + shown + " במקום " + wanted;
            }
        }
        return null;
    }

    private List<String> portsOfType(Robot robot, String actuatorType) {
        List<String> out = new ArrayList<>();
        for (Placed p : plugin.placements().partsOf(robot.key()).values()) {
            Part part = plugin.parts().get(p.partId());
            if (part != null && part.isActuator() && actuatorType.equals(part.actuator())) out.add(p.port());
        }
        return out;
    }

    private String actuatorName(String type) {
        for (Part p : plugin.parts().all()) if (type.equals(p.actuator())) return p.name();
        return type;
    }

    // -------------------------------------------------------------- outcomes

    private void pass(Run run, Robot robot) {
        runs.remove(robot.key());
        lastOutcome.put(robot.key(), Outcome.PASS);

        List<String> unlocked = new ArrayList<>();
        if (run.owner != null) {
            plugin.store().completeMission(run.owner, run.mission.id());
            for (String partId : run.mission.reward()) {
                if (plugin.parts().has(partId) && plugin.store().unlock(run.owner, partId)) {
                    unlocked.add(plugin.parts().get(partId).name());
                }
            }
        }

        if (run.sender instanceof Player player) {
            player.showTitle(Title.title(
                    Component.text("הצלחה!", NamedTextColor.GREEN),
                    Component.text(run.mission.name(), NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(600))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        run.sender.sendMessage(Component.text("✔ " + run.mission.name() + " עבר את כל הבדיקות",
                NamedTextColor.GREEN));

        if (!unlocked.isEmpty()) {
            run.sender.sendMessage(Component.text("נפתח: " + String.join(", ", unlocked), NamedTextColor.GOLD));
            plugin.board().build(plugin.store().getOrAssignPlotIndex(run.owner), run.owner);
        }
        if (run.owner != null) {
            Mission next = registry.nextFor(plugin.store().completedMissions(run.owner));
            if (next != null) {
                run.sender.sendMessage(Component.text(
                        "הבאה בתור: " + next.name() + " - /rc mission " + next.id(), NamedTextColor.AQUA));
            }
            if (run.sender instanceof Player player) plugin.statusBar().update(player);
        }
    }

    private void fail(Run run, Robot robot, Mission.Step step, String detail) {
        runs.remove(robot.key());
        lastOutcome.put(robot.key(), Outcome.FAIL);

        if (run.sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        }
        run.sender.sendMessage(Component.text("✖ הבדיקה לא עברה: " + detail, NamedTextColor.RED));
        if (plugin.getConfig().getBoolean("missions.show-failed-check", true) && !step.because().isEmpty()) {
            run.sender.sendMessage(Component.text("   " + step.because(), NamedTextColor.YELLOW));
        }
        if (!run.mission.hint().isEmpty()) {
            run.sender.sendMessage(Component.text("   רמז: " + run.mission.hint(), NamedTextColor.GRAY));
        }
        run.sender.sendMessage(Component.text("נסו שוב: /rc mission " + run.mission.id(),
                NamedTextColor.DARK_AQUA));
    }

    // --------------------------------------------------------------- helpers

    private String missingParts(Robot robot, Mission mission) {
        List<String> have = new ArrayList<>();
        for (Placed p : plugin.placements().partsOf(robot.key()).values()) have.add(p.partId());
        // The controller is the robot itself, so it never appears among its own attached parts.
        Placed self = plugin.placements().byKey(robot.key());
        if (self != null) have.add(self.partId());

        List<String> missing = new ArrayList<>();
        for (String need : mission.needs()) {
            if (!have.contains(need)) {
                Part part = plugin.parts().get(need);
                missing.add(part != null ? part.name() : need);
            }
        }
        return missing.isEmpty() ? null : "חסר לרובוט: " + String.join(", ", missing);
    }

    private int batteryCapacity(Robot robot) {
        return plugin.batteryCapacity(robot.key());
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return fallback; }
    }

    private static String nz(String s) { return (s == null || s.isEmpty()) ? "?" : s; }
}
