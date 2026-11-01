package com.volleyball.game.rules;

import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;

import java.util.Arrays;

/**
 * Rotation order of both teams (FIVB rule 7.6). For each team it stores which player is in
 * each of the six zones; the player in zone 1 serves. When a team wins the right to serve it
 * rotates one position clockwise: zone 2 → 1, 3 → 2, 4 → 3, 5 → 4, 6 → 5, 1 → 6.
 */
public final class RotationManager {
    private final int[][] startingLineUp = new int[2][];
    private final int[][] zones = new int[2][];
    /** Libero replacement per team: the replaced line-up player (-1 if none) and the libero's id. */
    private final int[] replaced = {-1, -1};
    private final int[] libero = {-1, -1};

    /**
     * @param homeLineUp player ids of the home team in zones 1..6 (index 0 = zone 1)
     * @param awayLineUp player ids of the away team in zones 1..6
     */
    public RotationManager(int[] homeLineUp, int[] awayLineUp) {
        startingLineUp[0] = validate(homeLineUp);
        startingLineUp[1] = validate(awayLineUp);
        reset();
    }

    private static int[] validate(int[] lineUp) {
        if (lineUp.length != 6) throw new IllegalArgumentException("A line-up has six players");
        if (Arrays.stream(lineUp).distinct().count() != 6) throw new IllegalArgumentException("Duplicate player in line-up");
        return lineUp.clone();
    }

    /** Back to the starting line-up (start of a set). */
    public void reset() {
        zones[0] = startingLineUp[0].clone();
        zones[1] = startingLineUp[1].clone();
        replaced[0] = -1;
        replaced[1] = -1;
    }

    // ------------------------------------------------------------------ libero (rule 19.3.2)

    /**
     * The libero takes the place of a back-row player (a "replacement", not a substitution: the
     * rotation order keeps the replaced player, who returns when the libero leaves).
     */
    public void replaceWithLibero(CourtSide side, int liberoId, int replacedId) {
        int zone = slotZone(side, replacedId);
        if (zone < 0) throw new IllegalArgumentException("Player " + replacedId + " is not in the line-up");
        if (CourtZones.isFrontRow(zone)) throw new IllegalArgumentException("The libero only replaces back-row players");
        libero[index(side)] = liberoId;
        replaced[index(side)] = replacedId;
    }

    /**
     * A regular substitution: {@code inId} takes the place of {@code outId} in the rotation order
     * (until the next set). If the libero was replacing the outgoing player, it now replaces the
     * incoming one.
     */
    public void substitute(CourtSide side, int outId, int inId) {
        int zone = slotZone(side, outId);
        if (zone < 0) throw new IllegalArgumentException("Player " + outId + " is not in the line-up");
        if (slotZone(side, inId) > 0) throw new IllegalArgumentException("Player " + inId + " is already in the line-up");
        zones[index(side)][zone - 1] = inId;
        if (replaced[index(side)] == outId) replaced[index(side)] = inId;
    }

    /** The replaced player comes back, the libero leaves the court. */
    public void releaseLibero(CourtSide side) {
        replaced[index(side)] = -1;
    }

    /** Id of the line-up player the libero currently replaces, or -1. */
    public int replacedByLibero(CourtSide side) {
        return replaced[index(side)];
    }

    /** Zone of a line-up player in the rotation order, ignoring the libero. */
    public int slotZone(CourtSide side, int playerId) {
        int[] team = zones[index(side)];
        for (int i = 0; i < 6; i++) if (team[i] == playerId) return i + 1;
        return -1;
    }

    /** Line-up player of a zone in the rotation order, ignoring the libero. */
    public int slotPlayer(CourtSide side, int zone) {
        return zones[index(side)][zone - 1];
    }

    /** Rotates the team one position clockwise. */
    public void rotate(CourtSide side) {
        int[] old = zones[index(side)];
        int[] rotated = new int[6];
        // New zone z (1-based) is taken by the player who was in zone z + 1; zone 6 by the one from zone 1.
        for (int zone = 1; zone <= 6; zone++) rotated[zone - 1] = old[zone % 6];
        zones[index(side)] = rotated;
    }

    /** The player on court in a zone (the libero when it replaces that zone's player). */
    public int playerInZone(CourtSide side, int zone) {
        int id = zones[index(side)][zone - 1];
        return id == replaced[index(side)] ? libero[index(side)] : id;
    }

    /** Zone (1..6) of the player on court, or -1 if not on court for that team. */
    public int zoneOf(CourtSide side, int playerId) {
        int i = index(side);
        if (replaced[i] >= 0) {
            if (playerId == replaced[i]) return -1;
            if (playerId == libero[i]) return slotZone(side, replaced[i]);
        }
        return slotZone(side, playerId);
    }

    public int server(CourtSide side) {
        return playerInZone(side, 1);
    }

    public boolean isFrontRow(CourtSide side, int playerId) {
        int zone = zoneOf(side, playerId);
        return zone > 0 && CourtZones.isFrontRow(zone);
    }

    /** Current zones of the team, index 0 = zone 1. */
    public int[] lineUp(CourtSide side) {
        return zones[index(side)].clone();
    }

    private static int index(CourtSide side) {
        return side == CourtSide.HOME ? 0 : 1;
    }
}
