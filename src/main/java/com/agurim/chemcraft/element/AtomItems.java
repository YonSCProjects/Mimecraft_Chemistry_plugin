package com.agurim.chemcraft.element;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class AtomItems {

    private AtomItems() {}

    public static ItemStack create(ChemCraftPlugin plugin, Element e, int amount) {
        ItemStack item = new ItemStack(Families.material(e.family()), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("אטום " + e.name() + " (" + e.symbol() + ")", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("יסוד #" + e.number() + "  -  רוצה " + e.valence() + " קשרים", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(e.fact(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(plugin.atomKey(), PersistentDataType.STRING, e.symbol());
        int base = plugin.getConfig().getInt("atoms.base-model-data", 7000);
        meta.setCustomModelData(base + e.number());
        item.setItemMeta(meta);
        return item;
    }

    public static String symbolOf(ChemCraftPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(plugin.atomKey(), PersistentDataType.STRING);
    }

    public static int countIn(ChemCraftPlugin plugin, Player player, String symbol) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) if (symbol.equals(symbolOf(plugin, it))) n += it.getAmount();
        return n;
    }

    public static void removeFrom(ChemCraftPlugin plugin, Player player, String symbol, int count) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (count <= 0) break;
            if (symbol.equals(symbolOf(plugin, it))) { int t = Math.min(count, it.getAmount()); it.setAmount(it.getAmount() - t); count -= t; }
        }
    }
}
