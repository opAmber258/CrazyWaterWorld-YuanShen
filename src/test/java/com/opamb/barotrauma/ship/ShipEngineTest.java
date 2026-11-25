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
    void 反向制动力和残余正向力按各自方向相加() {
        ShipSimulation sim = simulation(0);
        sim.reportReactor(BlockPos.ZERO, 900.0D, 900.0D, 50.0D, false, false);
        sim.reportEngine(new BlockPos(1, 0, 0), -45.0D, 600.0D, 300.0D);
        sim.reportEngine(new BlockPos(-1, 0, 0), 15.0D, 300.0D, 300.0D);
        sim.tick(1.0D);
        assertEquals(-18_000.0D, sim.engineForceRaw(), 1.0E-6);
        assertEquals(900.0D, sim.engineDemandKw(), 1.0E-6);
    }

    @Test
    void estimatesBrakingFromIdleConnectedEnginesAndReservesOtherLoads() {
        ShipSimulation sim = simulation(0);
        BlockPos online = new BlockPos(1, 0, 0);
        BlockPos offline = new BlockPos(-1, 0, 0);
        BlockPos deadBox = new BlockPos(-1, 1, 0);
        sim.reportEngine(online, 0.0D, 0.0D, 300.0D, 0.5D, 2250.0D);
        sim.reportEngine(offline, 0.0D, 0.0D, 300.0D);
        sim.reportConsumer(online, 0.0D, 0.5D, null);
        sim.reportConsumer(offline, 0.0D, 0.5D, deadBox);
        sim.reportConsumer(new BlockPos(0, 1, 0), 500.0D, 0.5D, null);
        sim.reportReactor(BlockPos.ZERO, 700.0D, 700.0D, 50.0D, false, false);
        sim.tick(0.1D);

        var capacity = sim.engineCapacity(100);
        double expectedFraction = Math.pow(375.0D / 2250.0D, 2.0D / 3.0D);
        assertEquals(expectedFraction, capacity.throttle(), 1.0E-9D);
        assertEquals(300.0D * 100.0D * 1.25D * expectedFraction * Math.pow(0.8D, 2.0D / 3.0D),
                capacity.rawForce(), 1.0E-6D);
        assertEquals(1.0D, sim.engineCapacity(20).throttle(), 1.0E-9D);
        assertEquals(0.0D, sim.engineForceRaw(), 1.0E-9D);
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
