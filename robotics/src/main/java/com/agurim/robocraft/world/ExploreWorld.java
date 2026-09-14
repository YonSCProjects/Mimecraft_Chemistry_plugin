package com.agurim.robocraft.world;

import com.agurim.robocraft.RoboCraftPlugin;
import com.agurim.robocraft.mission.Mission;
import com.agurim.robocraft.mission.MissionRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Set;

/**
 * The regular world: hills, caves, rivers, villages, animals - next to the workshop, not under it.
 *
 * <p>The students' first request (2026-09-03): "it would be more fun if the world were not flat
 * but with all the regular things." Yon chose "plains plots plus a normal world to explore", and
 * only the first half shipped; he asked again on 2026-09-14. This is the second half.
 *
 * <p>Why a second world rather than plots in real terrain: everything the plugin builds on a plot
 * - the board, the shelf, the charging pad, the yard's sites to come - sits at one ground height,
 * and three class servers hold student work in the flat world that regenerating it would destroy
 * (it did, once). So the workshop stays flat and the real world is one command away in each
 * direction. Robots only work in the workshop: a part placed out here is refused, because the
 * bench, the board and every label assume the plot grid.
 *
 * <p>The door is a reward. {@code explore.unlock-after} names the mission that opens it - the
 * first rung by default, so the world is something the ladder gives, not something free play
 * gives instead of the ladder. Set it empty to open the world to everyone from the first join.
 */
public class ExploreWorld {

    private final RoboCraftPlugin plugin;
    private World world;

    public ExploreWorld(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public boolean enabled()      { return plugin.getConfig().getBoolean("explore.enabled", true); }
    public String worldName()     { return plugin.getConfig().getString("explore.world", "world_explore"); }
    public String unlockAfter()   { return plugin.getConfig().getString("explore.unlock-after", "twilight"); }
    public World world()          { return world; }

    /** Create or load the world. Called on enable; the first time generates spawn terrain and takes a few seconds. */
    @SuppressWarnings("removal")   // GameRule.KEEP_INVENTORY is marked for removal on 26.2 but still the only stable API
    public void load() {
        if (!enabled()) return;
        World existing = plugin.getServer().getWorld(worldName());
        if (existing == null) {
            // The server only auto-loads its own three worlds; a plugin world is loaded through
            // the creator on every start. Paper 26.2 keeps it under the main world folder as a
            // dimension (world/dimensions/minecraft/<name>), and it persists there.
            plugin.getLogger().info("explore: loading world '" + worldName() + "' (generates terrain the first time)...");
            existing = new WorldCreator(worldName())
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generateStructures(true)
                    .createWorld();
        }
        world = existing;
        if (world == null) {
            plugin.getLogger().warning("explore: could not create world '" + worldName() + "' - /rc explore is off.");
            return;
        }
        String diff = plugin.getConfig().getString("explore.difficulty", "normal").toUpperCase();
        try { world.setDifficulty(Difficulty.valueOf(diff)); }
        catch (IllegalArgumentException e) { world.setDifficulty(Difficulty.NORMAL); }
        // Nobody loses their parts to a lava pool. The workshop world is not touched.
        world.setGameRule(GameRule.KEEP_INVENTORY, plugin.getConfig().getBoolean("explore.keep-inventory", true));
        int border = plugin.getConfig().getInt("explore.border", 2000);
        if (border > 0) {
            world.getWorldBorder().setCenter(world.getSpawnLocation());
            world.getWorldBorder().setSize(border);
        }
        plugin.getLogger().info("explore: world '" + world.getName() + "' ready, difficulty " + world.getDifficulty()
                + ", border " + border + ".");
    }

    public boolean ready() { return enabled() && world != null; }

    /** Is this the workshop - the one world where parts work and plots exist? */
    public boolean isWorkshop(World w) { return w != null && w.equals(plugin.plots().world()); }

    public boolean isExplore(World w) { return world != null && w != null && w.equals(world); }

    // ------------------------------------------------------------------ gate

    /**
     * Why this student may not go yet, or null if the door is open. Pure: the self-test drives it.
     *
     * @param done        the student's completed mission ids
     * @param unlockAfter the gating mission id from config; empty means no gate
     */
    public static Mission gate(Set<String> done, String unlockAfter, MissionRegistry registry) {
        if (unlockAfter == null || unlockAfter.isBlank()) return null;
        if (done.contains(unlockAfter)) return null;
        Mission m = registry.byId(unlockAfter);
        return m;   // null if config names a mission that does not exist: then no gate, and Validate says so
    }

    /** Teachers walk through any door; the gate is for students. */
    private Mission gateFor(Player player) {
        if (player.hasPermission("robocraft.admin")) return null;
        return gate(plugin.store().completedMissions(player.getUniqueId()), unlockAfter(),
                plugin.missions().registry());
    }

    public boolean open(Player player) { return gateFor(player) == null; }

    // ------------------------------------------------------------- commands

    /** {@code /rc explore}: through the door, or told which mission is the key. */
    public void go(Player player) {
        if (!ready()) {
            player.sendMessage(Component.text("העולם הגדול לא פתוח בשרת הזה.", NamedTextColor.GRAY));
            return;
        }
        Mission key = gateFor(player);
        if (key != null) {
            player.sendMessage(Component.text("העולם הגדול נפתח אחרי המשימה ", NamedTextColor.YELLOW)
                    .append(com.agurim.robocraft.ui.MissionCard.openLink(plugin.missions().registry(), key, NamedTextColor.YELLOW))
                    .append(Component.text("  "))
                    .append(com.agurim.robocraft.ui.MissionCard.openButton(key)));
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location safe = new Location(world, spawn.getBlockX() + 0.5,
                world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ()) + 1, spawn.getBlockZ() + 0.5);
        player.teleport(safe);
        player.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
        player.showTitle(Title.title(
                Component.text("העולם הגדול", NamedTextColor.GREEN),
                Component.text("/rc tp מחזיר לסדנה", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        player.sendMessage(Component.text("יצאתם לטיול. הרובוטים עובדים רק בסדנה - ", NamedTextColor.GREEN)
                .append(com.agurim.robocraft.ui.MissionCard.button("חזרה לסדנה", "/rc tp", "בחזרה לחלקה שלכם", NamedTextColor.AQUA)));
    }

    /** The clickable invitation, for the join line and the pass that opens the door. */
    public Component invitation() {
        return com.agurim.robocraft.ui.MissionCard.button("יציאה לעולם הגדול", "/rc explore",
                "הרים, מערות, כפרים - /rc tp מחזיר", NamedTextColor.GREEN);
    }
}
