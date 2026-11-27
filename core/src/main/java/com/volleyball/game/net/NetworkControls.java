package com.volleyball.game.net;

import com.volleyball.game.ai.ShotAdvisor;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.ServeSpin;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.simulation.GameWorld;

/** The same server-side interpretation of local and remote online input. */
public final class NetworkControls {
    private final GameWorld world;
    private final MatchSession session;
    private final MatchController match;
    private final ShotAdvisor advisor;

    public NetworkControls(GameWorld world, MatchSession session, MatchController match) {
        this.world = world;
        this.session = session;
        this.match = match;
        advisor = new ShotAdvisor(world);
    }

    public void apply(int slot, NetworkInput held, java.util.List<NetworkInput> presses) {
        MatchSession.Slot owner = session.slot(slot);
        if (owner == null || owner.isAutomatic()) return;
        VolleyballPlayer p = owner.player();
        boolean playing = match.phase() == MatchController.Phase.RALLY;
        boolean jump = false;
        boolean dive = false;
        float height = 1f;
        for (NetworkInput input : presses) {
            InputCommand command = input.command();
            if (input.timeout()) match.requestTimeout(owner.side);
            if (input.substituteOut() >= 0) {
                VolleyballPlayer out = world.player(input.substituteOut());
                VolleyballPlayer in = world.player(input.substituteIn());
                if (out != null && in != null && out.side == owner.side && in.side == owner.side) {
                    match.requestSubstitution(owner.side, out, in);
                }
            }
            if (match.phase() == MatchController.Phase.FINISHED) continue;
            if (command.has(InputCommand.SWITCH_PLAYER)) {
                world.intent(p).clear();
                world.ballHandling().cancel(p);
                p = session.switchPlayer(slot);
            }
            var handling = world.ballHandling();
            if (input.variant() >= 0) handling.selectVariant(p, input.variant());
            if (command.has(InputCommand.VARIANT_NEXT)) handling.cycleVariant(p, 1);
            if (command.has(InputCommand.VARIANT_PREVIOUS)) handling.cycleVariant(p, -1);
            if (input.spin() != 0) handling.toggleServeSpin(p, input.spin() < 0 ? ServeSpin.CURVE_LEFT : ServeSpin.CURVE_RIGHT);
            if (!playing || !p.isOnCourt()) continue;
            if (command.has(InputCommand.PRIMARY)) handling.armManual(p, ActionKind.PRIMARY, world.time());
            else if (command.has(InputCommand.SECONDARY)) handling.armManual(p, ActionKind.SECONDARY, world.time());
            if (command.has(InputCommand.JUMP)) {
                jump = true;
                height = command.jumpFactor();
            }
            dive |= command.has(InputCommand.DIVE);
        }
        var intent = world.intent(p);
        intent.clear();
        if (playing && held != null && p.isOnCourt()) {
            held.applyTo(intent, false);
            intent.jump = jump;
            intent.jumpHeightFactor = height;
            intent.dive = dive;
        }
        var handling = world.ballHandling();
        if (held != null && held.command().has(InputCommand.AIM)) handling.setAim(p, held.command().aimX(), held.command().aimZ());
        else handling.clearAim(p);
        if (handling.isAutoSet(p)) {
            var set = match.ai(p.side).playSet();
            handling.setSetType(p, set == null ? advisor.recommendSet(p, match.rules().rotation()) : set);
        }
        if (handling.isAutoAttack(p)) {
            handling.setAttackType(p, advisor.recommendAttack(p, p.role == PlayerRole.SETTER && handling.teamTouches(p.side) == 1));
        }
    }
}
