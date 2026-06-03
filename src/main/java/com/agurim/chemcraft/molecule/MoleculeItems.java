package com.agurim.chemcraft.molecule;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** A built molecule, captured as a bottled sample item (used as reaction input). Resource-pack ready. */
public final class MoleculeItems {

    private MoleculeItems() {}

    public static ItemStack create(ChemCraftPlugin plugin, Molecule m, int amount) {
        ItemStack item = new ItemStack(Material.GLASS_BOTTLE, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(m.name() + " (" + m.display() + ")", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(m.fact(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(plugin.moleculeKey(), PersistentDataType.STRING, m.id());
        int base = plugin.getConfig().getInt("molecules.base-model-data", 8000);
        meta.setCustomModelData(base + Math.floorMod(m.id().hashCode(), 1000));
        item.setItemMeta(meta);
        return item;
    }

    public static String idOf(ChemCraftPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(plugin.moleculeKey(), PersistentDataType.STRING);
    }

    public static int count(ChemCraftPlugin plugin, Player player, String id) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) if (id.equals(idOf(plugin, it))) n += it.getAmount();
        return n;
    }

    public static void remove(ChemCraftPlugin plugin, Player player, String id, int count) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (count <= 0) break;
            if (id.equals(idOf(plugin, it))) { int t = Math.min(count, it.getAmount()); it.setAmount(it.getAmount() - t); count -= t; }
        }
    }
}
