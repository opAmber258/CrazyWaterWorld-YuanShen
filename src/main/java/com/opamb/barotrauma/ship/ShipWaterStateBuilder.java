package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 把船状态翻译成"客户端水面渲染"用的数据包（物理船/方块船共用）。
 *
 * <p>每个舱室/连海空腔不再只发一个包围盒，而是拆成若干个**矩形棱柱**
 * （每个棱柱 7 个 float：minX/minY/minZ/maxX/maxY/maxZ + 水面高度）。
 * 包围盒会把非矩形舱室和它旁边的密闭小间一起盖住——用户实测"隔离的舱也在涨水"
 * 就是这个问题（连海空腔的 bbox 包住了旁边的密闭小间）。棱柱只覆盖真实格子，
 * 水面就不会画到别的舱里。</p>
 */
public final class ShipWaterStateBuilder {

    /**
     * 单个舱室最多拆成多少个棱柱。
     *
     * <p>正常房间逐层贪心切矩形只有几个；只有极端锯齿形状才会爆。超过上限时退回
     * 整个 bbox（旧行为），宁可视觉上多盖一点，也不能把网络包撑爆。</p>
     */
    private static final int MAX_BOXES_PER_COMPARTMENT = 512;

    private ShipWaterStateBuilder() {
    }

    public static ShipWaterStatePacket build(ShipState state, long shipId, boolean physical,
                                             float anchorX, float anchorY, float anchorZ) {
        List<ShipGeometry.Compartment> compartments = state.compartments();
        List<ShipGeometry.Compartment> pockets = state.geometry().openPockets();
        List<float[]> boxes = new ArrayList<>();
        // 每个连海空腔第一个"有水"棱柱的**盒子下标**：只在找不到破口自己那一列的盒子时兜底。
        // 连海空腔按列分水位后，不同列的水面并不相同，破口必须用自己那一列的水面。
        int[] pocketBoxIndex = new int[pockets.size()];
        // 破口是水的入口：连海空腔的水面/含水格子按它算，隔墙顶部的孔不会提前串水。
        List<List<BlockPos>> pocketSources = new ArrayList<>(pockets.size());
        for (int i = 0; i < pockets.size(); i++) {
            pocketSources.add(new ArrayList<>());
        }
        for (ShipState.Breach breach : state.breaches()) {
            int pocket = pocketIndex(pockets, breach.pos());
            if (pocket >= 0) {
                pocketSources.get(pocket).add(breach.pos());
            }
        }
        // 每个水体第一个盒子的下标：门两侧过水的视觉要用"接收侧"的盒子算水面。
        int[] bodyBox = new int[compartments.size() + pockets.size()];
        for (int i = 0; i < compartments.size(); i++) {
            bodyBox[i] = boxes.size();
            appendCompartment(boxes, compartments.get(i), state.simulation().waterUnits(i), null);
        }
        // 每个空腔在 boxes 里的下标范围 [start, end)：破口找自己那一列时只许在这个范围里找。
        // 干燥密闭舱室会先写一个整块 bbox 占位；全表扫描会让它的包围盒抢走别的空腔的破口格。
        int[] pocketBoxStart = new int[pockets.size()];
        int[] pocketBoxEnd = new int[pockets.size()];
        for (int i = 0; i < pockets.size(); i++) {
            bodyBox[compartments.size() + i] = boxes.size();
            pocketBoxStart[i] = boxes.size();
            pocketBoxIndex[i] = appendCompartment(boxes, pockets.get(i),
                    state.simulation().pocketWater(i), pocketSources.get(i));
            pocketBoxEnd[i] = boxes.size();
        }

        // 破口 8 个 float：格中心 x/y/z + boxes 里的**绝对 float 偏移** + 朝舱内的方向
        // + 所在空腔的 minY/maxY（客户端据此判断"顶部破口"和"是否灌满"）。
        // 偏移必须指向破口自己那一列（连海空腔逐列水位），不能指向空腔第一个有水块：
        // 第一块可能只是某个低处的 Y 段，水面远低于洞口，客户端就会只画半截。
        List<float[]> breachRows = new ArrayList<>();
        Set<BlockPos> breachCells = new HashSet<>();
        for (ShipState.Breach breach : state.breaches()) {
            breachCells.add(breach.pos());
        }
        for (ShipState.Breach breach : state.breaches()) {
            int pocket = pocketIndex(pockets, breach.pos());
            if (pocket < 0) {
                continue;
            }
            int ownBox = boxAtCell(boxes, pocketBoxStart[pocket], pocketBoxEnd[pocket], breach.pos());
            if (ownBox < 0) {
                // appendCompartment 返回的是盒子下标，协议要的是绝对 float 偏移。
                ownBox = pocketBoxIndex[pocket] * 7;
            }
            float[] range = yRange(pockets.get(pocket));
            float[] normal = breachNormal(pockets.get(pocket), breachCells, breach.pos(),
                    breach.dirX(), breach.dirZ());
            breachRows.add(new float[]{
                    breach.pos().getX() + 0.5F, breach.pos().getY() + 0.5F, breach.pos().getZ() + 0.5F,
                    ownBox, normal[0], normal[1], range[0], range[1]});
        }
        // 开着的门：两侧有水位差时，把水花/气泡画在门格上，方向朝"接水"的那一侧。
        for (ShipGeometry.DoorLink link : state.geometry().doorLinks()) {
            if (!link.open() || link.body() == link.other()) {
                continue;
            }
            int a = link.body();
            int b = link.other();
            double la = state.simulation().bodyLevel(a);
            double lb = state.simulation().bodyLevel(b);
            if (Math.abs(la - lb) <= 0.02D) {
                continue;
            }
            int wet = la > lb ? a : b;
            int dry = la > lb ? b : a;
            if (dry < 0 || dry >= bodyBox.length || bodyBox[dry] < 0
                    || bodyBox[dry] >= boxes.size()) {
                continue;
            }
            float[] range = yRange(cellsOf(compartments, pockets, dry));
            if (range == null) {
                continue;
            }
            double[] wetCenter = centerOf(cellsOf(compartments, pockets, wet));
            double dx = link.cell().getX() + 0.5D - wetCenter[0];
            double dz = link.cell().getZ() + 0.5D - wetCenter[1];
            double length = Math.sqrt(dx * dx + dz * dz);
            float dirX = length > 1.0E-4D ? (float) (dx / length) : 0.0F;
            float dirZ = length > 1.0E-4D ? (float) (dz / length) : 0.0F;
            breachRows.add(new float[]{
                    link.cell().getX() + 0.5F, link.cell().getY() + 0.5F, link.cell().getZ() + 0.5F,
                    // 门格本身属于来水侧；水面要取"接水侧"的第一个盒子，乘 7 转成绝对 float 偏移。
                    bodyBox[dry] * 7, dirX, dirZ, range[0], range[1]});
        }
        float[] breaches = new float[breachRows.size() * 8];
        int breachOffset = 0;
        for (float[] row : breachRows) {
            System.arraycopy(row, 0, breaches, breachOffset, 8);
            breachOffset += 8;
        }

        float[] flat = new float[boxes.size() * 7];
        int offset = 0;
        for (float[] box : boxes) {
            System.arraycopy(box, 0, flat, offset, 7);
            offset += 7;
        }
        return new ShipWaterStatePacket(shipId, physical, anchorX, anchorY, anchorZ, flat, breaches);
    }

