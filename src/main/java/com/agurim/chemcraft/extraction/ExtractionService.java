package com.agurim.chemcraft.extraction;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExtractionService {

    private final ChemCraftPlugin plugin;
    private final List<Recipe> recipes = new ArrayList<>();

    public ExtractionService(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "extraction.yml");
        if (!file.exists()) plugin.saveResource("extraction.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yml.getMapList("recipes")) {
            Map<String, Integer> in = toCountMap(raw.get("inputs"));
            Map<String, Integer> out = toCountMap(raw.get("outputs"));
            recipes.add(new Recipe(
                    String.valueOf(raw.get("id")),
                    String.valueOf(raw.get("station")),
                    String.valueOf(raw.get("icon")),
                    String.valueOf(raw.get("label")),
                    in, out));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> toCountMap(Object o) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
            }
        }
        return map;
    }

    public List<Recipe> recipesFor(String station) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe r : recipes) if (r.station().equals(station)) out.add(r);
        return out;
    }

    public Recipe byId(String id) {
        for (Recipe r : recipes) if (r.id().equals(id)) return r;
        return null;
    }

    /** Does the player have everything this recipe needs? */
    public boolean canAfford(Player player, Recipe r) {
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            if (e.getKey().startsWith("atom:")) {
                if (countAtoms(player, e.getKey().substring(5)) < e.getValue()) return false;
            } else {
                Material m = Material.matchMaterial(e.getKey());
                if (m == null || !inv.contains(m, e.getValue())) return false;
            }
        }
        return true;
    }

    /** Consume inputs, give output atoms, mark discovered, light the wall, play feedback. */
    public boolean extract(Player player, Recipe r) {
        if (!canAfford(player, r)) {
            player.sendMessage(Component.text("אין לכם את מה שצריך: " + describeInputs(r), NamedTextColor.RED));
            return false;
        }
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            if (e.getKey().startsWith("atom:")) {
                removeAtoms(player, e.getKey().substring(5), e.getValue());
            } else {
                inv.removeItem(new ItemStack(Material.matchMaterial(e.getKey()), e.getValue()));
            }
        }

        int plot = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        for (Map.Entry<String, Integer> e : r.outputs().entrySet()) {
            Element el = plugin.registry().get(e.getKey());
            if (el == null) continue;
            ItemStack atom = AtomItems.create(plugin, el, e.getValue());
            inv.addItem(atom).values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
            boolean isNew = !plugin.store().isDiscovered(player.getUniqueId(), el.symbol());
            plugin.store().discover(player.getUniqueId(), el.symbol());
            plugin.wall().lightUp(plot, el.symbol());
            if (isNew) {
                player.sendMessage(Component.text("התגלה " + el.name() + " (" + el.symbol() + ") - ", NamedTextColor.GREEN)
                        .append(Component.text(el.fact(), NamedTextColor.WHITE)));
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        return true;
    }

    public int countAtoms(Player player, String symbol) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (symbol.equals(AtomItems.symbolOf(plugin, it))) n += it.getAmount();
        }
        return n;
    }

    private void removeAtoms(Player player, String symbol, int count) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && count > 0; i++) {
            ItemStack it = contents[i];
            if (symbol.equals(AtomItems.symbolOf(plugin, it))) {
                int take = Math.min(count, it.getAmount());
                it.setAmount(it.getAmount() - take);
                count -= take;
            }
        }
    }

    public String describeInputs(Recipe r) {
        if (r.inputs().isEmpty()) return "(כלום)";
        List<String> parts = new ArrayList<>();
        r.inputs().forEach((k, v) -> parts.add(v + "x " + (k.startsWith("atom:") ? "אטום " + k.substring(5) : k.toLowerCase())));
        return String.join(", ", parts);
    }
}
