package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import com.opamb.barotrauma.ship.water.ShipFluidField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 把逐格水场翻译成客户端水面渲染数据包（物理船/方块船共用）。
 *
 * <p>每个 body 的每个 {@code (x,z)} 竖直列按连续 Y 段发 1×1 的水盒：
 * {@code surface = 段底 + 段内水量/100}。主体列的盒数只由几何决定；贴着实心
 * 设备旁的补洞盒也覆盖未淹没的空间，避免把干舱里柜子前沿的空气认成海水。</p>
 */
public final class ShipWaterStateBuilder {

    /** 全船盒子总数上限；超过时退回每个 body 一个 bbox（大船/极端形状兜底）。 */
    public static final int MAX_TOTAL_BOXES = 2048;

    /** 水平四邻：补洞只补贴在水体侧面的实心格，不补地板/天花板（会和水柱上下重叠）。 */
    private static final int[][] HORIZONTAL_NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** 有水的列至少画这么厚，避免整舱薄层被客户端当干燥跳过。 */
    public static final float MIN_VISIBLE_SURFACE = 1.0F / 32.0F;
    /** 水量超过这个值（units）才算"有水"，防止 0.001 的残水画出一层膜。 */
    private static final double MIN_VISIBLE_UNITS = 0.05D;

    private ShipWaterStateBuilder() {
    }

    public static ShipWaterStatePacket build(ShipState state, long shipId, boolean physical,
                                             float anchorX, float anchorY, float anchorZ) {
        return build(state, shipId, physical, anchorX, anchorY, anchorZ, null, "");
    }

    /** 带源维度：方块船水包是广播的，客户端要靠它拒绝异维度的同坐标水体。 */
    public static ShipWaterStatePacket build(ShipState state, long shipId, boolean physical,
                                             float anchorX, float anchorY, float anchorZ,
                                             String dimension) {
        return build(state, shipId, physical, anchorX, anchorY, anchorZ, null, dimension);
    }

    /**
     * @param fullCube 判定实心格是不是整格方块（船壳/地板/墙）。补洞只该给模型
     *                 比整格小的家具/机器用；给完整方块补水盒会把水画进墙和地板里，
     *                 客户端还会把那一格当成“舱内”，于是旁观者能在墙里游泳。
     *                 {@code null} = 旧行为（所有实心格都补），只用于旧测试。
     */
    public static ShipWaterStatePacket build(ShipState state, long shipId, boolean physical,
                                             float anchorX, float anchorY, float anchorZ,
                                             @org.jetbrains.annotations.Nullable
                                             Predicate<BlockPos> fullCube) {
        return build(state, shipId, physical, anchorX, anchorY, anchorZ, fullCube, "");
    }

