package com.volleyball.game.tactics;

import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.mechanics.SetType.Lane;
import com.volleyball.game.tactics.Combination.Option;
import com.volleyball.game.tactics.Combination.Run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The team's attacking combinations: the built-in ones (the classic plays of modern volleyball)
 * and the custom plays from the playbook editor. Positions are in the attacking team's frame:
 * depth from the net, lateral + = right (the setter stands at about +1).
 */
public final class Playbook {
    public static final String FIRST_TEMPO = "first-tempo";
    public static final String SECOND_TEMPO = "second-tempo";
    public static final String HIGH_OUTSIDE = "high-outside";
    public static final String DECOY = "decoy";
    public static final String CROSS = "cross";
    public static final String TANDEM = "tandem";
    public static final String PIPE = "pipe";
    public static final String BIC = "bic";
    public static final String QUICK_BACK = "quick-back";
    public static final String ZONE_TWO = "zone-two";
    public static final String QUICK_FAKE = "quick-fake";
    public static final String SETTER_DUMP = "setter-dump";
    public static final String SLIDE = "slide";
    public static final String EMERGENCY = "emergency";

    private static final List<Combination> BUILT_IN = List.of(
            play(FIRST_TEMPO, "First tempo", "The middle runs early and hits the quick right in front of the setter.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.4f, 0.7f, 0f, Tempo.FIRST, false),
                            run(Lane.OUTSIDE, 3.8f, -3.3f, 0.9f, -3.6f, Tempo.THIRD, false)),
                    List.of(opt(SetType.QUICK_MIDDLE, Lane.MIDDLE), opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE),
                            opt(SetType.BACK_SET, Lane.RIGHT))),
            play(SECOND_TEMPO, "Second tempo", "The middle starts a beat later and hits a faster short set in zone 3.",
                    PassRating.LIMITED, false,
                    List.of(run(Lane.MIDDLE, 2.8f, 0f, 0.8f, -0.9f, Tempo.SECOND, false),
                            run(Lane.OUTSIDE, 3.8f, -3.3f, 0.9f, -3.6f, Tempo.THIRD, false)),
                    List.of(opt(SetType.SHORT_MIDDLE, Lane.MIDDLE), opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            play(HIGH_OUTSIDE, "High ball to the outside", "The outside hitter gets the high ball in zone 4: line or cross.",
                    PassRating.EMERGENCY, false,
                    List.of(run(Lane.OUTSIDE, 4.0f, -3.4f, 0.9f, -3.6f, Tempo.THIRD, false)),
                    List.of(opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE), opt(SetType.BACK_SET, Lane.RIGHT))),
            play(DECOY, "Decoy", "The middle fakes the quick to pull the middle blocker; a fast set goes to the outside.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.4f, 0.7f, 0f, Tempo.FIRST, true),
                            run(Lane.OUTSIDE, 3.6f, -3.2f, 0.8f, -3.3f, Tempo.SECOND, false)),
                    List.of(opt(SetType.SHOOT, Lane.OUTSIDE), opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE),
                            opt(SetType.QUICK_MIDDLE, Lane.MIDDLE))),
            play(CROSS, "Cross run (X)", "The middle runs the quick; the outside cuts inside behind him for the short set.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.6f, 0.7f, 0.2f, Tempo.FIRST, true),
                            run(Lane.OUTSIDE, 3.4f, -3.0f, 0.8f, -0.9f, Tempo.SECOND, false)),
                    List.of(opt(SetType.SHORT_MIDDLE, Lane.OUTSIDE), opt(SetType.QUICK_MIDDLE, Lane.MIDDLE),
                            opt(SetType.BACK_SET, Lane.RIGHT))),
            play(TANDEM, "Tandem", "The middle threatens the quick; the second hitter follows right behind and hits a beat later.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.3f, 0.7f, 0.2f, Tempo.FIRST, false),
                            run(Lane.OUTSIDE, 3.6f, -1.6f, 0.8f, -0.9f, Tempo.SECOND, false)),
                    List.of(opt(SetType.SHORT_MIDDLE, Lane.OUTSIDE), opt(SetType.QUICK_MIDDLE, Lane.MIDDLE),
                            opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            play(PIPE, "Pipe", "A back-row hitter attacks from zone 6 behind the 3 m line; the middle draws the block.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.3f, 0.7f, 0f, Tempo.FIRST, true),
                            run(Lane.BACK_MIDDLE, 6.2f, 0f, 3.6f, 0f, Tempo.SECOND, false),
                            run(Lane.OUTSIDE, 3.8f, -3.3f, 0.9f, -3.6f, Tempo.THIRD, false)),
                    List.of(opt(SetType.PIPE, Lane.BACK_MIDDLE), opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE),
                            opt(SetType.BACK_SET, Lane.RIGHT))),
            play(BIC, "Bic", "A fast back-row attack from zone 6, timed with the quick.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.4f, 0.7f, 0f, Tempo.FIRST, true),
                            run(Lane.BACK_MIDDLE, 5.8f, -0.7f, 3.3f, -0.7f, Tempo.FIRST, false)),
                    List.of(opt(SetType.BIC, Lane.BACK_MIDDLE), opt(SetType.QUICK_MIDDLE, Lane.MIDDLE),
                            opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            play(QUICK_BACK, "Quick and back set", "The middle draws the block with the quick; the set goes behind the setter to zone 2.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.4f, 0.7f, 0f, Tempo.FIRST, true),
                            run(Lane.RIGHT, 3.6f, 3.2f, 0.9f, 3.6f, Tempo.SECOND, false)),
                    List.of(opt(SetType.BACK_SET, Lane.RIGHT), opt(SetType.QUICK_MIDDLE, Lane.MIDDLE),
                            opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            play(ZONE_TWO, "Attack through zone 2", "The play goes through the right side: back set to the opposite, or the D from zone 1.",
                    PassRating.LIMITED, false,
                    List.of(run(Lane.RIGHT, 3.8f, 3.3f, 0.9f, 3.6f, Tempo.THIRD, false),
                            run(Lane.BACK_RIGHT, 6.4f, 3.0f, 3.6f, 3.0f, Tempo.THIRD, false)),
                    List.of(opt(SetType.BACK_SET, Lane.RIGHT), opt(SetType.BACK_ROW_RIGHT, Lane.BACK_RIGHT),
                            opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            play(QUICK_FAKE, "Quick with a dummy", "The middle makes the full quick approach; the setter reads the block and sets the free side.",
                    PassRating.GOOD, true,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.4f, 0.7f, 0f, Tempo.FIRST, true),
                            run(Lane.OUTSIDE, 3.8f, -3.3f, 0.9f, -3.6f, Tempo.SECOND, false),
                            run(Lane.RIGHT, 3.8f, 3.3f, 0.9f, 3.6f, Tempo.SECOND, false)),
                    List.of(opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE), opt(SetType.BACK_SET, Lane.RIGHT),
                            opt(SetType.QUICK_MIDDLE, Lane.MIDDLE))),
            play(SLIDE, "Slide", "The middle runs behind the setter and takes off from one foot for the fast slide in zone 2.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.4f, 0.2f, 0.6f, 2.4f, Tempo.SECOND, false),
                            run(Lane.OUTSIDE, 3.8f, -3.3f, 0.9f, -3.6f, Tempo.THIRD, false)),
                    List.of(opt(SetType.SLIDE, Lane.MIDDLE), opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            play(SETTER_DUMP, "Setter dump", "A front-row setter with a good pass tips the second ball to the free side.",
                    PassRating.GOOD, false,
                    List.of(run(Lane.MIDDLE, 2.6f, 0.4f, 0.7f, 0f, Tempo.FIRST, true)),
                    List.of(new Option(null, Lane.RIGHT), opt(SetType.QUICK_MIDDLE, Lane.MIDDLE),
                            opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE))),
            new Combination(EMERGENCY, "After a poor pass", "A high, safe set to the most convenient hitter.",
                    PassRating.EMERGENCY, List.of(),
                    List.of(opt(SetType.HIGH_OUTSIDE, Lane.OUTSIDE), opt(SetType.BACK_SET, Lane.RIGHT),
                            opt(SetType.BACK_ROW_RIGHT, Lane.BACK_RIGHT), opt(SetType.PIPE, Lane.BACK_MIDDLE)),
                    false, true, true));

    private final List<Combination> custom = new ArrayList<>();

    /** The built-in plays only. */
    public static Playbook standard() {
        return new Playbook();
    }

    public static List<Combination> builtIn() {
        return BUILT_IN;
    }

    /** All plays: built-in first, then custom. */
    public List<Combination> all() {
        List<Combination> all = new ArrayList<>(BUILT_IN);
        all.addAll(custom);
        return all;
    }

    public List<Combination> custom() {
        return Collections.unmodifiableList(custom);
    }

    /** Adds or replaces a custom play (by id). Built-in ids cannot be replaced. */
    public void putCustom(Combination play) {
        if (find(play.id()) != null && find(play.id()).builtIn()) return;
        custom.removeIf(c -> c.id().equals(play.id()));
        custom.add(play);
    }

    public void removeCustom(String id) {
        custom.removeIf(c -> c.id().equals(id));
    }

    public Combination find(String id) {
        for (Combination c : BUILT_IN) if (c.id().equals(id)) return c;
        for (Combination c : custom) if (c.id().equals(id)) return c;
        return null;
    }

    public static Combination emergency() {
        for (Combination c : BUILT_IN) if (c.emergency()) return c;
        throw new IllegalStateException();
    }

    private static Combination play(String id, String name, String description, PassRating minPass, boolean readBlock,
                                    List<Run> runs, List<Option> options) {
        return new Combination(id, name, description, minPass, runs, options, readBlock, false, true);
    }

    private static Run run(Lane lane, float startDepth, float startLateral, float targetDepth, float targetLateral,
                           Tempo tempo, boolean decoy) {
        return new Run(lane, startDepth, startLateral, targetDepth, targetLateral, tempo, decoy);
    }

    private static Option opt(SetType set, Lane hitter) {
        return new Option(set, hitter);
    }
}
