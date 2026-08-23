package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Helper status ranks earned by assists (config ranks.tiers). Status only - a rank never buys
 * progression. Shown as a colored tab-list prefix; rank 2+ unlocks /cc visit.
 */
public final class Ranks {
    private Ranks() {}

    /** Highest tier name whose threshold <= assists, or null for unranked. */
    public static String nameFor(ChemCraftPlugin plugin, int assists) {
        return field(plugin, assists, "name");
    }

    public static NamedTextColor colorFor(ChemCraftPlugin plugin, int assists) {
        String c = field(plugin, assists, "color");
        NamedTextColor color = (c == null) ? null : NamedTextColor.NAMES.value(c.toLowerCase());
        return (color != null) ? color : NamedTextColor.GREEN;
    }

    private static String field(ChemCraftPlugin plugin, int assists, String field) {
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("ranks.tiers");
        if (tiers == null) return null;
        int best = -1;
        String out = null;
        for (String key : tiers.getKeys(false)) {
            int threshold;
            try { threshold = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
            if (threshold <= assists && threshold > best) {
                best = threshold;
                out = tiers.getString(key + "." + field);
            }
        }
        return out;
    }

    /** Apply (or clear) the rank prefix on the tab list. */
    public static void apply(ChemCraftPlugin plugin, Player p, int assists) {
        String name = nameFor(plugin, assists);
        if (name == null) { p.playerListName(null); return; }
        p.playerListName(Component.text("[" + name + "] ", colorFor(plugin, assists))
                .append(Component.text(p.getName(), NamedTextColor.WHITE)));
    }
}