    public static ShipWaterStatePacket build(ShipState state, long shipId, boolean physical,
                                             float anchorX, float anchorY, float anchorZ,
                                             @org.jetbrains.annotations.Nullable
                                             Predicate<BlockPos> fullCube,
                                             String dimension) {
        List<ShipGeometry.Compartment> compartments = state.compartments();
        List<ShipGeometry.Compartment> pockets = state.geometry().openPockets();
        ShipFluidField field = state.simulation().fluid();
        if (field == null) {
            return new ShipWaterStatePacket(shipId, physical, anchorX, anchorY, anchorZ,
                    new float[0], new float[0], new float[0], new float[0], dimension);
        }
        int bodyCount = compartments.size() + pockets.size();
        List<float[]> boxes = new ArrayList<>();
        Map<BlockPos, Integer> boxByCell = new HashMap<>();
        int[] bodyFirstBox = new int[bodyCount];
        java.util.Arrays.fill(bodyFirstBox, -1);
        Map<BlockPos, Float> doorSurfaces = doorSurfaces(state.geometry(), field);
        for (int body = 0; body < bodyCount; body++) {
            List<BlockPos> cells = field.cellsOfBody(body);
            if (cells.isEmpty()) {
                continue;
            }
            bodyFirstBox[body] = boxes.size();
            appendBodyColumns(boxes, boxByCell, cells, field, doorSurfaces);
        }
        // 破口/渗漏格不能补洞：破口格的 1×1 水片由客户端单独画，补洞会和水片
        // 在同一面叠成一块更深的"格子"。先收集出来排除掉。
        Set<BlockPos> breachCells = new HashSet<>();
        for (ShipState.Breach breach : state.breaches()) {
            breachCells.add(breach.pos());
        }
        // 柜子等较浅的设备按整格参与分舱，保留设备旁空隙的干湿覆盖。
        Set<BlockPos> solid = new HashSet<>(state.geometry().hull());
        solid.addAll(state.geometry().equipment());
        Set<BlockPos> excluded = new HashSet<>(breachCells);
        excluded.addAll(doorSurfaces.keySet());
        appendSolidHoles(boxes, boxByCell, solid, excluded, field, fullCube);
        // 兜底：水场里没有格子的舱室（例如连海空腔被 exteriorCells 清空、历史舱内格
        // 又没覆盖到）会一个水盒都没有。客户端找不到盒子就把人当舱外海水，出现
        // “货舱明明是干的却在游泳、还有水下滤镜”。这里给这些舱补一个干燥 bbox；
        // 有水的舱不受影响（湿盒仍然优先判 wet）。
        appendMissingDryCoverage(boxes, boxByCell, state, compartments, pockets, field, doorSurfaces);
        if (boxes.size() > MAX_TOTAL_BOXES) {
            // 先按“舱 + 层”拆包围盒：整舱一个包围盒在 L 形舱/相邻舱时会把别舱的
            // 干格也盖成湿的（客户端 dryAt 只看盒子覆盖）。按层拆能消掉跨层误覆盖；
            // 层内仍是包围盒，极端凹形几何仍可能覆盖，但不会把整艘船的楼层混在一起。
            List<float[]> layerBoxes = new ArrayList<>();
            Map<BlockPos, Integer> layerBoxByCell = new HashMap<>();
            java.util.Arrays.fill(bodyFirstBox, -1);
            for (int body = 0; body < bodyCount; body++) {
                List<BlockPos> cells = field.cellsOfBody(body);
                if (cells.isEmpty()) {
                    continue;
                }
                TreeMap<Integer, List<BlockPos>> byLayer = new TreeMap<>();
                for (BlockPos cell : cells) {
                    byLayer.computeIfAbsent(cell.getY(), key -> new ArrayList<>()).add(cell);
                }
                double level = field.bodyLevel(body);
                for (List<BlockPos> layer : byLayer.values()) {
                    int[] bounds = boundsOf(layer);
                    if (bounds == null) {
                        continue;
                    }
                    if (bodyFirstBox[body] < 0) {
                        bodyFirstBox[body] = layerBoxes.size();
                    }
                    // 本层水面 = 全舱水面夹在本层底面和顶面之间。
                    float top = quantize((float) Math.min(bounds[4] + 1.0D,
                            Math.max(bounds[1], level)));
                    int index = layerBoxes.size();
                    layerBoxes.add(new float[]{bounds[0], bounds[1], bounds[2],
                            bounds[3], bounds[4], bounds[5], top});
                    for (BlockPos cell : layer) {
                        layerBoxByCell.put(cell, index);
                    }
                }
            }
            boxes.clear();
            boxByCell.clear();
            if (layerBoxes.size() <= MAX_TOTAL_BOXES) {
                boxes.addAll(layerBoxes);
                boxByCell.putAll(layerBoxByCell);
            } else {
                // 层盒还是太多：退回每舱一个包围盒（覆盖凹角，但不会把包撑爆）。
                java.util.Arrays.fill(bodyFirstBox, -1);
                for (int body = 0; body < bodyCount; body++) {
                    List<BlockPos> cells = field.cellsOfBody(body);
                    if (cells.isEmpty()) {
                        continue;
                    }
                    int[] bounds = boundsOf(cells);
                    if (bounds == null) {
                        continue;
                    }
                    bodyFirstBox[body] = boxes.size();
                    boxes.add(new float[]{bounds[0], bounds[1], bounds[2],
                            bounds[3], bounds[4], bounds[5],
                            quantize((float) field.bodyLevel(body))});
                }
            }
        }

        // 破口行 9 个 float：格中心 x/y/z + boxes 里的绝对 float 偏移
        // + 朝舱内方向 + 所在空腔 minY/maxY + 开口类型（0 墙 / 1 舱顶 / 2 舱底）。
        List<float[]> breachRows = new ArrayList<>();
        for (ShipState.Breach breach : state.breaches()) {
            int pocket = state.simulation().pocketForBreach(breach.pos());
            if (pocket < 0) {
                continue;
            }
            int body = compartments.size() + pocket;
            if (field.bodyCapacity(body) <= 0.0D) {
                continue;
            }
            float[] range = yRange(pockets.get(pocket).cells());
            if (range == null) {
                continue;
            }
            BlockPos target = field.breachReceiver(breach.pos(), body, breachCells);
            // 粗化时按“舱+层”拆盒，破口所在格能直接定位到自己的层盒；
            // 只有退回整舱包围盒时才用该舱的第一个盒子。
            int boxIndex = boxIndex(boxByCell, target);
            if (boxIndex < 0) {
                boxIndex = bodyFirstBox[body];
            }
            if (boxIndex < 0) {
                continue;
            }
            BlockPos offsetToRoom = target == null ? BlockPos.ZERO : target.subtract(breach.pos());
            int kind = offsetToRoom.getY() < 0 ? 1 : offsetToRoom.getY() > 0 ? 2 : 0;
            breachRows.add(new float[]{
                    breach.pos().getX() + 0.5F, breach.pos().getY() + 0.5F,
                    breach.pos().getZ() + 0.5F,
                    boxIndex * 7, offsetToRoom.getX(), offsetToRoom.getZ(),
                    range[0], range[1], kind});
        }
        float[] breaches = new float[breachRows.size() * 9];
        int breachOffset = 0;
        for (float[] row : breachRows) {
            System.arraycopy(row, 0, breaches, breachOffset, 9);
            breachOffset += 9;
        }
        float[] flat = new float[boxes.size() * 7];
        int offset = 0;
        for (float[] box : boxes) {
            System.arraycopy(box, 0, flat, offset, 7);
            offset += 7;
        }
        return new ShipWaterStatePacket(shipId, physical, anchorX, anchorY, anchorZ,
                flat, breaches, streamRows(field), sideFlowRows(field), dimension);
    }

