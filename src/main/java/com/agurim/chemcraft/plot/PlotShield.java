package com.agurim.chemcraft.plot;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The plot shield: what happens when a student tries to touch someone else's plot.
 *
 * Design rules, deliberately chosen for 11-14 year olds:
 *  - The attempt ALREADY fails; this only changes how failing feels. So it must never be
 *    entertaining, or blocking becomes a toy that kids trigger on purpose.
 *  - Feedback is a solid "thunk" and a barrier flash, so protection feels real rather than
 *    like a dead click. That is feedback, not punishment.
 *  - Escalation is BORING: repeated attempts in a short window just send you home. Nothing
 *    is damaged, dropped, or lost, and it is dull to trigger twice.
 *  - Everything is counted and logged, because the lever that actually changes behaviour in a
 *    classroom is the teacher seeing it (`/cc report`), not the game retaliating.
 */
public class PlotShield {

    private final ChemCraftPlugin plugin;
    /** Recent blocked-attempt timestamps per player (in-memory; a restart forgives). */
    private final Map<UUID, Deque<Long>> recent = new HashMap<>();

    public PlotShield(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private boolean enabled()    { return plugin.getConfig().getBoolean("plot-shield.enabled", true); }
    private boolean particles()  { return plugin.getConfig().getBoolean("plot-shield.particles", true); }
    private int escalateAfter()  { return Math.max(2, plugin.getConfig().getInt("plot-shield.escalate-after", 4)); }
    private long windowMillis()  { return Math.max(5, plugin.getConfig().getInt("plot-shield.window-seconds", 30)) * 1000L; }

    /**
     * Called for every blocked attempt on ANOTHER student's plot.
     *
     * @param what short Hebrew noun for what they tried, used in the message and the log
     */
    public void blocked(Player player, Location loc, int targetPlot, String what) {
        if (!enabled()) {
            player.sendMessage(Component.text("אפשר לבנות רק בחלקה שלכם.", NamedTextColor.RED));
            return;
        }

        String ownerName = ownerName(targetPlot);
        player.sendMessage(Component.text("החלקה של " + ownerName + " מוגנת - " + what + " לא אפשרי כאן.",
                NamedTextColor.RED));

        if (particles() && loc.getWorld() != null) {
            Location c = loc.clone().add(0.5, 0.5, 0.5);
            loc.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, c, 3, 0.25, 0.25, 0.25);
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, c, 12, 0.3, 0.3, 0.3, 0.02);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.7f);

        int total = plugin.store().addShieldBlock(player.getUniqueId(), ownerName);
        plugin.getLogger().info("[shield] " + player.getName() + " -> plot " + targetPlot + " (" + ownerName + ") "
                + what + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + " | lifetime " + total);

        if (countRecent(player.getUniqueId()) >= escalateAfter()) {
            sendHome(player);
        }
    }

    /** Record this attempt and return how many happened inside the rolling window. */
    private int countRecent(UUID id) {
        long now = System.currentTimeMillis();
        Deque<Long> q = recent.computeIfAbsent(id, k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > windowMillis()) q.pollFirst();
        return q.size();
    }

    /**
     * The escalation, deliberately undramatic: back to your own plot, nothing lost.
     * Clears the counter so returning starts from zero rather than compounding.
     */
    private void sendHome(Player player) {
        recent.remove(player.getUniqueId());
        int mine = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        plugin.plots().teleportToPlot(player, mine);
        player.sendMessage(Component.text("החומה מוגנת - חזרתם לחלקה שלכם. בנו על שלכם, ועזרו לחברים בדרכים אחרות.",
                NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        plugin.getLogger().info("[shield] " + player.getName() + " escalated -> sent home to plot " + mine);
    }

    private String ownerName(int plotIndex) {
        UUID owner = plugin.store().uuidByPlot(plotIndex);
        return (owner != null) ? plugin.store().getName(owner) : "חבר/ה";
    }
}
