package com.agurim.chemcraft.reaction;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.molecule.Molecule;
import com.agurim.chemcraft.molecule.MoleculeItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReactionService {

    private final ChemCraftPlugin plugin;
    private final List<Reaction> reactions = new ArrayList<>();

    public ReactionService(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "reactions.yml");
        if (!file.exists()) plugin.saveResource("reactions.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yml.getMapList("reactions")) {
            reactions.add(new Reaction(
                    String.valueOf(raw.get("id")),
                    String.valueOf(raw.get("label")),
                    toCountMap(raw.get("inputs")),
                    toCountMap(raw.get("outputs")),
                    str(raw, "effect", "none"),
                    str(raw, "fact", "")));
        }
    }

    // Wildcard-typed YAML maps can't use getOrDefault with a typed default, so read + null-check here.
    private static String str(Map<?, ?> raw, String key, String def) {
        Object v = raw.get(key);
        return (v != null) ? String.valueOf(v) : def;
    }

    private Map<String, Integer> toCountMap(Object o) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) m.forEach((k, v) -> map.put(String.valueOf(k), ((Number) v).intValue()));
        return map;
    }

    public List<Reaction> all() { return reactions; }

    public Reaction byId(String id) {
        for (Reaction r : reactions) if (r.id().equals(id)) return r;
        return null;
    }

    public boolean canAfford(Player player, Reaction r) {
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            if (e.getKey().startsWith("atom:")) {
                if (AtomItems.countIn(plugin, player, e.getKey().substring(5)) < e.getValue()) return false;
            } else if (MoleculeItems.count(plugin, player, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public boolean react(Player player, Reaction r) {
        if (!canAfford(player, r)) {
            player.sendMessage(Component.text("You need: " + describeSide(r.inputs()), NamedTextColor.RED));
            return false;
        }
        for (Map.Entry<String, Integer> e : r.inputs().entrySet()) {
            if (e.getKey().startsWith("atom:")) AtomItems.removeFrom(plugin, player, e.getKey().substring(5), e.getValue());
            else MoleculeItems.remove(plugin, player, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : r.outputs().entrySet()) {
            if (e.getKey().startsWith("atom:")) {
                Element el = plugin.registry().get(e.getKey().substring(5));
                if (el != null) player.getInventory().addItem(AtomItems.create(plugin, el, e.getValue()))
                        .values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
            } else {
                Molecule m = plugin.moleculeRegistry().byId(e.getKey());
                if (m != null) player.getInventory().addItem(MoleculeItems.create(plugin, m, e.getValue()))
                        .values().forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
            }
        }
        playEffect(player, r.effect());
        player.sendMessage(Component.text("Reaction! ", NamedTextColor.GOLD)
                .append(Component.text(equation(r), NamedTextColor.YELLOW)));
        if (!r.fact().isEmpty()) player.sendMessage(Component.text(r.fact(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("Mass is conserved - the same atoms, just rearranged.", NamedTextColor.GRAY));
        return true;
    }

    public String equation(Reaction r) { return describeSide(r.inputs()) + "  ->  " + describeSide(r.outputs()); }

    private String describeSide(Map<String, Integer> side) {
        List<String> parts = new ArrayList<>();
        side.forEach((k, v) -> parts.add(v + " " + label(k)));
        return String.join(" + ", parts);
    }

    private String label(String key) {
        if (key.startsWith("atom:")) return key.substring(5);
        Molecule m = plugin.moleculeRegistry().byId(key);
        return (m != null) ? m.display() : key;
    }

    private void playEffect(Player p, String effect) {
        switch (effect) {
            case "explosion" -> { p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation().add(0, 1, 0), 1);
                                  p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f); }
            case "fire"      -> { p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.02);
                                  p.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f); }
            default          -> { p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 12);
                                  p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f); }
        }
    }
}
