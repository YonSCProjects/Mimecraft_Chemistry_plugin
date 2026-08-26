package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.robot.Robot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the teacher cannot see by walking the room: who is stuck, and on what.
 *
 * <p>Two views, because they answer different questions. The histogram answers "should I stop the
 * class and explain this to everyone?" - if nine of twelve are stuck on the thermostat, that is a
 * whiteboard moment, not nine separate conversations. The roster answers "who do I go to next?"
 *
 * <p>The roster is sorted by <em>least</em> progress first, deliberately. A teacher scanning a list
 * has attention for the top few lines, and those should be the students who need help - not the
 * ones who are fine.
 */
public final class ProgressReport {

    private ProgressReport() {}

    private static final int BAR = 12;

    public static void send(RoboCraftPlugin plugin, CommandSender sender) {
        List<UUID> roster = plugin.store().allPlayers();
        // The required ladder is the thing to track. Warm-ups are practice and would only make
        // the histogram look like progress a student did not need to make.
        List<Mission> missions = new ArrayList<>(plugin.missions().registry().required());

        if (roster.isEmpty()) {
            sender.sendMessage(Component.text("עוד לא נכנס אף תלמיד.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("==== התקדמות הכיתה ====", NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                "תלמידים: " + roster.size() + "   משימות: " + missions.size(), NamedTextColor.GRAY));

        // --- how far did the class get with each mission? ---
        Map<String, Integer> done = new LinkedHashMap<>();
        for (Mission m : missions) done.put(m.id(), 0);
        Map<String, Integer> stuckOn = new LinkedHashMap<>();

        for (UUID id : roster) {
            Set<String> completed = plugin.store().completedMissions(id);
            for (String missionId : completed) done.merge(missionId, 1, Integer::sum);
            Mission next = plugin.missions().registry().nextFor(completed);
            if (next != null) stuckOn.merge(next.id(), 1, Integer::sum);
        }

        for (Mission m : missions) {
            int n = done.getOrDefault(m.id(), 0);
            sender.sendMessage(Component.text(bar(n, roster.size()) + " ", barColor(n, roster.size()))
                    .append(Component.text(n + "/" + roster.size() + "  " + m.name(), NamedTextColor.WHITE)));
        }

        // --- where is the class right now? the whiteboard-moment signal ---
        List<Map.Entry<String, Integer>> hot = new ArrayList<>(stuckOn.entrySet());
        hot.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (!hot.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : hot.subList(0, Math.min(3, hot.size()))) {
                Mission m = plugin.missions().registry().byId(e.getKey());
                parts.add((m != null ? m.name() : e.getKey()) + " (" + e.getValue() + ")");
            }
            sender.sendMessage(Component.text("עובדים עכשיו על: " + String.join(", ", parts),
                    NamedTextColor.YELLOW));
        }

        // --- who needs you, most first ---
        sender.sendMessage(Component.text("---- תלמידים ----", NamedTextColor.AQUA));
        List<Row> rows = new ArrayList<>();
        for (UUID id : roster) rows.add(row(plugin, id));
        rows.sort(Comparator.comparingInt((Row r) -> r.completed).thenComparing(r -> r.name));

        for (Row r : rows) {
            sender.sendMessage(Component.text(pad(r.name, 14), NamedTextColor.WHITE)
                    .append(Component.text(r.completed + "/" + missions.size() + "  ", progressColor(r.completed, missions.size())))
                    .append(Component.text(String.format("רכיבים %-3d", r.parts), NamedTextColor.GRAY))
                    .append(Component.text(pad(r.next, 22), NamedTextColor.YELLOW))
                    .append(Component.text(r.robot, r.robotColor)));
        }
    }

    private record Row(String name, int completed, int parts, String next, String robot, NamedTextColor robotColor) { }

    private static Row row(RoboCraftPlugin plugin, UUID id) {
        Set<String> completed = plugin.store().completedMissions(id);
        Mission next = plugin.missions().registry().nextFor(completed);

        // A student with no controller placed has not started building at all - which is a
        // different problem from a student whose robot is built but failing, and needs a
        // different conversation.
        String robot = "אין בקר";
        NamedTextColor colour = NamedTextColor.DARK_GRAY;
        for (String key : plugin.placements().controllers()) {
            Robot r = plugin.robots().get(key);
            if (r == null || !id.equals(r.owner())) continue;
            if (r.running()) { robot = "פועל"; colour = NamedTextColor.GREEN; break; }
            robot = (r.halt() != null) ? r.halt() : "עצור";
            colour = (r.halt() != null) ? NamedTextColor.RED : NamedTextColor.GRAY;
        }

        return new Row(plugin.store().getName(id), plugin.missions().registry().requiredDone(completed),
                plugin.store().unlocked(id).size(),
                (next == null) ? "סיים הכול" : next.name(), robot, colour);
    }

    private static String bar(int n, int total) {
        int filled = (total <= 0) ? 0 : Math.round((float) n / total * BAR);
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, BAR - filled));
    }

    private static NamedTextColor barColor(int n, int total) {
        if (total <= 0) return NamedTextColor.GRAY;
        double f = (double) n / total;
        return (f >= 0.8) ? NamedTextColor.GREEN : (f >= 0.4) ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    private static NamedTextColor progressColor(int done, int total) {
        if (total <= 0) return NamedTextColor.GRAY;
        double f = (double) done / total;
        return (f >= 0.8) ? NamedTextColor.GREEN : (f >= 0.4) ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    /**
     * Pad for a fixed-width console column. Minecraft chat is proportional, so this only truly
     * lines up in the server console - which is where a teacher reads it anyway.
     */
    private static String pad(String s, int width) {
        if (s == null) s = "?";
        if (s.length() >= width) return s.substring(0, width - 1) + " ";
        return s + " ".repeat(width - s.length());
    }
}
