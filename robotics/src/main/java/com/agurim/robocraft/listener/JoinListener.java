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

        // A mission's reward list can grow after a student has passed it. Give them what they
        // have already earned, and rebuild the board if anything new lit up.
        boolean newlyUnlocked = plugin.store().reconcileUnlocks(id, plugin.missions().registry()) > 0;
        int parts = plugin.parts().size();
        int missions = plugin.missions().registry().all().size();

        if (!plugin.store().isBoardBuilt(id)) {
            plugin.board().build(plot, id);
            plugin.trophies().build(plot, id);
            plugin.kiosk().build(plot);
            plugin.store().setBoardBuilt(id, true);
            plugin.store().setLayout(id, parts, missions);
        } else if (newlyUnlocked || !plugin.store().isLayoutCurrent(id, parts, missions)) {
            // Content grew since this wall went up - a part appended, a mission added - or a
            // tile just lit. Either way the wall is out of date; redraw it. Cheap, idempotent.
            plugin.board().build(plot, id);
            plugin.trophies().build(plot, id);
            plugin.store().setLayout(id, parts, missions);
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
        plugin.pause().remindOnJoin(player);   // joining into a paused room: frozen, and told
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.statusBar().remove(event.getPlayer());
    }
}
