package com.volleyball.game.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetTest {
    @Test
    void heightDependsOnCategory() {
        Net net = new Net(NetCategory.MEN);
        assertEquals(2.43f, net.topHeight(), 1e-6f);
        assertEquals(1.43f, net.bottomHeight(), 1e-5f);
        net.setCategory(NetCategory.WOMEN);
        assertEquals(2.24f, net.topHeight(), 1e-6f);
    }

    @Test
    void antennasStandAtTheSidelinesAndRiseAboveTheNet() {
        Net net = new Net(NetCategory.MEN);
        assertEquals(4.505f, net.antennaAxisZ(), 1e-6f);
        assertEquals(3.23f, net.antennaTop(), 1e-5f);
        assertEquals(Net.ANTENNA_LENGTH, net.antennaTop() - net.antennaBottom(), 1e-5f);
        assertEquals(5.5f, net.postAxisZ(), 1e-6f);
    }

    @Test
    void crossingSpaceRequiresTheWholeBallBetweenAntennas() {
        Net net = new Net(NetCategory.MEN);
        float ballRadius = 0.105f;
        assertTrue(net.isWithinAntennas(0f, ballRadius));
        assertTrue(net.isWithinAntennas(4.39f, ballRadius));
        assertFalse(net.isWithinAntennas(4.41f, ballRadius), "ball overlaps the antenna");
        assertFalse(net.isWithinAntennas(-5f, ballRadius));
    }

    @Test
    void categoryCyclesAndThemesResolveUnknownNames() {
        assertEquals(NetCategory.WOMEN, NetCategory.MEN.next());
        assertEquals(NetCategory.MEN, NetCategory.WOMEN.next());
        assertEquals(CourtTheme.FIVB_ARENA, CourtTheme.fromName("nope"));
        assertEquals(LightingPreset.WARM_GYM, LightingPreset.fromName("WARM_GYM"));
    }
}
