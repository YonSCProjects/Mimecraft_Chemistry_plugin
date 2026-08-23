package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class MoleculeListener implements Listener {

    private final ChemCraftPlugin plugin;
    public MoleculeListener(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getPlayer().isSneaking())) return; // sneaking = bond mode; normal click = place mode
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (plugin.atoms().get(block.getLocation()) == null) return; // not an atom block
        event.setCancelled(true);
        Player player = event.getPlayer();
        // Bond editing is owner-only: a /cc visit guest may look, not touch - and the molecule
        // completion reward goes to whoever completes it, so this also closes reward theft.
        if (!player.isOp()) {
            int here = plugin.plots().plotIndexAt(block.getLocation());
            int mine = plugin.store().getOrAssignPlotIndex(player.getUniqueId());
            if (here != mine) {
                if (here == -1) player.sendMessage(Component.text("אפשר לערוך קשרים רק בחלקה שלכם.", NamedTextColor.RED));
                else plugin.shield().blocked(player, block.getLocation(), here, "שינוי קשרים");
                return;
            }
        }
        plugin.moleculeEngine().cycleBond(block.getLocation(), event.getBlockFace(), player);
    }
}
