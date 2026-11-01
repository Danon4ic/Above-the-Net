package com.volleyball.game.render;

import com.volleyball.game.world.LightingPreset;
import com.volleyball.game.world.Venue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VenueLightsTest {
    @Test
    void everyVenueHasItsOwnLightsWithinTheShaderLimit() {
        for (Venue venue : Venue.values()) {
            for (LightingPreset preset : LightingPreset.values()) {
                LightRig rig = LightRig.of(venue, preset);
                assertTrue(rig.count() > 0 && rig.count() <= LightRig.MAX_LIGHTS, venue + " " + preset + ": " + rig.count() + " lights");
                assertTrue(rig.shadowDirection.y > 0.5f, venue + ": the shadow light is overhead");
                assertTrue(rig.shadowDistance > 5f, venue + ": shadow view " + rig.shadowDistance);
            }
        }
    }

    @Test
    void venuesAreSavedByName() {
        for (Venue venue : Venue.values()) assertTrue(Venue.fromName(venue.name()) == venue);
        assertTrue(Venue.fromName("NO_SUCH_PLACE") == Venue.CHAMPIONSHIP);
    }
}
