package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;

/**
 * The shared "עוזרים מובילים" sidebar: one main-scoreboard objective scored by assists.
 * Cosmetic-only status - assists never buy progression. Known limit: a sidebar shows the
 * top ~15 entries; accepted at classroom scale.
 */
public class AssistBoard {

    private static final String OBJECTIVE = "cc_assists";
    private final ChemCraftPlugin plugin;

    public AssistBoard(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private boolean enabled() { return plugin.getConfig().getBoolean("assist-board.enabled", true); }

    private Objective objective() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Component.text(plugin.getConfig().getString("assist-board.title", "עוזרים מובילים"),
                            NamedTextColor.GOLD));
        }
        return obj;
    }

    /** Create the objective and load every saved score. Call on enable. */
    public void init() {
        if (!enabled()) return;
        Objective obj = objective();
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Map.Entry<UUID, Integer> e : plugin.store().allAssists().entrySet()) {
            obj.getScore(plugin.store().getName(e.getKey())).setScore(e.getValue());
        }
    }

    public void update(UUID helper, String helperName, int assists) {
        if (!enabled()) return;
        objective().getScore(helperName).setScore(assists);
        plugin.store().cacheName(helper, helperName);
    }
}