    private static int pocketIndex(List<ShipGeometry.Compartment> pockets, BlockPos pos) {
        for (int i = 0; i < pockets.size(); i++) {
            if (pockets.get(i).cells().contains(pos)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 覆盖这一格的盒子在 {@code boxes} 里的绝对 float 偏移（{@code index * 7}）；没有返回 -1。
     *
     * <p>只在 {@code [start, end)} 这段（破口所属空腔自己的盒子）里找：密闭舱室的干燥
     * 占位 bbox 可能把别的空腔的破口格一起盖住，全表扫描会先命中它，破口就取到错误水位。</p>
     */
    private static int boxAtCell(List<float[]> boxes, int start, int end, BlockPos pos) {
        for (int i = Math.max(0, start); i < Math.min(end, boxes.size()); i++) {
            float[] box = boxes.get(i);
            if (pos.getX() >= box[0] && pos.getX() <= box[3]
                    && pos.getY() >= box[1] && pos.getY() <= box[4]
                    && pos.getZ() >= box[2] && pos.getZ() <= box[5]) {
                return i * 7;
            }
        }
        return -1;
    }

    /**
     * 破口朝舱内的方向，舱壁破口发轴对齐的水平法线（±1/0），舱顶/舱底破口发 (0,0)。
     *
     * <p>客户端要用它决定洞口补面画成竖直还是水平。以前发的是"洞口→空腔质心"的斜方向，
     * 客户端只能靠水柱端点猜开口在哪一面：连续两格墙洞那一列正好只有两格高，就会被猜成
     * "上格是舱顶、下格是舱底"，补出两张水平面，竖排墙洞从舱内看不到水色。</p>
     *
     * <p>判定看隔壁是不是**舱内格**（空腔里、且不是另一个破口格——连续多格破口彼此
     * 相邻不构成舱内）：水平隔壁在 → 舱壁；否则上/下隔壁在 → 舱顶/舱底。</p>
     */
    private static float[] breachNormal(ShipGeometry.Compartment pocket, Set<BlockPos> breachCells,
                                        BlockPos pos, float dirX, float dirZ) {
        Set<BlockPos> cells = pocket.cells();
        int bestX = 0;
        int bestZ = 0;
        float bestScore = Float.NEGATIVE_INFINITY;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            BlockPos neighbor = pos.offset(side[0], 0, side[1]);
            if (!cells.contains(neighbor) || breachCells.contains(neighbor)) {
                continue;
            }
            float score = dirX * side[0] + dirZ * side[1];
            if (score > bestScore) {
                bestScore = score;
                bestX = side[0];
                bestZ = side[1];
            }
        }
        if (bestX != 0 || bestZ != 0) {
            return new float[]{bestX, bestZ};
        }
        boolean above = cells.contains(pos.above()) && !breachCells.contains(pos.above());
        boolean below = cells.contains(pos.below()) && !breachCells.contains(pos.below());
        if (above != below) {
            // 客户端按 y 与空腔 minY/maxY 的关系区分舱顶/舱底。
            return new float[]{0.0F, 0.0F};
        }
        // 厚船壳等兜底：保留质心方向，客户端仍按舱壁画。
        return new float[]{dirX, dirZ};
    }

    /** 舱室的 minY/maxY。 */
    private static float[] yRange(ShipGeometry.Compartment compartment) {
        return yRange(compartment.cells());
    }

    /** 一组格子的 minY/maxY。 */
    private static float[] yRange(java.util.Collection<BlockPos> cells) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            minY = Math.min(minY, cell.getY());
            maxY = Math.max(maxY, cell.getY());
        }
        if (minY == Integer.MAX_VALUE) {
            return new float[]{0.0F, 0.0F};
        }
        return new float[]{minY, maxY};
    }

