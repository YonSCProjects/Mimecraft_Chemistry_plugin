package com.agurim.robocraft.mission;

import java.util.List;
import java.util.Map;

/**
 * One mission: a goal, a scripted scenario, and the checks that decide pass or fail.
 *
 * <p>{@code optional} marks a warm-up. Warm-ups sit at the bottom of the ladder and cover the
 * things redstone already does well - a night light, a tripwire alarm, a pressure-plate door - so
 * a student who has never programmed gets an easy first success with the tools. They grant no
 * parts and gate nothing, because the point of the required ladder is the work redstone cannot do.
 * See docs/DESIGN.md 5.
 */
public record Mission(
        String id, int order, boolean optional, String name, String brief, String teaches, String hint,
        List<String> needs, List<String> reward, int startEnergy, List<Step> steps) {

    /**
     * One line of the scenario.
     *
     * <p>{@code env} is simulated sensor readings by sensor TYPE - the bench injects them instead
     * of changing the shared world. {@code expect} is checked the moment the step is reached, and
     * {@code because} is what the student is told when it fails: always the reason, never a score.
     */
    public record Step(String say, Map<String, Integer> env, int waitTicks,
                       Map<String, String> expect, String because) {

        public boolean hasEnv()    { return env != null && !env.isEmpty(); }
        public boolean hasExpect() { return expect != null && !expect.isEmpty(); }
    }
}
