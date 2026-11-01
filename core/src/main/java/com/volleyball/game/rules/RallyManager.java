package com.volleyball.game.rules;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.BallContactListener;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The referee of one rally. It listens to deliberate touches ({@link BallTouch}) and physical
 * contacts ({@link BallContact}) and checks the ball every step, and decides who wins the point.
 *
 * <p>Team touches: every contact with a player counts — deliberate plays and the ball bouncing
 * off a body — except a block, which does not count and after which the team has three more
 * touches (the blocker may also play the ball again). A fourth touch is a fault. The same
 * player touching twice in a row is a double contact, except right after a block or within one
 * continuous action (a body bounce in the same instant as a first-touch dig).
 *
 * <p>The ball ends the rally when it lands (in or out), touches an antenna, a post, a wall or the
 * ceiling, crosses the net outside the antennas or under the net. Players touching the net,
 * foot faults, overlaps, back-row and libero faults are called in official mode only.
 */
public final class RallyManager implements BallTouchListener, BallContactListener {
    public enum State { WAITING_SERVE, IN_PLAY, OVER }

    /** Abandon a rally that runs this long without ending (replayed). */
    public static final float MAX_RALLY_TIME = 40f;

    private final MatchFormat format;
    private final RotationManager rotation;
    private final Net net;
    private final Map<Integer, VolleyballPlayer> players = new HashMap<>();
    /** The same players in a fixed order, for the per-step net check without an iterator. */
    private final List<VolleyballPlayer> playerList = new ArrayList<>();
    private final Vector3 previousBall = new Vector3();

    private State state = State.OVER;
    private RallyOutcome outcome;
    private CourtSide servingSide;
    /** The server has been in the service zone since the whistle (stepping onto the court now is a fault). */
    private boolean serverInZone;
    private double startTime;
    private boolean serveHit;
    private boolean ballSeen;

    private CourtSide possession;
    private int touchCount;
    private int lastToucherId = -1;
    private boolean lastTouchWasBlock;
    private boolean lastTouchWasServe;
    private double lastTouchTime;
    private boolean liberoFrontZoneSet;
    /** Whether the current possession started with the opponent's serve (for the attack-on-serve rule). */
    private boolean possessionWasServe;

    /** Overhead contacts below this quality are a held ball (rule 9.2.2)... */
    static final float HELD_QUALITY = 0.2f;
    /** ...and for a setter only below this one. */
    static final float SETTER_HELD_QUALITY = 0.08f;

    public RallyManager(MatchFormat format, RotationManager rotation, List<VolleyballPlayer> allPlayers, Net net) {
        this.format = format;
        this.rotation = rotation;
        this.net = net;
        for (VolleyballPlayer p : allPlayers) {
            players.put(p.id, p);
            playerList.add(p);
        }
    }

    /** Whistle for the serve: the serving team's zone-1 player must serve within the time limit. */
    public void start(CourtSide serving, double now) {
        state = State.WAITING_SERVE;
        outcome = null;
        servingSide = serving;
        startTime = now;
        serveHit = false;
        ballSeen = false;
        possession = null;
        touchCount = 0;
        lastToucherId = -1;
        lastTouchWasBlock = false;
        lastTouchWasServe = false;
        possessionWasServe = false;
        liberoFrontZoneSet = false;
        serverInZone = false;
        clearNetContacts();
    }

    public State state() {
        return state;
    }

    public RallyOutcome outcome() {
        return outcome;
    }

    public CourtSide servingSide() {
        return servingSide;
    }

    /** Touches the team has used in its current possession (0..3). */
    public int touches(CourtSide side) {
        return side == possession ? touchCount : 0;
    }

    public double elapsed(double now) {
        return now - startTime;
    }

    /** Per-step checks: serve clock, ball crossing the net plane, players touching the net. */
    public void update(Ball ball, double now) {
        if (state == State.OVER) return;
        if (state == State.WAITING_SERVE && !ball.isActive() && now - startTime > format.serveTimeLimit()) {
            end(servingSide.opposite(), Fault.SERVE_TIMEOUT, rotation.server(servingSide), now);
            return;
        }
        if (state == State.WAITING_SERVE && format.isOfficial() && checkServerStep(now)) return;
        if (now - startTime > MAX_RALLY_TIME) {
            end(null, Fault.REPLAY, -1, now);
            return;
        }
        if (ball.isActive()) {
            if (ballSeen) checkNetCrossing(ball, now);
            previousBall.set(ball.position);
            ballSeen = true;
        }
        if (state != State.IN_PLAY) {
            // Touching the net is only a fault while the ball is in play (rule 11.3).
            clearNetContacts();
        } else if (format.isOfficial()) {
            for (int i = 0; i < playerList.size(); i++) {
                VolleyballPlayer p = playerList.get(i);
                if (p.consumeNetContact()) {
                    end(p.side.opposite(), Fault.PLAYER_NET_TOUCH, p.id, now);
                    return;
                }
            }
        }
    }

