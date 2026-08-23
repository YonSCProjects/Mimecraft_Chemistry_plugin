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
            // whisper is the delivery channel for an external assistant agent: on 26.2 the
            // vanilla tell/tellraw/msg console commands silently deliver nothing, so the
            // plugin sends the message itself.
            if (args.length >= 3 && args[0].equalsIgnoreCase("whisper")) {
                sender.sendMessage(whisper(args) ? "sent" : "player not online: " + args[1]);
                return true;
            }
            sender.sendMessage("לשחקנים בלבד.");
            return true;
        }
        UUID id = player.getUniqueId();

        if (args.length == 0) {
            player.sendMessage(Component.text("/chemcraft guide | kit | tp | region | visit <שם> | ask <שאלה> | report | questions | whisper | give <sym> [n] | givemol <id> [n] | discover <sym> | reset | buildwall | reload", NamedTextColor.YELLOW));
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
            case "whisper" -> {
                if (denyNonAdmin(player)) return true;
                if (args.length < 3) {
                    player.sendMessage(Component.text("שימוש: /cc whisper <שחקן> [chat|actionbar|title] <טקסט>", NamedTextColor.RED));
                    return true;
                }
                if (!whisper(args)) {
                    player.sendMessage(Component.text("השחקן לא מחובר: " + args[1], NamedTextColor.RED));
                }
            }
            case "ask" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("שימוש: /cc ask <שאלה>   -   למשל: /cc ask איפה מוצאים נתרן?", NamedTextColor.RED));
                    return true;
                }
                long wait = plugin.ask().cooldownRemaining(id);
                if (wait > 0) {
                    player.sendMessage(Component.text("רגע אחד - אפשר לשאול שוב בעוד " + wait + " שניות.", NamedTextColor.GRAY));
                    return true;
                }
                String question = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                if (plugin.ask().ask(player, question)) {
                    player.sendMessage(Component.text("השאלה נשלחה לעוזר/ת המעבדה. התשובה תגיע אליכם בקרוב.", NamedTextColor.LIGHT_PURPLE));
                } else {
                    player.sendMessage(Component.text("לא הצלחתי לשלוח את השאלה. נסו שוב.", NamedTextColor.RED));
                }
            }
            case "questions" -> {
                if (denyNonAdmin(player)) return true;
                int n = (args.length > 1) ? Math.max(1, Math.min(50, parseIntOr(args[1], 10))) : 10;
                var lines = plugin.ask().recent(n);
                if (lines.isEmpty()) {
                    player.sendMessage(Component.text("עדיין לא נשאלו שאלות.", NamedTextColor.GRAY));
                    return true;
                }
                player.sendMessage(Component.text("== " + lines.size() + " השאלות האחרונות ==", NamedTextColor.YELLOW));
                for (String l : lines) player.sendMessage(Component.text(summarize(l), NamedTextColor.WHITE));
            }
            case "report" -> {
                if (denyNonAdmin(player)) return true;
                if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                    for (UUID u : plugin.store().allShieldBlocks().keySet()) plugin.store().clearShield(u);
                    player.sendMessage(Component.text("דוח המגן אופס.", NamedTextColor.GREEN));
                    return true;
                }
                var blocks = plugin.store().allShieldBlocks();
                if (blocks.isEmpty()) {
                    player.sendMessage(Component.text("אין ניסיונות פגיעה בחלקות של אחרים. ", NamedTextColor.GREEN));
                    return true;
                }
                player.sendMessage(Component.text("== ניסיונות פגיעה בחלקות (מגן החלקה) ==", NamedTextColor.YELLOW));
                blocks.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .forEach(e -> player.sendMessage(Component.text(
                                "  " + plugin.store().getName(e.getKey()) + " - " + e.getValue()
                                        + " ניסיונות (אחרון: " + plugin.store().getShieldVictim(e.getKey()) + ")",
                                NamedTextColor.WHITE)));
                player.sendMessage(Component.text("איפוס: /cc report clear", NamedTextColor.DARK_AQUA));
            }
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

    /**
     * /cc whisper <player> [chat|actionbar|title] <text...>
     * The channel word is optional; without it the message goes to chat. Callable from the
     * console so an external agent can use it over RCON.
     */
    private boolean whisper(String[] args) {
        Player target = com.agurim.chemcraft.ui.Whisper.find(args[1]);
        if (target == null) return false;
        int from = 2;
        String channel = "chat";
        String maybe = args[2].toLowerCase();
        if (maybe.equals("chat") || maybe.equals("actionbar") || maybe.equals("title")) {
            channel = maybe;
            from = 3;
        }
        if (from >= args.length) return false;
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
        return switch (channel) {
            case "actionbar" -> com.agurim.chemcraft.ui.Whisper.actionbar(plugin, target, text);
            case "title"     -> com.agurim.chemcraft.ui.Whisper.title(plugin, target, text);
            default          -> com.agurim.chemcraft.ui.Whisper.chat(plugin, target, text);
        };
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    /** Pull just player + question out of a stored JSONL line, for the teacher list. */
    private static String summarize(String jsonLine) {
        String who = between(jsonLine, "\"player\":\"", "\"");
        String what = between(jsonLine, "\"question\":\"", "\"");
        return "  " + (who.isEmpty() ? "?" : who) + ": " + what;
    }

    private static String between(String s, String open, String close) {
        int a = s.indexOf(open);
        if (a < 0) return "";
        a += open.length();
        int b = s.indexOf(close, a);
        return (b < 0) ? s.substring(a) : s.substring(a, b);
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
