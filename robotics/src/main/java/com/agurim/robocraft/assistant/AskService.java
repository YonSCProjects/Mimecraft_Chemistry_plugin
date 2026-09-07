package com.agurim.robocraft.assistant;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartStore;
import com.agurim.robocraft.part.Placed;
import com.agurim.robocraft.program.Rule;
import com.agurim.robocraft.robot.Robot;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captures a student's question together with the state needed to answer it well, and appends it to
 * questions.jsonl (one JSON object per line, UTF-8).
 *
 * <p>The plugin deliberately does NOT talk to any AI. It owns capture and delivery; the answering
 * agent is external and pluggable - it tails this file and replies with {@code /rc whisper}. That
 * keeps secrets, cost and model choice out of the plugin, lets the assistant be swapped or switched
 * off without a rebuild, and means the questions survive as a teaching record even when no
 * assistant is running at all. Same arrangement as ChemCraft.
 *
 * <p>What differs is the context, and it is the whole point here. A robotics question is almost
 * always "why did it do that?", which is unanswerable in the abstract and easy to answer with the
 * student's own rule table and live readings in hand. The difference between a generic hint and
 * <em>"rule 2 only fires at S1 &gt;= 7, and S1 is reading 4 right now"</em> is entirely in this file.
 *
 * <p>Whether the agent should give that away or ask a question back is the agent's business, not
 * the plugin's - see docs/DESIGN.md 7.
 */
public class AskService {

    private final RoboCraftPlugin plugin;
    private final Map<UUID, Long> lastAsk = new HashMap<>();

    public AskService(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private int cooldown() { return plugin.getConfig().getInt("assistant.cooldown-seconds", 15); }

    /** Seconds the player must still wait, or 0 if they may ask now. */
    public long cooldownRemaining(UUID id) {
        Long last = lastAsk.get(id);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldown() - elapsed);
    }

