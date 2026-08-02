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
    private final java.util.Map<UUID, Long> buildwallLast = new java.util.HashMap<>();
    public ChemCraftCommand(ChemCraftPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // Console may drive region building (lets the teacher - or an MCP session - set up
            // the world without a logged-in player). Everything else stays player-only.
            if (args.length >= 2 && args[0].equalsIgnoreCase("region")) {
                switch (args[1].toLowerCase()) {
                    case "buildall" -> { sender.sendMessage("Built " + plugin.regionBuilder().buildAll() + " regions."); return true; }
                    case "build" -> {
                        if (args.length < 3) { sender.sendMessage("Usage: chemcraft region build <id>"); return true; }
                        sender.sendMessage(plugin.regionBuilder().build(args[2].toLowerCase()) ? "Built." : "Unknown region: " + args[2]);
                        return true;
                    }
                    case "list" -> { sender.sendMessage(plugin.regions().describeAll()); return true; }
                }
            }
            sender.sendMessage("לשחקנים בלבד.");
            return true;
        }
        UUID id = player.getUniqueId();

        if (args.length == 0) {
            player.sendMessage(Component.text("/chemcraft guide | kit | tp | region | visit <שם> | give <sym> [n] | givemol <id> [n] | discover <sym> | reset | buildwall | reload", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "guide", "help" -> Guide.send(plugin, player);
            case "kit" -> {
                if (!StarterKit.give(plugin, player)) {
                    player.sendMessage(Component.text(
                            plugin.store().isKitClaimed(id)
                                    ? "כבר קיבלתם את ערכת הפתיחה - השאר נמצא באזורים."
                                    : "ערכות הפתיחה כבויות בשרת הזה.",
                            NamedTextColor.RED));
                }
            }
            case "region" -> region(player, args);
            case "visit" -> {
                int min = plugin.getConfig().getInt("ranks.visit-min-assists", 8);
                if (!player.hasPermission("chemcraft.admin") && plugin.store().getAssists(id) < min) {
                    player.sendMessage(Component.text("/cc visit נפתח בדרגת לבורנט (" + min + " עזרות).", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) { player.sendMessage(Component.text("שימוש: /cc visit <שם שחקן>", NamedTextColor.RED)); return true; }
                UUID target = plugin.store().uuidByName(args[1]);
                if (target == null) { player.sendMessage(Component.text("שחקן לא מוכר: " + args[1], NamedTextColor.RED)); return true; }
                plugin.plots().teleportToPlot(player, plugin.store().getOrAssignPlotIndex(target));
                player.sendMessage(Component.text("ביקור אצל " + plugin.store().getName(target)
                        + " - אפשר להסתכל וללמוד, אי אפשר לבנות.", NamedTextColor.AQUA));
            }
            case "tp" -> {
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.plots().teleportToPlot(player, plot);
                player.sendMessage(Component.text("שוגרתם לחלקה שלכם (#" + plot + ").", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (denyNonAdmin(player)) return true;
                if (args.length < 2) { player.sendMessage(Component.text("שימוש: /chemcraft give <symbol> [amount]", NamedTextColor.RED)); return true; }
                Element e = plugin.registry().get(normalize(args[1]));
                if (e == null) { player.sendMessage(Component.text("יסוד לא מוכר.", NamedTextColor.RED)); return true; }
                int n = amount(args, 2);
                player.getInventory().addItem(AtomItems.create(plugin, e, n));
                player.sendMessage(Component.text("נתתי " + n + "x אטום " + e.symbol() + ".", NamedTextColor.GREEN));
            }
            case "givemol" -> {
                if (denyNonAdmin(player)) return true;
                if (args.length < 2) { player.sendMessage(Component.text("שימוש: /chemcraft givemol <id> [amount]  (למשל water, oxygen_gas)", NamedTextColor.RED)); return true; }
                Molecule m = plugin.moleculeRegistry().byId(args[1].toLowerCase());
                if (m == null) { player.sendMessage(Component.text("מזהה מולקולה לא מוכר.", NamedTextColor.RED)); return true; }
                int n = amount(args, 2);
                player.getInventory().addItem(MoleculeItems.create(plugin, m, n));
                player.sendMessage(Component.text("נתתי " + n + "x דגימת " + m.display() + ".", NamedTextColor.GREEN));
            }
            case "discover" -> {
                if (denyNonAdmin(player)) return true;
                if (args.length < 2) { player.sendMessage(Component.text("שימוש: /chemcraft discover <symbol>", NamedTextColor.RED)); return true; }
                String sym = normalize(args[1]);
                if (!plugin.registry().has(sym)) { player.sendMessage(Component.text("יסוד לא מוכר: " + sym, NamedTextColor.RED)); return true; }
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.store().discover(id, sym);
                plugin.wall().lightUp(plot, sym);
                player.sendMessage(Component.text("התגלה " + sym + " - ", NamedTextColor.GREEN)
                        .append(Component.text(plugin.registry().get(sym).fact(), NamedTextColor.WHITE)));
            }
            case "reset" -> {
                if (denyNonAdmin(player)) return true;
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.store().clearDiscovered(id);
                plugin.store().clearTileCredit(id); // else stale "discovered with X" lines resurface
                plugin.store().clearSeen(id);
                plugin.store().clearDemos(id);
                plugin.wall().build(plot);
                player.sendMessage(Component.text("הטבלה שלכם אופסה.", NamedTextColor.YELLOW));
            }
            case "buildwall" -> {
                // open to students (self-repair) but rate-limited: it does ~50 block/entity ops
                Long last = buildwallLast.get(id);
                if (last != null && System.currentTimeMillis() - last < 60_000 && !player.hasPermission("chemcraft.admin")) {
                    player.sendMessage(Component.text("חכו רגע לפני בנייה מחדש נוספת.", NamedTextColor.RED));
                    return true;
                }
                buildwallLast.put(id, System.currentTimeMillis());
                int plot = plugin.store().getOrAssignPlotIndex(id);
                plugin.wall().build(plot);
                for (String s : plugin.store().getDiscovered(id)) plugin.wall().lightUp(plot, s);
                plugin.kiosk().build(plot);
                plugin.store().setWallBuilt(id, true);
                player.sendMessage(Component.text("הקיר והעמדות נבנו מחדש.", NamedTextColor.GREEN));
            }
            case "reload" -> {
                if (denyNonAdmin(player)) return true;
                plugin.reloadConfig();
                plugin.regions().reload();
                player.sendMessage(Component.text("ההגדרות והאזורים נטענו מחדש. (הפעילו מחדש כדי לטעון יסודות/מתכונים/מולקולות.)", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("תת-פקודה לא מוכרת.", NamedTextColor.RED));
        }
        return true;
    }

    /** /cc region list | tp <id> | build <id> | buildall  (build/buildall admin-only). */
    private void region(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            if (plugin.regions().isEmpty()) {
                player.sendMessage(Component.text("אין אזורים מוגדרים עדיין.", NamedTextColor.GRAY));
                return;
            }
            player.sendMessage(Component.text("אזורים: ", NamedTextColor.AQUA)
                    .append(Component.text(plugin.regions().describeAll(), NamedTextColor.WHITE)));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "tp" -> {
                if (!plugin.getConfig().getBoolean("regions.student-tp", true) && denyNonAdmin(player)) return;
                if (args.length < 3) { player.sendMessage(Component.text("שימוש: /cc region tp <id>", NamedTextColor.RED)); return; }
                if (!plugin.regionBuilder().tp(player, args[2].toLowerCase())) {
                    player.sendMessage(Component.text("אזור לא מוכר: " + args[2], NamedTextColor.RED));
                }
            }
            case "build" -> {
                if (denyNonAdmin(player)) return;
                if (args.length < 3) { player.sendMessage(Component.text("שימוש: /cc region build <id>", NamedTextColor.RED)); return; }
                if (plugin.regionBuilder().build(args[2].toLowerCase())) {
                    player.sendMessage(Component.text("האזור " + args[2] + " נבנה.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("אזור לא מוכר: " + args[2], NamedTextColor.RED));
                }
            }
            case "buildall" -> {
                if (denyNonAdmin(player)) return;
                int n = plugin.regionBuilder().buildAll();
                player.sendMessage(Component.text("נבנו " + n + " אזורים.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("שימוש: /cc region list | tp <id> | build <id> | buildall", NamedTextColor.RED));
        }
    }

    private boolean denyNonAdmin(Player player) {
        if (player.hasPermission("chemcraft.admin")) return false;
        player.sendMessage(Component.text("למורים בלבד.", NamedTextColor.RED));
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
