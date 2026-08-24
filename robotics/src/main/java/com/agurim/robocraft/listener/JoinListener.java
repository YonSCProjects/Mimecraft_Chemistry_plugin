package com.agurim.robocraft.listener;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.StarterKit;
import com.agurim.robocraft.ui.Guide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Join: a plot, a board, a charging pad, a starter kit, and three lines of guidance.
 *
 * <p>Returning students get the same two facts a first-timer gets - where they are and what is
 * next - because a student who missed a week should not have to reconstruct their own state. That
 * absence-resilience rule is the most valuable thing ChemCraft learned in a real classroom.
 */
public class JoinListener implements Listener {

    private final RoboCraftPlugin plugin;

    public JoinListener(RoboCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        boolean firstTime = !plugin.store().hasPlot(id);
        int plot = plugin.store().getOrAssignPlotIndex(id);

        // offline-mode server: our own name cache is the only trustworthy uuid -> name map
        plugin.store().cacheName(id, player.getName());

        if (!plugin.store().isBoardBuilt(id)) {
            plugin.board().build(plot, id);
            plugin.kiosk().build(plot);
            plugin.store().setBoardBuilt(id, true);
        }

        if (firstTime) {
            player.teleport(plugin.board().arrivalSpot(plot));
            Guide.welcome(player);
            StarterKit.give(plugin, player);
            plugin.store().setKitClaimed(id);
            Guide.firstJoin(plugin, player);
        } else {
            Guide.sendProgress(plugin, player);
        }
        plugin.statusBar().update(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.statusBar().remove(event.getPlayer());
    }
}
