package com.agurim.chemcraft.world;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

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

    /** Create or refresh the label at this atom block. */
    public static void refresh(ChemCraftPlugin plugin, Location block) {
        String sym = plugin.atoms().get(block);
        if (sym == null) return;
        Element e = plugin.registry().get(sym);
        remove(plugin, block, sym);

        int used = plugin.moleculeEngine().usedValence(block);
        int wants = (e != null) ? e.valence() : 0;
        NamedTextColor countColor = (used == wants) ? NamedTextColor.GREEN
                : (used > wants) ? NamedTextColor.RED : NamedTextColor.YELLOW;

        block.getWorld().spawn(anchor(block), TextDisplay.class, td -> {
            td.text(Component.text(sym, NamedTextColor.WHITE)
                    .append(Component.text("\n" + used + "/" + wants, countColor)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBrightness(new Display.Brightness(15, 15));
            td.getPersistentDataContainer().set(plugin.atomKey(), PersistentDataType.STRING, sym);
        });
    }

    public static void remove(ChemCraftPlugin plugin, Location block, String sym) {
        for (Entity ent : block.getWorld().getNearbyEntities(anchor(block), 0.6, 0.6, 0.6)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(plugin.atomKey(), PersistentDataType.STRING);
                if (sym.equals(tag)) td.remove();
            }
        }
    }
}
