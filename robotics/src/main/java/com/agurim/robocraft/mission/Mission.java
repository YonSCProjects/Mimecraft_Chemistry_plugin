package com.agurim.robocraft.mission;

import java.util.List;
import java.util.Map;

/**
 * One mission: a goal, a scripted scenario, and the checks that decide pass or fail.
 *
 * <p>Three tiers. {@code optional} marks a WARM-UP: they sit at the bottom of the ladder and cover
 * the things redstone already does well, so a student who has never programmed gets an easy first
 * success with the tools. {@code bonus} marks a BONUS: it uses the parts the ladder unlocked but
 * never asked for, and is suggested only once the ladder is done. Both grant no parts and gate
 * nothing - {@link #skippable()} - because the point of the required ladder is the work redstone
 * cannot do, and a student who skips practice must never be stranded. See docs/DESIGN.md 5.
 *
 * <p>{@code optional} keeps its original meaning (warm-up) on purpose. Overloading it to mean
 * "warm-up or bonus" would silently misclassify every bonus mission at every existing call site -
 * the pass subtitle, the firework colour, the trophy metal, the status-bar track. Callers that
 * want "may be skipped" ask {@link #skippable()}; callers that want "is practice" ask
 * {@link #warmUp()}.
 *
 * <p>{@code site}, {@code quest} and {@code value} say where a mission is MET - a place on the
 * student's plot, what it says when clicked, what the robot is worth there. They are content for
 * the yard (docs/DESIGN.md 4.8); the bench that VERIFIES a mission ignores them.
 */
public record Mission(
        String id, int order, boolean optional, boolean bonus,
        String name, String brief, String teaches, String hint,
        String site, List<String> quest, String value,
        List<String> needs, List<String> reward, int startEnergy, List<Step> steps) {

    /** May a student skip this and still finish the course? Warm-ups and bonus missions, yes. */
    public boolean skippable() { return optional || bonus; }

    /** Is this practice - the tier the status bar counts as "חימום"? */
    public boolean warmUp()    { return optional && !bonus; }

    public boolean hasSite()   { return site != null && !site.isEmpty(); }

    /**
     * The world talking back to the bench: while ANY actuator of {@code actuator}'s type is ON,
     * the injected reading for a sensor type is raised by {@code add}, capped at {@code max}.
     *
     * <p>This is how the bench says "the lamp lights its own sensor" without reading the world.
     * A student's night light did exactly that in playtest and oscillated every tick - the whole
     * lesson of hysteresis, arrived at by accident. {@code feedback} makes it deterministic and
     * fifteen seconds long instead of waiting for dusk.
     */
    public record Feedback(String actuator, int add, int max) {}

    /**
     * One line of the scenario.
     *
     * <p>{@code env} is simulated sensor readings by sensor TYPE - the bench injects them instead
     * of changing the shared world. {@code feedback} is the loop back from outputs to readings.
     * {@code expect} is checked the moment the step is reached, and {@code because} is what the
     * student is told when it fails: always the reason, never a score.
     *
     * <p>{@code check} is how the mission card names this check BEFORE the run - the situation
     * half of "situation: expected outcome". Usually empty: the card derives it from the injected
     * readings ("אור 14: נורה כבויה"). It is authored where readings do not tell the story - a
     * deadline ("שנייה אחרי שהלך"), a count ("אחרי שתי כניסות"), a direction ("45 בדרך למעלה").
     */
    public record Step(String say, Map<String, Integer> env, Map<String, Feedback> feedback,
                       int waitTicks, Map<String, String> expect, String because, String check) {

        public boolean hasEnv()      { return env != null && !env.isEmpty(); }
        public boolean hasFeedback() { return feedback != null && !feedback.isEmpty(); }
        public boolean hasExpect()   { return expect != null && !expect.isEmpty(); }
        public boolean hasCheck()    { return check != null && !check.isEmpty(); }
    }
}
