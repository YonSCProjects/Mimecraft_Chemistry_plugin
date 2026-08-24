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
                    s.getString("name", id),
                    s.getString("brief", ""),
                    s.getString("teaches", ""),
                    s.getString("hint", ""),
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
            Map<String, String> expect = new LinkedHashMap<>();
            Object expRaw = raw.get("expect");
            if (expRaw instanceof Map<?, ?> m) {
                m.forEach((k, v) -> expect.put(String.valueOf(k), String.valueOf(v)));
            }
            out.add(new Mission.Step(
                    raw.get("say") == null ? "" : String.valueOf(raw.get("say")),
                    env,
                    raw.get("wait") instanceof Number n ? n.intValue() : 0,
                    expect,
                    raw.get("because") == null ? "" : String.valueOf(raw.get("because"))));
        }
        return out;
    }

    public Mission byId(String id)          { return missions.get(id); }
    public java.util.Collection<Mission> all() { return missions.values(); }
    public int size()                       { return missions.size(); }

    /** The first mission this player has not completed - what /rc missions points at. */
    public Mission nextFor(java.util.Set<String> done) {
        for (Mission m : missions.values()) if (!done.contains(m.id())) return m;
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
            available.addAll(m.reward());
        }

        for (com.agurim.robocraft.part.Part p : plugin.parts().all()) {
            if (!available.contains(p.id())) {
                plugin.getLogger().info("parts.yml: '" + p.id()
                        + "' is never unlocked by any mission (fine for a bonus part).");
            }
        }
    }
}