    private static float[] streamRows(ShipFluidField field) {
        var segments = field.waterStreams();
        int count = Math.min(segments.size(), ShipWaterStatePacket.MAX_STREAMS);
        float[] rows = new float[count * ShipWaterStatePacket.STREAM_STRIDE];
        for (int i = 0, at = 0; i < count; i++) {
            var flow = segments.get(i);
            rows[at++] = (float) flow.from().x;
            rows[at++] = (float) flow.from().y;
            rows[at++] = (float) flow.from().z;
            rows[at++] = (float) flow.to().x;
            rows[at++] = (float) flow.to().y;
            rows[at++] = (float) flow.to().z;
            rows[at++] = (float) Math.min(1.0D, flow.rate() / ShipFluidField.DOOR_FLOW_PER_CELL);
        }
        return rows;
    }

    private static float[] sideFlowRows(ShipFluidField field) {
        var flows = field.sideFlows();
        int count = Math.min(flows.size(), ShipWaterStatePacket.MAX_STREAMS);
        float[] rows = new float[count * ShipWaterStatePacket.SIDE_FLOW_STRIDE];
        for (int i = 0, at = 0; i < count; i++) {
            var flow = flows.get(i);
            rows[at++] = (float) flow.sill().x;
            rows[at++] = (float) flow.sill().y;
            rows[at++] = (float) flow.sill().z;
            rows[at++] = flow.dx();
            rows[at++] = flow.dz();
            rows[at++] = (float) flow.wetTop();
            rows[at++] = (float) flow.receivingSurface();
            rows[at++] = (float) Math.min(1.0D, flow.rate() / ShipFluidField.DOOR_FLOW_PER_CELL);
        }
        return rows;
    }
    /**
     * 每列按连续 Y 段发盒；段内水量求和后换算成水面。
     *
     * <p>段是几何决定的，干燥段也发（surface = 段底），所以盒子列表稳定；
     * 进水时只是 surface 变高，客户端可以逐帧插值。</p>
     */
    private static void appendBodyColumns(List<float[]> boxes, Map<BlockPos, Integer> boxByCell,
                                          List<BlockPos> cells, ShipFluidField field,
                                          Map<BlockPos, Float> doorSurfaces) {
        TreeMap<Long, List<BlockPos>> columns = new TreeMap<>();
        for (BlockPos cell : cells) {
            columns.computeIfAbsent(packXZ(cell.getX(), cell.getZ()),
                    key -> new ArrayList<>()).add(cell);
        }
        for (Map.Entry<Long, List<BlockPos>> entry : columns.entrySet()) {
            List<BlockPos> column = entry.getValue();
            column.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
            int i = 0;
            while (i < column.size()) {
                int runFrom = i;
                int runStart = column.get(i).getY();
                int runEnd = runStart;
                double units = field.waterAt(column.get(i++));
                while (i < column.size() && column.get(i).getY() == runEnd + 1) {
                    runEnd = column.get(i).getY();
                    units += field.waterAt(column.get(i));
                    i++;
                }
                int x = column.get(0).getX();
                int z = column.get(0).getZ();
                float surface = (float) Math.min(runEnd + 1,
                        runStart + units / ShipFluidField.UNITS_PER_BLOCK);
                // 归平后的浅水保留可见厚度。门洞独立取两侧实际触水高度。
                if (units > MIN_VISIBLE_UNITS && surface < runStart + MIN_VISIBLE_SURFACE) {
                    surface = runStart + MIN_VISIBLE_SURFACE;
                }
                surface = quantize(surface);
                int segment = runFrom;
                for (int k = runFrom; k < i; k++) {
                    BlockPos cell = column.get(k);
                    Float gateTop = doorSurfaces.get(cell);
                    if (gateTop != null) {
                        appendColumnRun(boxes, boxByCell, column, segment, k, x, z, surface);
                        appendColumnRun(boxes, boxByCell, column, k, k + 1, x, z, gateTop);
                        segment = k + 1;
                    }
                }
                appendColumnRun(boxes, boxByCell, column, segment, i, x, z, surface);
            }
        }
    }

