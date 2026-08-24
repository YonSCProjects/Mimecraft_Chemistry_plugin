package com.agurim.robocraft.data;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.part.Part;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player progress, persisted to players.yml:
 *   &lt;uuid&gt;.plot         -&gt; assigned plot index
 *   &lt;uuid&gt;.board-built   -&gt; has their Component Board been spawned
 *   &lt;uuid&gt;.unlocked      -&gt; part ids unlocked by mission rewards
 *   &lt;uuid&gt;.missions      -&gt; completed mission ids
 *   &lt;uuid&gt;.kit-claimed   -&gt; has the one-time starter kit been taken
 *   &lt;uuid&gt;.name          -&gt; last seen name (offline-mode server: our cache is the only map we trust)
 *   _meta.next-plot      -&gt; next free plot index
 */
public class PlayerStore {

    private final RoboCraftPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public PlayerStore(RoboCraftPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasPlot(UUID id) { return yml.contains(id + ".plot"); }

    public int getOrAssignPlotIndex(UUID id) {
        if (hasPlot(id)) return yml.getInt(id + ".plot");
        int next = yml.getInt("_meta.next-plot", 0);
        yml.set(id + ".plot", next);
        yml.set("_meta.next-plot", next + 1);
        save();
        return next;
    }

    public UUID uuidByPlot(int plot) {
        for (String key : yml.getKeys(false)) {
            if (key.startsWith("_")) continue;
            if (yml.getInt(key + ".plot", -1) == plot) {
                try { return UUID.fromString(key); } catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }

    public boolean isBoardBuilt(UUID id)          { return yml.getBoolean(id + ".board-built", false); }
    public void setBoardBuilt(UUID id, boolean v) { yml.set(id + ".board-built", v); save(); }

    // ---- parts -------------------------------------------------------------

    /** Every part this player may draw: the always-available ones plus whatever missions unlocked. */
    public Set<String> unlocked(UUID id) {
        Set<String> out = new LinkedHashSet<>();
        for (Part p : plugin.parts().all()) if (p.unlockedByDefault()) out.add(p.id());
        out.addAll(yml.getStringList(id + ".unlocked"));
        return out;
    }

    public boolean isUnlocked(UUID id, String partId) {
        Part p = plugin.parts().get(partId);
        return (p != null && p.unlockedByDefault()) || yml.getStringList(id + ".unlocked").contains(partId);
    }

    /** Returns true if this was newly unlocked (so the caller can announce it). */
    public boolean unlock(UUID id, String partId) {
        if (isUnlocked(id, partId)) return false;
        Set<String> set = new LinkedHashSet<>(yml.getStringList(id + ".unlocked"));
        set.add(partId);
        yml.set(id + ".unlocked", new ArrayList<>(set));
        save();
        return true;
    }

    // ---- missions ----------------------------------------------------------

    public Set<String> completedMissions(UUID id) {
        return new LinkedHashSet<>(yml.getStringList(id + ".missions"));
    }

    public boolean isMissionDone(UUID id, String missionId) {
        return yml.getStringList(id + ".missions").contains(missionId);
    }

    public void completeMission(UUID id, String missionId) {
        Set<String> set = completedMissions(id);
        if (set.add(missionId)) {
            yml.set(id + ".missions", new ArrayList<>(set));
            save();
        }
    }

    // ---- misc --------------------------------------------------------------

    public boolean isKitClaimed(UUID id) { return yml.getBoolean(id + ".kit-claimed", false); }
    public void setKitClaimed(UUID id)   { yml.set(id + ".kit-claimed", true); save(); }

    public void cacheName(UUID id, String name) { yml.set(id + ".name", name); save(); }
    public String getName(UUID id)              { return yml.getString(id + ".name", "?"); }

    /** Wipe progress but keep the plot, so a reset student stays where their build is. */
    public void reset(UUID id) {
        yml.set(id + ".unlocked", new ArrayList<String>());
        yml.set(id + ".missions", new ArrayList<String>());
        yml.set(id + ".board-built", false);
        save();
    }

    public void save() {
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save players.yml: " + e.getMessage()); }
    }
}
