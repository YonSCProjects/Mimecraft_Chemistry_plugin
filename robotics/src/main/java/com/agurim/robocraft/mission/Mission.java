package com.agurim.robocraft.mission;

import java.util.List;
import java.util.Map;

/** One mission: a goal, a scripted scenario, and the checks that decide pass or fail. */
public record Mission(
        String id, int order, String name, String brief, String teaches, String hint,
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
