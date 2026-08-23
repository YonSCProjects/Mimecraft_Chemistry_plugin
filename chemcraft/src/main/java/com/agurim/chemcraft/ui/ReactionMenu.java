package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.reaction.Reaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ReactionMenu implements InventoryHolder {

    private Inventory inventory;

    public Inventory open(ChemCraftPlugin plugin, Player player) {
        List<Reaction> rs = plugin.reactions().all();
        int rows = Math.max(1, (int) Math.ceil(rs.size() / 9.0));
        this.inventory = Bukkit.createInventory(this, Math.min(54, rows * 9), Component.text("כור", NamedTextColor.DARK_RED));
        for (Reaction r : rs) {
            boolean ok = plugin.reactions().canAfford(player, r);
            ItemStack item = new ItemStack(ok ? Material.GLASS_BOTTLE : Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(r.label(), ok ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(plugin.reactions().equation(r), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (!ok) lore.add(Component.text("אין לכם עדיין את החומרים המגיבים.", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(plugin.reactionKey(), PersistentDataType.STRING, r.id());
            item.setItemMeta(meta);
            inventory.addItem(item);
        }
        player.openInventory(inventory);
        return inventory;
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
