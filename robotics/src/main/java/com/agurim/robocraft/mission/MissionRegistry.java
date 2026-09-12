package com.agurim.robocraft.mission;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads missions.yml, ordered by the `order` field so the ladder is stable. */
public class MissionRegistry {

    private final Map<String, Mission> missions = new LinkedHashMap<>();

    public MissionRegistry(RoboCraftPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "missions.yml");
        if (!file.exists()) plugin.saveResource("missions.yml", false);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<Mission> loaded = new ArrayList<>();
        for (String id : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(id);
            if (s == null) continue;
            loaded.add(new Mission(
                    id,
                    s.getInt("order", 999),
                    s.getBoolean("optional", false),
                    s.getBoolean("bonus", false),
                    s.getString("name", id),
                    s.getString("brief", ""),
                    s.getString("teaches", ""),
                    s.getString("hint", ""),
                    s.getString("site", ""),
                    s.getStringList("quest"),
                    s.getString("value", ""),
                    s.getStringList("needs"),
                    s.getStringList("reward"),
                    s.getInt("start-energy", 0),
                    steps(s)));
        }
        loaded.sort(Comparator.comparingInt(Mission::order));
        for (Mission m : loaded) missions.put(m.id(), m);
    }

    private List<Mission.Step> steps(ConfigurationSection s) {
        List<Mission.Step> out = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("steps")) {
            Map<String, Integer> env = new LinkedHashMap<>();
            Object envRaw = raw.get("env");
            if (envRaw instanceof Map<?, ?> m) {
                m.forEach((k, v) -> { if (v instanceof Number n) env.put(String.valueOf(k), n.intValue()); });
            }
            // feedback: { light: { actuator: lamp, add: 9, max: 15 } }
            Map<String, Mission.Feedback> feedback = new LinkedHashMap<>();
            Object fbRaw = raw.get("feedback");
            if (fbRaw instanceof Map<?, ?> m) {
                m.forEach((k, v) -> {
                    if (v instanceof Map<?, ?> spec) {
                        Object act = spec.get("actuator"), add = spec.get("add"), max = spec.get("max");
                        feedback.put(String.valueOf(k), new Mission.Feedback(
                                act == null ? "" : String.valueOf(act),
                                add instanceof Number n ? n.intValue() : 0,
                                max instanceof Number n ? n.intValue() : Integer.MAX_VALUE));
                    }
                });
            }
            Map<String, String> expect = new LinkedHashMap<>();
            Object expRaw = raw.get("expect");
            if (expRaw instanceof Map<?, ?> m) {
                m.forEach((k, v) -> expect.put(String.valueOf(k), String.valueOf(v)));
            }
            out.add(new Mission.Step(
                    raw.get("say") == null ? "" : String.valueOf(raw.get("say")),
                    env,
                    feedback,
                    raw.get("wait") instanceof Number n ? n.intValue() : 0,
                    expect,
                    raw.get("because") == null ? "" : String.valueOf(raw.get("because")),
                    raw.get("check") == null ? "" : String.valueOf(raw.get("check"))));
        }
        return out;
    }

    public Mission byId(String id)          { return missions.get(id); }
    public java.util.Collection<Mission> all() { return missions.values(); }
    public int size()                       { return missions.size(); }

    /**
     * "1", "W2", "B3" - a mission's number on its own track, which is how the status bar counts
     * and therefore how the list, the card and every clickable reference must count too.
     */
    public String label(Mission m) {
        List<Mission> track = m.warmUp() ? warmUps() : m.bonus() ? bonus() : required();
        String prefix = m.warmUp() ? "W" : m.bonus() ? "B" : "";
        int i = track.indexOf(m);
        return prefix + (i + 1);
    }

    /** The mission whose reward unlocks this part, or null if it is free or unreachable. */
    public Mission unlocking(String partId) {
        for (Mission m : missions.values()) if (m.reward().contains(partId)) return m;
        return null;
    }

    /**
     * The first REQUIRED mission this player has not completed - what progress is counted against.
     * Warm-ups and bonus missions are skipped: they are practice and extras, and a student who
     * ignores them is not behind.
     */
    public Mission nextFor(java.util.Set<String> done) {
        for (Mission m : missions.values()) {
            if (!m.skippable() && !done.contains(m.id())) return m;
        }
        return null;
    }

    public List<Mission> required() { return missions.values().stream().filter(m -> !m.skippable()).toList(); }
    public List<Mission> warmUps()  { return missions.values().stream().filter(Mission::warmUp).toList(); }
    public List<Mission> bonus()    { return missions.values().stream().filter(Mission::bonus).toList(); }

    /** Progress is counted over the required ladder only, so warm-ups never make anyone look behind. */
    public int requiredCount() { return required().size(); }

    public int requiredDone(java.util.Set<String> done) {
        int n = 0;
        for (Mission m : required()) if (done.contains(m.id())) n++;
        return n;
    }

    public int warmUpCount() { return warmUps().size(); }

    public int warmUpsDone(java.util.Set<String> done) {
        int n = 0;
        for (Mission m : warmUps()) if (done.contains(m.id())) n++;
        return n;
    }

    public int bonusCount() { return bonus().size(); }

    public int bonusDone(java.util.Set<String> done) {
        int n = 0;
        for (Mission m : bonus()) if (done.contains(m.id())) n++;
        return n;
    }

    /**
     * What to actually point a student at next - which is not the same question as
     * {@link #nextFor}.
     *
     * <p>{@code nextFor} is the required ladder and nothing else. That is right for counting
     * progress and wrong for telling a beginner where to go: a student who had just finished
     * warm-up 1 was being sent straight past warm-ups 2 and 3 to the required ladder, so the two
     * missions written to give them an easy second and third success were never offered.
     *
     * <p>So while the required ladder is untouched, suggest the next warm-up. The moment they
     * complete a real rung, warm-ups stop being suggested - they are practice, and continuing to
     * nag about optional work is how optional work stops feeling optional.
     */
    public Mission nextSuggested(java.util.Set<String> done) {
        if (requiredDone(done) == 0) {
            for (Mission m : warmUps()) if (!done.contains(m.id())) return m;
        }
        Mission next = nextFor(done);
        if (next != null) return next;
        // The ladder is done, so every part is unlocked (each rung grants its own), and the first
        // undone bonus in order is always buildable. Bonus is only ever suggested here - after
        // the ladder - so a place can never pull a student off the required track.
        for (Mission m : bonus()) if (!done.contains(m.id())) return m;
        return null;
    }

    /**
     * Walk the ladder and complain if a mission needs a part nothing has unlocked yet.
     *
     * <p>A deadlock here is invisible in play - the student is simply told "חסר לרובוט" for a part
     * they have no way to obtain, and there is nothing on screen to suggest the content is at
     * fault rather than them. It happened on the first draft of missions.yml (nothing unlocked
     * the distance sensor or the gate), so it is worth checking on every enable.
     */
    public void validate(RoboCraftPlugin plugin) {
        java.util.Set<String> available = new java.util.LinkedHashSet<>();
        for (com.agurim.robocraft.part.Part p : plugin.parts().all()) {
            if (p.unlockedByDefault()) available.add(p.id());
        }

        for (Mission m : missions.values()) {
            for (String need : m.needs()) {
                if (!plugin.parts().has(need)) {
                    plugin.getLogger().warning("missions.yml: '" + m.id()
                            + "' needs unknown part '" + need + "'");
                } else if (!available.contains(need)) {
                    plugin.getLogger().warning("missions.yml: '" + m.id() + "' needs '" + need
                            + "' but no earlier mission unlocks it - the ladder is stuck here.");
                }
            }
            for (String reward : m.reward()) {
                if (!plugin.parts().has(reward)) {
                    plugin.getLogger().warning("missions.yml: '" + m.id()
                            + "' rewards unknown part '" + reward + "'");
                }
            }
            // Only a REQUIRED mission's rewards count towards what is reachable. Warm-ups and
            // bonus missions are skippable, so anything gated behind one would strand every
            // student who skipped it - and that student would see a part they cannot obtain with
            // no hint that the content is at fault. This check is what keeps them genuinely optional.
            if (!m.skippable()) available.addAll(m.reward());
            else if (!m.reward().isEmpty()) {
                plugin.getLogger().warning("missions.yml: " + (m.bonus() ? "bonus" : "warm-up") + " '"
                        + m.id() + "' grants " + m.reward()
                        + " - a skippable mission must not be the only source of a part.");
            }

            legibility(plugin, m);
            steps(plugin, m);
        }

        for (com.agurim.robocraft.part.Part p : plugin.parts().all()) {
            if (!available.contains(p.id())) {
                plugin.getLogger().info("parts.yml: '" + p.id()
                        + "' is never unlocked by any mission (fine for a bonus part).");
            }
        }
    }

    /**
     * Quest and value text have to fit the chat they are printed into, or the top of the story
     * scrolls off before anyone reads it - the exact failure /rc guide had. Same budget.
     */
    private void legibility(RoboCraftPlugin plugin, Mission m) {
        int width = com.agurim.robocraft.ui.Guide.CHAT_WIDTH;
        if (m.quest().size() > 5) {
            plugin.getLogger().warning("missions.yml: '" + m.id() + "' quest is " + m.quest().size()
                    + " lines - at most 5 fit under the chat's ten with room for the rest.");
        }
        for (String line : m.quest()) {
            if (line.length() > width) {
                plugin.getLogger().warning("missions.yml: '" + m.id() + "' quest line is "
                        + line.length() + " chars, wraps past " + width + ": " + line);
            }
        }
        if (m.value().length() > width) {
            plugin.getLogger().warning("missions.yml: '" + m.id() + "' value is "
                    + m.value().length() + " chars, wraps past " + width + ".");
        }

        // The card. A brief is its one-line goal; a check's situation text has to leave room for
        // the outcome beside it; and the whole card must be on screen at once, or the buttons at
        // the bottom push the goal at the top off before it is read.
        if (m.brief().length() > width) {
            plugin.getLogger().warning("missions.yml: '" + m.id() + "' brief is " + m.brief().length()
                    + " chars - the card's goal line wraps past " + width + ".");
        }
        if (m.hint().isEmpty()) {
            plugin.getLogger().warning("missions.yml: '" + m.id()
                    + "' has no hint - the card's [רמז] button and the failure text would say nothing.");
        }
        for (Mission.Step step : m.steps()) {
            if (step.hasCheck() && step.check().length() > com.agurim.robocraft.ui.MissionCard.CHECK_MAX) {
                plugin.getLogger().warning("missions.yml: '" + m.id() + "' check text is "
                        + step.check().length() + " chars, over " + com.agurim.robocraft.ui.MissionCard.CHECK_MAX
                        + ": " + step.check());
            }
        }
        java.util.Set<String> everyPart = new java.util.HashSet<>();
        for (com.agurim.robocraft.part.Part p : plugin.parts().all()) everyPart.add(p.id());
        int lines = com.agurim.robocraft.ui.MissionCard.plainLines(m, label(m), plugin.parts(),
                java.util.Set.of(), everyPart, false).size();
        if (lines > com.agurim.robocraft.ui.MissionCard.MAX_LINES) {
            plugin.getLogger().warning("missions.yml: '" + m.id() + "' card is " + lines
                    + " lines - more than the chat shows at once (" + com.agurim.robocraft.ui.MissionCard.MAX_LINES + ").");
        }
    }

    /**
     * A misspelt expect key or an impossible value used to pass silently - the bench treated an
     * unknown value as "no opinion". A typo in content should be a log line at enable, not a
     * mission that can never fail.
     */
    private void steps(RoboCraftPlugin plugin, Mission m) {
        java.util.Set<String> actuatorTypes = new java.util.HashSet<>();
        for (com.agurim.robocraft.part.Part p : plugin.parts().all()) {
            if (p.isActuator()) actuatorTypes.add(p.actuator());
        }
        for (Mission.Step step : m.steps()) {
            for (Map.Entry<String, Mission.Feedback> e : step.feedback().entrySet()) {
                if (!com.agurim.robocraft.sense.SensorReader.TYPES.contains(e.getKey())) {
                    plugin.getLogger().warning("missions.yml: '" + m.id() + "' feedback names sensor type '"
                            + e.getKey() + "' which nothing reads.");
                }
                if (!actuatorTypes.contains(e.getValue().actuator())) {
                    plugin.getLogger().warning("missions.yml: '" + m.id() + "' feedback names actuator type '"
                            + e.getValue().actuator() + "' which no part has.");
                }
            }
            for (Map.Entry<String, String> e : step.expect().entrySet()) {
                String key = e.getKey(), want = e.getValue().toLowerCase();
                boolean ok;
                if (key.equals("running")) {
                    ok = want.equals("true") || want.equals("false");
                } else if (key.equals("steady")) {
                    ok = actuatorTypes.contains(e.getValue());
                } else if (key.length() >= 2 && key.charAt(0) == 'M' && Character.isDigit(key.charAt(1))) {
                    ok = want.matches("-?\\d+");
                } else if (actuatorTypes.contains(key)) {
                    ok = want.equals("on") || want.equals("off") || want.equals("true")
                            || want.equals("false") || want.matches("-?\\d+");
                } else {
                    ok = false;
                }
                if (!ok) {
                    plugin.getLogger().warning("missions.yml: '" + m.id() + "' expects '" + key + ": "
                            + e.getValue() + "' - not an actuator type, memory slot, running, or steady; "
                            + "this check could never fail.");
                }
            }
        }
    }
}
