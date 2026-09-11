package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * A paused player cannot move, build, click, or run commands. They can look around and read
 * chat - a pause is "hands off the keyboard", not a blindfold.
 *
 * <p>LOWEST priority, unlike every other listener here: a pause has to be decided before plot
 * protection or the part listeners see the event, so that they - all {@code ignoreCancelled} -
 * simply never run. Commands stay open to teachers, or the teacher who paused themself by name
 * could never type {@code /rc resume}.
 */
public class PauseListener implements Listener {

    private final RoboCraftPlugin plugin;

    public PauseListener(RoboCraftPlugin plugin) { this.plugin = plugin; }

    private boolean frozen(Player p) { return plugin.pause().isPaused(p); }

    /** Cancel and say why, if the player is paused. */
    private boolean block(Player p, Cancellable event) {
        if (!frozen(p)) return false;
        event.setCancelled(true);
        plugin.pause().nudge(p);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition() || !frozen(event.getPlayer())) return;
        // Keep the feet where they are; let the head turn, so the title can be read from
        // anywhere and the student does not feel trapped in a wall.
        Location to = event.getFrom().clone();
        to.setYaw(event.getTo().getYaw());
        to.setPitch(event.getTo().getPitch());
        event.setTo(to);
        plugin.pause().nudge(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) { block(event.getPlayer(), event); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) { block(event.getPlayer(), event); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) { block(event.getPlayer(), event); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) { block(event.getPlayer(), event); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p) block(p, event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p) block(p, event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) { block(event.getPlayer(), event); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) { block(event.getPlayer(), event); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().hasPermission("robocraft.admin")) return;
        block(event.getPlayer(), event);
    }
}
