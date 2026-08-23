package com.agurim.chemcraft.region;

import com.agurim.chemcraft.ChemCraftPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads regions.yml and answers "which region is this location in / what may happen here".
 * With no regions defined, every query returns empty/null and the game behaves exactly as
 * before regions existed (legacy kiosk mode) - safe jar swap.
 */
public class RegionManager {

    private static final Material DEFAULT_PLACEHOLDER = Material.LIGHT_GRAY_STAINED_GLASS;

    private final ChemCraftPlugin plugin;
    private final Map<String, Region> regions = new LinkedHashMap<>();

    public RegionManager(ChemCraftPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        regions.clear();
        load();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "regions.yml");
        if (!file.exists()) {
            // Jar swap onto an existing server (players.yml present): stay in legacy mode
            // until an admin opts in by placing a regions.yml. Only fresh installs get seeded.
            if (new File(plugin.getDataFolder(), "players.yml").exists()) return;
            plugin.saveResource("regions.yml", false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("regions");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            int x = s.getInt("anchor.x");
            int z = s.getInt("anchor.z");
            int size = Math.max(8, s.getInt("size", 24));
            Material floor = Material.matchMaterial(s.getString("floor", "STONE"));
            if (floor == null) floor = Material.STONE;
            List<String> stations = s.getStringList("stations");

            Map<Material, Region.Gather> gather = new LinkedHashMap<>();
            ConfigurationSection g = s.getConfigurationSection("gather");
            if (g != null) {
                for (String matName : g.getKeys(false)) {
                    Material source = Material.matchMaterial(matName);
                    if (source == null) {
                        plugin.getLogger().warning("regions.yml: unknown gather material '" + matName + "' in " + id);
                        continue;
                    }
                    if (g.isConfigurationSection(matName)) {
                        ConfigurationSection n = g.getConfigurationSection(matName);
                        Material drop = Material.matchMaterial(n.getString("drop", matName));
                        Material placeholder = Material.matchMaterial(n.getString("placeholder", ""));
                        gather.put(source, new Region.Gather(
                                drop != null ? drop : source,
                                Math.max(1, n.getInt("count", 1)),
                                Math.max(5, n.getInt("regen", 60)),
                                placeholder != null ? placeholder : DEFAULT_PLACEHOLDER));
                    } else {
                        gather.put(source, new Region.Gather(source, 1, Math.max(5, g.getInt(matName, 60)), DEFAULT_PLACEHOLDER));
                    }
                }
            }
            regions.put(id, new Region(id, s.getString("name", id), x, z, size, floor, stations, gather));
        }
        validate();
    }

    /** Load-time sanity warnings so a broken zone map is a console line, not a silent dead recipe. */
    private void validate() {
        if (regions.isEmpty()) return;
        // (a) every configured station method must be reachable somewhere
        ConfigurationSection stationSec = plugin.getConfig().getConfigurationSection("stations");
        List<String> kioskStations = plugin.kioskStations();
        if (stationSec != null) {
            for (String method : stationSec.getKeys(false)) {
                boolean inRegion = regions.values().stream().anyMatch(r -> r.stations().contains(method));
                boolean inKiosk = kioskStations.isEmpty() || kioskStations.contains(method);
                if (!inRegion && !inKiosk) {
                    plugin.getLogger().warning("Station '" + method + "' exists in no region and no kiosk - its recipes are unreachable.");
                }
            }
        }
        for (Region r : regions.values()) {
            // (b) region boxes must not overlap plots - check all four corners
            int[][] corners = { {r.x(), r.z()}, {r.x() + r.size() - 1, r.z()},
                                {r.x(), r.z() + r.size() - 1}, {r.x() + r.size() - 1, r.z() + r.size() - 1} };
            for (int[] c : corners) {
                Location probe = new Location(plugin.plots().world(), c[0], plugin.getConfig().getInt("plot.ground-y", 64), c[1]);
                if (plugin.plots().plotIndexAt(probe) != -1) {
                    plugin.getLogger().warning("Region '" + r.id() + "' overlaps a plot at " + c[0] + "," + c[1]);
                    break;
                }
            }
            // (c) node patches must fit the box (patch i: 4 wide at offset 4+5i)
            int n = r.gather().size();
            if (n > 0 && 4 + 5 * (n - 1) + 4 > r.size()) {
                plugin.getLogger().warning("Region '" + r.id() + "': " + n + " gather nodes don't fit in size " + r.size());
            }
            // (c2) the floor must never itself be a gather material - that would make the
            // whole floor read as a node bed to players (harvest is patch-scoped, but the
            // visual invitation + cancel spam is a content bug worth catching at load)
            if (r.gather().containsKey(r.floor())) {
                plugin.getLogger().warning("Region '" + r.id() + "': floor " + r.floor()
                        + " is also a gather material - use a distinct floor block.");
            }
            // (d) unknown station names
            for (String m : r.stations()) {
                if (stationSec == null || !stationSec.contains(m)) {
                    plugin.getLogger().warning("Region '" + r.id() + "' lists unknown station '" + m + "'");
                }
            }
        }
    }

    public boolean isEmpty() { return regions.isEmpty(); }

    public Region byId(String id) { return regions.get(id); }

    public Map<String, Region> all() { return regions; }

    /** The region containing this location, or null. X/Z only - a region is a full column. */
    public Region at(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(plugin.plots().world())) return null;
        for (Region r : regions.values()) {
            if (r.contains(loc.getBlockX(), loc.getBlockZ())) return r;
        }
        return null;
    }

    /** May this station method be used at this location? */
    public boolean allowsStation(Location loc, String method) {
        Region r = at(loc);
        return r != null && r.stations().contains(method);
    }

    /** "מחצבה (quarry), מכרה (mines), ..." for /cc region list. */
    public String describeAll() {
        List<String> parts = new ArrayList<>();
        regions.values().forEach(r -> parts.add(r.name() + " (" + r.id() + ")"));
        return String.join(", ", parts);
    }
}
