package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShipWaterStateBuilder} 的 L1 单测：水面高度必须按"逐层实际格数"算。
 *
 * <p>这是 2026-11-02 的独立修复补丁：旧实现用包围盒底面积
 * {@code (maxX-minX+1)*(maxZ-minZ+1)}，非矩形舱室会算错水位。下面的 L 形舱
 * 底面积 4 格、实际每层只有 3/1 格，两种算法结果明显不同。</p>
 */
class ShipWaterStateBuilderTest {

    /** L 形：y=0 有 3 格，y=1 有 1 格；包围盒是 2×2×2。 */
    private static ShipGeometry.Compartment lShape() {
        Set<BlockPos> cells = Set.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(0, 1, 0)
        );
        return new ShipGeometry.Compartment(cells, 0);
    }

    /** 4 格全在同一层，矩形。 */
    private static ShipGeometry.Compartment flatRectangle() {
        Set<BlockPos> cells = Set.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(1, 0, 1)
        );
        return new ShipGeometry.Compartment(cells, 0);
    }

    private static ShipGeometry.Result result(ShipGeometry.Compartment compartment, boolean pocket) {
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 1, 1, 1);
        return new ShipGeometry.Result(
                pocket ? List.of() : List.of(compartment),
                pocket ? List.of(compartment) : List.of(),
                0, compartment.volume(), bounds, Set.of(), List.of(), Set.of());
    }

    /** 逐列水盒的水面不一定相同；测试取最高的一列作为"整体水位"读数。 */
    private static float maxSurface(ShipState state) {
        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0.0F, 0.0F, 0.0F);
        float[] boxes = packet.boxes();
        assertTrue(boxes.length >= 7 && boxes.length % 7 == 0,
                "boxes 必须是若干个 7 float 的水体块");
        float surface = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            surface = Math.max(surface, boxes[i + 6]);
        }
        return surface;
    }

    @Test
    @DisplayName("0 水量 -> 水面在舱底 minY")
    void zeroWater_surfaceAtBottom() {
        ShipState state = new ShipState(result(lShape(), false), MachineCount.EMPTY);
        assertEquals(0.0F, maxSurface(state), 1.0E-4F);
    }

    @Test
    @DisplayName("部分水（1.5 格）按底层 3 格算 -> 0.5，而不是 bbox 面积的 0.375")
    void partialWater_usesPerLayerCellCount() {
        ShipState state = new ShipState(result(lShape(), false), MachineCount.EMPTY);
        state.simulation().flood(0, 1.5D * ShipSimulation.UNITS_PER_BLOCK);

        float surface = maxSurface(state);

        assertEquals(0.5F, surface, 1.0E-4F,
                "底层 3 格铺 1.5 格水 = 半层；用 bbox 底面积 4 会错误地得到 0.375");
    }

    @Test
    @DisplayName("部分水（3.5 格）铺满底层 + 顶层一半 -> 1.5")
    void partialWater_crossesLayerBoundary() {
        ShipState state = new ShipState(result(lShape(), false), MachineCount.EMPTY);
        state.simulation().flood(0, 3.5D * ShipSimulation.UNITS_PER_BLOCK);

        assertEquals(1.5F, maxSurface(state), 1.0E-4F,
                "3 格底层 + 顶层 1 格的一半；bbox 算法会得到 0.875");
    }

    @Test
    @DisplayName("满水 -> 水面在 maxY + 1")
    void fullWater_surfaceAtTop() {
        ShipState state = new ShipState(result(lShape(), false), MachineCount.EMPTY);
        state.simulation().flood(0, 4.0D * ShipSimulation.UNITS_PER_BLOCK);

        assertEquals(2.0F, maxSurface(state), 1.0E-4F);
    }

    @Test
    @DisplayName("超量水被 clamp 到 maxY + 1")
    void overfilledWater_clamped() {
        ShipState state = new ShipState(result(lShape(), false), MachineCount.EMPTY);
        state.simulation().flood(0, 99.0D * ShipSimulation.UNITS_PER_BLOCK);

        assertEquals(2.0F, maxSurface(state), 1.0E-4F);
    }

    @Test
    @DisplayName("薄层水也有最小可见厚度，整舱薄层不会因为量化变成干燥")
    void thinWaterLayerStaysVisible() {
        ShipState state = new ShipState(result(flatRectangle(), false), MachineCount.EMPTY);
        // 0.3 单位摊到 4 列 ≈ 0.075 单位/列 = 0.00075 格，低于客户端的 0.01 干燥阈值。
        state.simulation().flood(0, 0.3D);

        assertEquals(ShipWaterStateBuilder.MIN_VISIBLE_SURFACE, maxSurface(state), 1.0E-4F,
                "有水的列至少画 1/32 格厚");
    }

    @Test
    @DisplayName("矩形舱室结果和旧 bbox 公式一致（回归）")
    void rectangularCompartment_matchesAreaFormula() {
        ShipState state = new ShipState(result(flatRectangle(), false), MachineCount.EMPTY);
        state.simulation().flood(0, 2.0D * ShipSimulation.UNITS_PER_BLOCK);

        assertEquals(0.5F, maxSurface(state), 1.0E-4F);
    }

    @Test
    @DisplayName("连海空腔走 pocketWater 分支，同样按逐层格数算")
    void openPocket_usesPocketWaterAndPerLayerFill() {
        ShipState state = new ShipState(result(lShape(), true), MachineCount.EMPTY);
        // 0.1 秒 × 6 格³/秒 × 深度系数 1.0 = 0.6 格 = 60 单位。
        state.simulation().tickSeaFlood(0.1D, 0.0D);

        float surface = maxSurface(state);
        assertTrue(surface > 0.0F && surface < 2.0F, "部分进水应该产生中间水面");
        // 水盒水面量化到 1/64；0.6 格分摊到 3 列后接近 0.2。
        assertEquals(0.2F, surface, 0.02F);
    }

    @Test
    @DisplayName("开着的门：门格水柱抬到较高一侧，不再额外发 1×1 补面")
    void openDoorFillsDoorwayWithoutExtraSheet() {
        // 门格 (0,0,0) 属于干舱 body 0；(0,0,1) 是进水舱 body 1。
        ShipGeometry.Compartment dry = new ShipGeometry.Compartment(Set.of(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), 0);
        ShipGeometry.Compartment wet = new ShipGeometry.Compartment(Set.of(
                new BlockPos(0, 0, 1)), 0);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 1, 0, 1);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(dry, wet), List.of(), 0, 3, bounds, Set.of(),
                List.of(new ShipGeometry.DoorLink(new BlockPos(0, 0, 0), 0, 1, true)),
                Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().flood(1, 100.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(
                state, 1L, false, 0.0F, 0.0F, 0.0F);

        assertEquals(0, packet.breaches().length,
                "门格不再发 1×1 补面（用户看到的“格子”）");
        float[] boxes = packet.boxes();
        boolean doorBoxFound = false;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (boxes[i] == 0.0F && boxes[i + 1] == 0.0F && boxes[i + 2] == 0.0F) {
                assertEquals(1.0F, boxes[i + 6], 1.0E-4F,
                        "干舱里的门格要画到进水侧的水面，门洞里才有水");
                doorBoxFound = true;
            }
        }
        assertTrue(doorBoxFound, "门格必须仍然有水体");
    }

    @Test
    @DisplayName("门格补水只抬门格，门下的干格不能跟着变湿")
    void doorFillDoesNotRaiseDryCellsBelow() {
        // 门格 (0,2,0) 属于干舱 body 0，下面 (0,0,0)/(0,1,0) 也是干舱；
        // (0,2,1) 是进水舱 body 1，灌满后水面到 3.0。
        ShipGeometry.Compartment dry = new ShipGeometry.Compartment(Set.of(
                new BlockPos(0, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, 2, 0)), 0);
        ShipGeometry.Compartment wet = new ShipGeometry.Compartment(Set.of(
                new BlockPos(0, 2, 1)), 0);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 0, 2, 1);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(dry, wet), List.of(), 0, 4, bounds, Set.of(),
                List.of(new ShipGeometry.DoorLink(new BlockPos(0, 2, 0), 0, 1, true)),
                Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().flood(1, 100.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(
                state, 1L, false, 0.0F, 0.0F, 0.0F);
        float[] boxes = packet.boxes();
        boolean doorBoxFound = false;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (boxes[i] != 0.0F || boxes[i + 2] != 0.0F) {
                continue;
            }
            float minY = boxes[i + 1];
            float surface = boxes[i + 6];
            if (minY < 2.0F) {
                assertTrue(surface <= minY + 0.01F,
                        "门下干格 (y=" + minY + ") 不能被门格补水抬成湿的");
            } else {
                assertEquals(3.0F, surface, 1.0E-4F, "门格自己要画到进水侧水面");
                doorBoxFound = true;
            }
        }
        assertTrue(doorBoxFound, "门格必须仍然有水体");
    }

    @Test
    @DisplayName("贴着水体的实心船体格按列补水面，模型边缘不留白")
    void solidNeighbourGetsWaterHoleMergedByColumn() {
        // 舱室两列水 (0,0,0)/(0,1,0)，紧贴它的柜子格 (1,0,0)/(1,1,0) 是 solid。
        ShipGeometry.Compartment room = new ShipGeometry.Compartment(Set.of(
                new BlockPos(0, 0, 0), new BlockPos(0, 1, 0)), 0);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 1, 1, 0);
        // 补洞格的四邻必须全是本水体或实心格；这里把柜子格周围补上实心船体，
        // 模拟真实船里柜子被墙/地板包住的情况。
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(room), List.of(), 0, 2, bounds,
                Set.of(new BlockPos(1, 0, 0), new BlockPos(1, 1, 0),
                        new BlockPos(1, 0, 1), new BlockPos(1, 0, -1),
                        new BlockPos(2, 0, 0), new BlockPos(1, 1, 1),
                        new BlockPos(1, 1, -1), new BlockPos(2, 1, 0)),
                List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        // 底层 100 + 上层 50 -> 整舱水位 1.5。
        state.simulation().flood(0, 150.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(
                state, 1L, false, 0.0F, 0.0F, 0.0F);

        float[] boxes = packet.boxes();
        int holes = 0;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (boxes[i] == 1.0F && boxes[i + 2] == 0.0F) {
                holes++;
                assertEquals(0.0F, boxes[i + 1], 1.0E-4F, "补洞从最低格开始");
                assertEquals(1.0F, boxes[i + 4], 1.0E-4F, "上下两格合并成一个盒");
                assertEquals(1.5F, boxes[i + 6], 1.0E-4F, "水面跟随水体");
            }
        }
        assertEquals(1, holes, "同一列不能出两个上下重叠的补洞盒");
    }

    @Test
    @DisplayName("整格方块不补水面：船壳/地板/墙里不能出现水盒")
    void fullCubeNeighbourDoesNotGetWaterHole() {
        // 和上一个测试同样的布局，但柜子格这次是“完整方块”（例如船壁）。
        ShipGeometry.Compartment room = new ShipGeometry.Compartment(Set.of(
                new BlockPos(0, 0, 0), new BlockPos(0, 1, 0)), 0);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 1, 1, 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(room), List.of(), 0, 2, bounds,
                Set.of(new BlockPos(1, 0, 0), new BlockPos(1, 1, 0),
                        new BlockPos(1, 0, 1), new BlockPos(1, 0, -1),
                        new BlockPos(2, 0, 0), new BlockPos(1, 1, 1),
                        new BlockPos(1, 1, -1), new BlockPos(2, 1, 0)),
                List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().flood(0, 150.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(
                state, 1L, false, 0.0F, 0.0F, 0.0F, pos -> true);

        float[] boxes = packet.boxes();
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            assertTrue(!(boxes[i] == 1.0F && boxes[i + 2] == 0.0F),
                    "整格方块 (1,0,0) 不能补水盒，否则水会画进舱壁/地板");
        }
    }

    @Test
    @DisplayName("水场缺格时补干燥盒：干舱不会被当成舱外海水")
    void missingBodyCellsGetDryCoverBox() {
        BlockPos room = new BlockPos(0, 0, 0);
        // 连海空腔的 interiorCells 被清空：水场会跳过它。
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(
                Set.of(room), 0, Set.of());
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 0, 0, 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(), List.of(pocket), 0, 1, bounds, Set.of(), List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        // 先让水场按“没有历史舱内格”建出来（这一格被跳过），再补历史格，
        // 模拟新房间/破口后被清空的水体。
        state.simulation();
        state.restoreKnownInterior(Set.of(room));

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(
                state, 1L, false, 0.0F, 0.0F, 0.0F, pos -> true);

        float[] boxes = packet.boxes();
        assertEquals(7, boxes.length, "缺格舱室必须补一个干燥 bbox");
        assertEquals(0.0F, boxes[0], 1.0E-4F);
        assertEquals(0.0F, boxes[1], 1.0E-4F);
        assertEquals(0.0F, boxes[6], 1.0E-4F, "干燥盒水面=舱底");
    }

    @Test
    @DisplayName("隔墙格不补水：压载舱的水不能隔着墙画到干舱")
    void wallBetweenBodiesDoesNotGetWaterHole() {
        // 干舱 (0,0,0) | 墙 (1,0,0) | 压载舱 (2,0,0)，压载舱灌满。
        ShipGeometry.Compartment dry = new ShipGeometry.Compartment(
                Set.of(new BlockPos(0, 0, 0)), 0);
        ShipGeometry.Compartment ballast = new ShipGeometry.Compartment(
                Set.of(new BlockPos(2, 0, 0)), 0);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 2, 0, 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(dry, ballast), List.of(), 0, 3, bounds,
                Set.of(new BlockPos(1, 0, 0)), List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().flood(1, 100.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(
                state, 1L, false, 0.0F, 0.0F, 0.0F);

        float[] boxes = packet.boxes();
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            assertTrue(!(boxes[i] == 1.0F && boxes[i + 2] == 0.0F),
                    "墙格 (1,0,0) 不能补水盒，否则水会隔墙渲染");
        }
        boolean ballastBox = false;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (boxes[i] == 2.0F && boxes[i + 2] == 0.0F) {
                ballastBox = true;
                assertEquals(1.0F, boxes[i + 6], 1.0E-4F);
            }
        }
        assertTrue(ballastBox, "压载舱自己的水体仍然要画");
    }

    @Test
    @DisplayName("连海空腔的棱柱不会盖住 bbox 里的密闭小间")
    void openPocketPrisms_doNotCoverSealedRoomInsideBoundingBox() {
        // 8 格连海空腔围着一个 1 格密闭舱：旧 bbox 实现会把那一格也画上水面。
        Set<BlockPos> pocketCells = Set.of(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(2, 0, 1),
                new BlockPos(0, 0, 2), new BlockPos(1, 0, 2), new BlockPos(2, 0, 2)
        );
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(pocketCells, 0);
        ShipGeometry.Compartment sealed =
                new ShipGeometry.Compartment(Set.of(new BlockPos(1, 0, 1)), 0);
        ShipGeometry.Bounds bounds = new ShipGeometry.Bounds(0, 0, 0, 2, 0, 2);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(sealed), List.of(pocket), 0, 9, bounds, Set.of(), List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        state.simulation().tickSeaFlood(0.1D, 0.0D);

        ShipWaterStatePacket packet = ShipWaterStateBuilder.build(state, 1L, false, 0.0F, 0.0F, 0.0F);
        float[] boxes = packet.boxes();
        // 密闭舱在前（1 块），连海空腔的棱柱从第 2 块开始。
        for (int i = 7; i + 6 < boxes.length; i += 7) {
            boolean coversSealedCell = 1 >= boxes[i] && 1 <= boxes[i + 3]
                    && 0 >= boxes[i + 1] && 0 <= boxes[i + 4]
                    && 1 >= boxes[i + 2] && 1 <= boxes[i + 5];
            assertTrue(!coversSealedCell,
                    "连海空腔的棱柱不能覆盖密闭舱那一格 (1,0,1)");
        }
    }
}
