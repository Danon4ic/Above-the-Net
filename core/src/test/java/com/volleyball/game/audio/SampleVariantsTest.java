package com.volleyball.game.audio;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SampleVariantsTest {
    @Test
    void everyTakePlaysOncePerShuffleWithoutBackToBackRepeats() {
        SampleVariants order = new SampleVariants(4, new Random(52));
        int last = -1;
        for (int round = 0; round < 20; round++) {
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                int next = order.next();
                assertNotEquals(last, next);
                used.add(next);
                last = next;
            }
            assertEquals(4, used.size());
        }
    }
}
