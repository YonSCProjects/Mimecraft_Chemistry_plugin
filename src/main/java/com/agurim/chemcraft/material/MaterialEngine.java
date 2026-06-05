package com.agurim.chemcraft.material;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Detects a completed 2x2x2 cube of atoms and crystallises it into a real block. */
public class MaterialEngine {

    private final ChemCraftPlugin plugin;
    private final List<MaterialDef> materials = new ArrayList<>();

    public MaterialEngine(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "materials.yml");
        if (!file.exists()) plugin.saveResource("materials.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("materials");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            String pattern = s.getString("pattern", "uniform");
            String e1, e2 = null;
            if ("checker".equals(pattern)) {
                List<String> els = s.getStringList("elements");
                e1 = els.size() > 0 ? els.get(0) : "";
                e2 = els.size() > 1 ? els.get(1) : "";
            } else {
                e1 = s.getString("element", "");
            }
            materials.add(new MaterialDef(id, pattern, e1, e2, s.getString("result", "STONE"), s.getString("name", id)));
        }
    }

    /** Returns true if placing at loc completed a lattice (and it was converted). */
    public boolean check(Location loc, Player player) {
        for (int i = 0; i <= 1; i++) for (int j = 0; j <= 1; j++) for (int k = 0; k <= 1; k++) {
            Location origin = loc.clone().add(-i, -j, -k);
            List<Location> locs = new ArrayList<>();
            List<String> syms = new ArrayList<>();
            List<Integer> parity = new ArrayList<>();
            boolean full = true;
            for (int dx = 0; dx <= 1 && full; dx++) for (int dy = 0; dy <= 1 && full; dy++) for (int dz = 0; dz <= 1; dz++) {
                Location c = origin.clone().add(dx, dy, dz);
                String sym = plugin.atoms().get(c);
                if (sym == null) { full = false; break; }
                locs.add(c); syms.add(sym); parity.add((dx + dy + dz) & 1);
            }
            if (!full) continue;
            for (MaterialDef def : materials) {
                if (matches(def, syms, parity)) { form(origin, def, locs, syms, player); return true; }
            }
        }
        return false;
    }

    private boolean matches(MaterialDef def, List<String> syms, List<Integer> parity) {
        if ("uniform".equals(def.pattern())) {
            for (String s : syms) if (!s.equals(def.e1())) return false;
            return true;
        }
        // checker: parity-0 corners one element, parity-1 the other (either assignment)
        boolean ok1 = true, ok2 = true;
        for (int idx = 0; idx < syms.size(); idx++) {
            String want1 = parity.get(idx) == 0 ? def.e1() : def.e2();
            String want2 = parity.get(idx) == 0 ? def.e2() : def.e1();
            if (!syms.get(idx).equals(want1)) ok1 = false;
            if (!syms.get(idx).equals(want2)) ok2 = false;
        }
        return ok1 || ok2;
    }

    private void form(Location origin, MaterialDef def, List<Location> locs, List<String> syms, Player player) {
        for (int idx = 0; idx < locs.size(); idx++) {
            Location c = locs.get(idx);
            plugin.atoms().remove(c);
            plugin.bonds().removeAround(c);
            removeLabel(c, syms.get(idx));
            c.getBlock().setType(Material.AIR);
        }
        Material result = Material.matchMaterial(def.result());
        if (result != null) {
            ItemStack block = new ItemStack(result);
            ItemMeta meta = block.getItemMeta();
            meta.displayName(Component.text(def.name(), NamedTextColor.AQUA));
            block.setItemMeta(meta);
            player.getInventory().addItem(block).values()
                    .forEach(l -> player.getWorld().dropItemNaturally(player.getLocation(), l));
        }
        Location center = origin.clone().add(1.0, 1.0, 1.0);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 20, 0.6, 0.6, 0.6);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        player.sendMessage(Component.text("התגבש: " + def.name() + "! ", NamedTextColor.GOLD)
                .append(Component.text(materialFact(def), NamedTextColor.WHITE)));
    }

    private String materialFact(MaterialDef def) {
        File file = new File(plugin.getDataFolder(), "materials.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        return yml.getString("materials." + def.id() + ".fact", "");
    }

    private void removeLabel(Location block, String sym) {
        Location anchor = block.clone().add(0.5, 1.0, 0.5);
        for (Entity ent : block.getWorld().getNearbyEntities(anchor, 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.atomKey(), PersistentDataType.STRING);
                if (sym.equals(tag)) td.remove();
            }
        }
    }
}
