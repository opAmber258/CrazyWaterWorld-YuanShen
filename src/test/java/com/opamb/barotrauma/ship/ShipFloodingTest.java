package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 破口进水（L1 纯逻辑）：船壳破一格 → 连海空腔的水量应随时间长。
 *
 * <p>对应《潜渊症》的 {@code Gap.UpdateRoomToOut}：水量是渐进累加的，不是瞬间灌满。</p>
 */
class ShipFloodingTest {

    /** 5×5×5 空心壳，但 (2,0,0) 那一格破掉，变成一个连海空腔。 */
    private static Set<BlockPos> breachedCube() {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(y) == 2 || Math.abs(z) == 2;
                    if (shell && !(x == 2 && y == 0 && z == 0)) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return solid;
    }

    private static ShipGeometry.Result scan(Set<BlockPos> solid) {
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
        return ShipGeometry.scanShip(new BlockPos(0, 0, 0), 12, isShipBlock, probe);
    }

    @Test
    @DisplayName("破口空腔的水量随时间增加，并被容积封顶")
    void breachFloodsGradually() {
        ShipGeometry.Result geometry = scan(breachedCube());
        assertFalse(geometry.openPockets().isEmpty(), "破了一格应该出现连海空腔");

        ShipSimulation sim = new ShipSimulation(geometry, MachineCount.EMPTY);
        assertEquals(0.0D, sim.pocketWater(0), 1.0E-9D, "刚破口时不该有水");

        // 用 0.1 秒的小步长：按当前进水速率（20 格³/秒 × 深度系数），
        // 27 格空腔约 1 秒灌满；步长太大会一步到顶，"继续增加"就测不出来了。
        sim.tickSeaFlood(0.1D, 50.0D);
        double first = sim.pocketWater(0);
        assertTrue(first > 0.0D, "0.1 秒后应该有水进来");

        sim.tickSeaFlood(0.1D, 50.0D);
        assertTrue(sim.pocketWater(0) > first, "水量应该继续增加");

        double capacity = geometry.openPockets().get(0).volume() * ShipSimulation.UNITS_PER_BLOCK;
        sim.tickSeaFlood(10000.0D, 50.0D);
        assertEquals(capacity, sim.pocketWater(0), 1.0E-6D, "最终不能超过空腔容积");
    }

    @Test
    @DisplayName("连海空腔的水也要算进进水比例，否则破船不会下沉")
    void floodRatioIncludesOpenPockets() {
        ShipGeometry.Result geometry = scan(breachedCube());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        assertEquals(0.0D, state.floodRatio(), 1.0E-9D, "刚破口时是干的");
        state.simulation().tickSeaFlood(0.1D, 50.0D);
        assertTrue(state.floodRatio() > 0.0D, "连海空腔进水后进水比例应该大于 0");
    }

    /**
     * 5×5×5 壳体，顶面正中一格是舱口（门格）。
     *
     * @param open 舱口是否打开
     */
    private static ShipGeometry.Result scanTopHatch(boolean open) {
        BlockPos hatch = new BlockPos(0, 2, 0);
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(y) == 2 || Math.abs(z) == 2;
                    if (shell && !new BlockPos(x, y, z).equals(hatch)) {
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

            @Override
            public boolean isDoorway(BlockPos pos) {
                return pos.equals(hatch);
            }

            @Override
            public boolean isOpenDoorway(BlockPos pos) {
                return open && pos.equals(hatch);
            }
        };
        return ShipGeometry.scanShip(new BlockPos(0, 0, 0), 12,
                pos -> solid.contains(pos) || pos.equals(hatch), probe);
    }

