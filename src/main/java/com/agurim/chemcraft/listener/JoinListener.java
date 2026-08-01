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
            player.sendMessage(Component.text(
                    "ברוכים הבאים ל-ChemCraft! זו החלקה שלכם - הטבלה המחזורית שלכם על הקיר.",
                    NamedTextColor.GREEN));
            StarterKit.give(plugin, player);
            Guide.send(plugin, player);
        }
    }
}
