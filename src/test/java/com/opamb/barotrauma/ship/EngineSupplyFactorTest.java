package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 推进的逐引擎供电汇总（纯逻辑，不需要开世界）。
 *
 * <p>8 台封顶必须在逐引擎汇总里做：9 台引擎在额定电压下加起来是 9，
 * 远低于系数上限（8×2^(2/3)≈12.7），只靠系数上限截不住多出来的推力。</p>
 */
class EngineSupplyFactorTest {

    private static final double EPS = 1.0E-6D;

    private static ShipSimulation simulation() {
        ShipGeometry.Compartment compartment = new ShipGeometry.Compartment(
                Set.of(new BlockPos(0, 0, 0)), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(compartment), List.of(), 1, 1,
                new ShipGeometry.Bounds(0, 0, 0, 0, 0, 0), Set.of(), List.of(), Set.of());
        return new ShipSimulation(geometry, MachineCount.EMPTY);
    }

    @Test
    @DisplayName("9 台引擎在额定电压下只算 8 台推力")
    void nineEnginesCapAtEight() {
        ShipSimulation sim = simulation();
        for (int i = 0; i < 9; i++) {
            sim.reportEngine(new BlockPos(i, 0, 0), 100.0D, 100.0D, 1000.0D);
        }
        sim.reportReactor(new BlockPos(0, 5, 0), 900.0D, 900.0D, 40.0D, false, false);
        sim.tick(0.05D);

        assertEquals(8.0D, sim.engineSupplyFactor(), EPS,
                "9 台 × 电压 1 不能变成 9 台推力");
    }

    @Test
    @DisplayName("超压时按每台 min(电压,2) 算，但台数仍封顶 8")
    void overVoltageStillCappedByCount() {
        ShipSimulation sim = simulation();
        for (int i = 0; i < 9; i++) {
            sim.reportEngine(new BlockPos(i, 0, 0), 100.0D, 100.0D, 1000.0D);
        }
        // 需求 900、供电 1800 → 电压 2，每台系数 2^(2/3)。
        sim.reportReactor(new BlockPos(0, 5, 0), 1800.0D, 1800.0D, 40.0D, false, false);
        sim.tick(0.05D);

        assertEquals(8.0D * Math.pow(2.0D, 2.0D / 3.0D), sim.engineSupplyFactor(), EPS);
    }

    @Test
    @DisplayName("绑在坏接线盒上的引擎不计入（部分断电）")
    void unpoweredEngineDoesNotCount() {
        ShipSimulation sim = simulation();
        BlockPos deadBox = new BlockPos(20, 0, 0);
        BlockPos liveBox = new BlockPos(21, 0, 0);
        for (int i = 0; i < 8; i++) {
            BlockPos engine = new BlockPos(i, 0, 0);
            sim.reportEngine(engine, 100.0D, 100.0D, 1000.0D);
            sim.reportConsumer(engine, 100.0D, 0.5D, i == 0 ? deadBox : liveBox);
        }
        sim.reportJunctionBox(deadBox, 0.0D);
        sim.reportJunctionBox(liveBox, 100.0D);
        // 有 consumers 时负载只算有效支路：7 台 × 100 kW，供电 700 → 电压 1。
        sim.reportReactor(new BlockPos(0, 5, 0), 700.0D, 700.0D, 40.0D, false, false);
        sim.tick(0.05D);

        assertEquals(7.0D, sim.engineSupplyFactor(), EPS,
                "断电那台引擎不产生推力，剩下 7 台各 1.0");
    }
}
