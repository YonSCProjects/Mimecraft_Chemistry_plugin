package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.region.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Students may only build on their own plot, and may not break their own wall tiles.
 * Shared regions carve out ONE permission: breaking a listed gather node, which drops the
 * configured item and regrows on a timer. Ops bypass everything.
 */
public class PlotProtection implements Listener {

    private final ChemCraftPlugin plugin;
    public PlotProtection(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;
        Location loc = event.getBlock().getLocation();
        int here = plugin.plots().plotIndexAt(loc);
        int mine = plugin.store().getOrAssignPlotIndex(p.getUniqueId());

        if (here == -1) {
            Region region = plugin.regions().at(loc);
            if (region == null) { event.setCancelled(true); deny(p); return; }
            // Harvest is scoped to the exact node-patch beds - Material alone is NOT enough,
            // or the region's own floor/scenery/underground veins become infinite farms.
            Region.Gather g = region.gather().get(event.getBlock().getType());
            if (g == null || !plugin.regionBuilder().isNodeBlock(region, event.getBlock())) {
                event.setCancelled(true);
                p.sendMessage(Component.text("כאן אפשר לאסוף רק את משאבי האזור המסומנים.", NamedTextColor.RED));
                return;
            }
            harvest(event.getBlock(), g);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }
        if (here != mine) { event.setCancelled(true); deny(p); return; }
        if (plugin.wall().isWallTile(mine, loc)) {
            event.setCancelled(true);
            p.sendMessage(Component.text("זה חלק מהטבלה המחזורית שלכם - אי אפשר לשבור אותו.", NamedTextColor.RED));
            return;
        }
        if (plugin.kiosk().isKioskBlock(mine, loc)) {
            event.setCancelled(true);
            p.sendMessage(Component.text("עמדות החלקה קבועות - אי אפשר לפרק אותן.", NamedTextColor.RED));
        }
    }

    /**
     * Pay out one tick later, and only if the block actually broke - a handler after us may
     * still cancel the event, and paying at NORMAL priority would be irrevocable. The regen
     * restore only fires if the placeholder is still standing (a teacher rebuild wins).
     */
    private void harvest(Block block, Region.Gather g) {
        Material source = block.getType();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()) return; // the break was vetoed after our handler ran
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(g.drop(), g.count()));
            block.setType(g.placeholder());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (block.getType() == g.placeholder()) block.setType(source);
            }, g.regenSeconds() * 20L);
        });
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;
        int here = plugin.plots().plotIndexAt(event.getBlock().getLocation());
        int mine = plugin.store().getOrAssignPlotIndex(p.getUniqueId());
        if (here != mine) { event.setCancelled(true); deny(p); }
    }

    /** Region-scoping the station hijack restores vanilla furnaces -> buckets and fire exist again. */
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;
        Location loc = event.getBlock().getLocation();
        if (!loc.getWorld().equals(plugin.plots().world())) return; // only the game world
        int here = plugin.plots().plotIndexAt(loc);
        int mine = plugin.store().getOrAssignPlotIndex(p.getUniqueId());
        if (here != mine) { event.setCancelled(true); deny(p); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!loc.getWorld().equals(plugin.plots().world())) return; // only the game world
        Player p = event.getPlayer();
        if (p != null) {
            if (p.isOp()) return;
            int here = plugin.plots().plotIndexAt(loc);
            int mine = plugin.store().getOrAssignPlotIndex(p.getUniqueId());
            if (here != mine) { event.setCancelled(true); deny(p); }
        } else if (plugin.regions().at(loc) != null) {
            event.setCancelled(true); // no natural fire spread inside regions
        }
    }

    /** Visitors (/cc visit) may look, not loot: block-backed inventories are owner-only on plots. */
    @EventHandler(ignoreCancelled = true)
    public void onContainerOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p) || p.isOp()) return;
        org.bukkit.inventory.InventoryHolder h = event.getInventory().getHolder();
        Location loc;
        if (h instanceof org.bukkit.inventory.BlockInventoryHolder bih) loc = bih.getBlock().getLocation();
        else if (h instanceof org.bukkit.block.DoubleChest dc) loc = dc.getLocation();
        else return; // plugin GUI menus and entity inventories are unaffected
        if (!loc.getWorld().equals(plugin.plots().world())) return;
        int here = plugin.plots().plotIndexAt(loc);
        if (here != -1 && here != plugin.store().getOrAssignPlotIndex(p.getUniqueId())) {
            event.setCancelled(true);
            deny(p);
        }
    }

    /** Explosions never eat blocks in the game world - they'd bypass every check above and desync AtomStore. */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getLocation().getWorld().equals(plugin.plots().world())) event.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.getBlock().getWorld().equals(plugin.plots().world())) event.blockList().clear();
    }

    private void deny(Player p) {
        p.sendMessage(Component.text("אפשר לבנות רק בחלקה שלכם.", NamedTextColor.RED));
    }
}
