package com.volleyball.game.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourtDimensionsTest {
    @Test
    void courtHasOfficialSize() {
        assertEquals(18f, CourtDimensions.LENGTH);
        assertEquals(9f, CourtDimensions.WIDTH);
        assertEquals(3f, CourtDimensions.ATTACK_LINE_DISTANCE);
    }

    @Test
    void linesBelongToTheCourt() {
        assertTrue(CourtDimensions.isInsideCourt(9f, 4.5f, 0f), "corner on the outer line edge is in");
        assertTrue(CourtDimensions.isInsideCourt(-8.99f, -4.49f, 0f));
        assertFalse(CourtDimensions.isInsideCourt(9.01f, 0f, 0f));
        assertFalse(CourtDimensions.isInsideCourt(0f, -4.51f, 0f));
    }

    @Test
    void contactPatchTouchingTheLineCountsAsIn() {
        assertFalse(CourtDimensions.isInsideCourt(9.02f, 0f, 0f));
        assertTrue(CourtDimensions.isInsideCourt(9.02f, 0f, 0.03f));
    }

    @Test
    void sidesAreSplitByTheNetPlane() {
        assertEquals(CourtSide.HOME, CourtDimensions.sideOf(-0.01f));
        assertEquals(CourtSide.AWAY, CourtDimensions.sideOf(0.01f));
        assertEquals(CourtSide.AWAY, CourtSide.HOME.opposite());
    }

    @Test
    void frontZoneIncludesTheAttackLine() {
        assertTrue(CourtDimensions.isInFrontZone(-3f, CourtSide.HOME));
        assertTrue(CourtDimensions.isInFrontZone(-0.5f, CourtSide.HOME));
        assertFalse(CourtDimensions.isInFrontZone(-3.01f, CourtSide.HOME));
        assertFalse(CourtDimensions.isInFrontZone(-1f, CourtSide.AWAY), "front zone is per side");
        assertTrue(CourtDimensions.isInFrontZone(2.98f, CourtSide.AWAY));
        assertEquals(2.975f, CourtDimensions.attackLineCenterX(CourtSide.AWAY), 1e-6f);
    }

    @Test
    void serviceZoneIsBehindTheEndLineBetweenSidelineExtensions() {
        assertTrue(CourtDimensions.isInServiceZone(-10f, 3f, CourtSide.HOME));
        assertFalse(CourtDimensions.isInServiceZone(-9f, 3f, CourtSide.HOME), "on the end line is a foot fault");
        assertFalse(CourtDimensions.isInServiceZone(-10f, 4.6f, CourtSide.HOME));
        assertFalse(CourtDimensions.isInServiceZone(10f, 0f, CourtSide.HOME));
        assertTrue(CourtDimensions.isInServiceZone(15f, 0f, CourtSide.AWAY));
    }

    @Test
    void playingAreaIncludesFreeZone() {
        assertTrue(CourtDimensions.isInPlayingArea(15.5f, 9.5f));
        assertFalse(CourtDimensions.isInPlayingArea(15.6f, 0f));
    }
}
