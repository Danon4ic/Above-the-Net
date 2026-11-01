package com.volleyball.game.audio;

/** Every sound the game can play, with the mixer channel whose volume applies to it. */
public enum SoundId {
    // Ball contacts
    SERVE_FLOAT(Channel.EFFECTS),
    SERVE_POWER(Channel.EFFECTS),
    SERVE_TOSS(Channel.EFFECTS),
    FOREARM_PASS(Channel.EFFECTS),
    OVERHEAD_SET(Channel.EFFECTS),
    SPIKE_HARD(Channel.EFFECTS),
    SPIKE_SOFT(Channel.EFFECTS),
    BLOCK(Channel.EFFECTS),
    DIG(Channel.EFFECTS),
    BALL_FLOOR(Channel.EFFECTS),
    BALL_NET(Channel.EFFECTS),
    BALL_METAL(Channel.EFFECTS),
    BALL_WALL(Channel.EFFECTS),
    BALL_BODY(Channel.EFFECTS),
    // Players
    SHOE_SQUEAK(Channel.EFFECTS),
    LANDING(Channel.EFFECTS),
    BODY_FALL(Channel.EFFECTS),
    GRUNT(Channel.EFFECTS),
    CALL(Channel.EFFECTS),
    // Referee and arena
    WHISTLE_SHORT(Channel.EFFECTS),
    WHISTLE_LONG(Channel.EFFECTS),
    POINT_CHIME(Channel.EFFECTS),
    SET_HORN(Channel.EFFECTS),
    TIMEOUT_BUZZER(Channel.EFFECTS),
    // Crowd
    CROWD_AMBIENT(Channel.CROWD),
    CROWD_CHEER(Channel.CROWD),
    CROWD_GROAN(Channel.CROWD),
    CROWD_OOH(Channel.CROWD),
    CROWD_CLAPS(Channel.CROWD),
    /** The fans chant and stamp at set point and match point. */
    CROWD_CHANT(Channel.CROWD),
    // Music
    MENU_MUSIC(Channel.MUSIC);

    /** Independent volume groups (settings: music, effects, crowd). */
    public enum Channel { EFFECTS, CROWD, MUSIC }

    public final Channel channel;

    SoundId(Channel channel) {
        this.channel = channel;
    }
}