    private void clearNetContacts() {
        for (int i = 0; i < playerList.size(); i++) playerList.get(i).consumeNetContact();
    }

    private void checkNetCrossing(Ball ball, double now) {
        float x0 = previousBall.x;
        float x1 = ball.position.x;
        if (x0 == 0f || Math.signum(x0) == Math.signum(x1)) return;
        float t = x0 / (x0 - x1);
        float y = previousBall.y + (ball.position.y - previousBall.y) * t;
        float z = previousBall.z + (ball.position.z - previousBall.z) * t;
        // The team that touched the ball last is responsible for where it crosses (a block touch counts).
        if (!net.isWithinAntennas(z, Ball.RADIUS) && y > net.bottomHeight()) {
            end(lastTouchLoser().opposite(), Fault.OUTSIDE_CROSSING_SPACE, lastToucherId, now);
        } else if (y + Ball.RADIUS < net.bottomHeight() && Math.abs(z) <= CourtDimensions.HALF_WIDTH) {
            end(lastTouchLoser().opposite(), Fault.UNDER_NET, lastToucherId, now);
        }
    }

    @Override
    public void onBallTouch(BallTouch touch) {
        if (state == State.OVER) return;
        VolleyballPlayer p = players.get(touch.playerId());
        if (p == null) return;
        double now = touch.time();
        if (touch.type() == ContactType.SERVE) {
            onServe(p, now);
            return;
        }
        if (state != State.IN_PLAY) return;
        boolean official = format.isOfficial();
        boolean backRow = !rotation.isFrontRow(p.side, p.id);
        if (touch.type() == ContactType.BLOCK) {
            if (official && p.role == PlayerRole.LIBERO) {
                end(p.side.opposite(), Fault.LIBERO_BLOCK, p.id, now);
            } else if (official && backRow) {
                end(p.side.opposite(), Fault.BACK_ROW_BLOCK, p.id, now);
            } else if (official && lastTouchWasServe) {
                end(p.side.opposite(), Fault.BLOCKED_SERVE, p.id, now);
            } else {
                // A block touch: the team now has three touches, the blocker may play again.
                possession = p.side;
                touchCount = 0;
                possessionWasServe = false;
                lastToucherId = p.id;
                lastTouchWasBlock = true;
                lastTouchWasServe = false;
                lastTouchTime = now;
            }
            return;
        }
        if (!registerTouch(p, now)) return;
        float top = net.topHeight();
        float contactY = touch.contactPoint().y;
        if (touch.type() == ContactType.ATTACK && official) {
            if (ViolationDetector.isLiberoAttackFault(p, contactY, top)) {
                end(p.side.opposite(), Fault.LIBERO_ATTACK, p.id, now);
                return;
            }
            if (ViolationDetector.isBackRowAttackFault(p, backRow, contactY, top)) {
                end(p.side.opposite(), Fault.BACK_ROW_ATTACK, p.id, now);
                return;
            }
            if (liberoFrontZoneSet && ViolationDetector.ballEntirelyAboveNet(contactY, top)) {
                end(p.side.opposite(), Fault.LIBERO_SET_ATTACK, p.id, now);
                return;
            }
            if (touchCount == 1 && possessionWasServe && ViolationDetector.inFrontZone(p)
                    && ViolationDetector.ballEntirelyAboveNet(contactY, top)) {
                end(p.side.opposite(), Fault.ATTACK_ON_SERVE, p.id, now);
                return;
            }
        }
        // A held / thrown ball: a very poor overhead contact. The setter, whose craft it is (fast sets such
        // as the bic included), is only called for a really thrown ball.
        float heldBelow = p.role == PlayerRole.SETTER ? SETTER_HELD_QUALITY : HELD_QUALITY;
        if (official && touch.type().fingerSet() && touch.teamTouchNumber() > 1 && touch.quality() < heldBelow) {
            end(p.side.opposite(), Fault.HELD_BALL, p.id, now);
            return;
        }
        liberoFrontZoneSet = official && p.role == PlayerRole.LIBERO && touch.type().fingerSet()
                && ViolationDetector.inFrontZone(p);
    }

