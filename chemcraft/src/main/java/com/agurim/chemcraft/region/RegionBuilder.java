package com.agurim.chemcraft.region;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Op tooling that turns a Region definition into blocks in the world.
 *
 * Two modes via config regions.flatten:
 *   true  (default) - flat themed platform: floor fill + air clear, then nodes/stations/labels.
 *   false           - "stations-only": terrain was sculpted by hand (MCP tools); only the gather
 *                     node patches, station blocks, and labels are placed, at plot.ground-y.
 *
 * Node patch layout is deterministic (patch i: 4x4 at offset 4+5i, near the far edge), which is
 * what lets refillBeds() self-heal regen timers lost to a restart with no persistence file.
 */
public class RegionBuilder {

    private final ChemCraftPlugin plugin;

    public RegionBuilder(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private World world()   { return plugin.plots().world(); }
    private int groundY()   { return plugin.getConfig().getInt("plot.ground-y", 64); }

    /** Build one region. Returns false for an unknown id. */
    public boolean build(String id) {
        Region r = plugin.regions().byId(id);
        if (r == null) return false;

        removeLabels(r);
        if (plugin.getConfig().getBoolean("regions.flatten", true)) {
            flatten(r);
        }
        placeNodes(r);
        placeStations(r);
        spawnNameLabel(r);
        return true;
    }

    public int buildAll() {
        int n = 0;
        for (String id : plugin.regions().all().keySet()) if (build(id)) n++;
        return n;
    }

    /** Teleport a player to the center of a region. Returns false for an unknown id. */
    public boolean tp(Player player, String id) {
        Region r = plugin.regions().byId(id);
        if (r == null) return false;
        double y = groundY() + 1.0;
        if (!isBuilt(r)) {
            // Unbuilt region = raw terrain; groundY+1 may be inside a hillside.
            if (!player.hasPermission("chemcraft.admin")) {
                player.sendMessage(Component.text("האזור עדיין לא נבנה - בקשו מהמורה להפעיל אותו.", NamedTextColor.RED));
                return true; // known region, just not ready - not "unknown"
            }
            y = world().getHighestBlockYAt(r.x() + r.size() / 2, r.z() + r.size() / 2) + 1.0;
        }
        Location c = new Location(world(), r.x() + r.size() / 2.0, y, r.z() + r.size() / 2.0);
        c.setYaw(player.getLocation().getYaw());
        player.teleport(c);
        player.sendMessage(Component.text("ברוכים הבאים ל" + r.name() + ".", NamedTextColor.AQUA));
        return true;
    }

    /**
     * Is this block one of the deterministic node-patch beds? Gather permission is scoped to
     * EXACTLY these 16 blocks per material - never the floor, sculpted scenery, or natural
     * terrain in the column - so harvesting is precisely the set refillBeds() self-heals.
     */
    public boolean isNodeBlock(Region r, org.bukkit.block.Block b) {
        if (b.getY() != groundY() + 1) return false;
        int i = 0;
        for (Material m : r.gather().keySet()) {
            int dx = b.getX() - r.x() - (4 + 5 * i);
            int dz = b.getZ() - r.z() - (r.size() - 8);
            if (m == b.getType() && dx >= 0 && dx < 4 && dz >= 0 && dz < 4) return true;
            i++;
        }
        return false;
    }

    /**
     * Restore every node patch of every BUILT region to its source material. Runs on enable:
     * a restart mid-regen leaves placeholders behind, and this heals them for free.
     */
    public void refillBeds() {
        for (Region r : plugin.regions().all().values()) {
            if (!isBuilt(r)) continue;
            int i = 0;
            for (Material source : r.gather().keySet()) {
                fillPatch(r, i, source);
                i++;
            }
        }
    }

    /** A region counts as built if its first station block (or its floor) is in place. */
    private boolean isBuilt(Region r) {
        if (!r.stations().isEmpty()) {
            Material want = stationMaterial(r.stations().get(0));
            if (want != null) {
                return world().getBlockAt(r.x() + 4, groundY() + 1, r.z() + 4).getType() == want;
            }
        }
        return world().getBlockAt(r.x() + 4, groundY(), r.z() + 4).getType() == r.floor();
    }

    private void flatten(Region r) {
        for (int dx = 0; dx < r.size(); dx++) {
            for (int dz = 0; dz < r.size(); dz++) {
                world().getBlockAt(r.x() + dx, groundY(), r.z() + dz).setType(r.floor());
                for (int dy = 1; dy <= 8; dy++) {
                    world().getBlockAt(r.x() + dx, groundY() + dy, r.z() + dz).setType(Material.AIR);
                }
            }
        }
    }

    private void placeNodes(Region r) {
        int i = 0;
        for (Map.Entry<Material, Region.Gather> e : r.gather().entrySet()) {
            fillPatch(r, i, e.getKey());
            i++;
        }
    }

    /** Patch i: a 4x4 bed at x offset 4+5i, near the far (max-z) edge, one above ground. */
    private void fillPatch(Region r, int index, Material source) {
        int ox = 4 + 5 * index;
        int oz = r.size() - 8;
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int x = r.x() + ox + dx, z = r.z() + oz + dz;
                if (!r.contains(x, z)) continue; // validator warns; never spill outside the box
                world().getBlockAt(x, groundY() + 1, z).setType(source);
            }
        }
    }

    private Material stationMaterial(String method) {
        return Material.matchMaterial(plugin.getConfig().getString("stations." + method, ""));
    }

    private void placeStations(Region r) {
        int j = 0;
        for (String method : r.stations()) {
            Material mat = stationMaterial(method);
            if (mat == null) continue;
            Location loc = new Location(world(), r.x() + 4 + 3 * j, groundY() + 1, r.z() + 4);
            world().getBlockAt(loc).setType(mat);
            spawnStationLabel(method, loc);
            j++;
        }
    }

    private void spawnStationLabel(String method, Location block) {
        Location anchor = block.clone().add(0.5, 1.4, 0.5);
        world().spawn(anchor, TextDisplay.class, td -> {
            td.text(Component.text(plugin.getConfig().getString("station-info." + method + ".name", method), NamedTextColor.AQUA)
                    .append(Component.text("\n" + plugin.getConfig().getString("station-info." + method + ".hint", ""), NamedTextColor.GRAY)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setSeeThrough(false);
            td.getPersistentDataContainer().set(plugin.stationKey(), PersistentDataType.STRING, method);
        });
    }

    private void spawnNameLabel(Region r) {
        Location anchor = new Location(world(), r.x() + r.size() / 2.0, groundY() + 4.0, r.z() + r.size() / 2.0);
        world().spawn(anchor, TextDisplay.class, td -> {
            td.text(Component.text(r.name(), NamedTextColor.GOLD));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.getPersistentDataContainer().set(plugin.regionKey(), PersistentDataType.STRING, r.id());
        });
    }

    /** Clear this region's old TextDisplays (station + name labels) before a rebuild. */
    private void removeLabels(Region r) {
        Location center = new Location(world(), r.x() + r.size() / 2.0, groundY() + 3.0, r.z() + r.size() / 2.0);
        for (Entity ent : world().getNearbyEntities(center, r.size() / 2.0 + 1, 8, r.size() / 2.0 + 1)) {
            if (!(ent instanceof TextDisplay td)) continue;
            var pdc = td.getPersistentDataContainer();
            if (pdc.has(plugin.stationKey(), PersistentDataType.STRING)
                    || pdc.has(plugin.regionKey(), PersistentDataType.STRING)) {
                td.remove();
            }
        }
    }
}
