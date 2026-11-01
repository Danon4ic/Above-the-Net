package com.volleyball.game.ai;

import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.tactics.Playbook;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.tactics.TeamTactics;
import com.volleyball.game.world.CourtSide;

import java.util.HashMap;
import java.util.Map;

/**
 * The computer team's head coach: watches the match from the bench and gives the team the same
 * commands a person can give (defence scheme, attacking plan, serving plan), from what it has
 * seen: how the other team scores against it, whether its own attacks get blocked, how its
 * reception holds, which of its plays score, which receiver struggles with its serves.
 *
 * <p>Only for a team without a person in it (the person commands their own team). The commands
 * are not shown anywhere in the match interface: the other side has to read them on the court.
 * A coach does not change its mind after every point: it waits a few rallies between changes and
 * gives older rallies less weight.
 */
final class TacticalCoach {
    /** Weight of everything seen so far after each rally (older rallies count less). */
    static final float DECAY = 0.93f;

    private final CourtSide own;
    private Difficulty difficulty;
    private final TeamTactics tactics;
    // The other team's attacks: all of them, and those that scored against us, by kind.
    private float oppAttacks;
    private float oppTips;
    private float killsCross;
    private float killsLine;
    private float killsTip;
    private float killsHard;
    // Our attacks.
    private float ownAttacks;
    private float ownBlocked;
    // Our reception of their serves.
    private float receptions;
    private float poorReceptions;
    // Our serves and how their receivers passed them.
    private float serves;
    private float serveErrors;
    private final Map<Integer, float[]> receiverPasses = new HashMap<>();
    // The current rally (from the touches).
    private BallTouch lastOppAttack;
    private boolean oppAttackTip;
    private boolean oppAttackHard;
    private boolean oppAttackCross;
    private int rallyTouches;
    private boolean ourServe;
    private boolean ourAttackThisRally;

    private int ralliesSinceChange = 99;
    private int setNumber = 1;

    TacticalCoach(CourtSide own, Difficulty difficulty, TeamTactics tactics) {
        this.own = own;
        this.difficulty = difficulty;
        this.tactics = tactics;
    }

    void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /** Whether this coach makes decisions at all (beginner teams play without a plan). */
    boolean active() {
        return difficulty != Difficulty.BEGINNER;
    }

    void newRally() {
        lastOppAttack = null;
        rallyTouches = 0;
        ourServe = false;
        ourAttackThisRally = false;
    }

    /** Every touch of the rally (both teams). */
    void record(BallTouch touch) {
        rallyTouches++;
        if (touch.type() == ContactType.SERVE) {
            ourServe = touch.side() == own;
            return;
        }
        // The other team's first touch after our serve: how that receiver copes.
        if (ourServe && rallyTouches == 2 && touch.side() != own && touch.rating() != null) {
            float[] r = receiverPasses.computeIfAbsent(touch.playerId(), id -> new float[2]);
            r[1] += 1f;
            if (poor(touch.rating())) r[0] += 1f;
        }
        // Our reception of their serve.
        if (!ourServe && rallyTouches == 2 && touch.side() == own && touch.rating() != null) {
            receptions += 1f;
            if (poor(touch.rating())) poorReceptions += 1f;
        }
        if (touch.type() == ContactType.ATTACK) {
            if (touch.side() == own) {
                ownAttacks += 1f;
                ourAttackThisRally = true;
            } else {
                oppAttacks += 1f;
                lastOppAttack = touch;
                float speed = touch.outgoingVelocity().len();
                oppAttackTip = AttackType.TIP.label.equals(touch.variant()) || speed < 12f;
                oppAttackHard = speed > 22f;
                float forward = Math.abs(touch.outgoingVelocity().x);
                float lateral = touch.outgoingVelocity().z;
                float hitZ = touch.contactPoint().z;
                oppAttackCross = -Math.signum(hitZ == 0f ? 1f : hitZ) * lateral / Math.max(1f, forward) > 0.2f;
                if (oppAttackTip) oppTips += 1f;
            }
        }
    }

    private static boolean poor(PassRating rating) {
        return rating == PassRating.LIMITED || rating == PassRating.EMERGENCY;
    }

    /**
     * After a rally: counts what decided it and, when it is time, rethinks the plan.
     *
     * @param lastTouchSide   the team of the rally's last touch (null: none)
     * @param lastTouchType   its kind
     * @param scoreOwn        our points in the set
     * @param scoreOther      theirs
     * @param set             the set being played
     * @param plays           our plays' results so far (tries and points per play id)
     */
    void rallyFinished(boolean won, CourtSide lastTouchSide, ContactType lastTouchType, int scoreOwn, int scoreOther, int set,
                       PlayResults plays) {
        decay();
        if (set != setNumber) {
            // A new set: the plan stays, the evidence fades faster.
            setNumber = set;
            decay();
            decay();
        }
        if (ourServe) {
            serves += 1f;
            // The serve itself lost the point: into the net, out, a foot fault.
            if (!won && lastTouchSide == own && lastTouchType == ContactType.SERVE) serveErrors += 1f;
        }
        if (!won && lastOppAttack != null && lastTouchSide != own && lastTouchType == ContactType.ATTACK) {
            // Their attack scored.
            if (oppAttackTip) killsTip += 1f;
            else if (oppAttackCross) killsCross += 1f;
            else killsLine += 1f;
            if (oppAttackHard) killsHard += 1f;
        }
        if (!won && ourAttackThisRally && lastTouchSide != own && lastTouchType == ContactType.BLOCK) ownBlocked += 1f;
        ralliesSinceChange++;
        int wait = difficulty == Difficulty.PROFESSIONAL ? 4 : 7;
        if (active() && ralliesSinceChange >= wait) decide(scoreOwn, scoreOther, plays);
    }

