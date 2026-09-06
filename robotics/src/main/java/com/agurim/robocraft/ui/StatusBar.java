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

    /** What the bar should say and how full it should be. Data, so it can be tested. */
    public record Status(String text, float progress, boolean warmUp) {}

    /**
     * The bar's contents, with no Bukkit in sight - {@code SelfTest} drives this directly.
     *
     * <p>The warm-up track exists because the required ladder alone made a student's first
     * success invisible: finishing a warm-up moved nothing, since {@link
     * com.agurim.robocraft.mission.MissionRegistry#requiredDone} deliberately does not count them.
     * A beginner would complete their first mission and watch the bar stay exactly where it was.
     * So while they are still on the warm-ups, the bar counts warm-ups.
     */
    public static Status compute(String nextName, boolean nextIsWarmUp,
                                 int warmDone, int warmTotal, int reqDone, int reqTotal) {
        if (nextName == null) {
            return new Status("כל המשימות הושלמו! בנו מנגנון משלכם", 1f, false);
        }
        if (nextIsWarmUp && warmTotal > 0) {
            return new Status("חימום " + (warmDone + 1) + "/" + warmTotal + ": " + nextName,
                    (float) warmDone / warmTotal, true);
        }
        int total = Math.max(1, reqTotal);
        return new Status("משימה " + (reqDone + 1) + "/" + total + ": " + nextName,
                Math.min(1f, (float) reqDone / total), false);
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("status-bar.enabled", true)) return;

        UUID id = player.getUniqueId();
        var registry = plugin.missions().registry();
        var done = plugin.store().completedMissions(id);
        Mission next = registry.nextSuggested(done);

        Status status = compute(
                next == null ? null : next.name(),
                next != null && next.optional(),
                registry.warmUpsDone(done), registry.warmUpCount(),
                registry.requiredDone(done), registry.requiredCount());

        // A different colour for the warm-up track, so the switch to the real ladder is a visible
        // promotion rather than a number quietly changing.
        BossBar.Color color = status.warmUp() ? BossBar.Color.YELLOW : BossBar.Color.BLUE;

        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = BossBar.bossBar(Component.text(status.text(), NamedTextColor.AQUA),
                    status.progress(), color, BossBar.Overlay.PROGRESS);
            bars.put(id, bar);
            player.showBossBar(bar);
        } else {
            bar.name(Component.text(status.text(), NamedTextColor.AQUA));
            bar.progress(status.progress());
            bar.color(color);
        }
    }

    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
}
