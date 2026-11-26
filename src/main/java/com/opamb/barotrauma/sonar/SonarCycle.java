package com.opamb.barotrauma.sonar;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Runtime state shared by everyone viewing the same terminal, accessed on the server thread. */
public final class SonarCycle {
    private SonarPulse pulse;
    private SonarScan.Mode mode = SonarScan.Mode.ACTIVE;
    private int range = SonarScan.DEFAULT_RANGE;
    private Vec3 direction = new Vec3(0, 0, 1);

    public void configure(SonarScan.Mode mode, int range, Vec3 direction) {
        this.mode = mode;
        this.range = Mth.clamp(range, SonarScan.MIN_RANGE, SonarScan.MAX_RANGE);
        if (Double.isFinite(direction.lengthSqr()) && direction.lengthSqr() > 1.0E-8D) {
            this.direction = direction.normalize();
        }
    }

    public SonarPulse update(boolean online, long now, BlockPos center, Supplier<SonarScan.Frame> scan) {
        if (!online) {
            pulse = null;
        } else if (pulse == null || now - pulse.tick() >= SonarPulse.interval(pulse.mode())) {
            SonarScan.Frame echoes = scan.get();
            pulse = new SonarPulse(now, center, echoes.range(), mode, direction, echoes.points());
        }
        return pulse;
    }

    public SonarScan.Mode mode() {
        return mode;
    }

    public int range() {
        return range;
    }

    public Vec3 direction() {
        return direction;
    }

    public double sonarPowerKw() {
        return mode == SonarScan.Mode.PASSIVE ? 10.0D : 100.0D;
    }
}
