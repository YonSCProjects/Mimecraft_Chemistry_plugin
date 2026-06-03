package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** Students may only build on their own plot, and may not break their own wall tiles. Ops bypass. */
public class PlotProtection implements Listener {

    private final ChemCraftPlugin plugin;
    public PlotProtection(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;
        int here = plugin.plots().plotIndexAt(event.getBlock().getLocation());
        int mine = plugin.store().getOrAssignPlotIndex(p.getUniqueId());
        if (here != mine) { event.setCancelled(true); deny(p); return; }
        if (plugin.wall().isWallTile(mine, event.getBlock().getLocation())) {
            event.setCancelled(true);
            p.sendMessage(Component.text("That's part of your periodic table - you can't break it.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;
        int here = plugin.plots().plotIndexAt(event.getBlock().getLocation());
        int mine = plugin.store().getOrAssignPlotIndex(p.getUniqueId());
        if (here != mine) { event.setCancelled(true); deny(p); }
    }

    private void deny(Player p) {
        p.sendMessage(Component.text("You can only build on your own plot.", NamedTextColor.RED));
    }
}