    /** Record a question. Returns false if the file could not be written. */
    public boolean ask(Player player, String question) {
        lastAsk.put(player.getUniqueId(), System.currentTimeMillis());

        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.putAll(playerContext(player));
        ctx.putAll(robotContext(myRobot(player)));

        StringBuilder json = new StringBuilder("{");
        json.append(q("ts")).append(':').append(System.currentTimeMillis()).append(',');
        json.append(q("player")).append(':').append(q(player.getName())).append(',');
        json.append(q("uuid")).append(':').append(q(player.getUniqueId().toString())).append(',');
        json.append(q("question")).append(':').append(q(question));
        ctx.forEach((k, v) -> json.append(',').append(q(k)).append(':').append(q(v)));
        json.append('}');

        File f = new File(plugin.getDataFolder(), "questions.jsonl");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
            w.write(json.toString());
            w.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not append questions.jsonl: " + e.getMessage());
            return false;
        }
        plugin.getLogger().info("[ask] " + player.getName() + ": " + question);
        return true;
    }

    /** Where they are and how far along - the part that needs a Player. */
    private Map<String, String> playerContext(Player player) {
        Map<String, String> m = new LinkedHashMap<>();
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();
        m.put("x", String.valueOf(loc.getBlockX()));
        m.put("y", String.valueOf(loc.getBlockY()));
        m.put("z", String.valueOf(loc.getBlockZ()));

        int plot = plugin.plots().plotIndexAt(loc);
        m.put("where", plot == plugin.store().getOrAssignPlotIndex(id) ? "own_plot"
                : plot >= 0 ? "plot:" + plot : "outside");

        var done = plugin.store().completedMissions(id);
        m.put("missions_done", plugin.missions().registry().requiredDone(done)
                + "/" + plugin.missions().registry().requiredCount());
        m.put("parts_unlocked", plugin.store().unlocked(id).size() + "/" + plugin.parts().size());

        // nextSuggested, not nextFor: this is the one record a helper reads to work out where the
        // student actually is. nextFor skips the warm-ups, so a beginner who had not started the
        // required ladder was reported as working on twilight - and any answer built on that would
        // be about a mission they had not reached. Caught by a real /rc ask on 2026-09-06.
        Mission next = plugin.missions().registry().nextSuggested(done);
        if (next != null) {
            m.put("current_mission", next.id());
            m.put("current_mission_name", next.name());
            m.put("current_mission_brief", next.brief());
            if (!next.hint().isEmpty()) m.put("current_mission_hint", next.hint());
        }
        String failure = plugin.missions().lastFailure(id);
        if (failure != null) m.put("last_bench_failure", failure);
        return m;
    }

    /**
     * The robot itself: its program, what its sensors read, and what it decided.
     *
     * <p>Free of {@link Player} so the self-test can exercise it - this is the half that silently
     * goes stale whenever the program model changes, and a stale context is worse than none: the
     * assistant would answer confidently about a rule table the student is not looking at.
     */
    public Map<String, String> robotContext(Robot robot) {
        Map<String, String> m = new LinkedHashMap<>();
        if (robot == null) {
            m.put("robot", "none");
            return m;
        }
        m.put("robot", robot.running() ? "running" : "stopped");
        if (robot.halt() != null) m.put("robot_halted", robot.halt());
        m.put("energy", String.valueOf(robot.energy()));

        List<String> ports = new ArrayList<>();
        for (Placed p : plugin.placements().partsOf(robot.key()).values()) {
            Part part = plugin.parts().get(p.partId());
            if (part == null) continue;
            ports.add((p.port().isEmpty() ? part.id() : p.port() + "=" + part.id()));
        }
        m.put("parts", String.join(", ", ports));

        List<String> readings = new ArrayList<>();
        robot.inputs().forEach((port, v) -> readings.add(port + "=" + v));
        m.put("readings", String.join(", ", readings));

        List<String> mem = new ArrayList<>();
        for (int i = 0; i < robot.memory().length; i++) {
            if (robot.mem(i) != 0) mem.add("M" + (i + 1) + "=" + robot.mem(i));
        }
        m.put("memory", String.join(", ", mem));

        // The rule table, as the student sees it. Without this the assistant is guessing.
        List<Rule> rules = robot.program().rules();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) lines.add((i + 1) + ". " + rules.get(i).text());
        m.put("program", String.join(" ; ", lines));
        m.put("rules_used", rules.size() + "/" + plugin.getConfig().getInt("program.max-rules", 5));
        if (robot.lastFired() >= 0) m.put("last_rule_fired", String.valueOf(robot.lastFired() + 1));

        List<String> outputs = new ArrayList<>();
        robot.outputs().forEach((port, v) -> outputs.add(port + "=" + (v != 0 ? "ON" : "OFF")));
        m.put("outputs", String.join(", ", outputs));
        return m;
    }

    /** The player's robot: the only one they own, or the nearest if they have several. */
    private Robot myRobot(Player player) {
        Robot best = null;
        double bestDist = Double.MAX_VALUE;
        for (String key : plugin.placements().controllers()) {
            Robot r = plugin.robots().get(key);
            if (r == null || !player.getUniqueId().equals(r.owner())) continue;
            Location loc = PartStore.fromKey(key);
            if (loc == null || !loc.getWorld().equals(player.getWorld())) { if (best == null) best = r; continue; }
            double d = loc.distance(player.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }

    /** Read back the most recent questions, newest last, for the teacher view. */
    public List<String> recent(int limit) {
        File f = new File(plugin.getDataFolder(), "questions.jsonl");
        List<String> all = new ArrayList<>();
        if (!f.exists()) return all;
        try {
            all = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read questions.jsonl: " + e.getMessage());
        }
        return all.size() <= limit ? all : all.subList(all.size() - limit, all.size());
    }

    /** Minimal JSON string quoting - enough for names, Hebrew questions and our own keys. */
    public static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '\"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c >= 0x20) b.append(c);
        }
        return b.append('\"').toString();
    }
}
