package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import com.opamb.barotrauma.ship.water.ShipFluidField;
import net.minecraft.core.BlockPos;

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
 * 船体格的补洞盒只在"水面没过该格底面"时出现，所以进水跨过格子时会新增/减少
 * 少量盒子，客户端的插值数组会按盒数变化重置一次（单个包的位移很小）。</p>
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
        return build(state, shipId, physical, anchorX, anchorY, anchorZ, null);
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
        List<ShipGeometry.Compartment> compartments = state.compartments();
        List<ShipGeometry.Compartment> pockets = state.geometry().openPockets();
        ShipFluidField field = state.simulation().fluid();
        if (field == null) {
            return new ShipWaterStatePacket(shipId, physical, anchorX, anchorY, anchorZ,
                    new float[0], new float[0]);
        }
        int bodyCount = compartments.size() + pockets.size();
        List<float[]> boxes = new ArrayList<>();
        Map<BlockPos, Integer> boxByCell = new HashMap<>();
        int[] bodyFirstBox = new int[bodyCount];
        java.util.Arrays.fill(bodyFirstBox, -1);
        // 开着的门/舱口：门格本身属于某一侧水体，但另一侧水位更高时，水会从
        // 门洞流出来。这里把门格的水面抬到较高一侧，让门洞里有真实水体，而不是
        // 另外补一张 1×1 的水片（那张水片就是用户看到的"格子"）。
        Map<BlockPos, Float> doorFills = doorFills(state.geometry().doorLinks(), field, bodyCount);
        for (int body = 0; body < bodyCount; body++) {
            List<BlockPos> cells = field.cellsOfBody(body);
            if (cells.isEmpty()) {
                continue;
            }
            bodyFirstBox[body] = boxes.size();
            appendBodyColumns(boxes, boxByCell, cells, field, doorFills);
        }
        // 破口/渗漏格不能补洞：破口格的 1×1 水片由客户端单独画，补洞会和水片
        // 在同一面叠成一块更深的"格子"。先收集出来排除掉。
        Set<BlockPos> breachCells = new HashSet<>();
        for (ShipState.Breach breach : state.breaches()) {
            breachCells.add(breach.pos());
        }
        // 柜子/机器/反应堆/柜式家具这些格在分舱时按实心处理，不属于水体；
        // 它们的模型比整格小，水面如果不补这几格，模型边缘会留 1×1 的干洞
        // （用户实测"水在模型边缘会留白"）。以前只补 equipment 集合，但很多
        // 家具接在船体连通块里、不在 equipment 里，所以现在把所有贴着水体的
        // 船体实心格都补上（按列合并，避免上下重叠）。
        Set<BlockPos> solid = new HashSet<>(state.geometry().hull());
        solid.addAll(state.geometry().equipment());
        appendSolidHoles(boxes, boxByCell, solid, breachCells, field, bodyCount, fullCube);
        // 兜底：水场里没有格子的舱室（例如连海空腔被 exteriorCells 清空、历史舱内格
        // 又没覆盖到）会一个水盒都没有。客户端找不到盒子就把人当舱外海水，出现
        // “货舱明明是干的却在游泳、还有水下滤镜”。这里给这些舱补一个干燥 bbox；
        // 有水的舱不受影响（湿盒仍然优先判 wet）。
        appendMissingDryCoverage(boxes, boxByCell, state, compartments, pockets);
        boolean coarse = false;
        if (boxes.size() > MAX_TOTAL_BOXES) {
            coarse = true;
            boxes.clear();
            boxByCell.clear();
            for (int body = 0; body < bodyCount; body++) {
                List<BlockPos> cells = field.cellsOfBody(body);
                if (cells.isEmpty()) {
                    continue;
                }
                int[] bounds = boundsOf(cells);
                if (bounds == null) {
                    bodyFirstBox[body] = -1;
                    continue;
                }
                bodyFirstBox[body] = boxes.size();
                boxes.add(new float[]{bounds[0], bounds[1], bounds[2],
                        bounds[3], bounds[4], bounds[5], quantize((float) field.bodyLevel(body))});
            }
        }

        // 破口行 9 个 float：格中心 x/y/z + boxes 里的绝对 float 偏移
        // + 朝舱内方向 + 所在空腔 minY/maxY + 开口类型（0 墙 / 1 舱顶 / 2 舱底）。
        List<float[]> breachRows = new ArrayList<>();
        for (ShipState.Breach breach : state.breaches()) {
            int pocket = pocketIndex(pockets, breach.pos());
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
            Opening opening = breachOpening(pockets.get(pocket), breachCells, breach);
            BlockPos target = breachTargetCell(breach, body, field, opening.kind(),
                    opening.normalX(), opening.normalZ());
            int boxIndex = coarse ? bodyFirstBox[body] : boxIndex(boxByCell, target);
            if (boxIndex < 0) {
                continue;
            }
            breachRows.add(new float[]{
                    breach.pos().getX() + 0.5F, breach.pos().getY() + 0.5F,
                    breach.pos().getZ() + 0.5F,
                    boxIndex * 7, opening.normalX(), opening.normalZ(),
                    range[0], range[1], opening.kind()});
        }
        // 门不再额外发 1×1 补面：门格本身已经是场格，水面由 doorFills 抬到较高
        // 一侧；那个 1×1 补面正是用户看到的"开着的门渲染有格子"。
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
        return new ShipWaterStatePacket(shipId, physical, anchorX, anchorY, anchorZ, flat, breaches);
    }

    /**
     * 每列按连续 Y 段发盒；段内水量求和后换算成水面。
     *
     * <p>段是几何决定的，干燥段也发（surface = 段底），所以盒子列表稳定；
     * 进水时只是 surface 变高，客户端可以逐帧插值。</p>
     */
    private static void appendBodyColumns(List<float[]> boxes, Map<BlockPos, Integer> boxByCell,
                                          List<BlockPos> cells, ShipFluidField field,
                                          Map<BlockPos, Float> doorFills) {
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
                double units = 0.0D;
                float doorFill = Float.NEGATIVE_INFINITY;
                i++;
                units += field.waterAt(column.get(i - 1));
                doorFill = Math.max(doorFill, doorFills.getOrDefault(column.get(i - 1),
                        Float.NEGATIVE_INFINITY));
                while (i < column.size() && column.get(i).getY() == runEnd + 1) {
                    runEnd = column.get(i).getY();
                    units += field.waterAt(column.get(i));
                    doorFill = Math.max(doorFill, doorFills.getOrDefault(column.get(i),
                            Float.NEGATIVE_INFINITY));
                    i++;
                }
                int x = column.get(0).getX();
                int z = column.get(0).getZ();
                float surface = (float) Math.min(runEnd + 1,
                        runStart + units / ShipFluidField.UNITS_PER_BLOCK);
                // 归平后一小股水会摊成整间房的薄层（可能只有 0.008 格）。客户端
                // 把 ≤0.01 的水面当干燥跳过，薄层会完全看不见，所以给有水列一个
                // 最小可见厚度：水量是真的，只是画出来至少 1/32 格。
                if (units > MIN_VISIBLE_UNITS && surface < runStart + MIN_VISIBLE_SURFACE) {
                    surface = runStart + MIN_VISIBLE_SURFACE;
                }
                surface = quantize(surface);
                if (doorFill > surface + 1.0E-4F) {
                    // 开着的门另一侧水位更高：只抬门格自己那一格的水面。
                    // 把整段水柱一起抬，会把门下没有水的格子也画成湿的，
                    // 服务端/客户端的干湿判定跟着错。
                    for (int k = runFrom; k < i; k++) {
                        BlockPos cell = column.get(k);
                        float top = (float) Math.min(cell.getY() + 1, surface);
                        float fill = doorFills.getOrDefault(cell, Float.NEGATIVE_INFINITY);
                        if (fill > top) {
                            top = (float) Math.min(cell.getY() + 1, fill);
                        }
                        boxes.add(new float[]{x, cell.getY(), z, x, cell.getY(), z, quantize(top)});
                        boxByCell.put(cell, boxes.size() - 1);
                    }
                } else {
                    int boxIndex = boxes.size();
                    boxes.add(new float[]{x, runStart, z, x, runEnd, z, surface});
                    for (int k = runFrom; k < i; k++) {
                        boxByCell.put(column.get(k), boxIndex);
                    }
                }
            }
        }
    }

    /**
     * 开着的门/舱口：门格水面 = 两侧较高的水面。
     *
     * <p>门格只属于某一侧水体，另一侧水位更高时门格本身是干的；之前靠客户端
     * 额外画一张 1×1 水片，看起来就是一个悬空的格子。现在直接把门格的水柱抬到
     * 较高一侧的水面，门洞里是真实水体，关着的门不抬。</p>
     */
    private static Map<BlockPos, Float> doorFills(List<ShipGeometry.DoorLink> links,
                                                  ShipFluidField field, int bodyCount) {
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<BlockPos, Float> fills = new HashMap<>();
        for (ShipGeometry.DoorLink link : links) {
            if (!link.open() || link.body() == link.other()
                    || link.other() == ShipGeometry.SEA_BODY) {
                continue;
            }
            int a = link.body();
            int b = link.other();
            if (a < 0 || b < 0 || a >= bodyCount || b >= bodyCount) {
                continue;
            }
            double level = Double.NEGATIVE_INFINITY;
            if (field.bodyWater(a) > 0.05D) {
                level = Math.max(level, field.bodyLevel(a));
            }
            if (field.bodyWater(b) > 0.05D) {
                level = Math.max(level, field.bodyLevel(b));
            }
            if (level <= link.cell().getY() + 0.02D) {
                continue;
            }
            float fill = (float) level;
            fills.merge(link.cell(), fill, Math::max);
        }
        return fills;
    }

    /**
     * 贴着水体的实心船体格补水面：柜子/机器/床的内侧模型通常比整格缩进，
     * 水面停在格子边界就会在模型边缘留白。这里给每个"水平相邻水体、且水面已经
     * 没过格子底面"的实心格补一个水盒，水面和水体一致；同一列上下连续格合并成
     * 一个盒，避免上下重叠导致叠色。
     *
     * <p>只补水平四邻，不补地板/天花板：那两类格补出来的水盒会和上方/下方的
     * 水柱在竖直方向重叠，正是之前"一格一条缝"的来源。</p>
     *
     * <p><b>整格方块（船壳/地板/墙）绝不补。</b>以前把所有实心船体都补上，
     * 结果水盒覆盖了墙体内部：渲染时水在墙里，客户端 {@code isInsideCompartment}
     * 也把墙格当成舱内，旁观者钻进船壁还能游泳。家具/机器才需要补缝。</p>
     */
    private static void appendSolidHoles(List<float[]> boxes, Map<BlockPos, Integer> boxByCell,
                                         Set<BlockPos> solid, Set<BlockPos> excluded,
                                         ShipFluidField field, int bodyCount,
                                         @org.jetbrains.annotations.Nullable
                                         Predicate<BlockPos> fullCube) {
        if (solid.isEmpty()) {
            return;
        }
        // 列键 → (y → 水面)。TreeMap 保证包的盒子顺序跨帧稳定，客户端插值不会错位。
        TreeMap<Long, TreeMap<Integer, Double>> columns = new TreeMap<>();
        for (int body = 0; body < bodyCount; body++) {
            if (field.bodyWater(body) <= 0.05D) {
                continue;
            }
            List<BlockPos> cells = field.cellsOfBody(body);
            if (cells.isEmpty()) {
                continue;
            }
            double level = field.bodyLevel(body);
            for (BlockPos cell : cells) {
                int y = cell.getY();
                if (level <= y + 0.02D) {
                    continue;
                }
                for (int[] direction : HORIZONTAL_NEIGHBOURS) {
                    BlockPos hole = new BlockPos(cell.getX() + direction[0], y,
                            cell.getZ() + direction[1]);
                    if (!solid.contains(hole) || excluded.contains(hole)
                            || field.bodyOf(hole) >= 0 || boxByCell.containsKey(hole)) {
                        continue;
                    }
                    if (fullCube != null && fullCube.test(hole)) {
                        // 整格方块自己没有“模型边缘留白”，补水只会把水画进墙/地板。
                        continue;
                    }
                    if (!holeHasOnlyOwnBody(hole, body, field, solid)) {
                        continue;
                    }
                    double surface = Math.min(y + 1.0D, level);
                    columns.computeIfAbsent(packXZ(hole.getX(), hole.getZ()),
                                    key -> new TreeMap<>())
                            .merge(y, surface, Math::max);
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
                if (runStart != null && y == runEnd + 1) {
                    runEnd = y;
                    runSurface = Math.max(runSurface, surface);
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
     * 给“水场里没有格子”的舱室补一个干燥包围盒。
     *
     * <p>连海空腔的 {@code interiorCells} 可能在破口/切掉外壁后被清空，
     * {@link ShipFluidField#fromGeometry} 会跳过它；历史舱内格也没覆盖到的新房间
     * 更会整间消失。客户端拿不到水盒，就把人当成舱外海水 → 干舱里也在游泳、
     * 还有水下滤镜（用户实测货舱）。这里按 body 的舱内格补一个 surface=舱底的
     * 干盒，保证每个舱室至少有一个盒子；真有水的湿盒仍然会让 wet 优先。</p>
     */
    private static void appendMissingDryCoverage(List<float[]> boxes,
                                                 Map<BlockPos, Integer> boxByCell,
                                                 ShipState state,
                                                 List<ShipGeometry.Compartment> compartments,
                                                 List<ShipGeometry.Compartment> pockets) {
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
            if (missing.isEmpty()) {
                continue;
            }
            int[] bounds = boundsOf(missing);
            if (bounds == null) {
                continue;
            }
            boxes.add(new float[]{bounds[0], bounds[1], bounds[2],
                    bounds[3], bounds[4], bounds[5], bounds[1]});
        }
    }

    /**
     * 补洞格必须"四邻只有本水体或实心格"。
     *
     * <p>否则隔墙格会被补进水盒：干舱那一侧没有水盒，补洞盒的侧面就会在墙面上
     * 画出来，半透明水叠在墙方块上，看起来就是"压载舱的水隔着墙渲染"（用户实测）。
     * 另一侧是别的舱（包括舱外空气）时直接不补——墙本身是整格模型，不需要补缝。</p>
     */
    private static boolean holeHasOnlyOwnBody(BlockPos hole, int body,
                                              ShipFluidField field, Set<BlockPos> solid) {
        for (int[] direction : HORIZONTAL_NEIGHBOURS) {
            BlockPos neighbour = hole.offset(direction[0], 0, direction[1]);
            int neighbourBody = field.bodyOf(neighbour);
            if (neighbourBody >= 0) {
                if (neighbourBody != body) {
                    return false;
                }
            } else if (!solid.contains(neighbour)) {
                // 场外空气：不知道另一边是什么，补出来的侧面会画到舱外/墙外。
                return false;
            }
        }
        return true;
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

    private record Opening(float normalX, float normalZ, int kind) {
    }

    /**
     * 破口开口方向和类型：优先找破口旁边的船内格，给出轴对齐法线（墙洞）；
     * 上下只有一侧有船内格时是舱顶/舱底；厚船壳才退回质心斜方向。
     */
    private static Opening breachOpening(ShipGeometry.Compartment pocket, Set<BlockPos> breachCells,
                                         ShipState.Breach breach) {
        Set<BlockPos> cells = pocket.cells();
        BlockPos pos = breach.pos();
        int bestX = 0;
        int bestZ = 0;
        float bestScore = Float.NEGATIVE_INFINITY;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            BlockPos neighbor = pos.offset(side[0], 0, side[1]);
            if (!cells.contains(neighbor) || breachCells.contains(neighbor)) {
                continue;
            }
            float score = breach.dirX() * side[0] + breach.dirZ() * side[1];
            if (score > bestScore) {
                bestScore = score;
                bestX = side[0];
                bestZ = side[1];
            }
        }
        if (bestX != 0 || bestZ != 0) {
            return new Opening(bestX, bestZ, 0);
        }
        boolean above = cells.contains(pos.above()) && !breachCells.contains(pos.above());
        boolean below = cells.contains(pos.below()) && !breachCells.contains(pos.below());
        if (above != below) {
            // 客户端按 y 与空腔上下界区分舱顶/舱底；这里直接把类型算准。
            int kind = above ? 2 : 1;
            return new Opening(0.0F, 0.0F, kind);
        }
        return new Opening(breach.dirX(), breach.dirZ(), 0);
    }

    private static BlockPos breachTargetCell(ShipState.Breach breach, int body,
                                             ShipFluidField field, int kind,
                                             float normalX, float normalZ) {
        BlockPos pos = breach.pos();
        if (kind == 1) {
            if (field.bodyOf(pos.below()) == body) {
                return pos.below();
            }
        } else if (kind == 2) {
            if (field.bodyOf(pos.above()) == body) {
                return pos.above();
            }
        }
        int dx = (int) Math.signum(normalX);
        int dz = (int) Math.signum(normalZ);
        if (dx != 0) {
            BlockPos candidate = pos.offset(dx, 0, 0);
            if (field.bodyOf(candidate) == body) {
                return candidate;
            }
        }
        if (dz != 0) {
            BlockPos candidate = pos.offset(0, 0, dz);
            if (field.bodyOf(candidate) == body) {
                return candidate;
            }
        }
        for (BlockPos next : new BlockPos[]{
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (field.bodyOf(next) == body) {
                return next;
            }
        }
        return null;
    }

    private static int pocketIndex(List<ShipGeometry.Compartment> pockets, BlockPos pos) {
        for (int i = 0; i < pockets.size(); i++) {
            if (pockets.get(i).cells().contains(pos)) {
                return i;
            }
        }
        return -1;
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
