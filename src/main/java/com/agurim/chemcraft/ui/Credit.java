package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The single place an assist is paid: score, sidebar, chime (or offline banking), rank
 * refresh, and a rank-up announcement. Both credit paths - gift registration
 * (WallRegisterListener) and demonstration reproduced (ExtractionService) - route here,
 * so their payouts can never drift apart.
 */
public final class Credit {
    private Credit() {}

    public static void pay(ChemCraftPlugin plugin, UUID helper, String helperName, String helperMessage) {
        int assists = plugin.store().addAssist(helper);
        plugin.assistBoard().update(helper, helperName, assists);
        plugin.getLogger().info("[assist] +1 " + helperName + " (total " + assists + ")");

        Player online = Bukkit.getPlayer(helper);
        if (online != null) {
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            online.sendMessage(Component.text(helperMessage + " (" + assists + " עזרות)", NamedTextColor.GOLD));
            Ranks.apply(plugin, online, assists);
        } else {
            plugin.store().addPendingAssist(helper);
        }

        String prev = Ranks.nameFor(plugin, assists - 1);
        String now = Ranks.nameFor(plugin, assists);
        if (now != null && !now.equals(prev)) {
            Bukkit.getServer().sendMessage(Component.text(
                    "דרגה חדשה: " + helperName + " - " + now + "!", NamedTextColor.LIGHT_PURPLE));
        }
    }
}
