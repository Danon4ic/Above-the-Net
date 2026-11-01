package com.volleyball.game.world;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourtZonesTest {
    private final Vector2 p = new Vector2();

    @Test
    void homeZonesAreSeenFromATeamFacingTheNet() {
        // Home faces +X, so its right-hand side is +Z.
        CourtZones.basePosition(1, CourtSide.HOME, p);
        assertTrue(p.x < -CourtDimensions.ATTACK_LINE_DISTANCE && p.y > 0f, "zone 1 is back right");
        CourtZones.basePosition(2, CourtSide.HOME, p);
        assertTrue(p.x > -CourtDimensions.ATTACK_LINE_DISTANCE && p.y > 0f, "zone 2 is front right");
        CourtZones.basePosition(4, CourtSide.HOME, p);
        assertTrue(p.x > -CourtDimensions.ATTACK_LINE_DISTANCE && p.y < 0f, "zone 4 is front left");
        CourtZones.basePosition(6, CourtSide.HOME, p);
        assertEquals(0f, p.y);
    }

    @Test
    void awayZonesMirrorThroughTheCourtCentre() {
        Vector2 home = new Vector2();
        for (int zone = 1; zone <= 6; zone++) {
            CourtZones.basePosition(zone, CourtSide.HOME, home);
            CourtZones.basePosition(zone, CourtSide.AWAY, p);
            assertEquals(-home.x, p.x, 1e-6f);
            assertEquals(-home.y, p.y, 1e-6f);
            assertTrue(CourtDimensions.isInsideCourt(p.x, p.y, 0f));
        }
    }

    @Test
    void frontRowIsZonesTwoToFour() {
        assertTrue(CourtZones.isFrontRow(2) && CourtZones.isFrontRow(3) && CourtZones.isFrontRow(4));
        assertFalse(CourtZones.isFrontRow(1) || CourtZones.isFrontRow(5) || CourtZones.isFrontRow(6));
        assertThrows(IllegalArgumentException.class, () -> CourtZones.isFrontRow(7));
    }
}
