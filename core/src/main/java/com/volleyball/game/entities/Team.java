package com.volleyball.game.entities;

import com.badlogic.gdx.graphics.Color;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A team: its side of the court, colours and players on court. */
public final class Team {
    public final String name;
    public final String shortName;
    public final CourtSide side;
    public final Color shirtColor;
    public final Color shortsColor;
    private final List<VolleyballPlayer> players = new ArrayList<>();
    /** Libero shirt: a contrasting colour, as the rules require (see {@link #chooseLiberoShirt}). */
    public final Color liberoShirtColor = new Color();

    /** Libero shirt colours to choose from: bright, clearly different from one another. */
    static final Color[] LIBERO_COLORS = {
            new Color(1f, 0.86f, 0.1f, 1f),     // signal yellow
            new Color(0.55f, 0.9f, 0.18f, 1f),  // lime
            new Color(1f, 0.5f, 0.08f, 1f),     // orange
            new Color(0.92f, 0.2f, 0.62f, 1f),  // magenta
            new Color(0.1f, 0.78f, 0.88f, 1f),  // turquoise
            new Color(0.5f, 0.24f, 0.85f, 1f),  // violet
            new Color(0.95f, 0.95f, 0.95f, 1f), // white
            new Color(0.12f, 0.12f, 0.15f, 1f), // black
            new Color(0.86f, 0.1f, 0.14f, 1f),  // red
            new Color(0.1f, 0.3f, 0.86f, 1f)};  // royal blue
    private VolleyballPlayer libero;
    /** Substitutes on the bench (rule 15): they can replace line-up players between rallies. */
    private final List<VolleyballPlayer> substitutes = new ArrayList<>();
    /**
     * Cached {@link #squad()} and {@link #onCourt()} lists (the AI asks for them many times a step).
     * A returned list is never changed afterwards: when the roster or who is on court changes, a
     * new list is made, so a caller holding an old one keeps a consistent snapshot.
     */
    private List<VolleyballPlayer> squadCache;
    private List<VolleyballPlayer> onCourtCache = List.of();
    /** The head coach (drawn on the sideline). */
    private String coach = "Head coach";
    /** One line about how the team plays (team selection). */
    private String style = "";

    public Team(String name, String shortName, CourtSide side, Color shirtColor, Color shortsColor) {
        this.name = name;
        this.shortName = shortName;
        this.side = side;
        this.shirtColor = shirtColor;
        this.shortsColor = shortsColor;
        chooseLiberoShirt(null);
    }

    /**
     * Picks the libero shirt: of {@link #LIBERO_COLORS}, the one that differs most from the team's
     * own shirt and shorts and from the opponent's shirt (if known), so the libero stands out on
     * both sides of the net.
     */
    public void chooseLiberoShirt(Color opponentShirt) {
        Color best = LIBERO_COLORS[0];
        float bestScore = -1f;
        for (Color c : LIBERO_COLORS) {
            float score = Math.min(distance(c, shirtColor), distance(c, shortsColor) * 1.6f);
            if (opponentShirt != null) score = Math.min(score, distance(c, opponentShirt) * 1.25f);
            // Liberos are usually in a bright colour: white and black only when nothing bright stands out.
            if (luminance(c) > 0.9f || luminance(c) < 0.15f) score *= 0.8f;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        liberoShirtColor.set(best);
    }

    /** How different two colours look: RGB distance weighted by the eye's sensitivity, plus brightness. */
    static float distance(Color a, Color b) {
        float dr = a.r - b.r;
        float dg = a.g - b.g;
        float db = a.b - b.b;
        float dl = luminance(a) - luminance(b);
        return (float) Math.sqrt(0.3f * dr * dr + 0.59f * dg * dg + 0.11f * db * db + 0.6f * dl * dl);
    }

    public static float luminance(Color c) {
        return 0.299f * c.r + 0.587f * c.g + 0.114f * c.b;
    }

    public String coach() {
        return coach;
    }

    public String style() {
        return style;
    }

    public void setCoach(String coach, String style) {
        this.coach = coach;
        this.style = style;
    }

    public void addPlayer(VolleyballPlayer player) {
        if (player.side != side) throw new IllegalArgumentException("Player " + player.name + " is on the other side");
        players.add(player);
        squadCache = null;
    }

    /** The six players of the line-up (in their starting zones 1..6). */
    public List<VolleyballPlayer> players() {
        return Collections.unmodifiableList(players);
    }

    /** Registers the libero (rule 19): a back-row specialist who replaces a back-row player. */
    public void setLibero(VolleyballPlayer libero) {
        if (libero.side != side || libero.role != PlayerRole.LIBERO) throw new IllegalArgumentException("Not a libero of this team");
        this.libero = libero;
        libero.setOnCourt(false);
        squadCache = null;
    }

    public VolleyballPlayer libero() {
        return libero;
    }

    public void addSubstitute(VolleyballPlayer player) {
        if (player.side != side) throw new IllegalArgumentException("Player " + player.name + " is on the other side");
        player.setOnCourt(false);
        substitutes.add(player);
        squadCache = null;
    }

    public List<VolleyballPlayer> substitutes() {
        return Collections.unmodifiableList(substitutes);
    }

    /** The starting six, the libero and the substitutes: everyone on the team sheet. */
    public List<VolleyballPlayer> squad() {
        if (squadCache == null) {
            List<VolleyballPlayer> all = new ArrayList<>(players);
            if (libero != null) all.add(libero);
            all.addAll(substitutes);
            squadCache = Collections.unmodifiableList(all);
        }
        return squadCache;
    }

    /** Players currently on court (the libero replaces one back-row player while in). Do not modify. */
    public List<VolleyballPlayer> onCourt() {
        List<VolleyballPlayer> squad = squad();
        // Still the same players on court (in squad order)? Then the cached list is current.
        int matched = 0;
        boolean same = true;
        for (int i = 0; i < squad.size() && same; i++) {
            VolleyballPlayer p = squad.get(i);
            if (!p.isOnCourt()) continue;
            same = matched < onCourtCache.size() && onCourtCache.get(matched) == p;
            matched++;
        }
        if (same && matched == onCourtCache.size()) return onCourtCache;
        List<VolleyballPlayer> list = new ArrayList<>(7);
        for (VolleyballPlayer p : squad) if (p.isOnCourt()) list.add(p);
        onCourtCache = Collections.unmodifiableList(list);
        return onCourtCache;
    }
}
