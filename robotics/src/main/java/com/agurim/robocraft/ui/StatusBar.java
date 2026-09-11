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
    public record Status(String text, float progress, boolean warmUp, boolean bonus) {}

    /**
     * The bar's contents, with no Bukkit in sight - {@code SelfTest} drives this directly.
     *
     * <p>Three tracks, in the order a student meets them. The warm-up track exists because the
     * required ladder alone made a student's first success invisible: finishing a warm-up moved
     * nothing, since {@link com.agurim.robocraft.mission.MissionRegistry#requiredDone}
     * deliberately does not count them. The bonus track appears only once the ladder is done, so
     * the bar can never point past a required rung at something optional.
     */
    public static Status compute(String nextName, boolean nextIsWarmUp, boolean nextIsBonus,
                                 int warmDone, int warmTotal, int reqDone, int reqTotal,
                                 int bonusDone, int bonusTotal) {
        if (nextName == null) {
            return new Status("כל המשימות הושלמו! בנו מנגנון משלכם", 1f, false, false);
        }
        if (nextIsWarmUp && warmTotal > 0) {
            return new Status("חימום " + (warmDone + 1) + "/" + warmTotal + ": " + nextName,
                    (float) warmDone / warmTotal, true, false);
        }
        if (nextIsBonus && bonusTotal > 0) {
            return new Status("בונוס " + (bonusDone + 1) + "/" + bonusTotal + ": " + nextName,
                    (float) bonusDone / bonusTotal, false, true);
        }
        int total = Math.max(1, reqTotal);
        return new Status("משימה " + (reqDone + 1) + "/" + total + ": " + nextName,
                Math.min(1f, (float) reqDone / total), false, false);
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("status-bar.enabled", true)) return;

        UUID id = player.getUniqueId();
        var registry = plugin.missions().registry();
        var done = plugin.store().completedMissions(id);
        Mission next = registry.nextSuggested(done);

        Status status = compute(
                next == null ? null : next.name(),
                next != null && next.warmUp(),
                next != null && next.bonus(),
                registry.warmUpsDone(done), registry.warmUpCount(),
                registry.requiredDone(done), registry.requiredCount(),
                registry.bonusDone(done), registry.bonusCount());

        // A different colour per track, so moving from warm-ups to the ladder to the bonus jobs is
        // a visible promotion rather than a number quietly changing.
        BossBar.Color color = status.warmUp() ? BossBar.Color.YELLOW
                : status.bonus() ? BossBar.Color.GREEN : BossBar.Color.BLUE;

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
