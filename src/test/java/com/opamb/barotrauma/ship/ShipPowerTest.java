package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 船电：没堆没电、按输出/负载比例供电、上报过期。 */
class ShipPowerTest {

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
    void 没有反应堆全船没电() {
        ShipSimulation sim = simulation(1);
        sim.tick(1.0D);
        assertEquals(0.0D, sim.powerRatio(), 1.0E-6);
        assertEquals(0.0D, sim.report().powerOutput(), 1.0E-6);
    }

    @Test
    void 反应堆按输出与负载的比例供电() {
        ShipSimulation sim = simulation(1);
        // 一台泵请求 100 单位/秒 + 声纳 100 kW：负载 400 kW
        sim.reportPump(new BlockPos(0, 0, 0), 100.0D);
        sim.reportReactor(new BlockPos(1, 0, 0), 200.0D, 5000.0D, 50.0D, false, false);
        sim.tick(1.0D);
        assertEquals(400.0D, sim.report().powerDemand(), 1.0E-6);
        assertEquals(0.5D, sim.powerRatio(), 1.0E-6);
    }

    @Test
    void 反应堆没开机时输出为零也没电() {
        ShipSimulation sim = simulation(1);
        sim.reportReactor(new BlockPos(0, 0, 0), 0.0D, 5000.0D, 20.0D, false, false);
        sim.tick(1.0D);
        assertEquals(0.0D, sim.powerRatio(), 1.0E-6);
    }

    @Test
    void 反应堆上报过期后不再供电() {
        ShipSimulation sim = simulation(0);
        sim.reportReactor(new BlockPos(0, 0, 0), 4000.0D, 5000.0D, 50.0D, false, false);
        for (int i = 0; i < 41; i++) {
            sim.tick(0.1D);
        }
        assertEquals(0, sim.reactorCount());
        assertEquals(0.0D, sim.powerRatio(), 1.0E-6);
    }

    @Test
    void 没有引擎时只有声纳负载() {
        ShipSimulation sim = simulation(1);
        sim.reportReactor(new BlockPos(0, 0, 0), 5000.0D, 5000.0D, 50.0D, false, false);
        sim.tick(1.0D);
        assertEquals(0, sim.engineCount());
        assertEquals(100.0D, sim.report().powerDemand(), 1.0E-6);
        assertEquals(1.0D, sim.powerRatio(), 1.0E-6);
    }

    @Test
    void 多台反应堆输出相加() {
        ShipSimulation sim = simulation(0);
        sim.reportReactor(new BlockPos(0, 0, 0), 2000.0D, 5000.0D, 50.0D, false, false);
        sim.reportReactor(new BlockPos(1, 0, 0), 2000.0D, 5000.0D, 50.0D, false, false);
        sim.tick(1.0D);
        assertEquals(4000.0D, sim.report().powerOutput(), 1.0E-6);
        assertEquals(2, sim.reactorCount());
    }

    @Test
    void 熔毁与过热标志透传() {
        ShipSimulation sim = simulation(0);
        sim.reportReactor(new BlockPos(0, 0, 0), 100.0D, 5000.0D, 90.0D, true, true);
        sim.tick(1.0D);
        assertTrue(sim.report().overheated());
        assertTrue(sim.report().meltingDown());
        assertEquals(90.0D, sim.report().temperature(), 1.0E-6);
    }

    @Test
    void 泵停转时请求流量仍算负载() {
        ShipSimulation sim = simulation(0);
        // 实际流量 0（没电），但泵想跑 600 → 负载 300 kW
        sim.reportPump(new BlockPos(0, 0, 0), 0.0D, false, 600.0D);
        sim.reportReactor(new BlockPos(0, 0, 0), 300.0D, 5000.0D, 50.0D, false, false);
        sim.tick(1.0D);
        assertEquals(300.0D, sim.report().powerDemand(), 1.0E-6);
        assertEquals(1.0D, sim.powerRatio(), 1.0E-6);
        assertFalse(sim.report().meltingDown());
    }
}
