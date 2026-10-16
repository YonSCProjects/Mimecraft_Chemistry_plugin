package com.agurim.robocraft.mission;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.program.Program;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.robot.Robot;
import com.agurim.robocraft.ui.ProgramMenu;
import com.agurim.robocraft.ui.RuleWords;
import com.agurim.robocraft.ui.Walkthrough;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Watch mode: the robot writes the solution into its own table, one rule every couple of
 * seconds, reads each aloud, then runs the bench.
 *
 * <p>The floor of the three paths (docs/DESIGN.md 4.12). A student who is intimidated by the
 * table clicks "show me", sees a program appear row by row with the sentence beside it, sees it
 * pass, and gets the prize. Twenty seconds, nothing to type, no risk - and they have now seen a
 * program and seen it work, which a skip would not have given them.
 *
 * <p>The rules are the mission's verified {@code solution:} moved onto this robot's ports
 * ({@link Walkthrough#remap}), so a light sensor that happens to be S2 gets S2. The table is
 * opened for the show and repainted after every rule; chat draws over it, so the sentences read.
 */
public final class Demo {

    private final RoboCraftPlugin plugin;
    private final Map<UUID, BukkitTask> running = new HashMap<>();

    public Demo(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public boolean isRunning(UUID id) { return running.containsKey(id); }

    public void cancel(UUID id) {
        BukkitTask t = running.remove(id);
        if (t != null) t.cancel();
    }

    /** Start the show. Returns a Hebrew reason it cannot, or null. */
    public String start(Player player, Robot robot, Mission mission) {
        MissionRegistry reg = plugin.missions().registry();
        Program solution = reg.solution(mission);
        if (solution == null || !reg.solutionVerified(mission)) return "למשימה הזו אין הדגמה.";
        StringBuilder missing = new StringBuilder();
        Map<String, String> ports = plugin.walkthrough().portMap(mission, robot, missing);
        if (ports == null) return "חסר לרובוט: " + missing;

        UUID id = player.getUniqueId();
        cancel(id);
        if (plugin.missions().isRunning(robot.key())) plugin.missions().abort(robot.key());
        robot.stop("הדגמה");
        plugin.missions().focus(id, mission.id());

        Program written = Walkthrough.remap(solution, ports, false);
        List<Rule> rules = written.rules();
        Map<String, String> names = plugin.walkthrough().portNames(robot);

        robot.program(new Program());
        plugin.robots().save();
        ProgramMenu menu = new ProgramMenu(robot.key());
        menu.open(plugin, player);
        player.sendMessage(Component.text("הדגמה: הרובוט כותב את הפתרון, כלל אחרי כלל.", NamedTextColor.AQUA));

        int interval = Math.max(10, plugin.getConfig().getInt("walkthrough.demo-interval", 40));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int i = 0;

            @Override
            public void run() {
                if (!player.isOnline() || plugin.robots().get(robot.key()) == null) { cancel(id); return; }
                if (i < rules.size()) {
                    Rule rule = rules.get(i);
                    robot.program().add(rule);
                    plugin.robots().save();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                    player.sendMessage(Component.text((i + 1) + ". " + rule.text(), NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("   " + RuleWords.hebrew(rule, names), NamedTextColor.GRAY));
                    repaint(player, robot.key());
                    i++;
                    return;
                }
                // All written. Close the table so the bench's narration can be read, and run.
                cancel(id);
                plugin.walkthrough().demoDone(id, mission);
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ProgramMenu) {
                    player.closeInventory();
                }
                player.sendMessage(Component.text("זו התוכנית. עכשיו הבוחן:", NamedTextColor.AQUA));
                String why = plugin.missions().start(player, robot, mission);
                if (why != null) player.sendMessage(Component.text(why, NamedTextColor.RED));
            }
        }, interval, interval);
        running.put(id, task);
        return null;
    }

    /** Repaint the table if it is the one open, so the new row appears without reopening the window. */
    private void repaint(Player player, String robotKey) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ProgramMenu menu
                && menu.robotKey().equals(robotKey)) {
            menu.render(plugin);
            player.updateInventory();
        }
    }
}
