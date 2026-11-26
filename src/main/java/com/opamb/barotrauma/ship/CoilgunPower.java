package com.opamb.barotrauma.ship;

import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** PowerContainer eligibility and equal-split pulse consumption from Powered.cs and Turret.cs. */
public final class CoilgunPower {
    private static final double FRAMES_PER_MINUTE = 3600;
    private static final double FRAMES_PER_SECOND = 60;
    private static final double EPSILON = 1e-9;

    private CoilgunPower() {}

    public record Source(double chargeKwm, double maxOutputKw, double capacityKwm) {
        public double availablePulseKw() {
            return Math.min(chargeKwm * FRAMES_PER_MINUTE, maxOutputKw / FRAMES_PER_SECOND);
        }
    }

    public static List<Source> read(ServerLevel level, List<PowerStorage> devices) {
        return devices.stream().map(device -> new Source(device.weaponCharge(level),
                device.weaponMaxOutputKw(), device.capacityKwm())).toList();
    }

    public static boolean canFire(List<Source> sources, double requiredKw) {
        return sources.stream().mapToDouble(Source::availablePulseKw).sum() + EPSILON >= requiredKw;
    }

    public static double shotCharge(double requiredKw) {
        return requiredKw / FRAMES_PER_MINUTE;
    }

    /** A capped source contributes what it can, and the remaining sources share the rest. */
    public static double[] pulseShare(List<Source> sources, double requiredKw) {
        double[] shares = new double[sources.size()];
        double[] remaining = sources.stream().mapToDouble(source ->
                Math.min(source.chargeKwm * FRAMES_PER_MINUTE, source.maxOutputKw)).toArray();
        while (requiredKw > EPSILON) {
            int active = 0;
            double smallest = Double.POSITIVE_INFINITY;
            for (double kw : remaining) {
                if (kw > EPSILON) {
                    active++;
                    smallest = Math.min(smallest, kw);
                }
            }
            if (active == 0) break;
            double take = Math.min(requiredKw / active, smallest);
            for (int i = 0; i < remaining.length; i++) {
                if (remaining[i] <= EPSILON) continue;
                remaining[i] -= take;
                shares[i] += shotCharge(take);
                requiredKw -= take;
            }
        }
        return shares;
    }
}
