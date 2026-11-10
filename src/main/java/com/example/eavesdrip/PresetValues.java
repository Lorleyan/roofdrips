package com.example.eavesdrip;

/** The settings that a preset controls. Used for saved user presets. */
public class PresetValues {
    public int radius, maxParticles, scanSpeed;
    public float density, streamShare, edgeVariation;
    public int streamLength;
    public float streamLengthVar, fallSpeed, fallSpeedVar, dropSize;
    public boolean splashes;
    public float faceStreakAmount, faceStreakLength, faceStreakLengthVar, faceStreakSpeed, faceStreakSpeedVar;
    public float faceWander, faceWanderStrength, opacity;

    public static PresetValues from(RoofDripsConfig c) {
        PresetValues p = new PresetValues();
        p.radius = c.radius; p.maxParticles = c.maxParticles; p.scanSpeed = c.scanSpeed;
        p.density = c.density; p.streamShare = c.streamShare; p.edgeVariation = c.edgeVariation;
        p.streamLength = c.streamLength; p.streamLengthVar = c.streamLengthVar;
        p.fallSpeed = c.fallSpeed; p.fallSpeedVar = c.fallSpeedVar; p.dropSize = c.dropSize; p.splashes = c.splashes;
        p.faceStreakAmount = c.faceStreakAmount; p.faceStreakLength = c.faceStreakLength;
        p.faceStreakLengthVar = c.faceStreakLengthVar; p.faceStreakSpeed = c.faceStreakSpeed;
        p.faceStreakSpeedVar = c.faceStreakSpeedVar; p.faceWander = c.faceWander;
        p.faceWanderStrength = c.faceWanderStrength; p.opacity = c.opacity;
        return p;
    }

    public void applyTo(RoofDripsConfig c) {
        c.radius = radius; c.maxParticles = maxParticles; c.scanSpeed = scanSpeed;
        c.density = density; c.streamShare = streamShare; c.edgeVariation = edgeVariation;
        c.streamLength = streamLength; c.streamLengthVar = streamLengthVar;
        c.fallSpeed = fallSpeed; c.fallSpeedVar = fallSpeedVar; c.dropSize = dropSize; c.splashes = splashes;
        c.faceStreakAmount = faceStreakAmount; c.faceStreakLength = faceStreakLength;
        c.faceStreakLengthVar = faceStreakLengthVar; c.faceStreakSpeed = faceStreakSpeed;
        c.faceStreakSpeedVar = faceStreakSpeedVar; c.faceWander = faceWander;
        c.faceWanderStrength = faceWanderStrength; c.opacity = opacity;
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 0.001f; }

    public boolean matches(RoofDripsConfig c) {
        return radius == c.radius && maxParticles == c.maxParticles && scanSpeed == c.scanSpeed
                && near(density, c.density) && near(streamShare, c.streamShare)
                && near(edgeVariation, c.edgeVariation) && streamLength == c.streamLength
                && near(streamLengthVar, c.streamLengthVar) && near(fallSpeed, c.fallSpeed)
                && near(fallSpeedVar, c.fallSpeedVar) && near(dropSize, c.dropSize) && splashes == c.splashes
                && near(faceStreakAmount, c.faceStreakAmount) && near(faceStreakLength, c.faceStreakLength)
                && near(faceStreakLengthVar, c.faceStreakLengthVar) && near(faceStreakSpeed, c.faceStreakSpeed)
                && near(faceStreakSpeedVar, c.faceStreakSpeedVar) && near(faceWander, c.faceWander)
                && near(faceWanderStrength, c.faceWanderStrength) && near(opacity, c.opacity);
    }
}
