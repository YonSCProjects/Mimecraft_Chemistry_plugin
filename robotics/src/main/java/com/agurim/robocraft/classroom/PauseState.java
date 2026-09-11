package com.agurim.robocraft.classroom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who is paused, and what they were told. No Bukkit, so the self-test can drive it.
 *
 * <p>Two modes that compose: <b>everyone</b> (the teacher stops the room) and <b>named</b> (the
 * teacher stops one student). Everyone-mode never catches a teacher, and a student released by
 * name while it is on stays released until the room is resumed. A named pause catches anyone,
 * teacher included, because typing a name is explicit - and it is how Yon sees what a student
 * sees, on his own account.
 */
public class PauseState {

    private final Set<UUID> paused = new HashSet<>();
    private final Set<UUID> released = new HashSet<>();
    private final Map<UUID, String> messages = new HashMap<>();
    private boolean all;
    private String allMessage;

    public boolean applies(UUID id, boolean teacher) {
        return paused.contains(id) || (all && !teacher && !released.contains(id));
    }

    public boolean anyone() { return all || !paused.isEmpty(); }

    public boolean everyone() { return all; }

    /** The text this player should be shown, or null for the default line. */
    public String textFor(UUID id) {
        String own = messages.get(id);
        if (own != null) return own;
        return paused.contains(id) ? null : allMessage;
    }

    public void pauseAll(String text) {
        all = true;
        allMessage = clean(text);
        released.clear();
    }

    public void pause(UUID id, String text) {
        paused.add(id);
        released.remove(id);
        String t = clean(text);
        if (t != null) messages.put(id, t); else messages.remove(id);
    }

    /** Returns true if this id was paused before the call. */
    public boolean resume(UUID id, boolean teacher) {
        boolean was = applies(id, teacher);
        paused.remove(id);
        messages.remove(id);
        if (all) released.add(id);
        return was;
    }

    public void resumeAll() {
        all = false;
        allMessage = null;
        paused.clear();
        released.clear();
        messages.clear();
    }

    private static String clean(String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }
}
