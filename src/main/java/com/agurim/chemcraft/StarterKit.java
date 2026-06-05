package com.agurim.chemcraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The raw-material kit a player needs to bootstrap the stations, defined in config.yml. */
public final class StarterKit {
    private StarterKit() {}

    /** Give the configured kit. Returns false if kits are disabled or empty. */
    public static boolean give(ChemCraftPlugin plugin, Player player) {
        if (!plugin.getConfig().getBoolean("starter-kit.enabled", true)) return false;
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("starter-kit.items");
        if (items == null) return false;
        boolean gave = false;
        for (String key : items.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            int amount = Math.max(1, items.getInt(key, 1));
            if (mat == null) continue;
            player.getInventory().addItem(new ItemStack(mat, amount)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            gave = true;
        }
        if (gave) {
            player.sendMessage(Component.text(
                    "ערכת פתיחה של חומרי גלם נמצאת במלאי שלכם - קחו אותה לעמדות.",
                    NamedTextColor.GREEN));
        }
        return gave;
    }
}
