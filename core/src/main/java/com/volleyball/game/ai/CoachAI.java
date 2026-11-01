package com.volleyball.game.ai;

import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.RallyOutcome;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.rules.SubstitutionManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The computer coach of a team: takes a player out after repeated errors in a set and brings a
 * substitute of the same role, and brings the starter back when the substitute struggles too.
 * Human-controlled players are never taken out.
 */
public final class CoachAI {
    /** Errors in a set after which a player is substituted. */
    static final int ERRORS_BEFORE_SUBSTITUTION = 3;

    private final Team team;
    private final Map<Integer, Integer> errors = new HashMap<>();

    /** A substitution the coach asks for. */
    public record Request(VolleyballPlayer out, VolleyballPlayer in) {
    }

    public CoachAI(Team team) {
        this.team = team;
    }

    public void newSet() {
        errors.clear();
    }

    public int errors(VolleyballPlayer p) {
        return errors.getOrDefault(p.id, 0);
    }

    /** After a rally: counts the team's errors and may ask for a substitution (or returns null). */
    public Request afterRally(RallyOutcome outcome, RotationManager rotation, SubstitutionManager substitutions,
                              Collection<VolleyballPlayer> humans) {
        if (outcome.isReplay() || outcome.winner() == team.side || outcome.playerId() < 0) {
            return null;
        }
        // The player responsible: the last toucher on this team. A ball that came down in our court
        // after our own touch (a shanked pass, a dig off the arms) is an error too; a ball that came
        // down after the opponent's touch (their kill) is nobody's error here.
        VolleyballPlayer culprit = null;
        for (VolleyballPlayer p : team.squad()) if (p.id == outcome.playerId()) culprit = p;
        if (outcome.reason() == Fault.BALL_IN && culprit == null) return null;
        if (culprit == null || culprit.role == PlayerRole.LIBERO || humans.contains(culprit)) return null;
        int count = errors.merge(culprit.id, 1, Integer::sum);
        if (count < ERRORS_BEFORE_SUBSTITUTION || rotation.slotZone(team.side, culprit.id) < 0) return null;
        int liberoId = team.libero() == null ? -1 : team.libero().id;
        // A substitute comes back out for the starter; a starter goes out for a substitute of the same role.
        int partner = substitutions.partner(team.side, culprit.id);
        for (VolleyballPlayer candidate : team.squad()) {
            boolean fits = partner >= 0 ? candidate.id == partner : candidate.role == culprit.role;
            if (!fits || candidate == culprit || humans.contains(candidate)) continue;
            if (substitutions.check(team.side, culprit.id, candidate.id, rotation, liberoId) == SubstitutionManager.Result.OK) {
                errors.remove(culprit.id);
                return new Request(culprit, candidate);
            }
        }
        return null;
    }
}
