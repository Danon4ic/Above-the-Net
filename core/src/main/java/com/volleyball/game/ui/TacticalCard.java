package com.volleyball.game.ui;

import com.volleyball.game.ai.TeamAI.PlayAssignment;
import com.volleyball.game.ai.ShotAdvisor;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.i18n.Lang;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.tactics.Combination;
import com.volleyball.game.tactics.Playbook;

/** The team's call and up to two lines of live tactical context. */
public record TacticalCard(String plan, String assignment, String detail) {
    public TacticalCard(String plan, String assignment) {
        this(plan, assignment, null);
    }

    public static TacticalCard offSystem(VolleyballPlayer setter, SetType selected, ShotAdvisor advisor, RotationManager rotation) {
        var hitters = advisor.hitters(setter, rotation);
        String high = setOption(setter, SetType.HIGH_OUTSIDE, hitters.get(SetType.Lane.OUTSIDE), advisor);
        String pipe = setOption(setter, SetType.PIPE, hitters.get(SetType.Lane.BACK_MIDDLE), advisor);
        String shoot = setOption(setter, SetType.SHOOT, hitters.get(SetType.Lane.OUTSIDE), advisor);
        return new TacticalCard(Lang.f("Off-system pass · %s", Lang.t(selected.shortLabel)), high + "  /  " + pipe, shoot);
    }

    private static String setOption(VolleyballPlayer setter, SetType type, VolleyballPlayer hitter, ShotAdvisor advisor) {
        String name = Lang.t(type.shortLabel) + (hitter == null ? "" : " #" + hitter.number);
        return name + " · " + readiness(advisor.setReadiness(setter, type, hitter));
    }

    public static String readiness(ShotAdvisor.SetReadiness readiness) {
        return Lang.t(switch (readiness) {
            case APPROACHING -> "Running in";
            case AVAILABLE -> "Can reach";
            case RISKY -> "Risky timing";
            case UNAVAILABLE -> "Unavailable";
        });
    }

    public static TacticalCard forPlayer(PlayAssignment play, VolleyballPlayer player) {
        SetType set = play.set();
        String target = Lang.t(set == null ? "Setter dump" : set.label);
        String receiver = play.hitter() == null ? "" : " · " + Lang.f("Target #%d", play.hitter().number);
        String plan;
        String decoy = null;
        if (play.play().emergency()) {
            plan = Lang.f("Safe option · %s", target) + receiver;
        } else if (play.alternate()) {
            plan = Lang.f("Alternative · %s", target) + receiver;
        } else {
            // Use the combination name where it adds information about the routes.
            plan = switch (play.play().id()) {
                case Playbook.HIGH_OUTSIDE, Playbook.ZONE_TWO, Playbook.PIPE, Playbook.BIC, Playbook.SLIDE, Playbook.SETTER_DUMP -> target;
                default -> Lang.t(play.play().name()) + " · " + Lang.t(set == null ? "Setter dump" : set.shortLabel);
            };
            plan += receiver;
            if (play.decoy() != null) {
                decoy = play.decoy().role == PlayerRole.MIDDLE_BLOCKER
                        ? Lang.t("Middle draws the block") : Lang.f("#%d draws the block", play.decoy().number);
            }
        }
        return new TacticalCard(plan, Lang.f("Your assignment · %s", assignment(play, player)), decoy);
    }

    private static String assignment(PlayAssignment play, VolleyballPlayer player) {
        VolleyballPlayer hitter = play.hitter();
        Combination.Run run = play.run();
        if (play.setting()) {
            if (play.set() == null) return Lang.t("Tip on the second contact");
            return hitter == null ? Lang.t("Deliver the set") : Lang.f("Set to #%d", hitter.number);
        }
        if (hitter == player) {
            if (run == null || run.decoy()) return Lang.t("Attack the set");
            boolean backRow = play.set() != null && play.set().depth >= 3f;
            return Lang.f(backRow ? "Back-row approach · %s" : "Attack approach · %s", Lang.t(run.tempo().label));
        }
        if (run != null && run.decoy() && !play.alternate()) {
            return Lang.f("Decoy approach · %s", Lang.t(run.tempo().label));
        }
        if (run != null && !play.committed()) {
            return Lang.f("Backup approach · %s", Lang.t(run.tempo().label));
        }
        return hitter == null ? Lang.t("Cover the attack") : Lang.f("Cover #%d", hitter.number);
    }
}
