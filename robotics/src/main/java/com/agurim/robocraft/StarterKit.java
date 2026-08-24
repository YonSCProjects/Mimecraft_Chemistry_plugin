package com.agurim.robocraft;

import com.agurim.robocraft.part.Part;
import com.agurim.robocraft.part.PartItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * The parts a student is handed on first join (and via {@code /rc kit}).
 *
 * <p>Deliberately exactly enough to build the first mission and nothing more: a controller, a
 * battery, one sensor and one actuator. Handing over the whole catalogue would remove the reason
 * to run a mission at all.
 */
public final class StarterKit {

    private StarterKit() {}

    public static void give(RoboCraftPlugin plugin, Player player) {
        if (!plugin.getConfig().getBoolean("starter-kit.enabled", true)) return;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("starter-kit.parts");
        if (section == null) return;

        int given = 0;
        for (String id : section.getKeys(false)) {
            Part part = plugin.parts().get(id);
            if (part == null) {
                plugin.getLogger().warning("starter-kit: no such part '" + id + "'");
                continue;
            }
            PartItems.give(plugin, player, part, Math.max(1, section.getInt(id, 1)));
            given++;
        }
        if (given > 0) {
            player.sendMessage(Component.text("קיבלתם ערכת פתיחה - בקר, סוללה, חיישן ומפעיל.",
                    NamedTextColor.GREEN));
        }
    }
}
