package com.volleyball.game.audio;

import java.util.Locale;

/** Every sound the game can play, with the mixer channel whose volume applies to it. */
public enum SoundId {
    // Ball contacts
    SERVE_UNDERHAND(Channel.EFFECTS),
    SERVE_FLOAT(Channel.EFFECTS),
    SERVE_POWER(Channel.EFFECTS),
    SERVE_JUMP_FLOAT(Channel.EFFECTS),
    SERVE_JUMP_TOPSPIN(Channel.EFFECTS),
    SERVE_TOSS(Channel.EFFECTS),
    FOREARM_PASS(Channel.EFFECTS, 2),
    OVERHEAD_SET(Channel.EFFECTS),
    SPIKE_HARD(Channel.EFFECTS, 2),
    SPIKE_SOFT(Channel.EFFECTS),
    BLOCK(Channel.EFFECTS),
    DIG(Channel.EFFECTS),
    BALL_FLOOR(Channel.EFFECTS, 2),
    BALL_NET(Channel.EFFECTS),
    BALL_METAL(Channel.EFFECTS),
    BALL_WALL(Channel.EFFECTS),
    BALL_BODY(Channel.EFFECTS),
    // Players
    FOOTSTEP(Channel.EFFECTS, 4),
    FOOTSTEP_WOOD(Channel.EFFECTS, 4),
    FOOTSTEP_SAND(Channel.EFFECTS, 3),
    FOOTSTEP_CONCRETE(Channel.EFFECTS, 3),
    BALL_SAND(Channel.EFFECTS, 2),
    LANDING_SAND(Channel.EFFECTS, 2),
    SHOE_SQUEAK(Channel.EFFECTS, 2),
    JUMP_TAKEOFF(Channel.EFFECTS),
    LANDING(Channel.EFFECTS),
    BODY_FALL(Channel.EFFECTS),
    GRUNT(Channel.EFFECTS, 2),
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
    final int variants;

    SoundId(Channel channel) {
        this(channel, 1);
    }

    SoundId(Channel channel, int variants) {
        this.channel = channel;
        this.variants = variants;
    }

    boolean isLoop() {
        return this == CROWD_AMBIENT || this == MENU_MUSIC;
    }

    String resource(int variant) {
        String suffix = variant == 0 ? "" : "-" + (variant + 1);
        return "audio/" + name().toLowerCase(Locale.ROOT) + suffix + (isLoop() ? ".ogg" : ".wav");
    }
}
