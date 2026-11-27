package com.volleyball.game.entities;

/** Body shape for ball actions, used by the renderer to pose arms (prepared or executing). */
public enum ActionPose {
    NONE,
    /** Forearms joined in front of the hips. */
    PLATFORM,
    /** A standing dig with one arm outside the platform's reach. */
    ONE_ARM_DIG,
    /** Reaching for the ball during the dive or its slide. */
    DIVE_DIG,
    /** Hands above the forehead. */
    OVERHEAD,
    SET_FORWARD, SET_BACK, SET_FAST, SET_FAST_BACK, SET_ONE_HAND,
    /** A short arm draw before an attack in the same jump as the set. */
    QUICK_READY, QUICK_SWING,
    /** Hitting arm cocked or swinging (attack, overhand serve). */
    SWING,
    /** Toss arm raised (serve). */
    TOSS,
    /** Both arms up and pressed over the net. */
    BLOCK,
    /** Ball held in both hands in front of the body, waiting to serve. */
    HOLD_BALL,
    /** Point won: arms up, fists pumping. */
    CELEBRATE,
    /** A teammate close by after a point won: the hitting hand up to slap theirs. */
    HIGH_FIVE,
    BENCH_SIT, BENCH_RISE,
    /** After a point lost: a short clap of encouragement in front of the chest. */
    CLAP,
    /** After a point lost: head down, hands on the hips (or on the knees), a moment of frustration. */
    DISAPPOINTED,
    // The first referee's hand signals (right / left: which arm points).
    /** Authorisation to serve: the arm sweeps towards the serving team. */
    REF_SERVE_RIGHT, REF_SERVE_LEFT,
    /** The team that serves next: the arm extended towards it. */
    REF_POINT_RIGHT, REF_POINT_LEFT,
    /** Ball in: the arm and fingers point to the floor. */
    REF_IN,
    /** Ball out: both forearms raised, hands open, palms towards the body. */
    REF_OUT,
    /** Net touch (or the ball under the net): a hand on the net's side. */
    REF_NET,
    /** Four hits: the arm up, four fingers spread. */
    REF_FOUR_HITS,
    /** Double contact: the arm up, two fingers. */
    REF_DOUBLE,
    /** Held / thrown ball: the forearm lifted slowly, palm up. */
    REF_HELD,
    /** Timeout: the hands form a T. */
    REF_TIMEOUT;

    public boolean set() {
        return this == SET_FORWARD || this == SET_BACK || this == SET_FAST || this == SET_FAST_BACK || this == SET_ONE_HAND;
    }

    public boolean fastSet() {
        return this == SET_FAST || this == SET_FAST_BACK;
    }

    public boolean backSet() {
        return this == SET_BACK || this == SET_FAST_BACK;
    }

    public boolean overhead() {
        return this == OVERHEAD || set();
    }

    public boolean swing() {
        return this == SWING || this == QUICK_SWING;
    }
}