    private static void appendColumnRun(List<float[]> boxes, Map<BlockPos, Integer> boxByCell,
                                        List<BlockPos> column, int start, int end, int x, int z, float surface) {
        if (start == end) {
            return;
        }
        int floor = column.get(start).getY();
        int ceiling = column.get(end - 1).getY() + 1;
        int boxIndex = boxes.size();
        boxes.add(new float[]{x, floor, z, x, ceiling - 1, z, Math.max(floor, Math.min(ceiling, surface))});
        for (int i = start; i < end; i++) {
            boxByCell.put(column.get(i), boxIndex);
        }
    }

    /** A doorway contains a standing water surface only where both sides already touch water. */
    private static Map<BlockPos, Float> doorSurfaces(ShipGeometry.Result geometry, ShipFluidField field) {
        Map<BlockPos, Float> surfaces = new HashMap<>();
        geometry.doorwayAxes().keySet().forEach(cell -> surfaces.put(cell, (float) cell.getY()));
        for (ShipGeometry.DoorLink link : geometry.doorLinks()) {
            BlockPos gate = link.cell();
            surfaces.putIfAbsent(gate, (float) gate.getY());
            if (!link.open() || link.body() == link.other()) {
                continue;
            }
            if (link.other() == ShipGeometry.SEA_BODY) {
                Direction sea = link.seaSide();
                if (sea != null && field.bodyOf(gate.relative(sea)) < 0
                        && field.bodyOf(gate.relative(sea.getOpposite())) == link.body()) {
                    double depth = sharedWaterDepth(field, gate, sea, true);
                    surfaces.merge(gate, quantize((float) (gate.getY() + depth)), Math::max);
                }
                continue;
            }
            Direction.Axis axis = geometry.doorwayAxes().get(gate);
            for (Direction side : Direction.values()) {
                if ((axis != null && side.getAxis() != axis)
                        || field.bodyOf(gate.relative(side)) != link.other()
                        || field.bodyOf(gate.relative(side.getOpposite())) != link.body()) {
                    continue;
                }
                double depth = sharedWaterDepth(field, gate, side, false);
                surfaces.merge(gate, quantize((float) (gate.getY() + depth)), Math::max);
            }
        }
        return surfaces;
    }

