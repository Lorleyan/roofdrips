package com.example.eavesdrip;

/** The simulated wind, updated once per tick by the client and read by falling droplets. Blocks per tick. */
public final class Wind {
    public static volatile boolean active;
    public static volatile float x;
    public static volatile float z;
    private Wind() { }
}
