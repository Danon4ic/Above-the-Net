package com.volleyball.game.multiplayer;

/** Who plays a match: one person, two people on one PC (against each other or together), or over the network. */
public enum SessionMode {
    SINGLE("1 player", false, false),
    /** Two people on one PC, on different teams. */
    LOCAL_VERSUS("2 players - versus", true, false),
    /** Two people on one PC, on the same team. */
    LOCAL_COOP("2 players - same team", true, false),
    /** Prepared: a networked match, this PC is the authoritative host. */
    ONLINE_VERSUS("Network - versus (prepared)", false, true),
    /** Prepared: a networked match, both people on one team. */
    ONLINE_COOP("Network - same team (prepared)", false, true);

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
}
