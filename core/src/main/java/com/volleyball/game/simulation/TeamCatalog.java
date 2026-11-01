package com.volleyball.game.simulation;

import com.badlogic.gdx.graphics.Color;
import com.volleyball.game.entities.PlayerTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The clubs that can be picked (or drawn at random) for a quick match: names, home and away kits,
 * rosters, a strength rating, a coach and a playing identity. Northern Wolves and Coastal Sharks
 * are the default home and away teams.
 */
public final class TeamCatalog {
    /**
     * One club.
     *
     * @param strength  added to every rating of every player (-8 weak .. +8 strong)
     * @param starters  line-up in the 5-1 order of {@link TeamFactory}: S, OH, MB, OPP, OH, MB
     * @param subs      substitutes: S, OH, MB, OPP
     * @param focus     the traits the club is known for: they come first when traits are drawn
     */
    public record Entry(String id, String name, String shortName, Color shirt, Color shorts, Color awayShirt, Color awayShorts,
                        int strength, String coach, String style, String[] starters, int[] numbers, String libero,
                        int liberoNumber, String[] subs, int[] subNumbers,
                        List<PlayerTrait> focus) {
        /** The kit for a match: the away kit when the home kit looks too much like the opponent's. */
        public Color[] kitAgainst(Entry other, boolean home) {
            return kitAgainst(other.shirt, home, false);
        }

        /**
         * The kit against an opponent wearing {@code otherShirt}: the away kit when asked for, or
         * (for the visiting team) when the home kit looks too much like the opponent's shirt.
         */
        public Color[] kitAgainst(Color otherShirt, boolean home, boolean preferAway) {
            if (preferAway || (!home && clash(shirt, otherShirt))) return new Color[]{awayShirt, awayShorts};
            return new Color[]{shirt, shorts};
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        add("wolves", "Northern Wolves", "NWO", rgb(0.12f, 0.32f, 0.78f), rgb(0.95f, 0.95f, 0.97f),
                rgb(0.92f, 0.93f, 0.96f), rgb(0.12f, 0.32f, 0.78f), 0, "Igor Petrov", "Balanced, disciplined, strong serve receive",
                new String[]{"Volkov", "Smirnov", "Orlov", "Belov", "Kuznetsov", "Morozov"}, new int[]{7, 10, 14, 9, 11, 3},
                "Sokolov", 1, new String[]{"Lebedev", "Kozlov", "Novikov", "Popov"}, new int[]{15, 4, 12, 17},
                List.of(PlayerTrait.READER, PlayerTrait.WORKHORSE));
        add("sharks", "Coastal Sharks", "CSH", rgb(0.82f, 0.14f, 0.16f), rgb(0.12f, 0.12f, 0.14f),
                rgb(0.97f, 0.95f, 0.92f), rgb(0.82f, 0.14f, 0.16f), 0, "Marco Bellini", "Aggressive serving and power hitting",
                new String[]{"Rossi", "Silva", "Novak", "Kowalski", "Tanaka", "Moreau"}, new int[]{5, 12, 18, 2, 4, 16},
                "Garcia", 8, new String[]{"Bianchi", "Costa", "Weber", "Dubois"}, new int[]{9, 13, 6, 11},
                List.of(PlayerTrait.POWER_SERVER, PlayerTrait.CANNON));
        add("titans", "Desert Titans", "DTI", rgb(0.93f, 0.62f, 0.12f), rgb(0.30f, 0.18f, 0.08f),
                rgb(0.22f, 0.14f, 0.07f), rgb(0.93f, 0.62f, 0.12f), 5, "Omar Haddad", "Tall and heavy: a big block and huge hitting",
                new String[]{"Haddad", "Karimi", "Farouk", "Mansour", "Nasser", "Aziz"}, new int[]{3, 8, 19, 11, 6, 21},
                "Saleh", 2, new String[]{"Rahimi", "Tariq", "Bakr", "Yusuf"}, new int[]{14, 7, 10, 17},
                List.of(PlayerTrait.WALL, PlayerTrait.CANNON));
        add("falcons", "Alpine Falcons", "ALF", rgb(0.10f, 0.58f, 0.36f), rgb(0.95f, 0.95f, 0.95f),
                rgb(0.95f, 0.96f, 0.94f), rgb(0.10f, 0.45f, 0.30f), 2, "Lukas Brandt", "Fast tempo: quicks, slides and the pipe",
                new String[]{"Brandt", "Huber", "Steiner", "Gruber", "Wagner", "Hofer"}, new int[]{4, 9, 13, 17, 2, 20},
                "Egger", 1, new String[]{"Mayer", "Bauer", "Koch", "Frei"}, new int[]{11, 5, 15, 8},
                List.of(PlayerTrait.TEMPO_SETTER, PlayerTrait.CRAFTY));
        add("storm", "Harbor Storm", "HST", rgb(0.10f, 0.12f, 0.22f), rgb(0.10f, 0.12f, 0.22f),
                rgb(0.62f, 0.82f, 0.95f), rgb(0.10f, 0.12f, 0.22f), 3, "Jenna Walsh", "Relentless defence: nothing hits the floor",
                new String[]{"Walsh", "Murphy", "O'Brien", "Kelly", "Byrne", "Doyle"}, new int[]{6, 1, 15, 10, 12, 18},
                "Ryan", 5, new String[]{"Nolan", "Quinn", "Burke", "Lynch"}, new int[]{3, 14, 9, 16},
                List.of(PlayerTrait.DIGGER, PlayerTrait.READER));
        add("dragons", "Jade Dragons", "JDR", rgb(0.70f, 0.08f, 0.30f), rgb(0.95f, 0.80f, 0.20f),
                rgb(0.98f, 0.86f, 0.30f), rgb(0.70f, 0.08f, 0.30f), 4, "Kenji Watanabe", "Precise: floats, tips and the open spots",
                new String[]{"Watanabe", "Sato", "Ishikawa", "Nishida", "Takahashi", "Yamamoto"}, new int[]{2, 11, 20, 14, 5, 7},
                "Ogawa", 6, new String[]{"Suzuki", "Fujii", "Kato", "Ito"}, new int[]{9, 12, 18, 3},
                List.of(PlayerTrait.FLOAT_SPECIALIST, PlayerTrait.CRAFTY));
        add("bears", "Granite Bears", "GRB", rgb(0.45f, 0.47f, 0.52f), rgb(0.15f, 0.15f, 0.17f),
                rgb(0.15f, 0.16f, 0.19f), rgb(0.45f, 0.47f, 0.52f), -4, "Pavel Dvorak", "Big-hearted underdogs: high balls and grit",
                new String[]{"Dvorak", "Horak", "Novotny", "Prochazka", "Cerny", "Marek"}, new int[]{8, 3, 16, 12, 7, 19},
                "Kral", 4, new String[]{"Svoboda", "Pokorny", "Hajek", "Benes"}, new int[]{10, 2, 13, 6},
                List.of(PlayerTrait.HIGH_BALL_SETTER, PlayerTrait.WORKHORSE));
        add("comets", "Sunset Comets", "SCO", rgb(0.55f, 0.22f, 0.75f), rgb(0.98f, 0.55f, 0.15f),
                rgb(0.98f, 0.62f, 0.20f), rgb(0.40f, 0.15f, 0.55f), -2, "Diego Alvarez", "Young and wild: big swings and bigger errors",
                new String[]{"Alvarez", "Mendes", "Duarte", "Pereira", "Lopes", "Ramos"}, new int[]{9, 13, 4, 1, 18, 22},
                "Vieira", 7, new String[]{"Moura", "Teixeira", "Rocha", "Neves"}, new int[]{5, 11, 15, 2},
                List.of(PlayerTrait.HOTHEAD, PlayerTrait.POWER_SERVER));
    }

    private TeamCatalog() {}

    private static void add(String id, String name, String shortName, Color shirt, Color shorts, Color awayShirt, Color awayShorts,
                            int strength, String coach, String style, String[] starters, int[] numbers, String libero,
                            int liberoNumber, String[] subs, int[] subNumbers, List<PlayerTrait> focus) {
        ENTRIES.add(new Entry(id, name, shortName, shirt, shorts, awayShirt, awayShorts, strength, coach, style, starters, numbers,
                libero, liberoNumber, subs, subNumbers, focus));
    }

    private static Color rgb(float r, float g, float b) {
        return new Color(r, g, b, 1f);
    }

    public static List<Entry> all() {
        return List.copyOf(ENTRIES);
    }

    /** The club with this id (case does not matter), or null. */
    public static Entry byId(String id) {
        if (id == null) return null;
        for (Entry e : ENTRIES) if (e.id().equalsIgnoreCase(id) || e.shortName().equalsIgnoreCase(id)) return e;
        return null;
    }

    public static Entry defaultHome() {
        return ENTRIES.get(0);
    }

    public static Entry defaultAway() {
        return ENTRIES.get(1);
    }

    /** A random club other than {@code not} (null: any). */
    public static Entry random(Random random, Entry not) {
        while (true) {
            Entry e = ENTRIES.get(random.nextInt(ENTRIES.size()));
            if (e != not) return e;
        }
    }

    /** Whether two shirt colours are too alike to tell the teams apart. */
    static boolean clash(Color a, Color b) {
        float dr = a.r - b.r;
        float dg = a.g - b.g;
        float db = a.b - b.b;
        return dr * dr + dg * dg + db * db < 0.12f;
    }
}
