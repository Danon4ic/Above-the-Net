package com.volleyball.game.match;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;

/**
 * The first referee on the stand at the net, with the official hand signals (a figure only, not
 * part of the world): the authorisation to serve towards the serving team; after a rally the
 * nature of the fault (ball in, ball out, net touch, four hits, double contact, held ball), then
 * the arm towards the team that serves next; a timeout with the T.
 */
public final class Referee implements MatchListener {
    public static final int ID = 210;
    /** Height of the stand's platform (see ArenaModelFactory.buildRefereeStand). */
    static final float PLATFORM = 1.45f;
    static final float STAND_Z = CourtDimensions.HALF_WIDTH + Net.POST_DISTANCE_FROM_SIDELINE + 0.75f;

    private final VolleyballPlayer figure = new VolleyballPlayer(ID, "Referee", 0, PlayerRole.SETTER, CourtSide.HOME, 1.84f,
            new PlayerAttributes());
    private float benchSign = 1f;
    /** The signal after the current one (the next server after the fault), and when it starts. */
    private ActionPose next;
    private float nextIn;

    public Referee() {
        place();
    }

    public VolleyballPlayer figure() {
        return figure;
    }

    /** Call once per simulation step; {@code benchSign} as in {@code GameWorld.benchSign}. */
    public void update(float dt, float benchSign) {
        if (benchSign != this.benchSign) {
            this.benchSign = benchSign;
            place();
        }
        figure.previousPosition.set(figure.position);
        figure.previousFacingYaw = figure.facingYaw;
        figure.tickActionPose(dt);
        if (next != null) {
            nextIn -= dt;
            if (nextIn <= 0f) {
                figure.setActionPose(next, 1.3f);
                next = null;
            }
        }
    }

    /** On the stand opposite the benches, standing on the platform, facing the court. */
    private void place() {
        figure.placeAt(0f, -benchSign * (STAND_Z - 0.2f));
        figure.position.y = PLATFORM;
        figure.previousPosition.set(figure.position);
        figure.facingYaw = -benchSign * MathUtils.HALF_PI;
        figure.previousFacingYaw = figure.facingYaw;
    }

    /** The arm signal pointing to a team: the referee's right arm points to the home half while facing +z. */
    private ActionPose point(CourtSide side, boolean serve) {
        boolean right = side.sign() == -benchSign;
        if (serve) return right ? ActionPose.REF_SERVE_RIGHT : ActionPose.REF_SERVE_LEFT;
        return right ? ActionPose.REF_POINT_RIGHT : ActionPose.REF_POINT_LEFT;
    }

    @Override
    public void onServeWhistle(VolleyballPlayer server, MatchScore score) {
        figure.setActionPose(point(server.side, true), 1.0f);
        next = null;
    }

    @Override
    public void onPoint(MatchRules.PointSummary point, MatchScore score) {
        ActionPose fault = switch (point.outcome().reason()) {
            case BALL_IN -> ActionPose.REF_IN;
            case BALL_OUT, ANTENNA, OUTSIDE_CROSSING_SPACE, OUT_OF_PLAY_OBJECT -> ActionPose.REF_OUT;
            case PLAYER_NET_TOUCH, UNDER_NET -> ActionPose.REF_NET;
            case FOUR_HITS -> ActionPose.REF_FOUR_HITS;
            case DOUBLE_CONTACT -> ActionPose.REF_DOUBLE;
            case HELD_BALL -> ActionPose.REF_HELD;
            default -> null;
        };
        ActionPose serve = point.matchWon() ? null : point(point.nextServingSide(), false);
        if (fault != null) {
            figure.setActionPose(fault, 1.2f);
            next = serve;
            nextIn = 1.2f;
        } else if (serve != null) {
            figure.setActionPose(serve, 1.3f);
        }
    }

    @Override
    public void onTimeout(CourtSide side, int used) {
        figure.setActionPose(ActionPose.REF_TIMEOUT, 1.6f);
        next = null;
    }
}
