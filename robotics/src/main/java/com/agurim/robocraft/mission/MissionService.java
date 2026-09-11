package com.agurim.robocraft.mission;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

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
    private final Map<UUID, String> lastFailure = new LinkedHashMap<>();

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
        final Map<String, Mission.Feedback> feedback = new LinkedHashMap<>();
        /** Outputs as of the last advance(), so a switch can be counted. */
        final Map<String, Integer> lastOutputs = new LinkedHashMap<>();
        /** Port -> how many times it switched on/off during the current wait. */
        final Map<String, Integer> switches = new LinkedHashMap<>();
        int index;
        int waitLeft;

        Run(Mission mission, CommandSender sender, UUID owner) {
            this.mission = mission;
            this.sender = sender;
            this.owner = owner;
        }
    }

    /**
     * Simulated readings for this robot, or null when it is not on the bench.
     *
     * <p>With {@code feedback} in play the view is computed: for each fed-back sensor type, if any
     * actuator of the named type is currently ON, its reading is raised. This is read by the engine
     * at the top of a tick, BEFORE this tick's ACT phase, so the outputs it sees are last tick's -
     * the lamp that was lit last tick is what lights the sensor now. That one-tick lag is exactly
     * what makes a single-threshold program oscillate, and exactly what real feedback does.
     */
    public Map<String, Integer> envFor(String robotKey) {
        Run run = runs.get(robotKey);
        if (run == null) return null;
        if (run.feedback.isEmpty()) return run.env;

        Robot robot = plugin.robots().get(robotKey);
        Map<String, Integer> view = new LinkedHashMap<>(run.env);
        for (Map.Entry<String, Mission.Feedback> e : run.feedback.entrySet()) {
            String sensorType = e.getKey();
            Mission.Feedback fb = e.getValue();
            if (!view.containsKey(sensorType) || robot == null) continue;
            boolean anyOn = false;
            for (String port : portsOfType(robot, fb.actuator())) {
                if (robot.outputs().getOrDefault(port, 0) != 0) { anyOn = true; break; }
            }
            if (anyOn) view.put(sensorType, Math.min(fb.max(), view.get(sensorType) + fb.add()));
        }
        return view;
    }

    public boolean isRunning(String robotKey)      { return runs.containsKey(robotKey); }

    /**
     * The last bench check this student failed, if any - "which check, and why".
     *
     * <p>Kept for the assistant: "why isn't it working?" is nearly always about the run that just
     * failed, and an answering agent that knows which check went wrong gives a situated hint
     * instead of a general one.
     */
    public String lastFailure(UUID owner)          { return lastFailure.get(owner); }
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

        // Count on/off switches every tick, including during a wait - a `steady` check at the
        // next step asks how many happened while it waited. A flickering lamp switches eight
        // times in eighty ticks; a hysteretic one, once.
        countSwitches(run, robot);

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
            if (step.hasFeedback()) run.feedback.putAll(step.feedback());
            if (step.hasExpect()) {
                String failure = check(run, robot, step);
                if (failure != null) { fail(run, robot, step, failure); return; }
            }
            run.index++;
            if (step.waitTicks() > 0) {
                run.waitLeft = step.waitTicks();
                // A fresh wait starts a fresh count, so `steady` measures only this wait.
                run.switches.clear();
                run.lastOutputs.clear();
                run.lastOutputs.putAll(robot.outputs());
                return;
            }
        }
    }

    private static void countSwitches(Run run, Robot robot) {
        for (Map.Entry<String, Integer> e : robot.outputs().entrySet()) {
            boolean was = run.lastOutputs.getOrDefault(e.getKey(), 0) != 0;
            boolean is  = e.getValue() != 0;
            if (was != is) run.switches.merge(e.getKey(), 1, Integer::sum);
        }
        run.lastOutputs.clear();
        run.lastOutputs.putAll(robot.outputs());
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
    private String check(Run run, Robot robot, Mission.Step step) {
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
            if ("steady".equalsIgnoreCase(key)) {
                String detail = checkSteady(run, robot, want);
                if (detail != null) return detail;
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

    /**
     * Did every actuator of this type hold still during the wait that just ended? One switch is
     * allowed - the lamp legitimately coming on when night falls. Two or more is a flicker.
     */
    private String checkSteady(Run run, Robot robot, String type) {
        List<String> ports = portsOfType(robot, type);
        String label = actuatorName(type);
        if (ports.isEmpty()) return "אין " + label + " מחובר לרובוט";
        for (String port : ports) {
            int n = run.switches.getOrDefault(port, 0);
            if (n > 1) return label + " (" + port + ") החליף מצב " + n + " פעמים בזמן ההמתנה - ריצוד";
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
                String detail = label + " (" + port + ") = " + shown + " במקום " + wanted;
                // A second lamp left attached from an earlier job fails the very first check with
                // no visible reason - "A2 = OFF" when the student only ever thinks about A1.
                if (ports.size() > 1) {
                    detail += " (יש " + ports.size() + " " + label + " מחוברים - המשימה מצפה לאחד)";
                }
                return detail;
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
        if (run.owner != null) lastFailure.remove(run.owner);   // solved; stop advertising it

        List<String> unlocked = new ArrayList<>();
        boolean firstTime = false;
        if (run.owner != null) {
            // Read before recording, or every retry looks like a first success.
            firstTime = !plugin.store().isMissionDone(run.owner, run.mission.id());
            plugin.store().completeMission(run.owner, run.mission.id());
            for (String partId : run.mission.reward()) {
                if (plugin.parts().has(partId) && plugin.store().unlock(run.owner, partId)) {
                    unlocked.add(plugin.parts().get(partId).name());
                }
            }
        }

        // A budget mission started the robot nearly flat and it passed on what it had. Left like
        // that it dies seconds after the celebration - at real dusk the lamp drains 8 a tick.
        // The pass was the point; the robot may now keep working.
        if (run.mission.startEnergy() > 0) {
            robot.energy(batteryCapacity(robot));
            run.sender.sendMessage(Component.text("הסוללה מולאה לרגל ההצלחה.", NamedTextColor.GRAY));
        }

        // Say what they GAINED, not the mission name they already know they just ran. A warm-up
        // or bonus has nothing to unlock, so the subtitle carries the encouragement instead.
        String gained = unlocked.isEmpty()
                ? (run.mission.warmUp() ? "חימום הושלם" : run.mission.bonus() ? "בונוס הושלם" : run.mission.name())
                : "נפתח: " + String.join(", ", unlocked);

        if (run.sender instanceof Player player) {
            player.showTitle(Title.title(
                    Component.text("הצלחה!", NamedTextColor.GREEN),
                    Component.text(gained, NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(800))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        run.sender.sendMessage(Component.text("✔ " + run.mission.name() + " עבר את כל הבדיקות",
                NamedTextColor.GREEN));

        if (!unlocked.isEmpty()) {
            run.sender.sendMessage(Component.text("נפתח: " + String.join(", ", unlocked), NamedTextColor.GOLD));
        }

        // Everything below needs a real student behind the run. `/rc selftest` drives the bench
        // with no owner, and must not light a trophy or announce anything to the class.
        if (run.owner != null) {
            int plot = plugin.store().getOrAssignPlotIndex(run.owner);
            if (!unlocked.isEmpty()) plugin.board().build(plot, run.owner);
            plugin.trophies().award(plot, run.mission);
            if (firstTime) celebrate(run, plot);

            Mission next = registry.nextSuggested(plugin.store().completedMissions(run.owner));
            if (next != null) {
                run.sender.sendMessage(Component.text(
                        "הבאה בתור: " + next.name() + " - /rc mission " + next.id(), NamedTextColor.AQUA));
            }
            if (run.sender instanceof Player player) plugin.statusBar().update(player);
        }
    }

    /**
     * The moment itself: a firework over the student's own board, and one line to the class.
     *
     * <p>First completion only. Re-running a mission you have already passed is encouraged - it is
     * how you check a change - but it must not re-announce, or the class chat fills with noise and
     * the announcement stops meaning anything.
     *
     * <p>The broadcast is the cheapest motivation in the plugin: public recognition, costing no
     * part, no gear and no gate. It is also the only place a student's work is visible to the room
     * without someone walking to their plot.
     */
    private void celebrate(Run run, int plot) {
        if (plugin.getConfig().getBoolean("celebrate.fireworks", true)) {
            // Above the board, where they are standing and looking - and high enough that the
            // burst cannot hurt anybody. Fireworks damage on detonation at point-blank range.
            Location spot = plugin.board().arrivalSpot(plot).add(0, 2, 0);
            spawnFirework(spot, run.mission);
        }

        if (plugin.getConfig().getBoolean("celebrate.broadcast", true)) {
            String who = plugin.store().getName(run.owner);
            plugin.getServer().broadcast(Component.text(
                    "★ " + who + " סיים/ה את " + run.mission.name(), NamedTextColor.GOLD));
        }
    }

    /** Silver for a warm-up, gold for a rung of the ladder, green for a bonus - same as the trophies. */
    private void spawnFirework(Location spot, Mission mission) {
        if (spot.getWorld() == null) return;
        boolean warmUp = mission.warmUp();
        Color colour = warmUp ? Color.SILVER : mission.bonus() ? Color.LIME : Color.YELLOW;
        spot.getWorld().spawn(spot, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(warmUp ? FireworkEffect.Type.BALL : FireworkEffect.Type.BALL_LARGE)
                    .withColor(colour)
                    .withFade(Color.WHITE)
                    .trail(!warmUp)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        });
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
        // The readings at the moment it failed. Without these a student is told the answer was
        // wrong but not what their program was looking at when it decided - which is the only
        // thing that lets them find the bug themselves rather than guessing at the threshold.
        String readings = snapshot(robot);
        if (!readings.isEmpty()) {
            run.sender.sendMessage(Component.text("   באותו רגע: " + readings, NamedTextColor.GRAY));
        }
        if (!run.mission.hint().isEmpty()) {
            run.sender.sendMessage(Component.text("   רמז: " + run.mission.hint(), NamedTextColor.GRAY));
        }
        run.sender.sendMessage(Component.text("נסו שוב: /rc mission " + run.mission.id(),
                NamedTextColor.DARK_AQUA));

        if (run.owner != null) {
            lastFailure.put(run.owner, run.mission.id() + ": " + detail
                    + (step.because().isEmpty() ? "" : " (" + step.because() + ")"));
        }
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

    /** "S1 = 14, M1 = 2, ⚡ 4120" - what the program was looking at when the check failed. */
    private String snapshot(Robot robot) {
        List<String> parts = new ArrayList<>();
        robot.inputs().forEach((port, value) -> parts.add(port + " = " + value));
        for (int i = 0; i < robot.memory().length; i++) {
            if (robot.mem(i) != 0) parts.add("M" + (i + 1) + " = " + robot.mem(i));
        }
        if (plugin.getConfig().getBoolean("power.enabled", true)) parts.add("⚡ " + robot.energy());
        return String.join(", ", parts);
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return fallback; }
    }

    private static String nz(String s) { return (s == null || s.isEmpty()) ? "?" : s; }
}
