package com.agurim.chemcraft.element;

import com.agurim.chemcraft.ChemCraftPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ElementRegistry {

    private final Map<String, Element> elements = new LinkedHashMap<>();

    public ElementRegistry(ChemCraftPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "elements.yml");
        if (!file.exists()) plugin.saveResource("elements.yml", false);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String sym : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(sym);
            if (s == null) continue;
            elements.put(sym, new Element(
                    sym,
                    s.getString("name", sym),
                    s.getInt("number"),
                    s.getInt("group"),
                    s.getInt("period"),
                    s.getString("family", "nonmetal"),
                    s.getInt("valence"),
                    s.getString("region", ""),
                    s.getString("raw", ""),
                    s.getString("extraction", ""),
                    s.getString("fact", "")
            ));
        }
    }

    public Element get(String symbol)   { return elements.get(symbol); }
    public boolean has(String symbol)   { return elements.containsKey(symbol); }
    public Collection<Element> all()    { return elements.values(); }
}
