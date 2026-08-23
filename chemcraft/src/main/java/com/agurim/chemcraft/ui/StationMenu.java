package com.agurim.chemcraft.ui;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.element.Families;
import com.agurim.chemcraft.extraction.Recipe;
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

public class StationMenu implements InventoryHolder {

    private final String station;
    private Inventory inventory;

    public StationMenu(String station) { this.station = station; }

    public String station() { return station; }

    public Inventory open(ChemCraftPlugin plugin, Player player) {
        List<Recipe> recipes = plugin.extraction().recipesFor(station);
        int rows = Math.max(1, (int) Math.ceil(recipes.size() / 9.0));
        int size = Math.min(54, rows * 9);
        this.inventory = Bukkit.createInventory(this, size, Component.text(title(), NamedTextColor.DARK_AQUA));

        for (Recipe r : recipes) {
            Element icon = plugin.registry().get(r.icon());
            Material mat = (icon != null) ? Families.material(icon.family()) : Material.GLASS;
            boolean affordable = plugin.extraction().canAfford(player, r);

            ItemStack item = new ItemStack(affordable ? mat : Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(r.label(), affordable ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("צריך: " + plugin.extraction().describeInputs(r), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("נותן: " + describeOutputs(r), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (!affordable) lore.add(Component.text("חסר לכם משהו.", NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(plugin.recipeKey(), PersistentDataType.STRING, r.id());
            item.setItemMeta(meta);
            inventory.addItem(item);
        }
        player.openInventory(inventory);
        return inventory;
    }

    private String describeOutputs(Recipe r) {
        List<String> parts = new ArrayList<>();
        r.outputs().forEach((k, v) -> parts.add(v + "x " + k));
        return String.join(" + ", parts);
    }

    private String title() {
        return switch (station) {
            case "panning"        -> "עמדת ניפוי";
            case "air_separation" -> "מגדל הפרדת אוויר";
            case "smelter"        -> "מתיך";
            case "electrolysis"   -> "מעבדת אלקטרוליזה";
            default               -> "עמדה";
        };
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
