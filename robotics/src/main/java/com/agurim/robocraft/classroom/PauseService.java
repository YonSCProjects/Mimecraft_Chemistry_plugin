package com.agurim.robocraft.classroom;

import com.agurim.robocraft.RoboCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The teacher's hand on the room: freeze a student or the class, and put big text on screens.
 *
 * <p>A workshop of thirty twelve-year-olds does not stop because someone at the front says
 * "stop". It stops when the screens stop. So a pause freezes the <i>player</i> - no moving,
 * building, clicking or commands - and pins a title to the screen that does not fade until the
 * teacher lifts it. The world keeps going: robots tick, a bench run finishes. That is deliberate,
 * and it is the honest choice - a "paused" robot mid-bench would fail its checks for a reason
 * the student never caused.
 *
 * <p>The state lives in {@link PauseState}, which has no Bukkit in it and is self-tested; this
 * class is the delivery.
 */
public class PauseService {

    /** What a paused student reads when the teacher gave no text. */
    public static final String DEFAULT_TEXT = "עצרו רגע והקשיבו";
    public static final String PAUSE_HEAD = "⏸ הפסקה";
    public static final String RESUME_HEAD = "▶ ממשיכים";
    public static final String NUDGE = "⏸ הפסקה - חכו למורה";

    private final RoboCraftPlugin plugin;
    private final PauseState state = new PauseState();
    private final Map<UUID, Long> lastNudge = new HashMap<>();
    private BukkitTask ticker;
    private int page;

    public PauseService(RoboCraftPlugin plugin) { this.plugin = plugin; }

    public PauseState state() { return state; }

    private static boolean teacher(Player p) { return p.hasPermission("robocraft.admin"); }

    public boolean isPaused(Player p) { return state.applies(p.getUniqueId(), teacher(p)); }

    private String teacherName() { return plugin.getConfig().getString("classroom.teacher-name", "המורה"); }

    private int pageTicks() { return Math.max(40, plugin.getConfig().getInt("classroom.page-seconds", 6) * 20); }

    // ---------------------------------------------------------------- pause

    /** Freeze every student online (never a teacher). Returns how many it froze. */
    public int pauseAll(String text) {
        state.pauseAll(text);
        page = 0;
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isPaused(p)) { freeze(p); n++; }
        }
        startTicker();
        return n;
    }

    /** Freeze one player, teacher or not - a name is explicit. */
    public void pause(Player p, String text) {
        state.pause(p.getUniqueId(), text);
        page = 0;
        freeze(p);
        startTicker();
    }

    /** Lift everything. Returns how many online players were released. */
    public int resumeAll() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isPaused(p)) { release(p); n++; }
        }
        state.resumeAll();
        stopTicker();
        return n;
    }

    /** Lift one player. Returns false if they were not paused. */
    public boolean resume(Player p) {
        boolean was = state.resume(p.getUniqueId(), teacher(p));
        if (was) release(p);
        if (!state.anyone()) stopTicker();
        return was;
    }

    private void freeze(Player p) {
        p.closeInventory();                       // the rule table included - editing is doing
        show(p);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        String text = state.textFor(p.getUniqueId());
        if (text != null) p.sendMessage(fromTeacher(text));
    }

    private void release(Player p) {
        p.clearTitle();
        p.showTitle(Title.title(
                Component.text(RESUME_HEAD, NamedTextColor.GREEN),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    /**
     * The pause title, current page. Pinned: the ticker re-sends it before it can fade, and a
     * message longer than one subtitle line cycles through its lines.
     */
    public void show(Player p) {
        String text = state.textFor(p.getUniqueId());
        List<String> pages = BigText.pages(text == null ? DEFAULT_TEXT : text);
        String sub = pages.isEmpty() ? "" : pages.get(page % pages.size());
        p.showTitle(Title.title(
                Component.text(PAUSE_HEAD, NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text(sub, NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(20), Duration.ofMillis(500))));
    }

    /** A player who joins into a pause gets the title once the client is ready to draw one. */
    public void remindOnJoin(Player p) {
        if (!isPaused(p)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) freeze(p); }, 40L);
    }

    /** Tell a frozen player why nothing happens - once a second at most; a move event fires every tick. */
    public void nudge(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastNudge.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastNudge.put(p.getUniqueId(), now);
        p.sendActionBar(Component.text(NUDGE, NamedTextColor.YELLOW));
    }

    private void startTicker() {
        if (ticker != null) return;
        int period = pageTicks();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            page++;
            for (Player p : Bukkit.getOnlinePlayers()) if (isPaused(p)) show(p);
        }, period, period);
    }

    private void stopTicker() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
    }

    public void shutdown() { stopTicker(); }

    // ------------------------------------------------------------- announce

    /**
     * Big text, no freeze. Short enough for the big line, it <i>is</i> the big line; longer, the
     * teacher's name is the big line and the text pages through the subtitle. Echoed to chat so
     * a student who looked away can still read it.
     */
    public int announce(Collection<? extends Player> targets, String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return 0;
        int period = pageTicks();
        Duration stay = Duration.ofMillis(period * 50L + 1000);
        Title.Times times = Title.Times.times(Duration.ofMillis(200), stay, Duration.ofMillis(800));
        int n = 0;
        for (Player p : targets) {
            n++;
            p.sendMessage(fromTeacher(t));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
            if (BigText.fitsHead(t)) {
                p.showTitle(Title.title(Component.text(t, NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.empty(), times));
                continue;
            }
            List<String> pages = BigText.pages(t);
            for (int i = 0; i < pages.size(); i++) {
                String sub = pages.get(i);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline()) return;
                    p.showTitle(Title.title(Component.text(teacherName(), NamedTextColor.YELLOW, TextDecoration.BOLD),
                            Component.text(sub, NamedTextColor.WHITE), times));
                }, (long) i * period);
            }
        }
        return n;
    }

    private Component fromTeacher(String text) {
        return Component.text("[" + teacherName() + "] ", NamedTextColor.GOLD)
                .append(Component.text(text, NamedTextColor.WHITE));
    }
}
