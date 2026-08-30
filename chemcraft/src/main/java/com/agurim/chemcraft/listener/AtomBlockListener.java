package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class AtomBlockListener implements Listener {

    private final ChemCraftPlugin plugin;
    public AtomBlockListener(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String sym = AtomItems.symbolOf(plugin, event.getItemInHand());
        if (sym == null) return;
        Location loc = event.getBlockPlaced().getLocation();
        plugin.atoms().put(loc, sym);
        com.agurim.chemcraft.world.AtomLabels.refresh(plugin, loc);
        // a completed lattice takes priority; otherwise evaluate the molecule
        if (!plugin.materials().check(loc, event.getPlayer())) {
            plugin.moleculeEngine().evaluate(loc, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        String sym = plugin.atoms().get(loc);
        if (sym == null) return;
        Player player = event.getPlayer();
        Element e = plugin.registry().get(sym);
        event.setDropItems(false);
        plugin.atoms().remove(loc);
        com.agurim.chemcraft.world.AtomLabels.remove(plugin, loc, sym);
        if (e != null) loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), AtomItems.create(plugin, e, 1));
        plugin.moleculeEngine().onAtomRemoved(loc, player);
    }

}
