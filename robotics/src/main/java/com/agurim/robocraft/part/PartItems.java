package com.agurim.robocraft.part;

import com.agurim.robocraft.RoboCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Parts as items: the part's own block, PDC-tagged so placing it can be recognised, plus a
 * custom_model_data slot for a future resource pack (parts base + index, like ChemCraft's atoms).
 */
public final class PartItems {

    private PartItems() {}

    @SuppressWarnings("deprecation") // setCustomModelData(int) is deprecated in 1.21.4 but still works
    public static ItemStack of(RoboCraftPlugin plugin, Part part, int amount) {
        ItemStack item = new ItemStack(part.block(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(part.name(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(kindLabel(part), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (!part.range().isEmpty()) {
            lore.add(Component.text("טווח: " + part.range(), NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (part.capacity() > 0) {
            lore.add(Component.text("קיבולת: " + part.capacity(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (part.drain() > 0) {
            lore.add(Component.text("צריכה: " + part.drain(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (!part.hint().isEmpty()) {
            lore.add(Component.text(part.hint(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(plugin.partKey(), PersistentDataType.STRING, part.id());
        meta.setCustomModelData(plugin.getConfig().getInt("parts.base-model-data", 9000) + index(plugin, part));
        item.setItemMeta(meta);
        return item;
    }

    /** The part id carried by this item, or null if it is just a vanilla block. */
    public static String idOf(RoboCraftPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.partKey(), PersistentDataType.STRING);
    }

    public static void give(RoboCraftPlugin plugin, Player player, Part part, int amount) {
        player.getInventory().addItem(of(plugin, part, amount))
                .values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    private static String kindLabel(Part part) {
        if (part.isController()) return "בקר";
        if (part.isBattery())    return "מקור אנרגיה";
        if (part.isSolar())      return "מקור אנרגיה";
        if (part.isSensor())     return "חיישן - קלט";
        if (part.isActuator())   return "מפעיל - פלט";
        return "רכיב";
    }

    private static int index(RoboCraftPlugin plugin, Part part) {
        int i = 0;
        for (Part p : plugin.parts().all()) {
            if (p.id().equals(part.id())) return i;
            i++;
        }
        return 0;
    }
}
