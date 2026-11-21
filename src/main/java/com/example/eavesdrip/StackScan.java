package com.example.eavesdrip;

import java.util.function.IntConsumer;

/**
 * Finds the stacks of solid blocks that lie below the first (top) one in a single column of the world,
 * for example a balcony under a roof, or the tiers of a tower.
 *
 * No Minecraft classes in here, so it can be tested on its own.
 */
final class StackScan {

    interface Cells {
        /** Air, plants, or other things water simply falls through. */
        boolean passable(int x, int y, int z);
        /** Water or lava: the scan stops here. */
        boolean fluid(int x, int y, int z);
    }

    private StackScan() { }

    /**
     * @param firstTop  Y of the top block of the first (highest) stack
     * @param minY      do not look below this height
     * @param maxCount  most stacks to report
     * @param maxThick  a stack thicker than this counts as solid terrain, not an overhang: the scan ends there
     * @param out       receives the Y of the top block of each lower stack, from highest to lowest
     */
    static void lowerTops(Cells c, int x, int z, int firstTop, int minY, int maxCount, int maxThick, IntConsumer out) {
        int y = firstTop;
        for (int n = 0; n < maxCount; n++) {
            // down through the solid stack to the gap beneath it
            int g = y;
            int thick = 0;
            while (!c.passable(x, g, z)) {
                if (c.fluid(x, g, z) || ++thick > maxThick || --g < minY) return;
            }
            // down through the air to the next solid block
            int s = g;
            while (c.passable(x, s, z)) {
                if (--s < minY) return;
            }
            if (c.fluid(x, s, z)) return;
            out.accept(s);
            y = s;
        }
    }
}
