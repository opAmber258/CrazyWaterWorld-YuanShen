package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 排水泵（L1 纯逻辑）：按速率抽水、不抽到负数、能抽连海空腔的积水。 */
class ShipDrainPumpTest {

    /** 5×5×5 封闭空心船壳（无破口）。 */
    private static Set<BlockPos> sealedCube() {
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
        return solid;
    }

    /** 5×5×5 空心船壳，(2,0,0) 破一格 → 一个连海空腔。 */
    private static Set<BlockPos> breachedCube() {
        Set<BlockPos> solid = sealedCube();
        solid.remove(new BlockPos(2, 0, 0));
        return solid;
    }

    private static ShipSimulation simulation(Set<BlockPos> solid) {
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
        return new ShipSimulation(geometry, new MachineCount(0, 0, 0, 0, 0, 0, 1));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("自动泵按水量判断积水（不能用绝对 Y 和 0.05 比）")
    void autoPumpWetCheckUsesWaterUnits() {
        ShipSimulation sim = simulation(sealedCube());
        assertTrue(!sim.bodyIsWet(0), "空舱不该算有水");
        sim.flood(0, 100.0D);
        assertTrue(sim.bodyIsWet(0), "有 1 格水时自动泵应该启动");
        // bodyLevel 是绝对 Y（深海为负），旧写法 bodyLevel >= 0.05 永远为 false
        assertTrue(sim.bodyLevel(0) < 0.05D, "深海里的水位是负的绝对 Y");
    }

    @Test
    void 按速率抽密闭舱室的水() {        ShipSimulation sim = simulation(sealedCube());
        // 水泵只抽自己所在的那个水体，必须给泵格（装配坐标）。
        sim.flood(0, 1000.0D);
        assertEquals(1000.0D, sim.waterUnits(), 1.0E-6);

        sim.reportPump(new BlockPos(0, 0, 0), 600.0D);
        sim.tick(1.0D); // 秒

        // 600 单位/秒 × 1 秒 = 6 格；剩余 400
        assertEquals(400.0D, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 关闭时不排水() {
        ShipSimulation sim = simulation(sealedCube());
        sim.flood(0, 1000.0D);
        sim.reportPump(new BlockPos(0, 0, 0), 0.0D);
        sim.tick(1.0D);
        assertEquals(1000.0D, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 同舱两台泵速率相加() {
        ShipSimulation sim = simulation(sealedCube());
        sim.flood(0, 1000.0D);
        sim.reportPump(new BlockPos(0, 0, 0), 300.0D);
        sim.reportPump(new BlockPos(1, 0, 0), 300.0D);
        sim.tick(1.0D);
        assertEquals(400.0D, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 超过一秒没上报的泵自动移除() {
        ShipSimulation sim = simulation(sealedCube());
        sim.flood(0, 1000.0D);
        sim.reportPump(new BlockPos(0, 0, 0), 600.0D);
        for (int i = 0; i < 41; i++) {
            sim.tick(0.0D);
        }
        assertEquals(0, sim.pumpCount(), "超过 40 tick 没上报应视为已拆除");
        sim.tick(1.0D);
        assertEquals(1000.0D, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 不会抽成负数且能抽连海空腔() {
        ShipSimulation sim = simulation(breachedCube());
        // 破口空腔里先灌 500 单位（flood 只作用于密闭舱室，连海空腔要用 seedWater）
        assertEquals(1, sim.geometry().openPockets().size());
        sim.seedWater(0.0D, 500.0D);
        double before = sim.pocketWaterTotal() + sim.waterUnits();
        assertTrue(before > 0.0D);

        sim.reportPump(new BlockPos(0, 0, 0), 3000.0D);
        for (int i = 0; i < 10; i++) {
            sim.tick(1.0D);
        }
        assertEquals(0.0D, sim.pocketWaterTotal() + sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 负流量往舱内泵入但不超容量() {
        ShipSimulation sim = simulation(sealedCube());
        assertEquals(0.0D, sim.waterUnits(), 1.0E-6);

        // -100 单位/秒 泵入 1 秒 = 1 格
        sim.reportPump(new BlockPos(0, 0, 0), -100.0D);
        sim.tick(1.0D);
        assertEquals(100.0D, sim.waterUnits(), 1.0E-6);

        // 一直泵入只会灌满舱室，不会超过容量
        for (int i = 0; i < 200; i++) {
            sim.tick(1.0D);
        }
        double capacity = sim.compartmentVolume(0) * ShipSimulation.UNITS_PER_BLOCK;
        assertEquals(capacity, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 同舱泵出与泵入分别结算() {
        ShipSimulation sim = simulation(sealedCube());
        sim.flood(0, 1000.0D);
        // 一台 300 泵出、一台 100 泵入：净效果 200 单位/秒
        sim.reportPump(new BlockPos(0, 0, 0), 300.0D);
        sim.reportPump(new BlockPos(1, 0, 0), -100.0D);
        sim.tick(1.0D);
        assertEquals(800.0D, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 压载泵所在舱计入压载水量与容量() {
        ShipSimulation sim = simulation(sealedCube());
        // 停车（0 流量）也要登记压载舱，否则水位不变时浮力会突然归零
        sim.reportPump(new BlockPos(0, 0, 0), 0.0D, true);
        sim.tick(1.0D);
        assertEquals(0.0D, sim.ballastPercent(), 1.0E-6);
        assertTrue(sim.ballastCapacityUnits() > 0.0D, "压载舱容量应来自泵所在舱室");

        // 负流量泵入 1 秒：100 单位进压载舱
        sim.reportPump(new BlockPos(0, 0, 0), -100.0D, true);
        sim.tick(1.0D);
        assertEquals(100.0D, sim.ballastUnits(), 1.0E-6);
        assertEquals(100.0D / sim.ballastCapacityUnits(), sim.ballastPercent(), 1.0E-6);

        // 正流量泵出：压载水回落
        sim.reportPump(new BlockPos(0, 0, 0), 100.0D, true);
        sim.tick(1.0D);
        assertEquals(0.0D, sim.ballastUnits(), 1.0E-6);
    }

    @Test
    void 普通泵不登记压载舱() {
        ShipSimulation sim = simulation(sealedCube());
        sim.reportPump(new BlockPos(0, 0, 0), -100.0D);
        sim.tick(1.0D);
        assertEquals(0.0D, sim.ballastCapacityUnits(), 1.0E-6);
        assertEquals(0.0D, sim.ballastPercent(), 1.0E-6);
        // 水还是进了舱室，只是不算压载——排水泵/小泵原有行为不变
        assertEquals(100.0D, sim.waterUnits(), 1.0E-6);
    }

    @Test
    void 速率被夹紧到合法范围() {
        ShipGeometry.Probe emptyProbe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }
        };
        ShipSimulation sim = new ShipSimulation(
                ShipGeometry.scanShip(BlockPos.ZERO, 8, p -> false, emptyProbe),
                new MachineCount(0, 0, 0, 0, 0, 0, 0));
        // 带符号流量：正值泵出、负值泵入，两个方向都夹在 30000 以内
        // （单台普通泵按原作换算后约 14062，旧上限 3000 会把泵削到 1/5）。
        sim.reportPump(new BlockPos(0, 0, 0), -99999.0D);
        assertEquals(30000.0D, sim.pumpTotalRate(), 1.0E-6);
        sim.reportPump(new BlockPos(0, 0, 0), 99999.0D);
        assertEquals(30000.0D, sim.pumpTotalRate(), 1.0E-6);
    }

    @Test
    void 压载舱注水停在舱门下方那一格() {
        // 5×7×5 船壳，中间 y=0 有一层地板，只留 (0,0,0) 一格舱门。
        // 下层舱 y=-2..0（含门格）是压载舱，上层 y=1..3 是驾驶室。
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -3 || y == 3;
                    boolean deck = y == 0 && Math.abs(x) <= 1 && Math.abs(z) <= 1;
                    if (shell || deck) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        BlockPos door = new BlockPos(0, 0, 0);
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isDoorway(BlockPos pos) {
                return pos.equals(door);
            }

            @Override
            public boolean isOpenDoorway(BlockPos pos) {
                return pos.equals(door);
            }
        };
        Set<BlockPos> shipBlocks = new HashSet<>(solid);
        shipBlocks.add(door);
        ShipGeometry.Result geometry = ShipGeometry.scanShip(
                BlockPos.ZERO, 8, shipBlocks::contains, probe);
        ShipSimulation sim = new ShipSimulation(geometry, new MachineCount(0, 0, 0, 0, 0, 0, 1));

        BlockPos pumpCell = new BlockPos(0, -1, 0);
        int body = sim.pumpBody(pumpCell);
        assertTrue(body >= 0, "泵格应落在下层压载舱里");
        double full = sim.bodyVolume(body) * ShipSimulation.UNITS_PER_BLOCK;
        assertTrue(full > 0.0D);

        // 带 door 上限的压载泵一直泵入：水面只能到门格下沿（y=0），门格本身保持干。
        sim.reportPump(pumpCell, -600.0D, true, -600.0D, true);
        for (int i = 0; i < 200; i++) {
            sim.tick(1.0D);
        }
        assertEquals(0.0D, sim.bodyLevel(body), 1.0E-6, "水面应停在舱门下方那一格");
        assertTrue(sim.bodyWater(body) < full, "注水上限必须小于整舱容量");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("泵流量按原作换算：普通泵 ≈140 格³/秒，小泵 ≈23 格³/秒")
    void pumpFlowMatchesOriginalUnits() {
        // 原作 Pump.Update: WaterVolume += MaxFlow * deltaTime * 60（1/60 秒一帧），
        // 原版一格 256 水体单位，我们一格 100 单位 → 600×60×100/256。
        assertEquals(14_062, com.opamb.barotrauma.blockentity.DrainPumpBlockEntity.MAX_FLOW);
        assertEquals(2_344, com.opamb.barotrauma.blockentity.DrainPumpBlockEntity.SMALL_MAX_FLOW);
        // 普通泵 : 小型泵仍是原版的 6 : 1
        assertEquals(6.0D, com.opamb.barotrauma.blockentity.DrainPumpBlockEntity.MAX_FLOW
                / (double) com.opamb.barotrauma.blockentity.DrainPumpBlockEntity.SMALL_MAX_FLOW, 0.01D);
        // 33 格的压载舱（3300 单位）满流量 1 秒内能灌满——不再有"等泵"的延迟
        assertTrue(com.opamb.barotrauma.blockentity.DrainPumpBlockEntity.MAX_FLOW > 3_300);
    }
}
