package com.volleyball.game.multiplayer;

/** Who plays a match: one person, two people on one PC (against each other or together), or over the network. */
public enum SessionMode {
    SINGLE("1 player", false, false),
    /** Two people on one PC, on different teams. */
    LOCAL_VERSUS("2 players - versus", true, false),
    /** Two people on one PC, on the same team. */
    LOCAL_COOP("2 players - same team", true, false),
    ONLINE_VERSUS("Host online - versus", false, true),
    ONLINE_COOP("Host online - same team", false, true);

    public final String label;
    public final boolean twoLocalPlayers;
    public final boolean networked;

    SessionMode(String label, boolean twoLocalPlayers, boolean networked) {
        this.label = label;
        this.twoLocalPlayers = twoLocalPlayers;
        this.networked = networked;
    }

    /** Next mode playable on this PC (the network modes are skipped until the network stage). */
    public SessionMode nextLocal() {
        SessionMode[] all = values();
        SessionMode m = this;
        do m = all[(m.ordinal() + 1) % all.length]; while (m.networked);
        return m;
    }

    public SessionMode nextMatch() {
        return values()[(ordinal() + 1) % values().length];
    }
}
