package com.example.livingrain;

/** Chooses which wind the rain uses: this mod's own, or (if Roof Drips is in charge) Roof Drips'. */
public final class WindSource {
    private static final float[] OUT = new float[3];

    private WindSource() { }

    /** {x, z, turbulence}: blocks per tick, before the rain-influence multiplier. The array is reused. */
    public static float[] get(LivingRainConfig cfg) {
        if (RoofDripsLink.present() && !cfg.overrideRoofDrips) {
            float[] rd = RoofDripsLink.readWind();
            if (rd != null) {
                OUT[0] = rd[0];
                OUT[1] = rd[1];
                OUT[2] = cfg.windTurbulence;
                return OUT;
            }
        }
        OUT[0] = StormWind.x;
        OUT[1] = StormWind.z;
        OUT[2] = StormWind.turbulence;
        return OUT;
    }
}
