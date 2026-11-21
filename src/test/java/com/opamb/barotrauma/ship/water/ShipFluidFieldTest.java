package com.opamb.barotrauma.ship.water;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 逐格水场的纯数学测试：重力下落、压力平衡、守恒、门洞开关。
 */
class ShipFluidFieldTest {

    private static ShipFluidField.Cell cell(int x, int y, int z, int body) {
        return new ShipFluidField.Cell(new BlockPos(x, y, z), body);
    }

    @Test
    @DisplayName("同列注水先落到底格")
    void settleFillsBottomFirst() {
        ShipFluidField field = ShipFluidField.of(List.of(
                cell(0, 0, 0, 0), cell(0, 1, 0, 0), cell(0, 2, 0, 0)),
                List.of(), 1);
        field.inject(new BlockPos(0, 2, 0), 150.0D);
        field.settle();

        assertEquals(100.0D, field.waterAt(new BlockPos(0, 0, 0)), 1.0E-6D);
        assertEquals(50.0D, field.waterAt(new BlockPos(0, 1, 0)), 1.0E-6D);
        assertEquals(0.0D, field.waterAt(new BlockPos(0, 2, 0)), 1.0E-6D);
    }

    @Test
    @DisplayName("连通格经过压力流后水位一致，且总水量守恒")
    void connectedCellsEqualizeAndConserve() {
        ShipFluidField field = ShipFluidField.of(List.of(
                cell(0, 0, 0, 0), cell(1, 0, 0, 0), cell(2, 0, 0, 0)),
                List.of(), 1);
        field.inject(new BlockPos(0, 0, 0), 90.0D);
        for (int i = 0; i < 80; i++) {
            field.settle();
            field.flow(0.1D);
        }

        assertEquals(90.0D, field.bodyWater(0), 1.0E-6D);
        for (int x = 0; x <= 2; x++) {
            assertEquals(30.0D, field.waterAt(new BlockPos(x, 0, 0)), 2.0D,
                    "连通格水位应该拉平（死区 0.02 格 = 2 units）");
        }
    }

    @Test
    @DisplayName("关着的门阻断水流，开着的门允许通过")
    void closedGateBlocksAndOpenGatePasses() {
        BlockPos left = new BlockPos(0, 0, 0);
        BlockPos right = new BlockPos(1, 0, 0);
        ShipFluidField.Cell a = cell(0, 0, 0, 0);
        ShipFluidField.Cell b = cell(1, 0, 0, 1);

        ShipFluidField closed = ShipFluidField.of(List.of(a, b),
                List.of(new ShipFluidField.Gate(left, right, false)), 2);
        closed.inject(left, 100.0D);
        for (int i = 0; i < 20; i++) {
            closed.flow(0.1D);
        }
        assertEquals(100.0D, closed.waterAt(left), 1.0E-6D);
        assertEquals(0.0D, closed.waterAt(right), 1.0E-6D);

        ShipFluidField open = ShipFluidField.of(List.of(a, b),
                List.of(new ShipFluidField.Gate(left, right, true)), 2);
        open.inject(left, 100.0D);
        for (int i = 0; i < 40; i++) {
            open.flow(0.1D);
        }
        assertTrue(open.waterAt(right) > 40.0D, "开着的门应该让水过去");
        assertEquals(100.0D, open.bodyWater(0) + open.bodyWater(1), 1.0E-6D);
    }

    @Test
    @DisplayName("两侧同时向中间灌水不会把中间格灌到 100 以上")
    void multipleInflowsRespectCellCapacity() {
        ShipFluidField field = ShipFluidField.of(List.of(
                cell(0, 0, 0, 0), cell(1, 0, 0, 0), cell(2, 0, 0, 0)),
                List.of(), 1);
        field.inject(new BlockPos(0, 0, 0), 100.0D);
        field.inject(new BlockPos(2, 0, 0), 100.0D);
        for (int i = 0; i < 40; i++) {
            field.flow(0.1D);
        }
        for (int x = 0; x <= 2; x++) {
            assertTrue(field.waterAt(new BlockPos(x, 0, 0)) <= 100.0D + 1.0E-6D,
                    "单格水量不能超过一格容量");
        }
        assertEquals(200.0D, field.bodyWater(0), 1.0E-6D);
    }

