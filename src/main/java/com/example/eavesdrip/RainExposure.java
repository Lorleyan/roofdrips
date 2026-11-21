package com.example.eavesdrip;

/**
 * Can slanted rain reach the top of a block that has another roof above it?
 *
 * Rain falls along a slant, not straight down. From the top face of the block, trace the rain's path backwards (up, and
 * toward the side it comes from). If that line gets out into open sky without hitting anything, rain reaches the block.
 * Five points on the top face are tried, so a block that is only partly sheltered gets a partial result (0.2 to 1.0).
 *
 * No Minecraft classes in here, so it can be tested on its own.
 */
final class RainExposure {

    interface Grid {
        /** True if rain cannot pass through this cell. */
        boolean blocked(int x, int y, int z);
        /** The first height at which this column is open to the sky (Minecraft's motion-blocking height map). */
        int skyY(int x, int z);
    }

    private static final double[][] POINTS = { { 0, 0 }, { -0.3, -0.3 }, { 0.3, -0.3 }, { -0.3, 0.3 }, { 0.3, 0.3 } };

    private RainExposure() { }

    /**
     * @param topY       Y of the block whose top face we test
     * @param slantX,slantZ how far the rain moves sideways per block it falls (blocks), in the wind's x/z directions
     * @param maxUp      how far up to follow the line before giving up
     * @return 0 = fully sheltered, 1 = every test point sees open sky
     */
    static float exposure(Grid g, int x, int topY, int z, double slantX, double slantZ, double maxUp) {
        double y0 = topY + 1.02;
        double steepness = Math.max(Math.abs(slantX), Math.abs(slantZ));
        double dyStep = 0.5 / (1.0 + steepness);      // keeps each step under about half a block sideways
        int seen = 0;
        for (double[] o : POINTS) {
            double sx = x + 0.5 + o[0];
            double sz = z + 0.5 + o[1];
            for (double dy = dyStep; dy <= maxUp; dy += dyStep) {
                int cx = (int) Math.floor(sx - slantX * dy);
                int cy = (int) Math.floor(y0 + dy);
                int cz = (int) Math.floor(sz - slantZ * dy);
                if (g.blocked(cx, cy, cz)) break;                 // something is in the way
                if (cy >= g.skyY(cx, cz)) { seen++; break; }      // out in the open
            }
        }
        return seen / (float) POINTS.length;
    }
}
