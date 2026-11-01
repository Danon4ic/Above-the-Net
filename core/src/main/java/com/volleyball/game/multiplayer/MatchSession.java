package com.volleyball.game.multiplayer;

import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.rules.SubstitutionManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The people taking part in a match: one slot per person, each with an input device, a team
 * and the player currently controlled. The match AI plays everyone not held by a slot.
 *
 * <ul>
 *   <li>{@link SessionMode#SINGLE}: one person on the home team.</li>
 *   <li>{@link SessionMode#LOCAL_VERSUS}: person 1 on home, person 2 on away.</li>
 *   <li>{@link SessionMode#LOCAL_COOP}: both on home, each controlling a different player.</li>
 *   <li>Network modes: the same slot layout with person 2 as a {@link ControllerKind#REMOTE} slot.</li>
 * </ul>
 */
public final class MatchSession {
    /** One person taking part. */
    public static final class Slot {
        public final int index;
        public final ControllerKind controller;
        public final CourtSide side;
        private VolleyballPlayer player;
        /** The player the person chose, while another one stands in for them (libero replacement). */
        private VolleyballPlayer home;
        /** True while the person has handed this player to the computer (demo / spectator). */
        private boolean automatic;

        Slot(int index, ControllerKind controller, CourtSide side, VolleyballPlayer player) {
            this.index = index;
            this.controller = controller;
            this.side = side;
            this.player = player;
        }

        public VolleyballPlayer player() {
            return player;
        }

        public boolean isAutomatic() {
            return automatic;
        }

        public String label() {
            return "P" + (index + 1);
        }
    }

    private final SessionMode mode;
    private final GameWorld world;
    private final List<Slot> slots = new ArrayList<>();
    private PlayerRole chosenRole = PlayerRole.OUTSIDE_HITTER;
    /** The middle the libero last replaced, per side (for following the libero's slot). */
    private final int[] liberoPartner = {-1, -1};

    private MatchSession(SessionMode mode, GameWorld world) {
        this.mode = mode;
        this.world = world;
    }

    public static MatchSession create(SessionMode mode, GameWorld world) {
        return create(mode, world, PlayerRole.OUTSIDE_HITTER);
    }

    /** @param role the role person 1 plays (chosen before the match) */
    public static MatchSession create(SessionMode mode, GameWorld world, PlayerRole role) {
        MatchSession session = new MatchSession(mode, world);
        Team home = world.home();
        session.chosenRole = role;
        session.slots.add(new Slot(0, ControllerKind.KEYBOARD_MOUSE, CourtSide.HOME, defaultPlayer(home, role, null)));
        ControllerKind second = mode.networked ? ControllerKind.REMOTE : ControllerKind.KEYBOARD_ARROWS;
        switch (mode) {
            case LOCAL_VERSUS, ONLINE_VERSUS -> session.slots.add(new Slot(1, second, CourtSide.AWAY,
                    defaultPlayer(world.away(), PlayerRole.OUTSIDE_HITTER, null)));
            case LOCAL_COOP, ONLINE_COOP -> session.slots.add(new Slot(1, second, CourtSide.HOME,
                    defaultPlayer(home, role == PlayerRole.SETTER ? PlayerRole.OUTSIDE_HITTER : PlayerRole.SETTER,
                            session.slots.get(0).player)));
            case SINGLE -> {
            }
        }
        return session;
    }

    /** First player of the role (not {@code taken}), else the first free player of the team. */
    private static VolleyballPlayer defaultPlayer(Team team, PlayerRole role, VolleyballPlayer taken) {
        for (VolleyballPlayer p : team.squad()) if (p.role == role && p != taken) return p;
        for (VolleyballPlayer p : team.players()) if (p != taken) return p;
        throw new IllegalStateException("No free player in " + team.name);
    }

    public SessionMode mode() {
        return mode;
    }

    /** The role person 1 chose before the match. */
    public PlayerRole chosenRole() {
        return chosenRole;
    }

    /**
     * Keeps every person on court when the libero comes in or goes out: a person whose player
     * goes to the bench takes over the one who replaced them (the libero for a middle blocker,
     * the middle for the libero), and gets their own player back when it returns.
     *
     * @return true if a slot changed player
     */
    public boolean followReplacements(RotationManager rotation) {
        return followReplacements(rotation, null);
    }

    /**
     * As {@link #followReplacements(RotationManager)}, also following regular substitutions: a
     * person whose player was substituted plays the substitute who took the place.
     */
    public boolean followReplacements(RotationManager rotation, SubstitutionManager substitutions) {
        boolean changed = false;
        for (Slot slot : slots) {
            CourtSide side = slot.side;
            int replaced = rotation.replacedByLibero(side);
            if (replaced >= 0) liberoPartner[side.ordinal()] = replaced;
            Team team = side == CourtSide.HOME ? world.home() : world.away();
            VolleyballPlayer owner = slot.home != null ? slot.home : slot.player;
            VolleyballPlayer wanted = owner;
            if (!owner.isOnCourt()) {
                VolleyballPlayer libero = team.libero();
                if (owner.role == PlayerRole.LIBERO) wanted = world.player(liberoPartner[side.ordinal()]);
                else if (libero != null && libero.isOnCourt() && replaced == owner.id) wanted = libero;
                else wanted = null;
                if (wanted == null && substitutions != null) {
                    VolleyballPlayer partner = world.player(substitutions.partner(side, owner.id));
                    if (partner != null && partner.isOnCourt()) wanted = partner;
                    // The substitute is replaced by the libero: play the libero.
                    else if (partner != null && libero != null && libero.isOnCourt() && replaced == partner.id) wanted = libero;
                }
            }
            if (wanted == null || !wanted.isOnCourt() || heldByOther(slot, wanted)) {
                wanted = slot.player.isOnCourt() && !heldByOther(slot, slot.player) ? slot.player : firstFree(slot, team);
            }
            if (wanted != null && wanted != slot.player) {
                if (slot.home == null) slot.home = owner;
                slot.player = wanted;
                changed = true;
            }
            if (slot.player == owner) slot.home = null;
        }
        return changed;
    }

    private VolleyballPlayer firstFree(Slot slot, Team team) {
        for (VolleyballPlayer p : team.onCourt()) if (!heldByOther(slot, p)) return p;
        return null;
    }

    public List<Slot> slots() {
        return Collections.unmodifiableList(slots);
    }

    public Slot slot(int index) {
        return index < slots.size() ? slots.get(index) : null;
    }

    /** Players controlled by people right now (automatic slots excluded). */
    public List<VolleyballPlayer> humans() {
        List<VolleyballPlayer> humans = new ArrayList<>(slots.size());
        for (Slot slot : slots) if (!slot.automatic) humans.add(slot.player);
        return humans;
    }

    /** Hands a slot's player to the computer (true) or back to the person. */
    public void setAutomatic(int index, boolean automatic) {
        Slot slot = slot(index);
        if (slot != null) slot.automatic = automatic;
    }

    /** Explicitly assigns a player to a slot if it is on the slot's team and not held by another slot. */
    public boolean assign(int index, VolleyballPlayer player) {
        Slot slot = slot(index);
        if (slot == null || player == null || player.side != slot.side || heldByOther(slot, player)) return false;
        slot.player = player;
        slot.home = null;
        return true;
    }

    /** The slot's next player on its team, skipping players held by the other person (co-op). */
    public VolleyballPlayer switchPlayer(int index) {
        Slot slot = slot(index);
        if (slot == null) return null;
        List<VolleyballPlayer> team = slot.side == CourtSide.HOME ? world.home().squad() : world.away().squad();
        int start = team.indexOf(slot.player);
        for (int step = 1; step <= team.size(); step++) {
            VolleyballPlayer candidate = team.get((start + step) % team.size());
            if (candidate.isOnCourt() && !heldByOther(slot, candidate)) {
                slot.player = candidate;
                slot.home = null;
                break;
            }
        }
        return slot.player;
    }

    private boolean heldByOther(Slot slot, VolleyballPlayer player) {
        for (Slot other : slots) if (other != slot && other.player == player) return true;
        return false;
    }
}
