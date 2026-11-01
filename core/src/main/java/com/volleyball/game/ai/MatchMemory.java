package com.volleyball.game.ai;

import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a team remembers: the touches of the current rally (both teams), and over the match the
 * opponent hitters' habits (line, cross or tip) and how well its own choices worked (which sets
 * score, which serve zones break the reception). Only what could be seen on court is recorded.
 */
public final class MatchMemory {
    /** One touch of the current rally. */
    public record Touch(int playerId, CourtSide side, ContactType type, PassRating rating, String variant) {
    }

    /** An opponent hitter's attack directions so far. */
    public static final class Habit {
        public int line;
        public int cross;
        public int tips;

        public int total() {
            return line + cross + tips;
        }

        /** -1 (always line) .. +1 (always cross); 0 without data. */
        public float crossBias() {
            int hits = line + cross;
            return hits == 0 ? 0f : (cross - line) / (float) hits;
        }

        public float tipShare() {
            return total() == 0 ? 0f : tips / (float) total();
        }
    }

    private final CourtSide own;
    private final List<Touch> rally = new ArrayList<>();
    private final Map<Integer, Habit> habits = new HashMap<>();
    private final EnumMap<SetType, float[]> setResults = new EnumMap<>(SetType.class);
    private final float[] serveZoneWeight = {1f, 1f, 1f, 1f, 1f, 1f, 1f};
    private int lastServeZone = -1;
    private SetType lastSet;

    public MatchMemory(CourtSide own) {
        this.own = own;
        for (SetType type : SetType.values()) setResults.put(type, new float[]{1f, 2f}); // prior: 1 of 2
    }

    public void newRally() {
        rally.clear();
        lastSet = null;
    }

    public void record(BallTouch touch) {
        rally.add(new Touch(touch.playerId(), touch.side(), touch.type(), touch.rating(), touch.variant()));
        if (touch.side() == own && touch.purpose() == ContactPurpose.SET) {
            for (SetType type : SetType.values()) if (type.label.equals(touch.variant())) lastSet = type;
        }
        boolean receptionOfOurServe = rally.size() == 2 && rally.get(0).type() == ContactType.SERVE
                && rally.get(0).side() == own && touch.side() != own;
        if (receptionOfOurServe && lastServeZone > 0) {
            // The opponent's reception of our serve: reward the zone if it was poor.
            PassRating rating = touch.rating();
            if (rating == PassRating.LIMITED || rating == PassRating.EMERGENCY) serveZoneWeight[lastServeZone] *= 1.25f;
            else if (rating == PassRating.PERFECT) serveZoneWeight[lastServeZone] *= 0.9f;
            normalizeServeWeights();
        }
        if (touch.side() != own && touch.type() == ContactType.ATTACK) {
            Habit habit = habits.computeIfAbsent(touch.playerId(), id -> new Habit());
            if (isTip(touch)) habit.tips++;
            else if (isCross(touch)) habit.cross++;
            else habit.line++;
        }
    }

    /** An attack played soft: a tip (or anything slower than a real swing). */
    static boolean isTip(BallTouch attack) {
        return AttackType.TIP.label.equals(attack.variant()) || attack.outgoingVelocity().len() < 12f;
    }

    /** A cross-court attack: the ball travels towards the other half of the court's width. */
    static boolean isCross(BallTouch attack) {
        float forward = Math.abs(attack.outgoingVelocity().x);
        float lateral = attack.outgoingVelocity().z;
        float hitZ = attack.contactPoint().z;
        return -Math.signum(hitZ == 0f ? 1f : hitZ) * lateral / Math.max(1f, forward) > 0.2f;
    }

    /** The rally's result for this team: what worked is used more often (adaptive tactics). */
    public void rallyResult(boolean won) {
        if (lastSet != null && decidedByOwnAttack()) {
            float[] r = setResults.get(lastSet);
            r[1] += 1f;
            if (won) r[0] += 1f;
        }
        boolean ace = won && rally.size() == 1 && rally.get(0).side() == own && rally.get(0).type() == ContactType.SERVE;
        if (lastServeZone > 0 && ace) {
            serveZoneWeight[lastServeZone] *= 1.3f;
            normalizeServeWeights();
        }
    }

    /**
     * Whether the rally was decided by our attack: it was the last touch, or only the opponent's
     * block touched the ball after it (a stuff block or a tool off the block).
     */
    public boolean decidedByOwnAttack() {
        int i = rally.size() - 1;
        while (i >= 0 && rally.get(i).side() != own && rally.get(i).type() == ContactType.BLOCK) i--;
        return i >= 0 && rally.get(i).side() == own && rally.get(i).type() == ContactType.ATTACK;
    }

    public void servedTo(int zone) {
        lastServeZone = zone;
    }

    private void normalizeServeWeights() {
        float sum = 0f;
        for (int z = 1; z <= 6; z++) sum += serveZoneWeight[z];
        for (int z = 1; z <= 6; z++) serveZoneWeight[z] = Math.max(0.3f, Math.min(3f, serveZoneWeight[z] * 6f / sum));
    }

    /** Relative preference (around 1) for serving into a zone of the opponent's court. */
    public float serveZoneWeight(int zone) {
        return serveZoneWeight[zone];
    }

    /** Share of points won after this set (with a neutral prior of 50 %). */
    public float setSuccess(SetType type) {
        float[] r = setResults.get(type);
        return r[0] / r[1];
    }

    public Habit habit(int playerId) {
        return habits.get(playerId);
    }

    public List<Touch> rallyTouches() {
        return rally;
    }

    /** Rating of the own team's last first-touch pass in this rally, or null. */
    public PassRating lastOwnPass() {
        for (int i = rally.size() - 1; i >= 0; i--) {
            Touch t = rally.get(i);
            if (t.side() == own && t.rating() != null) return t.rating();
        }
        return null;
    }
}