    @Test
    @DisplayName("顶舱口开在海里：必须有通往外海的门连线，开门就往舱里灌")
    void openHatchConnectsToSea() {
        ShipGeometry.Result geometry = scanTopHatch(true);
        boolean seaLink = geometry.doorLinks().stream()
                .anyMatch(link -> link.other() == ShipGeometry.SEA_BODY && link.open());
        assertTrue(seaLink, "开着的顶舱口应该连到外海，否则开门不进水");

        ShipSimulation sim = new ShipSimulation(geometry, MachineCount.EMPTY);
        BlockPos hatch = new BlockPos(0, 2, 0);
        int body = 0;
        while (body < geometry.compartments().size()
                && !geometry.compartments().get(body).cells().contains(hatch)) {
            body++;
        }
        assertTrue(body < geometry.compartments().size(), "舱口格应该属于某个密闭舱室");
        assertEquals(0.0D, sim.bodyWater(body), 1.0E-9D);
        sim.tickDoorFlow(0.1D);
        assertTrue(sim.bodyWater(body) > 0.0D, "开门 0.1 秒后舱里应该开始进水");

        for (int i = 0; i < 200; i++) {
            sim.tickDoorFlow(0.1D);
        }
        double capacity = geometry.compartments().get(body).volume() * ShipSimulation.UNITS_PER_BLOCK;
        assertEquals(capacity, sim.bodyWater(body), 1.0E-6D, "水最多灌满这间舱");
    }

    @Test
    @DisplayName("关着的顶舱口不能进水（门连线存在但 open=false）")
    void closedHatchStaysDry() {
        ShipGeometry.Result geometry = scanTopHatch(false);
        ShipSimulation sim = new ShipSimulation(geometry, MachineCount.EMPTY);
        for (int i = 0; i < 50; i++) {
            sim.tickDoorFlow(0.1D);
        }
        assertEquals(0.0D, sim.waterUnits(), 1.0E-9D, "关着的舱口不能进水");
    }

    /**
     * 圆角船体：包围盒四角是空气（直接连着外海），船壳只包住中间 3×3。
     * 对应儒艮号 17×9×5 的包围盒里、船壳外的那些格子。
     */
    private static Set<BlockPos> roundedHull() {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean wall = (Math.abs(x) == 2 || Math.abs(y) == 2) && Math.abs(z) <= 1;
                    boolean cap = Math.abs(z) == 2 && Math.abs(x) <= 1 && Math.abs(y) <= 1;
                    if (wall || cap) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return solid;
    }

    private static ShipGeometry.Result scanRounded(Set<BlockPos> solid, Set<BlockPos> shell) {
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
        return ShipGeometry.scanShip(new BlockPos(0, 0, 0), 12, solid::contains, probe, 0, shell::contains);
    }

    @Test
    @DisplayName("包围盒四角的外海海水不算船内进水")
    void exteriorSeaInsideBoundsIsNotFlooding() {
        Set<BlockPos> hull = roundedHull();
        ShipGeometry.Result geometry = scanRounded(hull, hull);
        assertFalse(geometry.openPockets().isEmpty(), "四角应该有连海空腔");
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            assertEquals(0, pocket.interiorVolume(), "四角空腔不在船壳内");
        }
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().tickSeaFlood(10000.0D, 50.0D);
        assertEquals(0.0D, state.floodRatio(), 1.0E-9D,
                "舱内是干的，四角海水不能把船算成进水");
    }

    @Test
    @DisplayName("船壳破口后的舱内进水仍然算进水（破口格按船壳处理）")
    void breachedInteriorStillCountsAsFlooding() {
        Set<BlockPos> hull = roundedHull();
        Set<BlockPos> brokenHull = new HashSet<>(hull);
        BlockPos hole = new BlockPos(2, 0, 0);
        brokenHull.remove(hole);
        // 破口方块还在（breached 状态）：对"船壳包住哪些空气"来说它仍是墙。
        ShipGeometry.Result geometry = scanRounded(brokenHull, hull);
        assertFalse(geometry.openPockets().isEmpty(), "破口应该出现连海空腔");
        boolean interiorPocket = geometry.openPockets().stream()
                .anyMatch(pocket -> pocket.interiorVolume() > 0);
        assertTrue(interiorPocket, "舱内这一片空腔应保留在船壳内");
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().tickSeaFlood(10000.0D, 50.0D);
        assertTrue(state.floodRatio() > 0.0D, "舱内进水后进水比例应该大于 0");
    }
}
