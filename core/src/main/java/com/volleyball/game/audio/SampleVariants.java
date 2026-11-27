package com.volleyball.game.audio;

import java.util.Random;

/** Plays every alternate take once per shuffle and avoids a repeat at the boundary. */
final class SampleVariants {
    private final int[] order;
    private final Random random;
    private int cursor;
    private int last = -1;

    SampleVariants(int count, Random random) {
        this.random = random;
        order = new int[count];
        for (int i = 0; i < count; i++) order[i] = i;
        cursor = count;
    }

    int next() {
        if (cursor == order.length) {
            for (int i = order.length - 1; i > 0; i--) swap(i, random.nextInt(i + 1));
            if (order.length > 1 && order[0] == last) swap(0, 1 + random.nextInt(order.length - 1));
            cursor = 0;
        }
        last = order[cursor++];
        return last;
    }

    private void swap(int first, int second) {
        int value = order[first];
        order[first] = order[second];
        order[second] = value;
    }
}
