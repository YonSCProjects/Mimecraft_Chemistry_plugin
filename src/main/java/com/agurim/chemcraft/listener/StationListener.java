package com.agurim.chemcraft.listener;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.extraction.Recipe;
import com.agurim.chemcraft.reaction.Reaction;
import com.agurim.chemcraft.ui.ReactionMenu;
import com.agurim.chemcraft.ui.StationMenu;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class StationListener implements Listener {

    private final ChemCraftPlugin plugin;
    public StationListener(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String method = methodForMaterial(block.getType());
        if (method == null) return;
        event.setCancelled(true);
        if (method.equals("reactor")) new ReactionMenu().open(plugin, event.getPlayer());
        else new StationMenu(method).open(plugin, event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();

        if (holder instanceof StationMenu) {
            event.setCancelled(true);
            String id = pdc(clicked, plugin.recipeKey());
            if (id == null) return;
            Recipe r = plugin.extraction().byId(id);
            if (r != null && plugin.extraction().extract(player, r)) new StationMenu(r.station()).open(plugin, player);
        } else if (holder instanceof ReactionMenu) {
            event.setCancelled(true);
            String id = pdc(clicked, plugin.reactionKey());
            if (id == null) return;
            Reaction r = plugin.reactions().byId(id);
            if (r != null && plugin.reactions().react(player, r)) new ReactionMenu().open(plugin, player);
        }
    }

    private String pdc(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private String methodForMaterial(Material mat) {
        ConfigurationSection stations = plugin.getConfig().getConfigurationSection("stations");
        if (stations == null) return null;
        for (String method : stations.getKeys(false)) {
            Material m = Material.matchMaterial(stations.getString(method, ""));
            if (m == mat) return method;
        }
        return null;
    }
}
