package com.volleyball.game.world;

/** Where the match is played. Purely visual: the court, the rules and the physics are the same. */
public enum Venue {
    /**
     * A championship arena: a two-tier bowl, light trusses with floodlights over the court, LED
     * ribbon boards, a centre-hung video cube, TV cameras and a cable camera.
     */
    CHAMPIONSHIP("Championship arena", false),
    /** An open-air beach stadium: sand, temporary bleachers, sun and sea, palm trees. */
    BEACH("Beach stadium", true),
    /**
     * A big school gym: daylight through high windows, fluorescent lights, wall bars, basketball
     * backboards, climbing ropes, mats and fold-out bleachers with parents and classmates.
     */
    SCHOOL_GYM("School gym", false),
    /**
     * An old sports palace: one steep bowl of wooden benches, a ribbed concrete ceiling with a ring
     * of round lamps, a mosaic panel over one end, club pennants and warm light.
     */
    SPORTS_PALACE("Sports palace", false),
    /**
     * A court on a skyscraper roof at night: the city's lights all around, floodlight masts, string
     * lights, neon boards and glass barriers.
     */
    ROOFTOP("Rooftop court", true);

    public final String label;
    /** Under the open sky (a sky dome instead of a roof). */
    public final boolean outdoor;

    Venue(String label, boolean outdoor) {
        this.label = label;
        this.outdoor = outdoor;
    }

    public Venue next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Venue fromName(String name) {
        for (Venue v : values()) if (v.name().equals(name)) return v;
        return CHAMPIONSHIP;
    }
}
