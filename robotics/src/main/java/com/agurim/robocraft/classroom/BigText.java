package com.agurim.robocraft.classroom;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-wrap for the centre-screen title, and the rule for what fits on the big line.
 *
 * <p>A title renders at four times chat size and does not wrap: a long one runs off both edges
 * of the screen, and nothing tells the sender. A subtitle renders at twice chat size and does
 * not wrap either. So a message a teacher types has to be cut here, by us, into lines that fit -
 * {@link #HEAD_MAX} characters on the big line, {@link #SUB_MAX} on the medium one - at word
 * boundaries, and shown as pages. Pure, so the self-test can hold it to that.
 */
public final class BigText {

    /** Characters that fit on the big (title) line at the GUI scale a laptop usually picks. */
    public static final int HEAD_MAX = 20;
    /** Characters that fit on the subtitle line. */
    public static final int SUB_MAX = 40;

    private BigText() {}

    /** True if the whole text can be the big line by itself. */
    public static boolean fitsHead(String text) {
        return text != null && !text.isBlank() && text.trim().length() <= HEAD_MAX;
    }

    /**
     * Word-wrap into lines of at most {@code width} characters. A single word longer than that
     * is cut, because the alternative is a line that does not fit.
     */
    public static List<String> lines(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            while (word.length() > width) {
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                out.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** The pages of a message: subtitle lines, {@link #SUB_MAX} wide. Blank text gives none. */
    public static List<String> pages(String text) { return lines(text, SUB_MAX); }
}