    /**
     * Before the serve: once the server has taken the ball into the service zone, stepping onto the
     * court with it (end line included, on the ground) is a fault at once. Returns true if called.
     */
    private boolean checkServerStep(double now) {
        VolleyballPlayer server = players.get(rotation.server(servingSide));
        if (server == null || server.isAirborne()) return false;
        if (!ViolationDetector.onCourt(server, servingSide)) {
            if (ViolationDetector.depth(server.position, servingSide) > CourtDimensions.HALF_LENGTH + 0.1f) serverInZone = true;
            return false;
        }
        if (!serverInZone) return false;
        end(servingSide.opposite(), Fault.SERVER_ON_COURT, server.id, now);
        return true;
    }

    private void onServe(VolleyballPlayer server, double now) {
        if (state != State.WAITING_SERVE) return;
        state = State.IN_PLAY;
        serveHit = true;
        possession = server.side;
        touchCount = 1;
        lastToucherId = server.id;
        lastTouchWasBlock = false;
        lastTouchWasServe = true;
        lastTouchTime = now;
        if (server.side != servingSide || server.id != rotation.server(servingSide)) {
            end(server.side.opposite(), Fault.SERVE_ORDER, server.id, now);
            return;
        }
        if (!format.isOfficial()) return;
        if (ViolationDetector.isFootFault(server, servingSide)) {
            end(servingSide.opposite(), Fault.FOOT_FAULT, server.id, now);
            return;
        }
        int serving = ViolationDetector.positionalFault(servingSide, rotation, id -> players.get(id).position, server.id);
        if (serving >= 0) {
            end(servingSide.opposite(), Fault.POSITIONAL, serving, now);
            return;
        }
        int receiving = ViolationDetector.positionalFault(servingSide.opposite(), rotation, id -> players.get(id).position, -1);
        if (receiving >= 0) end(servingSide, Fault.POSITIONAL, receiving, now);
    }

    /**
     * Counts a touch by {@code p}. Returns false if the touch itself ended the rally.
     */
    private boolean registerTouch(VolleyballPlayer p, double now) {
        if (p.side != possession) {
            possessionWasServe = lastTouchWasServe;
            possession = p.side;
            touchCount = 1;
        } else {
            boolean sameAction = p.id == lastToucherId && now - lastTouchTime < BallTouch.SAME_ACTION_WINDOW;
            if (sameAction) {
                lastTouchTime = now;
                return true;
            }
            // The serve must cross the net: nobody of the serving team may touch it on the way.
            if (lastTouchWasServe) {
                end(p.side.opposite(), Fault.SERVE_TEAM_TOUCH, p.id, now);
                return false;
            }
            if (p.id == lastToucherId && !lastTouchWasBlock) {
                end(p.side.opposite(), Fault.DOUBLE_CONTACT, p.id, now);
                return false;
            }
            touchCount++;
            if (touchCount > 3) {
                end(p.side.opposite(), Fault.FOUR_HITS, p.id, now);
                return false;
            }
        }
        lastToucherId = p.id;
        lastTouchWasBlock = false;
        lastTouchWasServe = false;
        lastTouchTime = now;
        return true;
    }

    @Override
    public void onBallContact(BallContact contact) {
        if (state == State.OVER) return;
        double now = contact.time();
        switch (contact.kind()) {
            case PLAYER_BODY -> {
                if (state != State.IN_PLAY) return;
                VolleyballPlayer p = players.get(contact.playerId());
                if (p != null) registerTouch(p, now);
            }
            case FLOOR -> onFloor(contact.position(), now);
            case ANTENNA -> end(lastTouchLoser().opposite(), Fault.ANTENNA, lastToucherId, now);
            case POST, WALL, CEILING -> end(lastTouchLoser().opposite(), Fault.OUT_OF_PLAY_OBJECT, lastToucherId, now);
            default -> {
            }
        }
    }

    private void onFloor(Vector3 at, double now) {
        if (!serveHit) {
            end(servingSide.opposite(), Fault.SERVE_NOT_HIT, rotation.server(servingSide), now);
            return;
        }
        if (CourtDimensions.isInsideCourt(at.x, at.z, 0.025f)) {
            CourtSide landedOn = CourtDimensions.sideOf(at.x);
            end(landedOn.opposite(), Fault.BALL_IN, lastToucherId, now);
        } else {
            end(lastTouchLoser().opposite(), Fault.BALL_OUT, lastToucherId, now);
        }
    }

    /** The team responsible for the ball now: the last one to touch it (a block counts here). */
    private CourtSide lastTouchLoser() {
        return possession != null ? possession : servingSide;
    }

    private void end(CourtSide winner, Fault reason, int playerId, double now) {
        if (state == State.OVER) return;
        if (reason.officialOnly && !format.isOfficial()) return;
        state = State.OVER;
        outcome = new RallyOutcome(winner, reason, playerId, now);
    }
}
