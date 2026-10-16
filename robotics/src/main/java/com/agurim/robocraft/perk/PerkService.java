package com.agurim.robocraft.perk;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.mission.MissionRegistry;
import com.agurim.robocraft.mission.Perk;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The prize is in Minecraft (docs/DESIGN.md 4.12): what a student holds, and keeping it true on
 * the player.
 *
 * <p>Nothing is stored for a perk. What a student holds is derived from the missions they have
 * passed, every time it is asked - so {@code /rc reset} takes the powers away with the progress,
 * and a perk added to a mission in missions.yml reaches everyone who already passed it on their
 * next join. The three things a power can be: a potion effect with infinite duration (speed,
 * haste, jump, night vision - re-asserted on join, removed when no longer held); flight, allowed
 * on the student's own plot or anywhere, checked once a second because a plot has edges; and
 * things that are given once and replaced if lost - an elytra with rockets, a tamed animal.
 *
 * <p>A <b>taste</b> lends a mission's powers for a few minutes: the first bench check that needs
 * a real rule earns it, once per mission, so the prize is concrete before it is won. Only powers
 * can be lent - a gate, an item and an animal cannot be taken back - and a taste never outlives
 * the session.
 */
public class PerkService {

    private final RoboCraftPlugin plugin;
    /** Lent perks: key -> when they expire (ms). Session only. */
    private final Map<UUID, Map<String, Long>> tastes = new HashMap<>();
    private BukkitTask ticker;

