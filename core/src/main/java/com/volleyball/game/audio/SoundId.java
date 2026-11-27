package com.volleyball.game.audio;

import java.util.Locale;

/** Every sound the game can play, with the mixer channel whose volume applies to it. */
public enum SoundId {
    // Ball contacts
    SERVE_UNDERHAND(Channel.EFFECTS, 3),
    SERVE_FLOAT(Channel.EFFECTS, 3),
    SERVE_POWER(Channel.EFFECTS, 3),
    SERVE_JUMP_FLOAT(Channel.EFFECTS, 3),
    SERVE_JUMP_TOPSPIN(Channel.EFFECTS, 3),
    SERVE_TOSS(Channel.EFFECTS),
    FOREARM_PASS(Channel.EFFECTS, 4),
    FOREARM_HARD(Channel.EFFECTS, 3),
    OVERHEAD_SET(Channel.EFFECTS, 4),
    SPIKE_HARD(Channel.EFFECTS, 4),
    SPIKE_SOFT(Channel.EFFECTS, 3),
    SPIKE_ROLL(Channel.EFFECTS, 3),
    BLOCK(Channel.EFFECTS, 3),
    DIG(Channel.EFFECTS, 3),
    DIG_ONE_ARM(Channel.EFFECTS, 3),
    BALL_FLOOR(Channel.EFFECTS, 2),
    BALL_NET(Channel.EFFECTS),
    BALL_TAPE(Channel.EFFECTS, 2),
    BALL_METAL(Channel.EFFECTS),
    BALL_WALL(Channel.EFFECTS),
    BALL_BODY(Channel.EFFECTS),
    // Players
    FOOTSTEP(Channel.EFFECTS, 6),
    FOOTSTEP_WOOD(Channel.EFFECTS, 6),
    FOOTSTEP_SAND(Channel.EFFECTS, 6),
    FOOTSTEP_CONCRETE(Channel.EFFECTS, 6),
    BALL_SAND(Channel.EFFECTS, 2),
    BALL_ROOF(Channel.EFFECTS, 2),
    LANDING_SAND(Channel.EFFECTS, 2),
    LANDING_ROOF(Channel.EFFECTS, 2),
    SHOE_SQUEAK(Channel.EFFECTS, 2),
    PLANT(Channel.EFFECTS, 3),
    PLANT_WOOD(Channel.EFFECTS, 3),
    PLANT_SAND(Channel.EFFECTS, 3),
    PLANT_ROOF(Channel.EFFECTS, 3),
    JUMP_TAKEOFF(Channel.EFFECTS),
    LANDING(Channel.EFFECTS, 2),
    BODY_FALL(Channel.EFFECTS),
    DIVE_SLIDE(Channel.EFFECTS, 2),
    DIVE_SLIDE_SAND(Channel.EFFECTS, 2),
    DIVE_SLIDE_ROOF(Channel.EFFECTS, 2),
    GRUNT(Channel.EFFECTS, 2),
    TEAM_RECEIVE_CUE(Channel.EFFECTS, 2),
    // Selection, mode and replay transitions
    ACTION_CHANGE(Channel.EFFECTS, 2),
    MODE_CHANGE(Channel.EFFECTS, 2),
    REPLAY_START(Channel.EFFECTS),
    // Referee and arena
    WHISTLE_SHORT(Channel.EFFECTS),
    WHISTLE_LONG(Channel.EFFECTS),
    POINT_CHIME(Channel.EFFECTS),
    SET_HORN(Channel.EFFECTS),
    TIMEOUT_BUZZER(Channel.EFFECTS),
    // Crowd
    CROWD_AMBIENT(Channel.CROWD, true),
    CROWD_BEACH(Channel.CROWD, true),
    CROWD_GYM(Channel.CROWD, true),
    CROWD_PALACE(Channel.CROWD, true),
    CROWD_ROOFTOP(Channel.CROWD, true),
    CROWD_CHEER(Channel.CROWD),
    CROWD_GROAN(Channel.CROWD),
    CROWD_OOH(Channel.CROWD),
    CROWD_CLAPS(Channel.CROWD),
    /** Anticipation swell on a set or match point. */
    CROWD_CHANT(Channel.CROWD),
    // Music
    MENU_MUSIC(Channel.MUSIC, true);

    /** Independent volume groups (settings: music, effects, crowd). */
    public enum Channel { EFFECTS, CROWD, MUSIC }

    public final Channel channel;
    final int variants;
    private final boolean loop;

    SoundId(Channel channel) {
        this(channel, 1);
    }

    SoundId(Channel channel, int variants) {
        this(channel, variants, false);
    }

    SoundId(Channel channel, boolean loop) {
        this(channel, 1, loop);
    }

    SoundId(Channel channel, int variants, boolean loop) {
        this.channel = channel;
        this.variants = variants;
        this.loop = loop;
    }

    boolean isLoop() {
        return loop;
    }

    String resource(int variant) {
        String suffix = variant == 0 ? "" : "-" + (variant + 1);
        return "audio/" + name().toLowerCase(Locale.ROOT) + suffix + (isLoop() ? ".ogg" : ".wav");
    }
}
