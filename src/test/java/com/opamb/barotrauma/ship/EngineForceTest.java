package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 原作 {@code Engine.Update} 的逐引擎推力：maxforce × 推力% × 电压^(2/3) × 耐久插值。
 *
 * <p>标准引擎 maxforce=300、满推力 100%、额定电压 1、满耐久：
 * 300 × 100 × 1 × lerp(0.5,2,1)=2 → 60000（原始力单位）。
 * 这个数再乘 0.1 驾驶系数、除以原作儒艮质量 47 t，才是实际加速度。</p>
 */
class EngineForceTest {

    private static final double EPS = 1.0E-6D;

    private static ShipSimulation simulation() {
        ShipGeometry.Compartment compartment = new ShipGeometry.Compartment(
                Set.of(new BlockPos(0, 0, 0)), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(compartment), List.of(), 1, 1,
                new ShipGeometry.Bounds(0, 0, 0, 0, 0, 0), Set.of(), List.of(), Set.of());
        return new ShipSimulation(geometry, MachineCount.EMPTY);
    }

    private static void ratedVoltage(ShipSimulation sim) {
        // 负载 2000 kW、供电 2000 kW → 电压 1。
        sim.reportReactor(new BlockPos(0, 5, 0), 2000.0D, 2000.0D, 40.0D, false, false);
    }

    @Test
    void 满推力满耐久按原作等于六万() {
        ShipSimulation sim = simulation();
        ratedVoltage(sim);
        sim.reportEngine(new BlockPos(1, 0, 0), 100.0D, 2000.0D, 300.0D, 1.0D);
        sim.tick(0.05D);

        assertEquals(60_000.0D, sim.engineForceRaw(), EPS);
    }

    @Test
    void 半耐久按0点5到2插值() {
        ShipSimulation sim = simulation();
        ratedVoltage(sim);
        sim.reportEngine(new BlockPos(1, 0, 0), 100.0D, 2000.0D, 300.0D, 0.5D);
        sim.tick(0.05D);

        // 300 × 100 × 1 × (0.5 + 1.5×0.5) = 37500
        assertEquals(37_500.0D, sim.engineForceRaw(), EPS);
    }

    @Test
    void 半推力按线性出力但耗电一点五次方() {
        ShipSimulation sim = simulation();
        sim.reportReactor(new BlockPos(0, 5, 0), 707.1D, 707.1D, 40.0D, false, false);
        sim.reportEngine(new BlockPos(1, 0, 0), 50.0D, 707.1D, 300.0D, 1.0D);
        sim.tick(0.05D);

        // 电压 1；推力 50% → 300 × 50 × 2 = 30000
        assertEquals(30_000.0D, sim.engineForceRaw(), EPS);
    }

    @Test
    void 九台引擎仍只算八台() {
        ShipSimulation sim = simulation();
        sim.reportReactor(new BlockPos(0, 5, 0), 900.0D, 900.0D, 40.0D, false, false);
        for (int i = 0; i < 9; i++) {
            sim.reportEngine(new BlockPos(i, 0, 0), 100.0D, 100.0D, 100.0D, 1.0D);
        }
        sim.tick(0.05D);

        assertEquals(8.0D * 100.0D * 100.0D * 2.0D, sim.engineForceRaw(), EPS);
    }
}
