package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

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

    private Location anchor(Location block) { return block.clone().add(0.5, 1.0, 0.5); }

    private void spawnLabel(Location block, String sym) {
        block.getWorld().spawn(anchor(block), TextDisplay.class, td -> {
            td.text(Component.text(sym, NamedTextColor.WHITE));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.getPersistentDataContainer().set(plugin.atomKey(), PersistentDataType.STRING, sym);
        });
    }

    private void removeLabel(Location block, String sym) {
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.atomKey(), PersistentDataType.STRING);
                if (sym.equals(tag)) td.remove();
            }
        }
    }
}
