package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.ai.AutoPlayer;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Developer probe (PROBE=1): a computer server with every serve type: contact, net, in, out, take-off. */
class ServeProbe {
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        StringBuilder out = new StringBuilder("serve type: hit / net / in / out / take-off x\n");
        for (ServeType type : ServeType.values()) {
            int hit = 0, net = 0, in = 0, outs = 0;
            float takeoff = 0f;
            int n = 20;
            for (int i = 0; i < n; i++) {
                Harness h = new Harness(300 + i);
                VolleyballPlayer p = h.home(0);
                p.placeAt(-10f, 1f + (i % 3));
                p.facingYaw = VolleyballPlayer.facingNetYaw(CourtSide.HOME);
                AutoPlayer auto = new AutoPlayer(h.world, i);
                h.handling.prepareServe(p, type);
                h.handling.setAim(p, 6f, 0f);
                int before = h.touches.size();
                boolean served = false;
                for (int k = 0; k < 120 * 5 && !served; k++) {
                    auto.act(p, AutoPlayer.Behavior.PASS, h.world.time());
                    h.step();
                    served = h.touches.size() > before;
                }
                if (!served) continue;
                hit++;
                takeoff += p.takeoffPosition.x;
                int c0 = h.contacts.size();
                Vector3 landing = h.landing(4f);
                boolean touchedNet = h.contacts.subList(c0, h.contacts.size()).stream()
                        .anyMatch(c -> c.kind() == ColliderKind.NET_MESH || c.kind() == ColliderKind.NET_BAND);
                if (type == ServeType.UNDERHAND) {
                    BallTouch t = h.touches.get(h.touches.size() - 1);
                    out.append(String.format("    underhand: contact (%.2f, %.2f, %.2f) q %.2f v(%.1f %.1f %.1f) landing %s%n",
                            t.contactPoint().x, t.contactPoint().y, t.contactPoint().z, t.quality(), t.outgoingVelocity().x,
                            t.outgoingVelocity().y, t.outgoingVelocity().z, landing));
                }
                if (touchedNet && (landing == null || landing.x < 0f)) net++;
                else if (landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY
                        && CourtDimensions.isInsideCourt(landing.x, landing.z, 0.025f)) in++;
                else outs++;
            }
            out.append(String.format("  %-14s %2d %2d %2d %2d  %.2f%n", type, hit, net, in, outs, hit == 0 ? 0f : takeoff / hit));
        }
        System.out.println(out);
    }
}
