package com.example.eavesdrip;

/** The simulated wind, updated once per tick by the client and read by falling droplets. Blocks per tick. */
public final class Wind {
    public static volatile boolean active;
    public static volatile float x;
    public static volatile float z;
    /** Turbulence in use right now (RoofDrips' own setting, or the one supplied by an overriding mod). */
    public static volatile float turbulence = 0.5f;
    /** True while another mod (Living Rain) is supplying the wind. */
    public static volatile boolean overridden;
    private Wind() { }
}
