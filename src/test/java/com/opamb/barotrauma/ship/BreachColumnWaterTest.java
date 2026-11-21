package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 破口水面回归（逐格水场版）：
 * <ul>
 *   <li>破口行必须指向破口旁边的舱内格水盒；</li>
 *   <li>墙洞发轴对齐法线，舱顶/舱底发 kind=1/2；</li>
 *   <li>破口列不会因为同 (x,z) 有更高的水盒而取错水面。</li>
 * </ul>
 */
class BreachColumnWaterTest {

    private static Set<BlockPos> pocketCells() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -71; y <= -69; y++) {
                for (int z = 17; z <= 29; z++) {
                    cells.add(new BlockPos(x, y, z));
                }
            }
        }
        for (int y = -71; y <= -70; y++) {
            for (int z = 24; z <= 26; z++) {
                cells.add(new BlockPos(4, y, z));
            }
        }
        // 第一列只留最底一格，制造"同列上下断档"。
        cells.remove(new BlockPos(-3, -70, 17));
        cells.remove(new BlockPos(-3, -69, 17));
        return cells;
    }

    private static ShipState state(Set<BlockPos> cells, List<BlockPos> breaches) {
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(cells, 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(), List.of(pocket), 0, cells.size(),
                new ShipGeometry.Bounds(-3, -71, 17, 4, -69, 29),
                Set.of(), List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.addBreaches(breaches);
        return state;
    }

    private static void fill(ShipState state, double fraction) {
        double capacity = state.geometry().openPockets().get(0).volume()
                * ShipSimulation.UNITS_PER_BLOCK;
        double target = capacity * fraction;
        for (int i = 0; i < 200 && state.simulation().pocketWaterTotal() < target; i++) {
            state.simulation().tickSeaFlood(1.0D, 0.0D);
        }
        // 让格间压力把水位拉平（旧版是 ShipPocketWater 一次算好；现在是场推进）。
        for (int i = 0; i < 60; i++) {
            state.simulation().tickWaterFlow(1.0D);
        }
    }

    private static List<BlockPos> holes() {
        List<BlockPos> breaches = new ArrayList<>();
        for (int y = -71; y <= -70; y++) {
            for (int z = 24; z <= 26; z++) {
                breaches.add(new BlockPos(4, y, z));
            }
        }
        return breaches;
    }

    @Test
    @DisplayName("破口行指向破口旁边的舱内格水柱，满水时该格是满的")
    void breachRowPointsAtAdjacentInteriorCell() {
        List<BlockPos> breaches = holes();
        ShipState state = state(pocketCells(), breaches);
        fill(state, 1.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] boxes = packet.boxes();
        float[] rows = packet.breaches();
        assertTrue(rows.length >= 9, "应该有破口行");

        int checked = 0;
        for (int i = 0; i + 8 < rows.length; i += 9) {
            int bx = (int) Math.floor(rows[i]);
            int by = (int) Math.floor(rows[i + 1]);
            int bz = (int) Math.floor(rows[i + 2]);
            int offset = Math.round(rows[i + 3]);
            int kind = Math.round(rows[i + 8]);
            assertEquals(0, kind, "墙洞的 kind 必须是 0");
            assertTrue(offset >= 0 && offset + 6 < boxes.length, "偏移必须在 boxes 内");

            int nx = bx + Math.round(rows[i + 4]);
            int nz = bz + Math.round(rows[i + 5]);
            assertTrue(nx >= boxes[offset] && nx <= boxes[offset + 3]
                            && by >= boxes[offset + 1] && by <= boxes[offset + 4]
                            && nz >= boxes[offset + 2] && nz <= boxes[offset + 5],
                    "破口行必须指向破口旁边的舱内格");

            float surface = boxes[offset + 6];
            assertEquals(boxes[offset + 4] + 1.0F, surface, 1.0E-3F,
                    "满水时洞口旁的格应该满到格顶");
            checked++;
        }
        assertEquals(6, checked, "6 个破口格都要检查到");
    }

    @Test
    @DisplayName("东墙连续两格破口仍按舱壁发轴对齐法线")
    void verticalWallHolesKeepWallNormals() {
        List<BlockPos> breaches = holes();
        ShipState state = state(pocketCells(), breaches);
        fill(state, 1.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] rows = packet.breaches();
        int checked = 0;
        for (int i = 0; i + 8 < rows.length; i += 9) {
            assertEquals(-1.0F, rows[i + 4], 1.0E-3F,
                    "墙洞必须发朝舱内的轴对齐法线");
            assertEquals(0.0F, rows[i + 5], 1.0E-3F,
                    "墙洞的朝向不能是斜的质心方向");
            checked++;
        }
        assertEquals(6, checked, "6 个墙洞都要检查到");
    }

    @Test
    @DisplayName("舱顶/舱底破口发 (0,0) + kind 1/2，客户端不再猜")
    void verticalHolesSendExplicitKind() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x <= 2; x++) {
            for (int y = -71; y <= -69; y++) {
                for (int z = 0; z <= 2; z++) {
                    cells.add(new BlockPos(x, y, z));
                }
            }
        }
        BlockPos ceilingHole = new BlockPos(1, -68, 1);
        BlockPos floorHole = new BlockPos(1, -72, 1);
        cells.add(ceilingHole);
        cells.add(floorHole);

        ShipState state = state(cells, List.of(ceilingHole, floorHole));
        fill(state, 1.0D);
        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] rows = packet.breaches();
        assertEquals(18, rows.length, "两个竖直破口各一行，每行 9 float");

        int ceiling = 0;
        int floor = 0;
        for (int i = 0; i + 8 < rows.length; i += 9) {
            assertEquals(0.0F, rows[i + 4], 1.0E-3F, "舱顶/舱底破口不能发水平法线");
            assertEquals(0.0F, rows[i + 5], 1.0E-3F);
            int kind = Math.round(rows[i + 8]);
            int y = (int) Math.floor(rows[i + 1]);
            if (y == ceilingHole.getY()) {
                assertEquals(1, kind, "舱顶洞 kind=1");
                ceiling++;
            } else if (y == floorHole.getY()) {
                assertEquals(2, kind, "舱底洞 kind=2");
                floor++;
            }
        }
        assertEquals(1, ceiling);
        assertEquals(1, floor);
    }

    @Test
    @DisplayName("干燥密闭舱的盒子不会抢走破口旁边的水柱")
    void dryCompartmentBoxDoesNotStealBreachColumn() {
        BlockPos hole = new BlockPos(4, -71, 24);
        Set<BlockPos> pocketCells = pocketCells();
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(pocketCells, 0);
        // 干燥密闭舱排在空腔前面，且它的格子正好在破口附近。
        ShipGeometry.Compartment dryRoom = new ShipGeometry.Compartment(Set.of(hole), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(dryRoom), List.of(pocket), 0, pocketCells.size(),
                new ShipGeometry.Bounds(-3, -71, 17, 4, -69, 29),
                Set.of(), List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.addBreaches(List.of(hole));
        fill(state, 1.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] boxes = packet.boxes();
        float[] rows = packet.breaches();
        assertEquals(9, rows.length);
        int offset = Math.round(rows[3]);
        assertTrue(offset >= 7, "偏移不能指向排在前面的干燥密闭舱盒子");
        float surface = boxes[offset + 6];
        assertEquals(boxes[offset + 4] + 1.0F, surface, 1.0E-3F,
                "满水时破口旁边那格应该满到格顶");
    }

    @Test
    @DisplayName("连通舱的水位经过逐格流动后趋于一致")
    void connectedColumnsEqualize() {
        Set<BlockPos> cells = new HashSet<>();
            for (int x = 0; x <= 4; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        cells.add(new BlockPos(x, y, z));
                    }
                }
            }
            ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(cells, 0);
            ShipGeometry.Result geometry = new ShipGeometry.Result(
                    List.of(), List.of(pocket), 0, cells.size(),
                    new ShipGeometry.Bounds(0, 0, 0, 4, 1, 1),
                    Set.of(), List.of(), Set.of());
            ShipState state = new ShipState(geometry, MachineCount.EMPTY);
            state.simulation().flood(0, 5.0D * ShipSimulation.UNITS_PER_BLOCK);
            for (int i = 0; i < 80; i++) {
                state.simulation().tickWaterFlow(0.1D);
            }
        double left = state.simulation().waterLevelAt(new BlockPos(0, 0, 0), List.of());
        double right = state.simulation().waterLevelAt(new BlockPos(4, 0, 0), List.of());
        assertEquals(left, right, 0.2D, "连通舱两侧水位应拉平");
    }
}
