package com.agurim.chemcraft.assistant;

import com.agurim.chemcraft.ChemCraftPlugin;
import com.agurim.chemcraft.element.AtomItems;
import com.agurim.chemcraft.element.Element;
import com.agurim.chemcraft.region.Region;
import com.agurim.chemcraft.ui.ElementHint;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captures a student's question together with the state needed to answer it well, and appends it
 * to questions.jsonl (one JSON object per line, UTF-8).
 *
 * The plugin deliberately does NOT talk to any AI. It owns capture and delivery; the answering
 * agent is external and pluggable - it tails this file and replies with `/cc whisper`. That keeps
 * secrets, cost and model choice out of the plugin, lets the assistant be swapped or switched off
 * without a rebuild, and means the questions themselves survive as a teaching record even when no
 * assistant is running at all.
 *
 * The captured context is what turns "how do I make water?" into an answerable question: where the
 * student is standing, what they are holding, how far along they are, and what they are stuck on.
 */
public class AskService {

    private final ChemCraftPlugin plugin;
    private final Map<UUID, Long> lastAsk = new HashMap<>();

    public AskService(ChemCraftPlugin plugin) { this.plugin = plugin; }

    private int cooldown() { return plugin.getConfig().getInt("assistant.cooldown-seconds", 15); }

    /** Seconds the player must still wait, or 0 if they may ask now. */
    public long cooldownRemaining(UUID id) {
        Long last = lastAsk.get(id);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldown() - elapsed);
    }

    /** Record a question. Returns false if the file could not be written. */
    public boolean ask(Player player, String question) {
        lastAsk.put(player.getUniqueId(), System.currentTimeMillis());
        Map<String, String> ctx = context(player);

        StringBuilder json = new StringBuilder("{");
        json.append(q("ts")).append(':').append(System.currentTimeMillis()).append(',');
        json.append(q("player")).append(':').append(q(player.getName())).append(',');
        json.append(q("uuid")).append(':').append(q(player.getUniqueId().toString())).append(',');
        json.append(q("question")).append(':').append(q(question));
        ctx.forEach((k, v) -> json.append(',').append(q(k)).append(':').append(q(v)));
        json.append('}');

        File f = new File(plugin.getDataFolder(), "questions.jsonl");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
            w.write(json.toString());
            w.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not append questions.jsonl: " + e.getMessage());
            return false;
        }
        plugin.getLogger().info("[ask] " + player.getName() + ": " + question);
        return true;
    }

    /** Everything an answering agent needs to give a situated hint rather than a generic one. */
    private Map<String, String> context(Player player) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();
        m.put("x", String.valueOf(loc.getBlockX()));
        m.put("y", String.valueOf(loc.getBlockY()));
        m.put("z", String.valueOf(loc.getBlockZ()));

        Region region = plugin.regions().at(loc);
        int plot = plugin.plots().plotIndexAt(loc);
        m.put("where", region != null ? "region:" + region.id()
                : plot == plugin.store().getOrAssignPlotIndex(id) ? "own_plot"
                : plot >= 0 ? "plot:" + plot : "outside");

        m.put("discovered", String.valueOf(plugin.store().getDiscovered(id).size()));
        m.put("total", String.valueOf(plugin.registry().all().size()));
        m.put("assists", String.valueOf(plugin.store().getAssists(id)));

        Element next = plugin.missionBar().nextTarget(id);
        if (next != null) {
            m.put("next_element", next.symbol());
            m.put("next_hint", ElementHint.where(plugin, next));
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        String heldAtom = AtomItems.symbolOf(plugin, held);
        m.put("holding", heldAtom != null ? "atom:" + heldAtom
                : (held == null || held.getType().isAir()) ? "nothing" : held.getType().name());

        m.put("atoms", atomSummary(player));
        m.put("missing", plugin.store().getDiscovered(id).size() >= plugin.registry().all().size()
                ? "" : String.join(",", undiscovered(id, 8)));
        return m;
    }

    /** "H x2, O x1" - the atoms in hand, which is usually what the question is about. */
    private String atomSummary(Player player) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack it : player.getInventory().getContents()) {
            String sym = AtomItems.symbolOf(plugin, it);
            if (sym != null) counts.merge(sym, it.getAmount(), Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((s, n) -> parts.add(s + " x" + n));
        return String.join(", ", parts);
    }

    private List<String> undiscovered(UUID id, int limit) {
        List<String> out = new ArrayList<>();
        for (Element e : plugin.registry().all()) {
            if (out.size() >= limit) break;
            if (!plugin.store().isDiscovered(id, e.symbol())) out.add(e.symbol());
        }
        return out;
    }

    /** Read back the most recent questions, newest last, for the teacher view. */
    public List<String> recent(int limit) {
        File f = new File(plugin.getDataFolder(), "questions.jsonl");
        List<String> all = new ArrayList<>();
        if (!f.exists()) return all;
        try {
            all = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read questions.jsonl: " + e.getMessage());
        }
        return all.size() <= limit ? all : all.subList(all.size() - limit, all.size());
    }

    /** Minimal JSON string quoting - enough for names, Hebrew questions and our own keys. */
    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '\"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c >= 0x20) b.append(c);
        }
        return b.append('\"').toString();
    }
}