    private static double sharedWaterDepth(ShipFluidField field, BlockPos gate, Direction side, boolean sea) {
        double near = sea ? ShipFluidField.UNITS_PER_BLOCK : field.waterAt(gate.relative(side));
        double far = field.waterAt(gate.relative(side.getOpposite()));
        if (Math.min(near, far) <= MIN_VISIBLE_UNITS) {
            return 0.0D;
        }
        if (side.getAxis().isVertical()) {
            double below = side == Direction.DOWN ? near : far;
            return below >= ShipFluidField.UNITS_PER_BLOCK - MIN_VISIBLE_UNITS ? 1.0D : 0.0D;
        }
        return Math.min(1.0D, Math.min(near, far) / ShipFluidField.UNITS_PER_BLOCK);
    }

    /** Give connected model gaps the water line of their own room on the same deck. */
    private static void appendSolidHoles(List<float[]> boxes, Map<BlockPos, Integer> boxByCell,
                                         Set<BlockPos> solid, Set<BlockPos> excluded,
                                         ShipFluidField field,
                                         @org.jetbrains.annotations.Nullable
                                         Predicate<BlockPos> fullCube) {
        Set<BlockPos> remaining = new HashSet<>();
        for (BlockPos cell : solid) {
            if (excluded.contains(cell) || field.bodyOf(cell) >= 0 || boxByCell.containsKey(cell)) {
                continue;
            }
            boolean partial = fullCube != null ? !fullCube.test(cell) : touchesBody(cell, field);
            if (partial) {
                remaining.add(cell);
            }
        }
        TreeMap<Long, TreeMap<Integer, Double>> columns = new TreeMap<>();
        Set<BlockPos> gaps = Set.copyOf(remaining);
        ArrayDeque<BlockPos> search = new ArrayDeque<>();
        List<BlockPos> connected = new ArrayList<>();
        while (!remaining.isEmpty()) {
            BlockPos start = remaining.iterator().next();
            remaining.remove(start);
            search.add(start);
            connected.clear();
            int owner = -1;
            boolean bounded = true;
            double surface = start.getY();
            while (!search.isEmpty()) {
                BlockPos cell = search.remove();
                connected.add(cell);
                for (int[] direction : HORIZONTAL_NEIGHBOURS) {
                    BlockPos neighbor = cell.offset(direction[0], 0, direction[1]);
                    if (gaps.contains(neighbor)) {
                        if (remaining.remove(neighbor)) {
                            search.add(neighbor);
                        }
                    } else if (!excluded.contains(neighbor) && field.bodyOf(neighbor) >= 0) {
                        int body = field.bodyOf(neighbor);
                        bounded &= owner < 0 || owner == body;
                        owner = body;
                        Integer box = boxByCell.get(neighbor);
                        if (box != null) {
                            surface = Math.max(surface, Math.min(cell.getY() + 1.0D, boxes.get(box)[6]));
                        }
                    } else if (!solid.contains(neighbor) || excluded.contains(neighbor)) {
                        // 破口格虽然也在 solid 里，但它本身就是开口：贴着破口的设备缺口
                        // 应该算“无界”，不能因为破口格被当墙就补出一个水盒。
                        bounded = false;
                    }
                }
            }
            if (bounded && owner >= 0) {
                for (BlockPos cell : connected) {
                    columns.computeIfAbsent(packXZ(cell.getX(), cell.getZ()), key -> new TreeMap<>())
                            .put(cell.getY(), surface);
                }
            }
        }
        for (Map.Entry<Long, TreeMap<Integer, Double>> entry : columns.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            Integer runStart = null;
            Integer runEnd = null;
            double runSurface = 0.0D;
            for (Map.Entry<Integer, Double> yEntry : entry.getValue().entrySet()) {
                int y = yEntry.getKey();
                double surface = yEntry.getValue();
                if (runStart != null && y == runEnd + 1 && (surface <= y || runSurface >= y)) {
                    runEnd = y;
                    if (surface > y) {
                        runSurface = surface;
                    }
                } else {
                    if (runStart != null) {
                        emitHole(boxes, x, z, runStart, runEnd, runSurface);
                    }
                    runStart = y;
                    runEnd = y;
                    runSurface = surface;
                }
            }
            if (runStart != null) {
                emitHole(boxes, x, z, runStart, runEnd, runSurface);
            }
        }
    }

