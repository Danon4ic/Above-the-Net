package com.volleyball.game.simulation;

import com.badlogic.gdx.graphics.Color;
import com.volleyball.game.entities.Team;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LiberoKitTest {
    private static float distance(Color a, Color b) {
        float dr = a.r - b.r;
        float dg = a.g - b.g;
        float db = a.b - b.b;
        return (float) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    @Test
    void theLiberoStandsOutFromBothTeamsInEveryPairing() {
        StringBuilder report = new StringBuilder();
        for (TeamCatalog.Entry home : TeamCatalog.all()) {
            for (TeamCatalog.Entry away : TeamCatalog.all()) {
                if (home == away) continue;
                Team h = TeamFactory.build(home, CourtSide.HOME, 0, away);
                Team a = TeamFactory.build(away, CourtSide.AWAY, 6, h.shirtColor, false);
                for (Team t : new Team[]{h, a}) {
                    Team other = t == h ? a : h;
                    float own = distance(t.liberoShirtColor, t.shirtColor);
                    float opp = distance(t.liberoShirtColor, other.shirtColor);
                    assertTrue(own > 0.45f, t.name + " libero too close to its own shirt: " + own);
                    assertTrue(opp > 0.3f, t.name + " libero too close to " + other.name + ": " + opp);
                }
            }
            Team alone = TeamFactory.build(home, CourtSide.HOME, 0, home);
            report.append(home.name()).append(": shirt ").append(home.shirt()).append(" libero ").append(alone.liberoShirtColor).append('\n');
        }
        System.out.println(report);
    }
}
