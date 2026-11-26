package com.example.livingrain;

/**
 * "Rain curtains": big soft patches where the rain is thicker or thinner, drifting along the wind, so a storm shows
 * visible walls of rain sweeping across the landscape instead of an even sprinkle. Pure maths, no Minecraft classes.
 */
public final class Curtains {

    private static double scrollX, scrollZ;
    private static double dirX = 0.9, dirZ = 0.3;

    private Curtains() { }

    /** Called once per tick with the rain's sideways speed. The pattern drifts along the wind. */
    public static void advance(float windX, float windZ) {
        double m = Math.sqrt((double) windX * windX + (double) windZ * windZ);
        if (m > 0.02) {
            // follow the wind direction smoothly
            dirX += (windX / m - dirX) * 0.05;
            dirZ += (windZ / m - dirZ) * 0.05;
            double l = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (l > 1e-6) { dirX /= l; dirZ /= l; }
        }
        double speed = Math.min(0.45, 0.10 + 0.30 * m);       // blocks per tick; about 6 blocks per second in a normal wind
        scrollX += dirX * speed;
        scrollZ += dirZ * speed;
    }

    public static void reset() { scrollX = 0; scrollZ = 0; }

    /**
     * Density multiplier at a spot: averages 1.0, ranges roughly (1 - amp) to (1 + amp).
     * @param amp 0 = even rain; 0.7 = clearly visible patches
     */
    public static float multiplier(double x, double z, float amp) {
        if (amp <= 0f) return 1f;
        double px = x - scrollX, pz = z - scrollZ;
        double n = 0.65 * noise(px / 16.0, pz / 16.0) + 0.35 * noise(px / 7.0 + 37.2, pz / 7.0 + 11.7);
        n = 0.5 + (n - 0.5) * 1.9;                              // more contrast: the sum of two noises hugs the middle
        n = Math.max(0.0, Math.min(1.0, n));
        return (float) (1.0 + amp * (2.0 * n - 1.0));
    }

    // ---- smooth value noise ----
    private static double hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double noise(double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(xi, zi), b = hash(xi + 1, zi), c = hash(xi, zi + 1), d = hash(xi + 1, zi + 1);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }
}
