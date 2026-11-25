package com.opamb.barotrauma.sonar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A single acquisition. All point offsets and the beam direction belong to its launch pose. */
public record SonarPulse(long tick, BlockPos origin, int range, SonarScan.Mode mode,
                         Vec3 direction, int[] points) {

    public static final int ACTIVE_TICKS = 40;
    public static final int PASSIVE_TICKS = 20;
    public static final int FADE_TICKS = 160;
    public static final int HISTORY_TICKS = ACTIVE_TICKS + FADE_TICKS;

    public static int interval(SonarScan.Mode mode) {
        return mode == SonarScan.Mode.PASSIVE ? PASSIVE_TICKS : ACTIVE_TICKS;
    }

    public float progress(double now) {
        return (float) Math.max(0.0, Math.min(1.0, (now - tick) / interval(mode)));
    }

    public double distance(int point) {
        int x = SonarScan.x(point), y = SonarScan.y(point), z = SonarScan.z(point);
        return Math.sqrt(x * x + y * y + z * z);
    }

    public float ageSeconds(double distance, double now) {
        double arrival = mode == SonarScan.Mode.PASSIVE ? tick : tick + ACTIVE_TICKS * distance / range;
        return (float) ((now - arrival) / 20.0D);
    }

    public float terrainSignal(int point) {
        return (mode == SonarScan.Mode.PASSIVE ? 0.5F : 2.0F) * (1.5F + 0.5F * noise(point));
    }

    /** The hash keeps the original random lifetime stable while a world-space echo is drawn. */
    public float noise(int point) {
        int hash = (int) tick ^ point;
        hash = (hash ^ (hash >>> 16)) * 0x7feb352d;
        hash = (hash ^ (hash >>> 15)) * 0x846ca68b;
        return ((hash ^ (hash >>> 16)) >>> 8) / 16777216.0F;
    }

    public float fadeTimer(int point, double now, float distortion) {
        double distance = distance(point);
        float age = ageSeconds(distance, now);
        if (age < 0.0F) {
            return 0.0F;
        }
        float signal = switch (SonarScan.type(point)) {
            case SonarScan.TYPE_CONTACT -> mode == SonarScan.Mode.PASSIVE ? 1.0F : 2.0F;
            case SonarScan.TYPE_ORE -> 2.0F;
            default -> terrainSignal(point) * (1.0F - (float) (distance / range));
        };
        return Math.max(0.0F, signal - age * fadeRate(distortion));
    }

    public static float fadeRate(float distortion) {
        return 0.5F + 1.5F * Math.max(0.0F, Math.min(1.0F, distortion));
    }
}