    /**
     * 给水场未覆盖的舱内格补干盒。
     *
     * <p>连海空腔的 {@code interiorCells} 可能在破口/切掉外壁后被清空，
     * {@link ShipFluidField#fromGeometry} 会跳过它；历史舱内格也没覆盖到的新房间
     * 更会整间消失。按列补齐舱内格可以保留干区边界。</p>
     */
    private static void appendMissingDryCoverage(List<float[]> boxes,
                                                 Map<BlockPos, Integer> boxByCell,
                                                 ShipState state,
                                                 List<ShipGeometry.Compartment> compartments,
                                                 List<ShipGeometry.Compartment> pockets,
                                                 ShipFluidField field,
                                                 Map<BlockPos, Float> doorSurfaces) {
        for (int body = 0; body < compartments.size() + pockets.size(); body++) {
            Set<BlockPos> interior;
            if (body < compartments.size()) {
                interior = compartments.get(body).cells();
            } else {
                interior = state.interiorCellsOf(pockets.get(body - compartments.size()));
            }
            if (interior == null || interior.isEmpty()) {
                continue;
            }
            List<BlockPos> missing = new ArrayList<>();
            for (BlockPos cell : interior) {
                if (!boxByCell.containsKey(cell)) {
                    missing.add(cell);
                }
            }
            appendBodyColumns(boxes, boxByCell, missing, field, doorSurfaces);
        }
    }

    private static boolean touchesBody(BlockPos hole, ShipFluidField field) {
        for (int[] direction : HORIZONTAL_NEIGHBOURS) {
            BlockPos neighbour = hole.offset(direction[0], 0, direction[1]);
            if (field.bodyOf(neighbour) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static void emitHole(List<float[]> boxes, int x, int z,
                                 int runStart, int runEnd, double surface) {
        float top = quantize((float) Math.min(runEnd + 1, surface));
        boxes.add(new float[]{x, runStart, z, x, runEnd, z, top});
    }

    private static int boxIndex(Map<BlockPos, Integer> boxByCell, BlockPos cell) {
        if (cell == null) {
            return -1;
        }
        Integer index = boxByCell.get(cell);
        return index == null ? -1 : index;
    }

    private static int[] boundsOf(List<BlockPos> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static float[] yRange(java.util.Collection<BlockPos> cells) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            min = Math.min(min, cell.getY());
            max = Math.max(max, cell.getY());
        }
        return min == Integer.MAX_VALUE ? null : new float[]{min, max};
    }

    /** 同 (x,z) 打包，TreeMap 保证盒顺序稳定。 */
    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** 水面量化到 1/64 格：相邻列同水位时浮点完全相等，客户端才能剔内部面。 */
    private static float quantize(float surface) {
        return Math.round(surface * 64.0F) / 64.0F;
    }
}
