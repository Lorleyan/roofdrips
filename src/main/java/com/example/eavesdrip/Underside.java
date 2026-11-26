package com.example.eavesdrip;

import java.util.List;

/**
 * Works out where the underside of a block really is along each of its four side faces, from its collision boxes.
 *
 * A single "lowest point" is wrong for shapes like an upside-down stair: the step half reaches down to the bottom of the
 * block but the other half is only as thick as a slab, so its underside is half way up. Drips along that half must start
 * at the real underside, not at the bottom of the block.
 *
 * No Minecraft classes in here, so it can be tested on its own.
 */
final class Underside {

    /** Sample points across one face. 16 matches Minecraft's 1/16 block shape grid. */
    static final int SAMPLES = 16;
    private static final double EPS = 1.0e-4;

    private Underside() { }

    /**
     * @param boxes block-local collision boxes, each {minX, minY, minZ, maxX, maxY, maxZ} with values 0..1
     * @param stepX,stepZ which side face: (1,0) east, (-1,0) west, (0,1) south, (0,-1) north
     * @return underside height (0..0.95) at SAMPLES evenly spaced points across that face,
     *         or NaN where no solid part of the block reaches the face there
     */
    static float[] sideProfile(List<double[]> boxes, int stepX, int stepZ) {
        float[] out = new float[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double u = (i + 0.5) / SAMPLES;
            double best = Double.NaN;
            for (double[] b : boxes) {
                boolean touches;
                double lo, hi;
                if (stepX != 0) {
                    touches = stepX > 0 ? b[3] >= 1.0 - EPS : b[0] <= EPS;
                    lo = b[2];
                    hi = b[5];
                } else {
                    touches = stepZ > 0 ? b[5] >= 1.0 - EPS : b[2] <= EPS;
                    lo = b[0];
                    hi = b[3];
                }
                if (!touches || u < lo - EPS || u > hi + EPS) continue;
                if (Double.isNaN(best) || b[1] < best) best = b[1];
            }
            out[i] = Double.isNaN(best) ? Float.NaN : (float) Math.max(0.0, Math.min(0.95, best));
        }
        return out;
    }

    /** Index of the sample nearest to position u (0..1) across a face. */
    static int index(double u) {
        int i = (int) (u * SAMPLES);
        return i < 0 ? 0 : Math.min(i, SAMPLES - 1);
    }
}
