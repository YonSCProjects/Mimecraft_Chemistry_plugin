package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.StarterKit;
import com.agurim.chemcraft.ui.Guide;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class JoinListener implements Listener {

    private final ChemCraftPlugin plugin;
    public JoinListener(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean firstTime = !plugin.store().hasPlot(id);
        int plot = plugin.store().getOrAssignPlotIndex(id);

        // offline-mode server: our own name cache is the only trustworthy uuid->name map
        plugin.store().cacheName(id, player.getName());
        com.agurim.chemcraft.ui.Ranks.apply(plugin, player, plugin.store().getAssists(id));
        int pending = plugin.store().flushPendingAssists(id);
        if (pending > 0) {
            player.sendMessage(Component.text(
                    "בזמן שלא הייתם: עזרתם ל-" + pending + " חברים לגלות יסודות!", NamedTextColor.GOLD));
        }

        if (!plugin.store().isWallBuilt(id)) {
            plugin.wall().build(plot);
            for (String sym : plugin.store().getDiscovered(id)) plugin.wall().lightUp(plot, sym);
            plugin.kiosk().build(plot);
            plugin.store().setWallBuilt(id, true);
        }

        if (firstTime) {
            plugin.plots().teleportToPlot(player, plot);
            Guide.welcome(player);
            StarterKit.give(plugin, player);
            Guide.firstJoin(plugin, player);
        } else {
            // Absence resilience: a student returning after a week gets the same two facts a
            // first-timer gets - where they are, and what to do next. The old build taught
            // nothing at all on any join after the first.
            Guide.sendProgress(plugin, player);
            com.agurim.chemcraft.element.Element next = plugin.missionBar().nextTarget(id);
            if (next != null) {
                player.sendMessage(Component.text("הבא בתור: " + next.name() + " (" + next.symbol() + ") - "
                        + com.agurim.chemcraft.ui.ElementHint.where(plugin, next), NamedTextColor.YELLOW));
            }
        }
        plugin.missionBar().update(player);
    }
}
