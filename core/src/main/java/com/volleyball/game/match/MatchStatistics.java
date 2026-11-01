package com.volleyball.game.match;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The match statistics, credited the way a scorer does it:
 * <ul>
 *   <li><b>serve</b>: aces (the serve wins the point) and serve errors;</li>
 *   <li><b>attack</b>: attempts, kills (the attack wins the point, a block-out included), errors
 *       (out, net) and attacks stopped by the block; efficiency = (kills − errors − blocked) / attempts;</li>
 *   <li><b>block</b>: block points (a block that wins the point) and block touches;</li>
 *   <li><b>defence</b>: digs (a first touch after the opponent's attack that the team plays on);</li>
 *   <li><b>reception</b>: receptions graded perfect 3, good 2, limited 1, emergency or error 0;</li>
 *   <li><b>setting</b>: assists (the set before a kill);</li>
 *   <li><b>errors</b>: everything that gave the opponent a point (a fault is the faulting player's).</li>
 * </ul>
 */
public final class MatchStatistics implements BallTouchListener {
    /** One player's numbers. */
    public static final class Line {
        public final int playerId;
        public int serves, aces, serveErrors;
        public int attacks, kills, attackErrors, blocked;
        public int blockPoints, blockTouches;
        public int digs;
        public int receptions, receptionScore, perfectReceptions, receptionErrors;
        public int sets, assists;
        public int errors;

        Line(int playerId) {
            this.playerId = playerId;
        }

        public int points() {
            return kills + aces + blockPoints;
        }

        /** (kills − errors − blocked) / attempts, or NaN without attacks. */
        public float attackEfficiency() {
            return attacks == 0 ? Float.NaN : (kills - attackErrors - blocked) / (float) attacks;
        }

        public float killRate() {
            return attacks == 0 ? Float.NaN : kills / (float) attacks;
        }

        /** Average reception grade 0..3, or NaN. */
        public float receptionAverage() {
            return receptions == 0 ? Float.NaN : receptionScore / (float) receptions;
        }

        /** Share of receptions graded good or perfect, or NaN. */
        public float positiveReception() {
            return receptions == 0 ? Float.NaN : goodReceptions / (float) receptions;
        }

        int goodReceptions;

        void add(Line o) {
            serves += o.serves;
            aces += o.aces;
            serveErrors += o.serveErrors;
            attacks += o.attacks;
            kills += o.kills;
            attackErrors += o.attackErrors;
            blocked += o.blocked;
            blockPoints += o.blockPoints;
            blockTouches += o.blockTouches;
            digs += o.digs;
            receptions += o.receptions;
            receptionScore += o.receptionScore;
            perfectReceptions += o.perfectReceptions;
            receptionErrors += o.receptionErrors;
            goodReceptions += o.goodReceptions;
            sets += o.sets;
            assists += o.assists;
            errors += o.errors;
        }
    }

    private final Map<Integer, Line> lines = new LinkedHashMap<>();
    private final Map<CourtSide, List<VolleyballPlayer>> rosters = new LinkedHashMap<>();
    private int rallies;
    private int homePoints;
    private int awayPoints;

    // The rally so far.
    private BallTouch serve;
    private BallTouch previous;
    private BallTouch last;
    /** The attack in the air (and the set before it), until something else happens to the ball. */
    private BallTouch attack;
    private BallTouch attackSet;
    /** A first touch after the opponent's attack: a dig if the team plays on. */
    private BallTouch digCandidate;
    private boolean receptionPending;
    private BallTouch reception;

    public MatchStatistics(List<VolleyballPlayer> home, List<VolleyballPlayer> away) {
        rosters.put(CourtSide.HOME, home);
        rosters.put(CourtSide.AWAY, away);
        for (VolleyballPlayer p : home) lines.put(p.id, new Line(p.id));
        for (VolleyballPlayer p : away) lines.put(p.id, new Line(p.id));
    }

    private Line line(int id) {
        return id < 0 ? null : lines.computeIfAbsent(id, Line::new);
    }

    @Override
    public void onBallTouch(BallTouch t) {
        Line l = line(t.playerId());
        if (l == null) return;
        // A dig: the team played on after the first touch that followed the opponent's attack.
        if (digCandidate != null && t.side() == digCandidate.side() && t.type() != ContactType.BLOCK && t.teamTouchNumber() >= 2) {
            line(digCandidate.playerId()).digs++;
            digCandidate = null;
        } else if (digCandidate != null && t.side() != digCandidate.side()) {
            digCandidate = null;
        }
        switch (t.type()) {
            case SERVE -> {
                newRally();
                serve = t;
                receptionPending = true;
                l.serves++;
            }
            case BLOCK -> l.blockTouches++;
            case ATTACK -> {
                l.attacks++;
                attack = t;
                attackSet = last != null && last.side() == t.side() && last.purpose() == ContactPurpose.SET ? last : null;
            }
            default -> {
            }
        }
        if (t.type() != ContactType.SERVE && t.type() != ContactType.BLOCK) {
            boolean firstTouch = t.teamTouchNumber() == 1;
            if (receptionPending && firstTouch && serve != null && t.side() != serve.side()) {
                receptionPending = false;
                reception = t;
                l.receptions++;
                int grade = grade(t.rating());
                l.receptionScore += grade;
                if (grade >= 2) l.goodReceptions++;
                if (t.rating() == PassRating.PERFECT) l.perfectReceptions++;
            } else if (firstTouch && attack != null && attack.side() != t.side()) {
                digCandidate = t;
            }
            if (t.purpose() == ContactPurpose.SET) l.sets++;
            // Anything but an attack (or a block touch) ends the attack in the air.
            if (t.type() != ContactType.ATTACK) attack = null;
        }
        previous = last;
        last = t;
    }

    private static int grade(PassRating rating) {
        if (rating == null) return 1;
        return switch (rating) {
            case PERFECT -> 3;
            case GOOD -> 2;
            case LIMITED -> 1;
            case EMERGENCY -> 0;
        };
    }

    /** The rally's result: credits the point and the error. */
    public void onPoint(MatchRules.PointSummary point) {
        if (point.outcome().isReplay()) {
            newRally();
            return;
        }
        rallies++;
        CourtSide winner = point.outcome().winner();
        if (winner == CourtSide.HOME) homePoints++;
        else awayPoints++;
        Fault reason = point.outcome().reason();
        boolean ballFault = reason == Fault.BALL_IN || reason == Fault.BALL_OUT || reason == Fault.ANTENNA
                || reason == Fault.OUTSIDE_CROSSING_SPACE || reason == Fault.OUT_OF_PLAY_OBJECT || reason == Fault.UNDER_NET;
        if (!ballFault) {
            // A player's fault (net touch, double contact, foot fault...): their error.
            Line offender = line(point.outcome().playerId());
            if (offender != null) offender.errors++;
            if (last != null && last.type() == ContactType.SERVE && serve != null && serve.side() != winner) line(serve.playerId()).serveErrors++;
            newRally();
            return;
        }
        if (last == null) {
            newRally();
            return;
        }
        Line l = line(last.playerId());
        boolean lastWon = last.side() == winner;
        switch (last.type()) {
            case SERVE -> {
                if (lastWon) l.aces++;
                else {
                    l.serveErrors++;
                    l.errors++;
                }
            }
            case ATTACK -> {
                if (lastWon) credit(last, attackSet);
                else {
                    l.attackErrors++;
                    l.errors++;
                }
            }
            case BLOCK -> {
                if (lastWon) {
                    l.blockPoints++;
                    if (attack != null && attack.side() != last.side()) line(attack.playerId()).blocked++;
                } else if (attack != null && attack.side() == winner) {
                    // Off the block and out (or down on the blocker's side): the attacker's kill.
                    credit(attack, attackSet);
                } else {
                    l.errors++;
                }
            }
            default -> {
                if (!lastWon) {
                    l.errors++;
                    if (last == reception) l.receptionErrors++;
                }
            }
        }
        // An ace through a reception: the serve won the point after the receiver touched it.
        if (serve != null && serve.side() == winner && reception != null && last == reception && previous == serve) {
            line(serve.playerId()).aces++;
        }
        newRally();
    }

    private void credit(BallTouch attackTouch, BallTouch set) {
        line(attackTouch.playerId()).kills++;
        if (set != null) line(set.playerId()).assists++;
    }

    private void newRally() {
        serve = null;
        previous = null;
        last = null;
        attack = null;
        attackSet = null;
        digCandidate = null;
        receptionPending = false;
        reception = null;
    }

    // ------------------------------------------------------------------ reading

    public int rallies() {
        return rallies;
    }

    public int points(CourtSide side) {
        return side == CourtSide.HOME ? homePoints : awayPoints;
    }

    /** A player's line (empty if the player never played). */
    public Line of(VolleyballPlayer p) {
        return line(p.id);
    }

    /** The team's players, in roster order. */
    public List<VolleyballPlayer> roster(CourtSide side) {
        return rosters.get(side);
    }

    /** The team's totals. */
    public Line total(CourtSide side) {
        Line sum = new Line(-1);
        for (VolleyballPlayer p : rosters.get(side)) sum.add(line(p.id));
        return sum;
    }

    /** Players of a team who did something in the match (for the per-player table). */
    public List<VolleyballPlayer> active(CourtSide side) {
        List<VolleyballPlayer> list = new ArrayList<>();
        for (VolleyballPlayer p : rosters.get(side)) {
            Line l = line(p.id);
            if (l.serves + l.attacks + l.blockTouches + l.digs + l.receptions + l.sets + l.errors > 0) list.add(p);
        }
        return list;
    }
}
