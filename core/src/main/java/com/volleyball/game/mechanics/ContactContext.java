package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;

/** Everything a mechanic needs to know about one contact. Filled by {@link BallHandling}. */
public final class ContactContext {
    public VolleyballPlayer player;
    public ContactType type;
    public ContactPurpose purpose;
    /** Ball centre at the moment of contact. */
    public final Vector3 ballPosition = new Vector3();
    public final Vector3 incomingVelocity = new Vector3();
    /** 0..1, from the spatial offset and the player's balance. */
    public float quality;
    /**
     * Signed sideways distance (m) of the ball from the ideal contact point, + = player's right.
     * An off-centre contact deflects the ball systematically to that side.
     */
    public float lateralOffset;
    /** Where the ball should go: a point in the air for passes and sets, on the floor otherwise. */
    public final Vector3 target = new Vector3();
    /** Desired peak height for lobbed contacts (passes, sets, free balls). */
    public float apex;
    public float netTop;
    public ServeType serveType;
    public SetType setType;
    public AttackType attackType;
}