    public PerkService(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public boolean enabled() { return plugin.getConfig().getBoolean("perks.enabled", true); }

    // ================================================================ pure

    /** Every perk key the passed missions pay. */
    public static Set<String> heldFrom(Set<String> completed, MissionRegistry reg) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : completed) {
            Mission m = reg.byId(id);
            if (m != null) for (Perk p : reg.perksOf(m)) out.add(p.key());
        }
        return out;
    }

    /** May this student fly here? {@code fly} anywhere; {@code fly-plot} on their own plot only. */
    public static boolean flightAllowed(Set<String> held, boolean onOwnPlot) {
        for (String key : held) {
            Perk p = Perk.parse(key);
            if (p == null) continue;
            if (p.kind() == Perk.Kind.FLY) return true;
            if (p.kind() == Perk.Kind.FLY_PLOT && onOwnPlot) return true;
        }
        return false;
    }

    /** What a first real check lends: the mission's lendable powers not already held. Nothing twice, nothing after a pass. */
    public static List<Perk> toTaste(List<Perk> missionPerks, Set<String> held, boolean tasted, boolean passed) {
        if (tasted || passed) return List.of();
        List<Perk> out = new ArrayList<>();
        for (Perk p : missionPerks) if (p.tastable() && !held.contains(p.key())) out.add(p);
        return out;
    }

    /**
     * The open-land gate. Ungated when no mission grants {@code wild}; a student who already has
     * a spot out there keeps it - three class servers held open-land builds before the gate
     * existed, and a gate must never lock a student out of their own work.
     */
    public static boolean wildAllowed(Set<String> held, boolean hasSpot, boolean gated) {
        return !gated || hasSpot || held.contains("wild");
    }

    /** "מהירות, השטח הפתוח" */
    public static String names(List<Perk> perks) {
        List<String> out = new ArrayList<>();
        for (Perk p : perks) out.add(p.hebrew());
        return String.join(", ", out);
    }

    // =============================================================== state

    public Set<String> permanent(UUID id) {
        return heldFrom(plugin.store().completedMissions(id), plugin.missions().registry());
    }

    /** Permanent plus lent, lent pruned of anything expired. */
    public Set<String> held(UUID id) {
        Set<String> out = permanent(id);
        Map<String, Long> mine = tastes.get(id);
        if (mine != null) {
            long now = System.currentTimeMillis();
            mine.entrySet().removeIf(e -> e.getValue() <= now);
            out.addAll(mine.keySet());
        }
        return out;
    }

    public boolean has(UUID id, String key) { return held(id).contains(key); }

    public boolean wildAllowed(UUID id) {
        boolean gated = plugin.missions().registry().granting("wild") != null;
        return wildAllowed(held(id), plugin.store().wildSpot(id) != null, gated);
    }

    /** Minutes left on a lent perk, or 0. */
    public long tasteMinutesLeft(UUID id, String key) {
        Map<String, Long> mine = tastes.get(id);
        if (mine == null || !mine.containsKey(key)) return 0;
        return Math.max(0, (mine.get(key) - System.currentTimeMillis() + 59_999) / 60_000);
    }

    // =============================================================== apply

    private static final Map<Perk.Kind, PotionEffectType> POTIONS = Map.of(
            Perk.Kind.SPEED, PotionEffectType.SPEED,
            Perk.Kind.HASTE, PotionEffectType.HASTE,
            Perk.Kind.JUMP, PotionEffectType.JUMP_BOOST,
            Perk.Kind.NIGHT_VISION, PotionEffectType.NIGHT_VISION);

    /** Make the player match what they hold. On join, on a pass, on a taste and when one ends. */
    public void apply(Player player) {
        if (!enabled()) return;
        Set<String> keys = held(player.getUniqueId());
        List<Perk> perks = new ArrayList<>();
        for (String k : keys) { Perk p = Perk.parse(k); if (p != null) perks.add(p); }
        potions(player, perks);
        flight(player, keys);
        elytra(player, perks);
        pet(player, perks);
    }

    /** Infinite effects are ours; a finite one (a potion they drank) is left alone either way. */
    private void potions(Player p, List<Perk> perks) {
        for (Map.Entry<Perk.Kind, PotionEffectType> e : POTIONS.entrySet()) {
            int level = 0;
            for (Perk perk : perks) if (perk.kind() == e.getKey()) level = Math.max(level, perk.level());
            PotionEffect cur = p.getPotionEffect(e.getValue());
            if (level == 0) {
                if (cur != null && cur.isInfinite()) p.removePotionEffect(e.getValue());
            } else if (cur == null || !cur.isInfinite() || cur.getAmplifier() != level - 1) {
                p.addPotionEffect(new PotionEffect(e.getValue(), PotionEffect.INFINITE_DURATION, level - 1, false, false, true));
            }
        }
    }

    public void flight(Player p) { flight(p, held(p.getUniqueId())); }

    private void flight(Player p, Set<String> keys) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        boolean onOwn = plugin.plots().plotIndexAt(p.getLocation())
                == plugin.store().getOrAssignPlotIndex(p.getUniqueId());
        boolean allow = flightAllowed(keys, onOwn);
        if (allow == p.getAllowFlight()) return;
        if (!allow && p.isFlying()) {
            // Over the edge of the plot: let them down gently, and say why.
            p.setFlying(false);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false, true));
            p.sendMessage(Component.text("התעופה עובדת רק בחלקה שלכם.", NamedTextColor.GRAY));
        }
        p.setAllowFlight(allow);
    }

    private void elytra(Player p, List<Perk> perks) {
        if (perks.stream().noneMatch(x -> x.kind() == Perk.Kind.ELYTRA)) return;
        PlayerInventory inv = p.getInventory();
        ItemStack chest = inv.getChestplate();
        boolean has = (chest != null && chest.getType() == Material.ELYTRA) || inv.contains(Material.ELYTRA);
        if (!has) give(p, new ItemStack(Material.ELYTRA));
        if (!inv.contains(Material.FIREWORK_ROCKET)) give(p, new ItemStack(Material.FIREWORK_ROCKET, 16));
    }

    private void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    private void pet(Player p, List<Perk> perks) {
        for (Perk perk : perks) {
            if (perk.kind() != Perk.Kind.PET) continue;
            if (!hasPet(p)) spawnPet(p, perk);
            return;      // one animal, whatever the list says
        }
    }

    private boolean hasPet(Player p) {
        for (Tameable t : p.getWorld().getEntitiesByClass(Tameable.class)) {
            if (t.isTamed() && p.getUniqueId().equals(t.getOwnerUniqueId())) return true;
        }
        return false;
    }

    private void spawnPet(Player p, Perk perk) {
        String name = perk.petName() + " של " + p.getName();
        switch (perk.arg()) {
            case "cat"    -> spawn(p, Cat.class, name);
            case "parrot" -> spawn(p, Parrot.class, name);
            case "horse"  -> spawn(p, Horse.class, name);
            default       -> spawn(p, Wolf.class, name);
        }
    }

    /** Tamed, named, and sitting where it was given - it follows once the student clicks it. */
    private <T extends Tameable> void spawn(Player p, Class<T> cls, String name) {
        p.getWorld().spawn(p.getLocation(), cls, t -> {
            t.setTamed(true);
            t.setOwner(p);
            t.customName(Component.text(name));
            t.setCustomNameVisible(true);
            if (t instanceof Sittable s) s.setSitting(true);
        });
    }

    // ============================================================== grants

    /**
     * On a pass: the mission's perks the student did not already hold from another mission,
     * applied at once. A lent copy of the same perk is superseded.
     */
    public List<Perk> granted(Player player, Mission m) {
        UUID id = player.getUniqueId();
        Set<String> others = new LinkedHashSet<>(plugin.store().completedMissions(id));
        others.remove(m.id());
        Set<String> before = heldFrom(others, plugin.missions().registry());
        List<Perk> fresh = new ArrayList<>();
        Map<String, Long> lent = tastes.get(id);
        for (Perk p : plugin.missions().registry().perksOf(m)) {
            if (lent != null) lent.remove(p.key());
            if (!before.contains(p.key())) fresh.add(p);
        }
        apply(player);
        return fresh;
    }

    /** Lend the mission's powers for {@code perks.taste-minutes}. False when there is nothing to lend. */
    public boolean taste(Player player, Mission m) {
        if (!enabled()) return false;
        UUID id = player.getUniqueId();
        List<Perk> lend = toTaste(plugin.missions().registry().perksOf(m), held(id),
                plugin.store().tasted(id, m.id()), plugin.store().isMissionDone(id, m.id()));
        if (lend.isEmpty()) return false;
        plugin.store().setTasted(id, m.id());
        int minutes = Math.max(1, plugin.getConfig().getInt("perks.taste-minutes", 10));
        long until = System.currentTimeMillis() + minutes * 60_000L;
        Map<String, Long> mine = tastes.computeIfAbsent(id, k -> new LinkedHashMap<>());
        for (Perk p : lend) mine.put(p.key(), until);
        apply(player);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        player.sendMessage(Component.text("✓ הבדיקה הראשונה עברה - טעימה: " + names(lend) + " ל-" + minutes
                + " דקות. הפרס הקבוע מחכה אחרי ההצלחה.", NamedTextColor.GOLD));
        return true;
    }

    // ============================================================== ticker

    /** Once a second: lent perks that ran out, and flight at the plot's edge. */
    public void start() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled()) return;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                expire(p);
                flight(p);
            }
        }, 20L, 20L);
    }

    private void expire(Player p) {
        UUID id = p.getUniqueId();
        Map<String, Long> mine = tastes.get(id);
        if (mine == null) return;
        long now = System.currentTimeMillis();
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, Long> e : mine.entrySet()) if (e.getValue() <= now) gone.add(e.getKey());
        if (gone.isEmpty()) return;
        gone.forEach(mine::remove);
        if (mine.isEmpty()) tastes.remove(id);
        apply(p);
        p.sendMessage(Component.text("הטעימה נגמרה. הפרס הקבוע מחכה אחרי ההצלחה במשימה.", NamedTextColor.GRAY));
    }

    public void stop() { if (ticker != null) ticker.cancel(); }

    public void forget(UUID id) { tastes.remove(id); }
}
