package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.ui.Credit;
import com.agurim.chemcraft.ui.ElementHint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Wall registration: right-click one of YOUR OWN gray periodic-table tiles while holding a
 * matching atom -> the atom is consumed, the tile lights, the fact prints. This is the second,
 * additive discovery path (extraction auto-discovery is untouched) and the reason traded or
 * gifted atoms count.
 *
 * The cooperation credit: if the consumed atom was extracted by someone ELSE (provenance stamp,
 * see AtomItems), the extractor earns an assist - once ever per (receiver, element), enforced
 * structurally because registration is refused on already-discovered tiles and the atom is gone.
 */
public class WallRegisterListener implements Listener {

    private final ChemCraftPlugin plugin;
    public WallRegisterListener(ChemCraftPlugin plugin) { this.plugin = plugin; }

    /**
     * An unlit tile is the goal object a student points at when they ask "what now?", and it
     * used to answer by restating the rule they had just tried to follow. Now it answers the
     * actual question: what this element is, how many bonds it wants, and where to go get it.
     * The wall stops being 25 dead squares and becomes 25 quest cards.
     */
    private void questCard(Player player, Element tile) {
        player.sendMessage(Component.text(tile.name() + " (" + tile.symbol() + ")", NamedTextColor.AQUA)
                .append(Component.text("  ·  יסוד #" + tile.number()
                        + "  ·  רוצה " + tile.valence() + " קשרים", NamedTextColor.GRAY)));
        String where = ElementHint.where(plugin, tile);
        String label = ElementHint.recipeLabel(plugin, tile.symbol());
        if (!where.isEmpty()) {
            Component line = Component.text("מפיקים " + where, NamedTextColor.YELLOW);
            if (!label.isEmpty()) line = line.append(Component.text("  -  " + label, NamedTextColor.GRAY));
            player.sendMessage(line);
        }
        player.sendMessage(Component.text(tile.fact(), NamedTextColor.DARK_GRAY));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 1.6f);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        int mine = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
        if (plugin.plots().plotIndexAt(block.getLocation()) != mine) return; // not my plot
        Element tile = plugin.wall().elementAt(mine, block.getLocation());
        if (tile == null) return;                                            // not a wall tile

        // Always cancel on a tile hit: atom items are placeable glass and this is the owner's
        // own plot, so without the cancel the click would place a block against the wall.
        event.setCancelled(true);

        UUID id = player.getUniqueId();
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldSym = AtomItems.symbolOf(plugin, held);

        if (plugin.store().isDiscovered(id, tile.symbol())) {
            if (tile.symbol().equals(heldSym)) {
                player.sendMessage(Component.text("כבר גיליתם את היסוד הזה - האטום נשאר אצלכם.", NamedTextColor.GRAY));
            } else {
                // A lit tile doubles as a reference card.
                player.sendMessage(Component.text(tile.name() + " (" + tile.symbol() + ") - ", NamedTextColor.AQUA)
                        .append(Component.text(tile.fact(), NamedTextColor.WHITE)));
            }
            return;
        }

        if (heldSym == null) {
            questCard(player, tile);
            return;
        }
        if (!tile.symbol().equals(heldSym)) {
            player.sendMessage(Component.text("האטום שביד (" + heldSym + ") לא מתאים לאריח הזה (" + tile.symbol() + ").", NamedTextColor.RED));
            questCard(player, tile);
            return;
        }

        // Read the provenance BEFORE consuming the item.
        String source = AtomItems.sourceOf(plugin, held);
        String sourceName = AtomItems.sourceNameOf(plugin, held);

        held.setAmount(held.getAmount() - 1); // the tile absorbs the atom

        boolean credited = false;
        UUID helper = null;
        if (source != null && sourceName != null) {
            try { helper = UUID.fromString(source); } catch (IllegalArgumentException ignored) {}
            if (helper != null && !helper.equals(id)) {
                // Registration is only reachable on a first discovery, so this fires at most
                // once per (receiver, element) - the whole anti-farming argument in one line.
                // Known, accepted exception: an admin /cc reset re-opens the receiver's slots;
                // each repeat payment still consumes a freshly extracted stamped atom.
                plugin.store().setTileCredit(id, tile.symbol(), sourceName);
                credited = true;
            }
        }

        plugin.store().discover(id, tile.symbol());
        plugin.wall().lightUp(mine, tile.symbol()); // reads the tile credit it just got, if any
        player.sendMessage(Component.text("נרשם " + tile.name() + " (" + tile.symbol() + ") - ", NamedTextColor.GREEN)
                .append(Component.text(tile.fact(), NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        plugin.missionBar().update(player);

        if (credited) {
            plugin.getLogger().info("[assist] gift " + sourceName + " -> " + player.getName() + " (" + tile.symbol() + ")");
            Bukkit.getServer().sendMessage(Component.text("גילוי חדש: " + tile.name() + " (" + tile.symbol() + ") - "
                    + player.getName() + " יחד עם " + sourceName + "!", NamedTextColor.GOLD));
            Credit.pay(plugin, helper, sourceName,
                    "עזרתם ל-" + player.getName() + " לגלות את " + tile.name() + "!");
        }
        // discovery reached via gift - any recorded demonstrator for this element is moot now
        plugin.store().clearDemonstrator(id, tile.symbol());
    }
}