    @Test
    @DisplayName("整舱归平：一小股水均摊到整个舱底，不堆在注水那一格")
    void levelSpreadsWaterAcrossWholeBody() {
        List<ShipFluidField.Cell> cells = new java.util.ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                cells.add(cell(x, 0, z, 0));
            }
        }
        ShipFluidField field = ShipFluidField.of(cells, List.of(), 1);
        field.inject(new BlockPos(0, 0, 0), 500.0D);
        field.level();

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                assertEquals(20.0D, field.waterAt(new BlockPos(x, 0, z)), 1.0E-6D,
                        "25 格均摊 500 units，每格 20");
            }
        }
    }

    @Test
    @DisplayName("归平先灌满最低层，多出来的才上第二层")
    void levelFillsLowestLayerFirst() {
        ShipFluidField field = ShipFluidField.of(List.of(
                cell(0, 0, 0, 0), cell(1, 0, 0, 0),
                cell(0, 1, 0, 0), cell(1, 1, 0, 0),
                cell(0, 2, 0, 0), cell(1, 2, 0, 0)), List.of(), 1);
        field.inject(new BlockPos(0, 2, 0), 300.0D);
        field.level();

        assertEquals(100.0D, field.waterAt(new BlockPos(0, 0, 0)), 1.0E-6D);
        assertEquals(100.0D, field.waterAt(new BlockPos(1, 0, 0)), 1.0E-6D);
        assertEquals(50.0D, field.waterAt(new BlockPos(0, 1, 0)), 1.0E-6D);
        assertEquals(50.0D, field.waterAt(new BlockPos(1, 1, 0)), 1.0E-6D);
        assertEquals(0.0D, field.waterAt(new BlockPos(0, 2, 0)), 1.0E-6D);
        assertEquals(300.0D, field.bodyWater(0), 1.0E-6D);
    }

    @Test
    @DisplayName("归平不跨门：关着的门两侧各自水平")
    void levelDoesNotCrossClosedDoor() {
        BlockPos left = new BlockPos(0, 0, 0);
        BlockPos right = new BlockPos(1, 0, 0);
        ShipFluidField field = ShipFluidField.of(
                List.of(cell(0, 0, 0, 0), cell(1, 0, 0, 1)),
                List.of(new ShipFluidField.Gate(left, right, false)), 2);
        field.inject(left, 100.0D);
        field.level();
        assertEquals(100.0D, field.waterAt(left), 1.0E-6D);
        assertEquals(0.0D, field.waterAt(right), 1.0E-6D);
    }

    @Test
    @DisplayName("舱灌满后，多出来的水沿开着的舱口往上一层溢")
    void overflowSpillsThroughOpenHatch() {
        BlockPos lower = new BlockPos(0, 0, 0);
        BlockPos upper = new BlockPos(0, 1, 0);
        ShipFluidField field = ShipFluidField.of(
                List.of(cell(0, 0, 0, 0), cell(0, 1, 0, 1)),
                List.of(new ShipFluidField.Gate(lower, upper, true)), 2);
        field.addBody(0, ShipFluidField.MAX_CELL_WATER, Double.POSITIVE_INFINITY);
        assertEquals(100.0D, field.bodyWater(0), 1.0E-6D);

        double added = field.injectFlood(0, 60.0D);

        assertEquals(60.0D, added, 1.0E-6D, "满舱装不下的水不能凭空丢掉");
        assertEquals(100.0D, field.bodyWater(0), 1.0E-6D);
        assertEquals(60.0D, field.bodyWater(1), 1.0E-6D, "水应该从开着的舱口涌到上一层");
    }

    @Test
    @DisplayName("舱口关着时，满舱装不下的水不会穿过舱口")
    void overflowBlockedByClosedHatch() {
        BlockPos lower = new BlockPos(0, 0, 0);
        BlockPos upper = new BlockPos(0, 1, 0);
        ShipFluidField field = ShipFluidField.of(
                List.of(cell(0, 0, 0, 0), cell(0, 1, 0, 1)),
                List.of(new ShipFluidField.Gate(lower, upper, false)), 2);
        field.addBody(0, ShipFluidField.MAX_CELL_WATER, Double.POSITIVE_INFINITY);

        field.injectFlood(0, 60.0D);

        assertEquals(0.0D, field.bodyWater(1), 1.0E-6D, "关着的舱口不能过水");
    }

    @Test
    @DisplayName("泵抽水从最高水面开始扣")
    void removeBodyTakesFromTop() {
        ShipFluidField field = ShipFluidField.of(List.of(
                cell(0, 0, 0, 0), cell(0, 1, 0, 0)), List.of(), 1);
        field.addBody(0, 200.0D, Double.POSITIVE_INFINITY);

        field.removeBody(0, 100.0D);

        assertEquals(100.0D, field.waterAt(new BlockPos(0, 0, 0)), 1.0E-6D);
        assertEquals(0.0D, field.waterAt(new BlockPos(0, 1, 0)), 1.0E-6D);
    }

    @Test
    @DisplayName("门限注水只算门下方的格容量")
    void capacityBelowCountsOnlyCellsUnderLimit() {
        ShipFluidField field = ShipFluidField.of(List.of(
                cell(0, 0, 0, 0), cell(0, 1, 0, 0), cell(0, 2, 0, 0)),
                List.of(), 1);
        assertEquals(200.0D, field.capacityBelow(0, 2.0D), 1.0E-6D);
        field.addBody(0, 500.0D, 2.0D);
        assertEquals(200.0D, field.bodyWater(0), 1.0E-6D);
        assertEquals(0.0D, field.waterAt(new BlockPos(0, 2, 0)), 1.0E-6D);
    }
}
