package com.agurim.robocraft.ui;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A per-player bossbar carrying the one always-visible objective, e.g.
 * "משימה 2/6: אזעקה". It replaces the text dump a first-time student would otherwise
 * have to remember, and it is the piece that makes coming back after a week's absence painless.
 */
public class StatusBar {

    private final RoboCraftPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public StatusBar(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("status-bar.enabled", true)) return;

        UUID id = player.getUniqueId();
        int done = plugin.store().completedMissions(id).size();
        int total = Math.max(1, plugin.missions().registry().size());
        Mission next = plugin.missions().registry().nextFor(plugin.store().completedMissions(id));

        String text = (next == null)
                ? "כל המשימות הושלמו! בנו מנגנון משלכם"
                : "משימה " + (done + 1) + "/" + total + ": " + next.name();

        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = BossBar.bossBar(Component.text(text, NamedTextColor.AQUA),
                    (float) done / total, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            bars.put(id, bar);
            player.showBossBar(bar);
        } else {
            bar.name(Component.text(text, NamedTextColor.AQUA));
            bar.progress(Math.min(1f, (float) done / total));
        }
    }

    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
}
