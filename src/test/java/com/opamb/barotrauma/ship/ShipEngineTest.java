package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 引擎上报：数量、耗电、过期；没有引擎时推进器拿不到引擎数。 */
class ShipEngineTest {

    private static ShipSimulation simulation(int sonar) {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(y) == 2 || Math.abs(z) == 2;
                    if (shell) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }
        };
        Predicate<BlockPos> isShipBlock = solid::contains;
        ShipGeometry.Result geometry = ShipGeometry.scanShip(BlockPos.ZERO, 8, isShipBlock, probe);
        return new ShipSimulation(geometry, new MachineCount(0, 0, 0, 0, 0, 0, sonar));
    }

    @Test
    void 没有引擎时数量为零() {
        ShipSimulation sim = simulation(0);
        sim.tick(1.0D);
        assertEquals(0, sim.engineCount());
        assertEquals(0.0D, sim.engineDemandKw(), 1.0E-6);
    }

    @Test
    void 一台引擎计入数量和耗电() {
        ShipSimulation sim = simulation(1);
        sim.reportReactor(new BlockPos(0, 0, 0), 5000.0D, 5000.0D, 50.0D, false, false);
        sim.reportEngine(new BlockPos(1, 0, 0), 100.0D, 2000.0D, 300.0D);
        sim.tick(1.0D);
        assertEquals(1, sim.engineCount());
        assertEquals(2000.0D, sim.engineDemandKw(), 1.0E-6);
        // 2000 引擎 + 100 声纳
        assertEquals(2100.0D, sim.report().powerDemand(), 1.0E-6);
        assertEquals(1.0D, sim.powerRatio(), 1.0E-6);
    }

    @Test
    void 多台引擎耗电相加() {
        ShipSimulation sim = simulation(0);
        sim.reportReactor(new BlockPos(0, 0, 0), 5000.0D, 5000.0D, 50.0D, false, false);
        sim.reportEngine(new BlockPos(1, 0, 0), 100.0D, 2000.0D, 300.0D);
        sim.reportEngine(new BlockPos(2, 0, 0), 100.0D, 2000.0D, 300.0D);
        sim.tick(1.0D);
        assertEquals(2, sim.engineCount());
        assertEquals(4000.0D, sim.engineDemandKw(), 1.0E-6);
        assertEquals(4000.0D, sim.report().powerDemand(), 1.0E-6);
        assertEquals(1.0D, sim.powerRatio(), 1.0E-6);
    }

    @Test
    void 半推力按一点五次方耗电() {
        ShipSimulation sim = simulation(0);
        // 50% 目标力 ≈ 2000 × 0.5^1.5 ≈ 707 kW
        sim.reportEngine(new BlockPos(0, 0, 0), 50.0D, 707.10678D, 300.0D);
        sim.tick(1.0D);
        assertEquals(707.10678D, sim.engineDemandKw(), 1.0E-4);
    }

    @Test
    void 引擎上报过期后数量归零() {
        ShipSimulation sim = simulation(0);
        sim.reportEngine(new BlockPos(0, 0, 0), 100.0D, 2000.0D, 300.0D);
        for (int i = 0; i < 41; i++) {
            sim.tick(0.1D);
        }
        assertEquals(0, sim.engineCount());
        assertEquals(0.0D, sim.engineDemandKw(), 1.0E-6);
    }

    @Test
    void 供电不足时比例低于半压阈值() {
        ShipSimulation sim = simulation(1);
        sim.reportReactor(new BlockPos(0, 0, 0), 1000.0D, 5000.0D, 50.0D, false, false);
        sim.reportEngine(new BlockPos(1, 0, 0), 100.0D, 2000.0D, 300.0D);
        sim.tick(1.0D);
        // 1000 / (2000 + 100) ≈ 0.476 < 0.5，VsThruster 会把推力归零
        assertEquals(1000.0D / 2100.0D, sim.powerRatio(), 1.0E-6);
    }
}
