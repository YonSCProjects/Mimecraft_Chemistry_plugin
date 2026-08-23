package com.agurim.chemcraft.extraction;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.ui.Credit;
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

    /** Every loaded recipe (used to answer "where does element X come from?"). */
    public List<Recipe> all() { return recipes; }

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
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            if (e.getKey().startsWith("atom:")) {
                if (countAtoms(player, e.getKey().substring(5)) < e.getValue()) return false;
            } else {
                Material m = Material.matchMaterial(e.getKey());
                if (m == null || countMaterial(player, m) < e.getValue()) return false;
            }
        }
        return true;
    }

    /**
     * Count/remove PLAIN vanilla inputs by Material, skipping anything with a ChemCraft PDC
     * tag (atoms are stained glass - a glass input must never eat them). Count and removal use
     * the same predicate: the old contains()/removeItem() pair diverged on renamed items
     * (isSimilar), letting an anvil-renamed stack pass the check but dodge consumption.
     */
    private int countMaterial(Player player, Material m) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == m && AtomItems.symbolOf(plugin, it) == null) n += it.getAmount();
        }
        return n;
    }

    private void removeMaterial(Player player, Material m, int count) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && count > 0; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() == m && AtomItems.symbolOf(plugin, it) == null) {
                int take = Math.min(count, it.getAmount());
                it.setAmount(it.getAmount() - take);
                count -= take;
            }
        }
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
                removeMaterial(player, Material.matchMaterial(e.getKey()), e.getValue());
            }
        }

        int plot = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        for (Map.Entry<String, Integer> e : r.outputs().entrySet()) {
            Element el = plugin.registry().get(e.getKey());
            if (el == null) continue;
            ItemStack atom = AtomItems.create(plugin, el, e.getValue(), player);
            inv.addItem(atom).values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
            boolean isNew = !plugin.store().isDiscovered(player.getUniqueId(), el.symbol());
            plugin.store().discover(player.getUniqueId(), el.symbol());
            plugin.wall().lightUp(plot, el.symbol());
            if (isNew) {
                player.sendMessage(Component.text("התגלה " + el.name() + " (" + el.symbol() + ") - ", NamedTextColor.GREEN)
                        .append(Component.text(el.fact(), NamedTextColor.WHITE)));
                payDemonstrator(player, el);
            }
            markWitnesses(player, el);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        plugin.missionBar().update(player);
        return true;
    }

    /**
     * The demonstration credit (Path 2 of the cooperation loop): if someone once showed this
     * player the extraction of this element (recorded at witness time), that demonstrator is
     * paid now - when the witness reproduces the experiment as their OWN first discovery.
     * Same +1 as the gift path, so walking a classmate to the volcano pays like handing over
     * the atom - but only teaching also works when the receiver holds nothing.
     */
    private void payDemonstrator(Player player, Element el) {
        String demo = plugin.store().getDemonstrator(player.getUniqueId(), el.symbol());
        if (demo != null) {
            try {
                java.util.UUID helper = java.util.UUID.fromString(demo);
                if (!helper.equals(player.getUniqueId())) {
                    // verb-free phrasing: no gendered verb about either player
                    Credit.pay(plugin, helper, plugin.store().getName(helper),
                            "ההדגמה שלכם עבדה! עכשיו גם ל-" + player.getName() + " יש " + el.name());
                    player.sendMessage(Component.text(
                            "שחזרתם את הניסוי שראיתם - הקרדיט על ההדגמה נרשם ל-" + plugin.store().getName(helper) + ".",
                            NamedTextColor.GOLD));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.store().clearDemonstrator(player.getUniqueId(), el.symbol());
    }

    /**
     * The "seen" half-state: nearby classmates who lack this element watch the experiment.
     * Their wall tile turns yellow with a where-to-reproduce hint, and the extractor is
     * recorded as their demonstrator (first demonstrator wins - re-demoing pays nobody twice).
     * Witnessing grants knowledge only: no items, no credit, nothing farmable by huddling.
     */
    private void markWitnesses(Player extractor, Element el) {
        int radius = plugin.getConfig().getInt("demo.radius", 8);
        if (radius <= 0) return;
        double r2 = (double) radius * radius;
        for (Player w : extractor.getWorld().getPlayers()) {
            if (w.equals(extractor)) continue;
            if (w.getLocation().distanceSquared(extractor.getLocation()) > r2) continue;
            if (plugin.store().isDiscovered(w.getUniqueId(), el.symbol())) continue;
            plugin.store().setDemonstratorIfAbsent(w.getUniqueId(), el.symbol(), extractor.getUniqueId());
            if (plugin.store().markSeen(w.getUniqueId(), el.symbol())) {
                plugin.wall().markSeen(plugin.store().getOrAssignPlotIndex(w.getUniqueId()), el.symbol());
                w.sendMessage(Component.text(
                        "צפיתם בניסוי של " + extractor.getName() + ": " + el.name() + " (" + el.symbol()
                                + "). חזרו עליו בעצמכם כדי להשלים את הגילוי!",
                        NamedTextColor.YELLOW));
            }
        }
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
