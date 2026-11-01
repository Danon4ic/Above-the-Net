package com.volleyball.game.mechanics;

/**
 * What a contact tries to achieve. Derived from the team's touch count: the first touch passes
 * to the setter, the second sets an attacker, the third must send the ball over.
 */
public enum ContactPurpose {
    SERVE("serve"),
    PASS("pass to setter"),
    SET("set"),
    SEND_OVER("free ball over"),
    ATTACK("attack"),
    BLOCK("block");

    public final String label;

    ContactPurpose(String label) {
        this.label = label;
    }
}
