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
 * 破口水面回归：连海空腔按列分水位后，破口行必须指向破口自己那一列的水柱，
 * 不能用空腔第一个有水块（可能只是低处的一段，水面比洞口低一整格）。
 *
 * <p>几何复刻实测船：7×3×13 的舱，东墙 x=4 上 y=-71/-70、z=24..26 被打穿；
 * 另外把 TreeMap 第一列 (-3,17) 做成只剩最底一格，让"第一个有水块"的水面
 * 明显低于洞口，从而复现"破口只画半截"。</p>
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
        // 东墙上的破口格。
        for (int y = -71; y <= -70; y++) {
            for (int z = 24; z <= 26; z++) {
                cells.add(new BlockPos(4, y, z));
            }
        }
        // 第一列只留最底一格：第一个有水块 = 这段 y=-71 的水柱，水面 -70。
        cells.remove(new BlockPos(-3, -70, 17));
        cells.remove(new BlockPos(-3, -69, 17));
        return cells;
    }

    private static ShipState state(Set<BlockPos> cells, List<BlockPos> breaches) {
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(cells, 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(), List.of(pocket), 0, cells.size(),
                new ShipGeometry.Bounds(-3, -71, 17, 4, -69, 29),
                Set.of(), List.of());
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
    @DisplayName("破口行指向破口自己的水柱：水位高于洞口时补的面是满格，不是低处那一段")
    void breachRowUsesOwnColumnNotFirstWetBox() {
        List<BlockPos> breaches = holes();
        ShipState state = state(pocketCells(), breaches);
        fill(state, 1.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] boxes = packet.boxes();
        float[] rows = packet.breaches();
        assertTrue(rows.length >= 8, "应该有破口行");

        int checked = 0;
        for (int i = 0; i + 7 < rows.length; i += 8) {
            int bx = (int) Math.floor(rows[i]);
            int by = (int) Math.floor(rows[i + 1]);
            int bz = (int) Math.floor(rows[i + 2]);
            int offset = Math.round(rows[i + 3]);
            assertTrue(offset >= 0 && offset + 6 < boxes.length, "偏移必须在 boxes 内");
            assertTrue(bx >= boxes[offset] && bx <= boxes[offset + 3]
                            && by >= boxes[offset + 1] && by <= boxes[offset + 4]
                            && bz >= boxes[offset + 2] && bz <= boxes[offset + 5],
                    "破口行的偏移必须覆盖破口格自己，不能指向空腔第一个有水块");

            float surface = boxes[offset + 6];
            float cellMaxY = rows[i + 1] + 0.5F;
            assertEquals(cellMaxY, Math.min(surface, cellMaxY), 1.0E-3F,
                    "整舱满水时，洞口这一格的水面应被压到格顶（补的面是满格）");
            assertTrue(surface > boxes[offset + 4] + 1.0F + 0.01F,
                    "满水时洞口柱整段在水下；渲染端据此跳过洞口那层假顶面");
            checked++;
        }
        assertEquals(6, checked, "6 个破口格都要检查到");

        // 分布本身不是根因：破口列和舱内列的水位一致（满水都在空腔顶 +1）。
        ShipPocketWater.Shape shape = ShipPocketWater.compute(
                state.geometry().openPockets().get(0).cells(), breaches,
                state.simulation().pocketWater(0));
        assertEquals(shape.levelAt(0, 23), shape.levelAt(4, 24), 0.05F,
                "满水时破口列不应比舱内低；低的是渲染取错了盒子，不是水位分配");
    }

    @Test
    @DisplayName("东墙连续两格破口仍按舱壁发轴对齐法线：不能被水柱端点判成舱顶+舱底")
    void verticalWallHolesKeepWallNormals() {
        List<BlockPos> breaches = holes();
        ShipState state = state(pocketCells(), breaches);
        fill(state, 1.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] rows = packet.breaches();
        int checked = 0;
        for (int i = 0; i + 7 < rows.length; i += 8) {
            // 东墙 x=4，舱内在 x<=3：朝内法线必须是 -X，且不能带 Z 分量。
            assertEquals(-1.0F, rows[i + 4], 1.0E-3F,
                    "墙洞（哪怕竖排只有两格高）必须发朝舱内的轴对齐法线");
            assertEquals(0.0F, rows[i + 5], 1.0E-3F,
                    "墙洞的朝向不能是斜的质心方向");
            checked++;
        }
        assertEquals(6, checked, "6 个墙洞都要检查到");
    }

    @Test
    @DisplayName("舱顶/舱底破口发 (0,0)，客户端按洞口 y 与空腔上下界区分水平补面")
    void verticalHolesSendZeroDirection() {
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
        assertEquals(16, rows.length, "两个竖直破口各一行");

        for (int i = 0; i + 7 < rows.length; i += 8) {
            assertEquals(0.0F, rows[i + 4], 1.0E-3F, "舱顶/舱底破口不能发水平法线");
            assertEquals(0.0F, rows[i + 5], 1.0E-3F);
            float cellBottom = rows[i + 1] - 0.5F;
            boolean ceiling = cellBottom >= rows[i + 7] - 0.01F;
            if (ceilingHole.getY() == (int) Math.floor(rows[i + 1])) {
                assertTrue(ceiling, "舱顶洞必须落在空腔顶格，客户端才会画朝下的水面");
            } else {
                assertTrue(!ceiling, "舱底洞不能落在顶格，客户端才会画朝上的水面");
            }
        }
    }

    @Test
    @DisplayName("干燥密闭舱的占位 bbox 盖住破口格时，破口仍取自己那一列的水面")
    void dryCompartmentBoundingBoxDoesNotStealBreachColumn() {
        BlockPos hole = new BlockPos(4, -71, 24);
        Set<BlockPos> pocketCells = pocketCells();
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(pocketCells, 0);
        // 干燥密闭舱的整块 bbox 正好盖住破口格，而且它排在空腔前面。
        ShipGeometry.Compartment dryRoom = new ShipGeometry.Compartment(Set.of(hole), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(dryRoom), List.of(pocket), 0, pocketCells.size(),
                new ShipGeometry.Bounds(-3, -71, 17, 4, -69, 29),
                Set.of(), List.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.addBreaches(List.of(hole));
        fill(state, 1.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0, 0, 0);
        float[] boxes = packet.boxes();
        float[] rows = packet.breaches();
        assertEquals(8, rows.length);
        int offset = Math.round(rows[3]);
        assertTrue(offset >= 7, "偏移不能指向排在前面的干燥密闭舱占位 bbox");
        float surface = boxes[offset + 6];
        assertTrue(surface > boxes[offset + 4] + 1.0F + 0.01F,
                "满水时破口列整段在水下，不能被干燥 bbox 的低水位顶替");
    }

    @Test
    @DisplayName("近满时破口列和舱内列水位一致：分布不是根因")
    void nearFull_holeColumnMatchesInterior() {
        List<BlockPos> breaches = holes();
        ShipState state = state(pocketCells(), breaches);
        fill(state, 0.9D);

        ShipPocketWater.Shape shape = ShipPocketWater.compute(
                state.geometry().openPockets().get(0).cells(), breaches,
                state.simulation().pocketWater(0));
        assertEquals(shape.levelAt(0, 23), shape.levelAt(4, 24), 0.05F,
                "破口列不该因为洞口高度比整舱低；近满时两列水位应在 5cm 内一致");
    }
}
