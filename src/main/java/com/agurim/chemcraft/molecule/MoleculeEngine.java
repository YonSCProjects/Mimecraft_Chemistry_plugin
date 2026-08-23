package com.agurim.chemcraft.molecule;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reads placed atom blocks as a bond graph, checks valence satisfaction, recognises molecules. */
public class MoleculeEngine {

    private static final BlockFace[] FACES =
            { BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };

    private final ChemCraftPlugin plugin;
    private final Set<String> celebrated = new HashSet<>(); // atom keys in a currently-complete molecule

    public MoleculeEngine(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private Location neighbor(Location l, BlockFace f) {
        return l.clone().add(f.getModX(), f.getModY(), f.getModZ());
    }

    private String atomAt(Location l) { return plugin.atoms().get(l); }

    private List<Location> component(Location start) {
        List<Location> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Location> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(key(start));
        while (!queue.isEmpty() && out.size() < 2000) {
            Location cur = queue.poll();
            out.add(cur);
            for (BlockFace f : FACES) {
                Location n = neighbor(cur, f);
                if (atomAt(n) != null && seen.add(key(n))) queue.add(n);
            }
        }
        return out;
    }

    /** Bonds currently used by this atom (public: the block label renders used/wanted). */
    public int usedValence(Location loc) {
        int used = 0;
        for (BlockFace f : FACES) {
            Location n = neighbor(loc, f);
            if (atomAt(n) != null) used += plugin.bonds().order(loc, n);
        }
        return used;
    }

    /** Re-check the molecule containing this atom and react to its state. */
    public void evaluate(Location start, Player player) {
        if (atomAt(start) == null) return;
        List<Location> comp = component(start);

        boolean anyOver = false, anyIncomplete = false;
        String overSym = null; int overUsed = 0, overWants = 0;
        for (Location loc : comp) {
            Element el = plugin.registry().get(atomAt(loc));
            if (el == null) continue;
            int used = usedValence(loc);
            if (used > el.valence()) {
                anyOver = true;
                if (overSym == null) { overSym = el.symbol(); overUsed = used; overWants = el.valence(); }
            } else if (used < el.valence()) {
                anyIncomplete = true;
            }
        }

        // The board must be readable while it is being played: every atom shows used/wanted.
        for (Location loc : comp) com.agurim.chemcraft.world.AtomLabels.refresh(plugin, loc);

        if (anyOver) {
            unmark(comp);
            player.sendMessage(Component.text("יותר מדי קשרים על " + overSym + " - הוא רוצה "
                    + overWants + " אבל יש לו " + overUsed + ".", NamedTextColor.RED));
            return;
        }
        if (anyIncomplete) {
            unmark(comp);
            nudgeIncomplete(player, comp);   // used to stay silent, which hid the whole puzzle
            return;
        }

        // complete: celebrate only on the transition
        boolean isNew = false;
        for (Location loc : comp) if (!celebrated.contains(key(loc))) isNew = true;
        if (!isNew) return;
        comp.forEach(loc -> celebrated.add(key(loc)));
        celebrate(player, comp);
    }

    /**
     * An actionbar nudge naming what is still missing. Actionbar, not chat: this fires on every
     * placement, and chat spam would be worse than the old silence.
     *
     * It names the COUNT, never the answer - "O needs 1 more bond" leaves the student to
     * discover that a second bond between the same pair is what a double bond means. The
     * Shift+right-click gesture is taught once, the first time a player is ever short a bond,
     * because it is undiscoverable and only useful at exactly this moment.
     */
    private void nudgeIncomplete(Player player, List<Location> comp) {
        for (Location loc : comp) {
            Element el = plugin.registry().get(atomAt(loc));
            if (el == null) continue;
            int missing = el.valence() - usedValence(loc);
            if (missing <= 0) continue;
            player.sendActionBar(Component.text(
                    "חסרים " + missing + " קשרים ל-" + el.symbol(), NamedTextColor.YELLOW));
            if (plugin.store().teachOnce(player.getUniqueId(), "bond")) {
                player.sendMessage(Component.text(
                        "טיפ: כיפוף (Shift) + לחיצה ימנית על הפאה שבין שני אטומים מחזקת את הקשר ביניהם.",
                        NamedTextColor.AQUA));
            }
            return;
        }
    }

    private void unmark(List<Location> comp) {
        comp.forEach(loc -> celebrated.remove(key(loc)));
    }

    private void celebrate(Player player, List<Location> comp) {
        Map<String, Integer> composition = new LinkedHashMap<>();
        for (Location loc : comp) composition.merge(atomAt(loc), 1, Integer::sum);

        for (Location loc : comp) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 0.7, 0.5), 6, 0.2, 0.2, 0.2);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);

        Molecule m = plugin.moleculeRegistry().match(composition);
        if (m != null) {
            player.sendMessage(Component.text("בניתם " + m.name() + " (" + m.display() + ")!", NamedTextColor.GOLD)
                    .append(Component.text("  " + m.fact(), NamedTextColor.WHITE)));
            if (m.reward() != null) {
                Material mat = Material.matchMaterial(m.reward());
                if (mat != null) player.getInventory().addItem(new ItemStack(mat));
            }
            // capture the molecule as a sample item, usable as a reaction input
            player.getInventory().addItem(MoleculeItems.create(plugin, m, 1));
        } else {
            player.sendMessage(Component.text("מולקולה יציבה! " + formula(composition)
                    + " - הקשרים של כל אטום מסופקים.", NamedTextColor.AQUA));
        }
    }

    /** Cleanup + re-evaluation when an atom block is broken. */
    public void onAtomRemoved(Location loc, Player player) {
        plugin.bonds().removeAround(loc);
        celebrated.remove(key(loc));
        for (BlockFace f : FACES) {
            Location n = neighbor(loc, f);
            if (atomAt(n) != null) evaluate(n, player);
        }
    }

    /** Sneak-right-click a face to cycle that bond between single/double/triple. */
    public void cycleBond(Location atomLoc, BlockFace face, Player player) {
        if (atomAt(atomLoc) == null) return;
        Location n = neighbor(atomLoc, face);
        if (atomAt(n) == null) {
            player.sendMessage(Component.text("אין אטום בצד הזה ליצור איתו קשר.", NamedTextColor.GRAY));
            return;
        }
        int next = (plugin.bonds().order(atomLoc, n) % 3) + 1;
        plugin.bonds().setOrder(atomLoc, n, next);
        player.sendMessage(Component.text("הקשר הוגדר ל" + bondName(next) + ".", NamedTextColor.YELLOW));
        Location mid = atomLoc.clone().add(0.5, 0.5, 0.5).add(n.clone().add(0.5, 0.5, 0.5)).multiply(0.5);
        player.getWorld().spawnParticle(Particle.CRIT, mid, 8, 0.1, 0.1, 0.1);
        com.agurim.chemcraft.world.AtomLabels.refresh(plugin, atomLoc);
        com.agurim.chemcraft.world.AtomLabels.refresh(plugin, n);
        evaluate(atomLoc, player);
    }

    private String bondName(int order) {
        return switch (order) { case 2 -> "כפול"; case 3 -> "משולש"; default -> "בודד"; };
    }

    private String formula(Map<String, Integer> comp) {
        TreeMap<String, Integer> sorted = new TreeMap<>(comp);
        StringBuilder sb = new StringBuilder();
        sorted.forEach((sym, n) -> sb.append(sym).append(n > 1 ? n : ""));
        return sb.toString();
    }
}