    /** 第 body 个水体的格子（0..n-1 密闭舱室，n..n+m-1 连海空腔）。 */
    private static java.util.Collection<BlockPos> cellsOf(List<ShipGeometry.Compartment> compartments,
                                                          List<ShipGeometry.Compartment> pockets,
                                                          int body) {
        if (body >= 0 && body < compartments.size()) {
            return compartments.get(body).cells();
        }
        int pocket = body - compartments.size();
        if (pocket >= 0 && pocket < pockets.size()) {
            return pockets.get(pocket).cells();
        }
        return List.of();
    }

    /** 水平质心（x,z）。 */
    private static double[] centerOf(java.util.Collection<BlockPos> cells) {
        double x = 0.0D;
        double z = 0.0D;
        long count = 0L;
        for (BlockPos cell : cells) {
            x += cell.getX() + 0.5D;
            z += cell.getZ() + 0.5D;
            count++;
        }
        if (count == 0L) {
            return new double[]{0.0D, 0.0D};
        }
        return new double[]{x / count, z / count};
    }

    /**
     * 把一个舱室/空腔写进 {@code boxes}，返回第一个"有水"块的下标。
     *
     * <p>连海空腔按 1×1 竖直列发（每列再按连续 Y 拆段）：每列有自己的水位，
     * 隔墙上的孔只有在某一列的水面漫到孔底后才把水传给下一列，多个隔断会依次传递。
     * 密闭舱室仍然用整块水位。</p>
     *
     * <p>块列表只由几何决定、不随水位变化：含水的列用当前水位，没水的列用自己的
     * 舱底（渲染端跳过）。这样客户端的水面平滑数组长度稳定，水位可以逐帧插值。</p>
     */
    private static int appendCompartment(List<float[]> boxes, ShipGeometry.Compartment compartment,
                                         double units, List<BlockPos> sources) {
        int start = boxes.size();
        int[] bounds = boundsOf(compartment);
        if (bounds == null) {
            return start;
        }
        float minY = bounds[1];
        // 0 水量：水面 = minY，渲染端会跳过；发一个 bbox 给干燥区判定用。
        if (units <= 0.0D) {
            boxes.add(new float[]{bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5], minY});
            return start;
        }
        if (sources != null && !sources.isEmpty()) {
            ShipPocketWater.Shape shape = ShipPocketWater.compute(compartment.cells(), sources, units);
            if (!shape.columns().isEmpty()) {
                return appendByColumn(boxes, compartment.cells(), shape);
            }
        }
        // 密闭舱室 / 没有破口记录的空腔：旧的"从舱底整层铺水"模型。
        float surface = surfaceOf(compartment, units);
        if (surface <= minY + 0.01F) {
            boxes.add(new float[]{bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5], minY});
            return start;
        }
        List<int[]> prisms = decompose(compartment.cells());
        if (prisms.isEmpty()) {
            return start;
        }
        if (prisms.size() > MAX_BOXES_PER_COMPARTMENT) {
            boxes.add(new float[]{bounds[0], bounds[1], bounds[2],
                    bounds[3], bounds[4], bounds[5], surface});
            return start;
        }
        for (int[] prism : prisms) {
            boxes.add(new float[]{prism[0], prism[1], prism[2], prism[3], prism[4], prism[5], surface});
        }
        return start;
    }

    /**
     * 连海空腔按列发：每列一个盒子（Y 有断档时拆成多段），盒子带这一列的**真实水位**。
     *
     * <p>水位可以高于这一段的顶格（这一段被更高的水淹没）：渲染端据此把顶面当水体内部面
     * 跳过，只画侧面/底面，并且侧面的顶边压到这一段顶格。若这里把水位 clamp 到顶格，
     * 渲染端就分不清"刚好满到顶格"和"整段在水下"，破口那种只有一个格子的列会在洞口
     * 画出一层假水面，看起来比舱内低一整格。</p>
     */
    private static int appendByColumn(List<float[]> boxes, Set<BlockPos> cells,
                                      ShipPocketWater.Shape shape) {
        TreeMap<Long, List<Integer>> columns = new TreeMap<>();
        for (BlockPos cell : cells) {
            columns.computeIfAbsent(packXZ(cell.getX(), cell.getZ()), key -> new ArrayList<>())
                    .add(cell.getY());
        }
        int firstWet = -1;
        for (Map.Entry<Long, List<Integer>> entry : columns.entrySet()) {
            int x = unpackX(entry.getKey());
            int z = unpackZ(entry.getKey());
            List<Integer> ys = entry.getValue();
            ys.sort(Integer::compare);
            float level = shape.levelAt(x, z);
            int i = 0;
            while (i < ys.size()) {
                int runStart = ys.get(i);
                int runEnd = runStart;
                i++;
                while (i < ys.size() && ys.get(i) == runEnd + 1) {
                    runEnd = ys.get(i);
                    i++;
                }
                float waterLevel = runStart;
                if (!Float.isNaN(level) && level > runStart + 0.001F) {
                    waterLevel = quantize(level);
                }
                int boxIndex = boxes.size();
                boxes.add(new float[]{x, runStart, z, x, runEnd, z, waterLevel});
                if (waterLevel > runStart + 0.01F && firstWet < 0) {
                    firstWet = boxIndex;
                }
            }
        }
        return firstWet < 0 ? 0 : firstWet;
    }

    /** 水面量化到 1/64 格：相邻列同水位时浮点完全相等，客户端才能把内部面剔掉。 */
    private static float quantize(float surface) {
        return Math.round(surface * 64.0F) / 64.0F;
    }

    private static int[] boundsOf(ShipGeometry.Compartment compartment) {
        return boundsOf(compartment.cells());
    }

    private static int[] boundsOf(Set<BlockPos> cells) {
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

    /**
     * 把一组格子拆成尽量少的矩形棱柱：逐 Y 层贪心切二维矩形，再把上下层完全相同的
     * 矩形合并成一根柱子。
     *
     * @return 每个元素 {minX, minY, minZ, maxX, maxY, maxZ}
     */
    private static List<int[]> decompose(Set<BlockPos> cells) {
        TreeMap<Integer, Set<Long>> layers = new TreeMap<>();
        for (BlockPos cell : cells) {
            layers.computeIfAbsent(cell.getY(), y -> new HashSet<>())
                    .add(packXZ(cell.getX(), cell.getZ()));
        }
        List<int[]> prisms = new ArrayList<>();
        // 上一层的矩形 footprint → 对应的柱子；下一层同样的 footprint 就延长它。
        Map<RectKey, int[]> open = new HashMap<>();
        for (Map.Entry<Integer, Set<Long>> layer : layers.entrySet()) {
            int y = layer.getKey();
            Map<RectKey, int[]> next = new HashMap<>();
            for (int[] rect : decomposeLayer(layer.getValue())) {
                RectKey key = new RectKey(rect[0], rect[1], rect[2], rect[3]);
                int[] prism = open.get(key);
                if (prism != null) {
                    prism[4] = y;
                    next.put(key, prism);
                } else {
                    int[] created = {rect[0], y, rect[1], rect[2], y, rect[3]};
                    prisms.add(created);
                    next.put(key, created);
                }
            }
            open = next;
        }
        return prisms;
    }

    /** 单层贪心：按 x/z 排序，先尽量向 +X 延伸，再整行向 +Z 延伸。 */
    private static List<int[]> decomposeLayer(Set<Long> layer) {
        List<long[]> cells = new ArrayList<>(layer.size());
        for (long packed : layer) {
            cells.add(new long[]{unpackX(packed), unpackZ(packed)});
        }
        cells.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        Set<Long> remaining = new HashSet<>(layer);
        List<int[]> rects = new ArrayList<>();
        for (long[] cell : cells) {
            long key = packXZ((int) cell[0], (int) cell[1]);
            if (!remaining.remove(key)) {
                continue;
            }
            int minX = (int) cell[0];
            int minZ = (int) cell[1];
            int maxX = minX;
            int maxZ = minZ;
            while (remaining.contains(packXZ(maxX + 1, minZ))) {
                maxX++;
                remaining.remove(packXZ(maxX, minZ));
            }
            boolean grow = true;
            while (grow) {
                for (int x = minX; x <= maxX; x++) {
                    if (!remaining.contains(packXZ(x, maxZ + 1))) {
                        grow = false;
                        break;
                    }
                }
                if (grow) {
                    maxZ++;
                    for (int x = minX; x <= maxX; x++) {
                        remaining.remove(packXZ(x, maxZ));
                    }
                }
            }
            rects.add(new int[]{minX, minZ, maxX, maxZ});
        }
        return rects;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    /** 矩形 footprint 作为合并键：不同形状不能混在一起。 */
    private record RectKey(int minX, int minZ, int maxX, int maxZ) {
    }

    /**
     * 一个舱室的水面高度。
     *
     * <p>必须按<b>实际逐层格数</b>算，不能用包围盒底面积：
     * 舱室很少是完整长方体（破口空腔、L 形舱、被机器占掉的格子都很常见）。
     * 用 bbox 面积会把水位算高/算低，和 {@link ShipWater} 的"从舱底逐格铺水"对不上。</p>
     */
    static float surfaceOf(ShipGeometry.Compartment compartment, double units) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        // 每个 Y 层有多少格；按 Y 升序累计，和 ShipWater 的填水顺序一致。
        TreeMap<Integer, Integer> cellsPerLayer = new TreeMap<>();
        for (BlockPos cell : compartment.cells()) {
            minY = Math.min(minY, cell.getY());
            maxY = Math.max(maxY, cell.getY());
            cellsPerLayer.merge(cell.getY(), 1, Integer::sum);
        }
        if (minY == Integer.MAX_VALUE) {
            return 0.0F;
        }
        int volume = compartment.volume();
        double filled = Math.max(0.0D, units / ShipSimulation.UNITS_PER_BLOCK);
        float surface;
        if (volume <= 0 || filled <= 0.0D) {
            surface = minY;
        } else if (filled >= volume) {
            surface = maxY + 1.0F;
        } else {
            double remaining = filled;
            float candidate = maxY + 1.0F;
            for (Map.Entry<Integer, Integer> layer : cellsPerLayer.entrySet()) {
                int count = layer.getValue();
                if (count <= 0) {
                    continue;
                }
                if (remaining >= count) {
                    remaining -= count;
                    candidate = layer.getKey() + 1.0F;
                } else {
                    candidate = layer.getKey() + (float) (remaining / count);
                    break;
                }
            }
            surface = candidate;
        }
        return Math.max((float) minY, Math.min((float) maxY + 1.0F, surface));
    }
}
