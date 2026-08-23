package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One always-visible objective per player: "יסודות: 6 / 25 · הבא: ברזל - במכרה".
 *
 * A bossbar, not a scoreboard sidebar: the main-scoreboard SIDEBAR slot is already owned by
 * AssistBoard, and a per-player sidebar would mean per-player Scoreboard objects for no gain.
 *
 * This replaces the old model where progress was shown once, at minute zero, at the moment of
 * least motivation, and thereafter only by typing a command - while the one always-on number in
 * the game was other students' assist scores.
 */
public class MissionBar {

    private final ChemCraftPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public MissionBar(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private boolean enabled() { return plugin.getConfig().getBoolean("mission-bar.enabled", true); }

    /** Show (or refresh) the bar for a player. */
    public void update(Player player) {
        if (!enabled()) return;
        UUID id = player.getUniqueId();
        int found = plugin.store().getDiscovered(id).size();
        int total = plugin.registry().all().size();
        float progress = (total == 0) ? 0f : Math.min(1f, (float) found / total);

        String text = "יסודות: " + found + " / " + total;
        Element next = nextTarget(id);
        if (next != null) text += "  ·  הבא: " + ElementHint.shortWhere(plugin, next);
        else if (found >= total) text += "  ·  הטבלה שלמה!";

        Component name = Component.text(text, NamedTextColor.AQUA);
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
            bars.put(id, bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(progress);
        }
    }

    /**
     * What to point at next: an element they have already WITNESSED (so the hint completes a
     * story they started) before falling back to the lowest undiscovered atomic number.
     */
    public Element nextTarget(UUID id) {
        Element fallback = null;
        for (Element e : plugin.registry().all()) {
            if (plugin.store().isDiscovered(id, e.symbol())) continue;
            if (plugin.store().isSeen(id, e.symbol())) return e;
            if (fallback == null || e.number() < fallback.number()) fallback = e;
        }
        return fallback;
    }

    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
}
