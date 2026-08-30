package com.agurim.chemcraft.world;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The floating label over a placed atom block.
 *
 * It used to show only the symbol, which meant the valence - the single number the whole bonding
 * puzzle is about - was visible on the atom ITEM and then vanished the instant you placed it.
 * Students were solving a valence puzzle with the valence hidden. Now the label carries a live
 * bond counter (used/wanted), so the board is visible while you play it.
 *
 * Deliberately shows the COUNTER, not the answer: "O 1/2" teaches; "oxygen needs a double bond"
 * would steal the discovery.
 */
public final class AtomLabels {
    private AtomLabels() {}

    private static Location anchor(Location block) { return block.clone().add(0.5, 1.0, 0.5); }

    private static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    /**
     * The display entity for each atom, and a signature of what it is currently showing.
     *
     * Both are caches - everything in them can be recovered from the world by {@link #find} - and
     * both exist because refresh is not the rare call it looks like. {@code MoleculeEngine.evaluate}
     * refreshes EVERY atom of the connected component, and it runs on every placement and every
     * bond change: one atom added to a 24-atom glucose build is 24 refreshes, not one.
     */
    private static final Map<String, UUID> DISPLAYS = new ConcurrentHashMap<>();
    private static final Map<String, String> SHOWING = new ConcurrentHashMap<>();

    /**
     * Create the label, or update the existing one in place.
     *
     * Never remove-and-respawn. That was the original implementation and it was wrong twice over:
     * it cost a spatial query plus an entity kill and spawn per atom per placement, and because a
     * respawned display fades back in, the label visibly blinked - on the exact feature added to
     * make the bonding puzzle readable. Rebuilding a molecule flickered the whole molecule.
     */
    public static void refresh(ChemCraftPlugin plugin, Location block) {
        String sym = plugin.atoms().get(block);
        if (sym == null) return;
        Element e = plugin.registry().get(sym);

        int used = plugin.moleculeEngine().usedValence(block);
        int wants = (e != null) ? e.valence() : 0;

        // Cheap first: most refreshes in a component are for atoms nothing has changed about.
        // Colour is a pure function of used vs wants, so these three values are the whole label.
        String cacheKey = key(block);
        String signature = sym + "|" + used + "/" + wants;
        if (signature.equals(SHOWING.get(cacheKey))) return;

        NamedTextColor countColor = (used == wants) ? NamedTextColor.GREEN
                : (used > wants) ? NamedTextColor.RED : NamedTextColor.YELLOW;
        final Component text = Component.text(sym, NamedTextColor.WHITE)
                .append(Component.text("\n" + used + "/" + wants, countColor));

        TextDisplay existing = display(plugin, cacheKey, block, sym);
        if (existing != null) {
            existing.text(text);
            SHOWING.put(cacheKey, signature);
            return;
        }
        TextDisplay spawned = block.getWorld().spawn(anchor(block), TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.getPersistentDataContainer().set(plugin.atomKey(), PersistentDataType.STRING, sym);
        });
        DISPLAYS.put(cacheKey, spawned.getUniqueId());
        SHOWING.put(cacheKey, signature);
    }

    /** The cached display if it is still alive, otherwise one spatial search to re-find it. */
    private static TextDisplay display(ChemCraftPlugin plugin, String cacheKey, Location block, String sym) {
        UUID id = DISPLAYS.get(cacheKey);
        if (id != null) {
            Entity cached = Bukkit.getEntity(id);
            if (cached instanceof TextDisplay td && td.isValid()) return td;
            DISPLAYS.remove(cacheKey);   // unloaded or killed; fall through and look again
        }
        TextDisplay found = find(plugin, block, sym);
        if (found != null) DISPLAYS.put(cacheKey, found.getUniqueId());
        return found;
    }

    private static TextDisplay find(ChemCraftPlugin plugin, Location block, String sym) {
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.atomKey(), PersistentDataType.STRING);
                if (sym.equals(tag)) return td;
            }
        }
        return null;
    }

    public static void remove(ChemCraftPlugin plugin, Location block, String sym) {
        String cacheKey = key(block);
        DISPLAYS.remove(cacheKey);
        // Must clear too: break an O and place an N here and the signature would otherwise still
        // read as the old atom's, so the new label would never be drawn.
        SHOWING.remove(cacheKey);
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.atomKey(), PersistentDataType.STRING);
                if (sym.equals(tag)) td.remove();
            }
        }
    }
}