    private void decay() {
        oppAttacks *= DECAY;
        oppTips *= DECAY;
        killsCross *= DECAY;
        killsLine *= DECAY;
        killsTip *= DECAY;
        killsHard *= DECAY;
        ownAttacks *= DECAY;
        ownBlocked *= DECAY;
        receptions *= DECAY;
        poorReceptions *= DECAY;
        serves *= DECAY;
        serveErrors *= DECAY;
        for (float[] r : receiverPasses.values()) {
            r[0] *= DECAY;
            r[1] *= DECAY;
        }
    }

    /** The plan for the coming rallies, group by group. */
    private void decide(int scoreOwn, int scoreOther, PlayResults plays) {
        boolean changed = false;
        changed |= apply(TeamCommand.Group.DEFENSE, defence());
        changed |= apply(TeamCommand.Group.OFFENSE, offence(plays));
        TeamCommand serve = serving(scoreOwn, scoreOther);
        changed |= apply(TeamCommand.Group.SERVE, serve);
        if (changed) ralliesSinceChange = 0;
    }

    private boolean apply(TeamCommand.Group group, TeamCommand command) {
        TeamCommand current = tactics.active(group);
        if (current == command && command != TeamCommand.SERVE_RECEIVER) return false;
        if (command == null) tactics.clear(group);
        else tactics.set(command);
        return current != command;
    }

    /** Against what scores: their tips, their cross-court or line shots, their hard hitting. */
    TeamCommand defence() {
        float kills = killsCross + killsLine + killsTip;
        if (kills < 2.5f) return tactics.active(TeamCommand.Group.DEFENSE);
        if (oppAttacks > 3f && oppTips / oppAttacks > 0.3f && killsTip >= 1.8f) return TeamCommand.EXPECT_TIP;
        if (killsCross >= 2.5f && killsCross >= 2f * killsLine) return TeamCommand.CLOSE_CROSS;
        if (killsLine >= 2.5f && killsLine >= 2f * killsCross) return TeamCommand.CLOSE_LINE;
        if (kills >= 3.5f && killsHard / kills > 0.6f) return TeamCommand.PLAY_DEEP;
        return null;
    }

    /** Careful after poor reception, decoys when the block reads us, else more of what scores. */
    TeamCommand offence(PlayResults plays) {
        if (receptions >= 5f && poorReceptions / receptions > 0.4f) return TeamCommand.CAUTIOUS;
        if (ownAttacks >= 4f && ownBlocked >= 2.5f && ownBlocked / ownAttacks > 0.25f) {
            return difficulty == Difficulty.PROFESSIONAL ? TeamCommand.DECOY : TeamCommand.VS_SINGLE_BLOCK;
        }
        if (difficulty != Difficulty.PROFESSIONAL || plays == null) return null;
        TeamCommand best = null;
        float bestRate = 0f;
        float second = 0f;
        Object[][] families = {
                {TeamCommand.QUICK_ATTACK, new String[]{Playbook.FIRST_TEMPO, Playbook.SECOND_TEMPO}},
                {TeamCommand.ZONE_TWO, new String[]{Playbook.ZONE_TWO, Playbook.QUICK_BACK}},
                {TeamCommand.PIPE, new String[]{Playbook.PIPE, Playbook.BIC}},
                {TeamCommand.HIGH_OUTSIDE, new String[]{Playbook.HIGH_OUTSIDE}}};
        for (Object[] f : families) {
            int tries = 0;
            int points = 0;
            for (String id : (String[]) f[1]) {
                tries += plays.tries(id);
                points += plays.points(id);
            }
            if (tries < 4) continue;
            float rate = points / (float) tries;
            if (rate > bestRate) {
                second = bestRate;
                bestRate = rate;
                best = (TeamCommand) f[0];
            } else if (rate > second) {
                second = rate;
            }
        }
        return best != null && bestRate >= 0.6f && bestRate - second >= 0.15f ? best : null;
    }

    /** Safe after serve errors, at the weakest receiver, hard when the set is slipping away. */
    TeamCommand serving(int scoreOwn, int scoreOther) {
        if (serves >= 4f && serveErrors >= 2.5f && serveErrors / serves > 0.25f) return TeamCommand.SAFE_SERVE;
        if (difficulty == Difficulty.PROFESSIONAL) {
            int weakest = weakestReceiver();
            if (weakest >= 0) {
                tactics.setServeTarget(weakest);
                return TeamCommand.SERVE_RECEIVER;
            }
            if (scoreOther - scoreOwn >= 3 && scoreOther >= 15) return TeamCommand.POWER_SERVE;
        }
        return null;
    }

    /** The receiver who passes our serves poorly at least half of the time (enough passes seen), or -1. */
    private int weakestReceiver() {
        int best = -1;
        float worst = 0.5f;
        for (Map.Entry<Integer, float[]> e : receiverPasses.entrySet()) {
            float[] r = e.getValue();
            if (r[1] < 2.5f) continue;
            float share = r[0] / r[1];
            if (share >= worst) {
                worst = share;
                best = e.getKey();
            }
        }
        return best;
    }

    /** Our plays' results, from the play caller. */
    interface PlayResults {
        int tries(String playId);

        int points(String playId);
    }
}
