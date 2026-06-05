package com.agurim.chemcraft.command;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.StarterKit;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.molecule.Molecule;
import com.agurim.chemcraft.molecule.MoleculeItems;
import com.agurim.chemcraft.ui.Guide;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ChemCraftCommand implements CommandExecutor {

    private final ChemCraftPlugin plugin;
    public ChemCraftCommand(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        UUID id = player.getUniqueId();

        if (args.length == 0) {
            player.sendMessage(Component.text("/chemcraft guide | kit | tp | give <sym> [n] | givemol <id> [n] | discover <sym> | reset | buildwall | reload", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "guide", "help" -> Guide.send(plugin, player);
            case "kit" -> {
                if (!StarterKit.give(plugin, player)) {
                    player.sendMessage(Component.text("Starter kits are turned off on this server.", NamedTextColor.RED));
                }
            }
            case "tp" -> {
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.plots().teleportToPlot(player, plot);
                player.sendMessage(Component.text("Teleported to your plot (#" + plot + ").", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /chemcraft give <symbol> [amount]", NamedTextColor.RED)); return true; }
                Element e = plugin.registry().get(normalize(args[1]));
                if (e == null) { player.sendMessage(Component.text("Unknown element.", NamedTextColor.RED)); return true; }
                int n = amount(args, 2);
                player.getInventory().addItem(AtomItems.create(plugin, e, n));
                player.sendMessage(Component.text("Gave " + n + "x " + e.symbol() + " atom.", NamedTextColor.GREEN));
            }
            case "givemol" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /chemcraft givemol <id> [amount]  (e.g. water, oxygen_gas)", NamedTextColor.RED)); return true; }
                Molecule m = plugin.moleculeRegistry().byId(args[1].toLowerCase());
                if (m == null) { player.sendMessage(Component.text("Unknown molecule id.", NamedTextColor.RED)); return true; }
                int n = amount(args, 2);
                player.getInventory().addItem(MoleculeItems.create(plugin, m, n));
                player.sendMessage(Component.text("Gave " + n + "x " + m.display() + " sample.", NamedTextColor.GREEN));
            }
            case "discover" -> {
                if (args.length < 2) { player.sendMessage(Component.text("Usage: /chemcraft discover <symbol>", NamedTextColor.RED)); return true; }
                String sym = normalize(args[1]);
                if (!plugin.registry().has(sym)) { player.sendMessage(Component.text("Unknown element: " + sym, NamedTextColor.RED)); return true; }
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.store().discover(id, sym);
                plugin.wall().lightUp(plot, sym);
                player.sendMessage(Component.text("Discovered " + sym + " - ", NamedTextColor.GREEN)
                        .append(Component.text(plugin.registry().get(sym).fact(), NamedTextColor.WHITE)));
            }
            case "reset" -> {
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.store().clearDiscovered(id);
                plugin.wall().build(plot);
                player.sendMessage(Component.text("Your table has been reset.", NamedTextColor.YELLOW));
            }
            case "buildwall" -> {
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.wall().build(plot);
                for (String s : plugin.store().getDiscovered(id)) plugin.wall().lightUp(plot, s);
                plugin.kiosk().build(plot);
                plugin.store().setWallBuilt(id, true);
                player.sendMessage(Component.text("Wall and stations (re)built.", NamedTextColor.GREEN));
            }
            case "reload" -> {
                if (!player.isOp()) { player.sendMessage(Component.text("Op only.", NamedTextColor.RED)); return true; }
                plugin.reloadConfig();
                player.sendMessage(Component.text("Config reloaded. (Restart to reload elements/recipes/molecules.)", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private int amount(String[] args, int idx) {
        if (args.length > idx) { try { return Math.max(1, Integer.parseInt(args[idx])); } catch (NumberFormatException ignored) {} }
        return 1;
    }

    private String normalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1).toLowerCase() : "");
    }
}
