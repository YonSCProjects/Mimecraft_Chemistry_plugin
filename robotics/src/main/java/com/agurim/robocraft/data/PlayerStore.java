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
 *   &lt;uuid&gt;.layout-parts / .layout-missions -&gt; how much content that wall was built for
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

    /** Every student this plugin has ever seen - the roster the teacher overview is built from. */
    public java.util.List<UUID> allPlayers() {
        java.util.List<UUID> out = new java.util.ArrayList<>();
        for (String key : yml.getKeys(false)) {
            if (key.startsWith("_")) continue;
            try { out.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { }
        }
        return out;
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

    /**
     * Was the board and shelf last built against this much content? False until recorded, so a
     * plot built before the count was tracked reads as stale exactly once.
     *
     * <p>{@code board-built} says a plot has its wall; it does not say the wall is the right
     * size. When a part is appended or a mission added, every plot already built keeps the old
     * tile count for ever - a student on a five-rung server would join a six-rung one and see a
     * shelf with no slot for the new rung, and a board with no tile for the new sensor. The first
     * time that happened it needed {@code board-built: false} set by hand on seven servers.
     * Recording the counts makes the join rebuild the wall by itself when content grows.
     */
    public boolean isLayoutCurrent(UUID id, int parts, int missions) {
        return yml.getInt(id + ".layout-parts", -1) == parts
                && yml.getInt(id + ".layout-missions", -1) == missions;
    }

    public void setLayout(UUID id, int parts, int missions) {
        yml.set(id + ".layout-parts", parts);
        yml.set(id + ".layout-missions", missions);
        save();
    }

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

    /**
     * Grant every reward of every mission this player has already passed. Idempotent.
     *
     * <p>Rewards are handed out at the moment of passing. When a mission's reward list later
     * grows - counter now also unlocks the animal sensor - a student who passed it last term
     * would never be given the new part, and the bonus job that needs it would sit on their list
     * forever with no way to reach it. Run on every join, it costs nothing and closes that gap.
     */
    public int reconcileUnlocks(UUID id, com.agurim.robocraft.mission.MissionRegistry registry) {
        int granted = 0;
        for (String missionId : completedMissions(id)) {
            com.agurim.robocraft.mission.Mission m = registry.byId(missionId);
            if (m == null) continue;
            for (String partId : m.reward()) {
                if (plugin.parts().has(partId) && unlock(id, partId)) granted++;
            }
        }
        return granted;
    }

    // ---- misc --------------------------------------------------------------

    public boolean isKitClaimed(UUID id) { return yml.getBoolean(id + ".kit-claimed", false); }
    public void setKitClaimed(UUID id)   { yml.set(id + ".kit-claimed", true); save(); }

    public void cacheName(UUID id, String name) { yml.set(id + ".name", name); save(); }
    public String getName(UUID id)              { return yml.getString(id + ".name", "?"); }

    /** Drop every record of this id. For the self-test's throwaway players; never for a student. */
    public void forget(UUID id) { yml.set(id.toString(), null); save(); }

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
