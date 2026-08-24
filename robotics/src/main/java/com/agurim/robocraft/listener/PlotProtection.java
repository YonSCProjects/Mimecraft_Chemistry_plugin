package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * You may only build on your own plot.
 *
 * <p>NORMAL priority, so the listeners that must run after protection can use HIGH +
 * ignoreCancelled. Failing is deliberately undramatic - a thunk and a barrier flash so the wall
 * feels solid, and nothing is ever damaged, dropped, or lost. ChemCraft learned that a dramatic
 * punishment turns griefing into a toy for this age group.
 */
public class PlotProtection implements Listener {

    private final RoboCraftPlugin plugin;

    public PlotProtection(RoboCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (blocked(event.getPlayer(), loc)) { event.setCancelled(true); return; }

        // The board and the charging pad are furniture, not loot - protect them on your own plot too.
        int plot = plugin.plots().plotIndexAt(loc);
        if (plot >= 0 && (plugin.board().isBoardTile(plot, loc) || plugin.kiosk().isKioskBlock(plot, loc))) {
            event.setCancelled(true);
            deny(event.getPlayer(), loc, "זה חלק מהסדנה - אי אפשר לפרק אותו.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** True if this player may not touch this location - and tells them so. */
    private boolean blocked(Player player, Location loc) {
        if (player.isOp()) return false;
        int here = plugin.plots().plotIndexAt(loc);
        if (here < 0) return false;                    // gaps and the world outside the grid are free
        int own = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        if (here == own) return false;

        deny(player, loc, "זו הסדנה של מישהו אחר. /rc tp מחזיר אתכם לשלכם.");
        return true;
    }

    private void deny(Player player, Location loc, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
        if (plugin.getConfig().getBoolean("plot-shield.particles", true)) {
            player.spawnParticle(Particle.BLOCK_MARKER, loc.clone().add(0.5, 0.5, 0.5), 1,
                    org.bukkit.Material.BARRIER.createBlockData());
        }
    }
}
