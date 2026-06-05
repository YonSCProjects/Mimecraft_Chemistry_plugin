package com.agurim.chemcraft.plot;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * Lays out the extraction/reaction stations as a labeled row of blocks next to a plot's wall,
 * so a player can start playing without an op placing anything. Fully driven by config:
 * {@code stations} (block per method), {@code station-info} (friendly name + hint), and
 * {@code kiosk} (where the row sits relative to the plot corner).
 */
public class StationKiosk {

    private final ChemCraftPlugin plugin;

    public StationKiosk(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private World world() { return plugin.plots().world(); }

    /** Place every configured station block in a row, each with a floating label. */
    public void build(int plotIndex) {
        ConfigurationSection stations = plugin.getConfig().getConfigurationSection("stations");
        if (stations == null) return;

        Location corner = plugin.plots().plotCorner(plotIndex);
        int ox = plugin.getConfig().getInt("kiosk.offset-x", 8);
        int oy = plugin.getConfig().getInt("kiosk.offset-y", 1);
        int oz = plugin.getConfig().getInt("kiosk.offset-z", 1);
        int spacing = Math.max(1, plugin.getConfig().getInt("kiosk.spacing", 3));

        int i = 0;
        for (String method : stations.getKeys(false)) {
            Material mat = Material.matchMaterial(stations.getString(method, ""));
            if (mat == null) { i++; continue; }
            Location loc = new Location(world(),
                    corner.getBlockX() + ox + i * spacing,
                    corner.getBlockY() + oy,
                    corner.getBlockZ() + oz);
            world().getBlockAt(loc).setType(mat);
            spawnLabel(method, loc);
            i++;
        }
    }

    private void spawnLabel(String method, Location block) {
        Location anchor = block.clone().add(0.5, 1.4, 0.5);
        removeLabel(method, anchor);
        world().spawn(anchor, TextDisplay.class, td -> {
            td.text(Component.text(stationName(method), NamedTextColor.AQUA)
                    .append(Component.text("\n" + stationHint(method), NamedTextColor.GRAY)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.stationKey(), PersistentDataType.STRING, method);
        });
    }

    private void removeLabel(String method, Location anchor) {
        for (Entity ent : world().getNearbyEntities(anchor, 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String m = td.getPersistentDataContainer().get(plugin.stationKey(), PersistentDataType.STRING);
                if (method.equals(m)) td.remove();
            }
        }
    }

    private String stationName(String method) {
        return plugin.getConfig().getString("station-info." + method + ".name", prettify(method));
    }

    private String stationHint(String method) {
        return plugin.getConfig().getString("station-info." + method + ".hint", "Right-click to use");
    }

    private String prettify(String method) {
        String s = method.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
