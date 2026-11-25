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

    @Test
    void 电池在供电缺口时补足电压() {
        ShipSimulation sim = simulation(0);
        // 设备 2000 kW，反应堆只有 1000 kW → 电池补 1000，电压回到 1
        sim.reportConsumer(new BlockPos(0, 0, 0), 2000.0D, 0.5D, null);
        sim.reportReactor(new BlockPos(1, 0, 0), 1000.0D, 5000.0D, 50.0D, false, false);
        sim.reportStorage(new BlockPos(0, 1, 0), 2000.0D, 2000.0D, 2000.0D, 1000.0D,
                null, 100.0D);
        sim.tick(1.0D);
        assertEquals(1.0D, sim.voltage(), 1.0E-6);
        assertEquals(1000.0D, sim.batteryOutputKw(), 1.0E-6);
        assertEquals(2000.0D, sim.report().powerDemand(), 1.0E-6);
        // 放电 1000 kW × 1 秒 = 16.67 kW·min
        assertEquals(2000.0D - 1000.0D / 60.0D, sim.storedKwm(), 1.0E-6);
    }

    @Test
    void 富余功率按效率给电池充电() {
        ShipSimulation sim = simulation(0);
        sim.reportConsumer(new BlockPos(0, 0, 0), 1000.0D, 0.5D, null);
        sim.reportReactor(new BlockPos(1, 0, 0), 3000.0D, 5000.0D, 50.0D, false, false);
        // 满充电功率 500 kW，效率 95%
        sim.reportStorage(new BlockPos(0, 1, 0), 0.0D, 2000.0D, 2000.0D, 500.0D,
                null, 100.0D);
        sim.tick(1.0D);
        assertEquals(500.0D, sim.batteryChargeKw(), 1.0E-6);
        assertEquals(500.0D / 60.0D * 0.95D, sim.storedKwm(), 1.0E-6);
    }

    @Test
    void 接线盒耐久为零时设备断电且不计入负载() {
        ShipSimulation sim = simulation(0);
        BlockPos box = new BlockPos(0, 1, 0);
        BlockPos device = new BlockPos(0, 0, 0);
        sim.reportJunctionBox(box, 0.0D);
        sim.reportConsumer(device, 500.0D, 0.5D, box);
        sim.reportReactor(new BlockPos(1, 0, 0), 5000.0D, 5000.0D, 50.0D, false, false);
        sim.tick(1.0D);
        // 断支路：设备拿不到电，需求也不能拖累其他设备
        assertEquals(0.0D, sim.consumerVoltage(device), 1.0E-6);
        assertEquals(0.0D, sim.report().powerDemand(), 1.0E-6);
        // 修好后同一台设备能正常取电
        sim.reportJunctionBox(box, 100.0D);
        sim.tick(1.0D);
        assertTrue(sim.consumerVoltage(device) >= 0.5D);
        assertEquals(500.0D, sim.report().powerDemand(), 1.0E-6);
    }

    @Test
    void 设备改绑接线盒后负载跟着走() {
        ShipSimulation sim = simulation(0);
        BlockPos boxA = new BlockPos(0, 1, 0);
        BlockPos boxB = new BlockPos(0, -1, 0);
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(1, 0, 0);
        sim.reportJunctionBox(boxA, 100.0D);
        sim.reportJunctionBox(boxB, 100.0D);
        sim.reportConsumer(first, 300.0D, 0.5D, boxA);
        sim.reportConsumer(second, 200.0D, 0.5D, boxB);
        sim.tick(1.0D);
        assertEquals(300.0D, sim.boxLoad(boxA), 1.0E-6);
        assertEquals(200.0D, sim.boxLoad(boxB), 1.0E-6);
        // 改绑到 B：A 的负载归零，B 变成两台之和
        sim.reportConsumer(first, 300.0D, 0.5D, boxB);
        sim.tick(1.0D);
        assertEquals(0.0D, sim.boxLoad(boxA), 1.0E-6);
        assertEquals(500.0D, sim.boxLoad(boxB), 1.0E-6);
    }

    @Test
    void 超级电容器按原作百分之一效率充电() {
        ShipSimulation sim = simulation(0);
        sim.reportConsumer(new BlockPos(0, 0, 0), 1000.0D, 0.5D, null);
        sim.reportReactor(new BlockPos(1, 0, 0), 3000.0D, 5000.0D, 50.0D, false, false);
        // 容量 20 kW·min、充电 1000 kW、效率 0.1；7 参数重载显式给效率。
        sim.reportStorage(new BlockPos(0, 1, 0), 0.0D, 20.0D, 20_000_000.0D, 1000.0D,
                null, 100.0D, 0.1D);
        sim.tick(1.0D);
        assertEquals(1000.0D, sim.batteryChargeKw(), 1.0E-6);
        // 充进去 1000 kW × 1 秒，只有 10% 存下来
        assertEquals(1000.0D / 60.0D * 0.1D, sim.storedKwm(), 1.0E-6);
    }

    @Test
    void 超级电容器单步放电受能量上限约束() {
        ShipSimulation sim = simulation(0);
        // 负载 15000 kW、无反应堆；电容满电 20 kW·min，单步 0.1 秒最多放 12000 kW
        sim.reportConsumer(new BlockPos(0, 0, 0), 15_000.0D, 0.5D, null);
        sim.reportStorage(new BlockPos(0, 1, 0), 20.0D, 20.0D, 20_000_000.0D, 1000.0D,
                null, 100.0D, 0.1D);
        sim.tick(0.1D);
        assertEquals(12_000.0D, sim.batteryOutputKw(), 1.0E-6);
        assertEquals(0.8D, sim.voltage(), 1.0E-6);
        // 20 kW·min 全部放完（12000 kW × 0.1 秒 = 20 kW·min）
        assertEquals(0.0D, sim.storedKwm(), 1.0E-6);
    }

    @Test
    void stockBatteryBridgesFullThrottleWhileTheReactorCatchesUp() {
        ShipSimulation sim = simulation(0);
        BlockPos engine = new BlockPos(-1, 0, 0);
        BlockPos bus = new BlockPos(0, 0, 0);
        sim.reportJunctionBox(bus, 100);
        sim.reportConsumer(engine, com.opamb.barotrauma.blockentity.EngineBlockEntity.DUGONG_POWER_KW, 0.5, bus);
        sim.reportConsumer(new BlockPos(0, 0, 1), 1000.0, 0.5, bus);
        sim.reportReactor(new BlockPos(0, 0, -1), 1000.0, 3000.0, 50.0, false, false);
        sim.reportStorage(new BlockPos(1, 0, 0), 500, 500, 650, 160, bus, 100);
        sim.tick(0.1);
        assertEquals(1.0, sim.consumerVoltage(engine), 1.0E-6);
        assertEquals(600.0, sim.batteryOutputKw(), 1.0E-6);
    }
}
