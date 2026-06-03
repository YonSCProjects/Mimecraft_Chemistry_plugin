package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
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
        plugin.moleculeEngine().cycleBond(block.getLocation(), event.getBlockFace(), player);
    }
}
