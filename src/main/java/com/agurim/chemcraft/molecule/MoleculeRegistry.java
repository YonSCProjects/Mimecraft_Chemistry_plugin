package com.agurim.chemcraft.molecule;

import com.agurim.chemcraft.ChemCraftPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MoleculeRegistry {

    private final List<Molecule> molecules = new ArrayList<>();

    public MoleculeRegistry(ChemCraftPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "molecules.yml");
        if (!file.exists()) plugin.saveResource("molecules.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("molecules");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Map<String, Integer> atoms = new LinkedHashMap<>();
            ConfigurationSection a = s.getConfigurationSection("atoms");
            if (a != null) for (String sym : a.getKeys(false)) atoms.put(sym, a.getInt(sym));
            molecules.add(new Molecule(id, s.getString("name", id), s.getString("display", id),
                    atoms, s.getString("fact", ""), s.getString("reward", null)));
        }
    }

    public static String canonical(Map<String, Integer> comp) {
        TreeMap<String, Integer> sorted = new TreeMap<>(comp);
        StringBuilder sb = new StringBuilder();
        sorted.forEach((sym, n) -> sb.append(sym).append(n));
        return sb.toString();
    }

    public Molecule match(Map<String, Integer> composition) {
        String target = canonical(composition);
        for (Molecule m : molecules) if (canonical(m.atoms()).equals(target)) return m;
        return null;
    }

    public Molecule byId(String id) {
        for (Molecule m : molecules) if (m.id().equals(id)) return m;
        return null;
    }

    public int size() { return molecules.size(); }
}
