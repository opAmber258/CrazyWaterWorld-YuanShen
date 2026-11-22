package com.opamb.barotrauma.ship.water;

import com.opamb.barotrauma.ship.ShipGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 船内逐格水量场。
 *
 * <p>和旧的"一个舱室一个水位"不同，这里每个船内格独立存 {@code 0..100} units
 * 的水（100 = 一格灌满）。水在相邻格之间按压力头（{@code y + 水量/100}）流动，
 * 同列的水受重力先往下落。密封舱和连海空腔用同一套场，区别只在于破口/开向海的
 * 门由 {@code ShipSimulation} 往场里注水。</p>
 *
 * <p>本类不碰 Minecraft 世界，只做纯数值，方便 L1 单测。格子的 body 下标沿用
 * {@link ShipGeometry}：先密封舱 {@code 0..n-1}，再连海空腔 {@code n..}。</p>
 */
public final class ShipFluidField {

    public static final double UNITS_PER_BLOCK = 100.0D;
    public static final double MAX_CELL_WATER = UNITS_PER_BLOCK;

    /** 水位差小于这个值就不再流动，避免每 tick 微流抖动。 */
    public static final double FLOW_DEADBAND = 0.02D;
    /** 普通舱内面的流量系数（units/秒，按 1 格水头）。 */
    public static final double NORMAL_FLOW_COEFF = 150.0D;
    /** 开门/舱口面的流量系数；同时受 {@link #DOOR_FLOW_PER_CELL} 封顶。 */
    public static final double DOOR_FLOW_COEFF = 3500.0D;
    /** 一格开着的门每秒最多过多少水（原作 Pump/Gap 同量级，沿用旧值）。 */
    public static final double DOOR_FLOW_PER_CELL = 3000.0D;
    /** 每个 tick 做几轮水平/门洞压力平衡。 */
    public static final int FLOW_ITERATIONS = 2;

    /** 一个场格：位置 + 所属 water body。 */
    public record Cell(BlockPos pos, int body) {
    }

    /**
     * 两个相邻格之间的门洞。只有 body 不同的相邻格才需要显式给门；
     * 同一 body 的水平邻接会自动生成普通边，竖直邻接会自动做重力下落。
     */
    public record Gate(BlockPos a, BlockPos b, boolean open) {
    }

    private final BlockPos[] positions;
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;
    private final int[] bodyOf;
    private final double[] water;
    private final double[] bodyWater;
    private final int[] bodyCapacityUnits;
    private final int[][] bodyCells;
    /** 每个 body 的格按 y 升序（同 y 保持插入顺序）；找水平面用。 */
    private final int[][] bodyCellsByY;
    private final int[][] columns;
    private final Edge[] edges;
    private final Edge[] gateEdges;
    private final int[] seaDoors;
    private final Set<BlockPos> ceilingSeaDoors;
    private final Map<BlockPos, Direction> sideSeaDoors;
    private final ShipWaterStreams streams;
    /** 溢流使用每对舱室之间最低的开放门槛。边按来源舱到接收舱定向。 */
    private final List<Map<Integer, Edge>> spillGates;
    private final int bodyCount;

    private final Map<Long, Integer> indexByKey = new HashMap<>();

    private record Edge(int a, int b) {
    }

    private ShipFluidField(List<Cell> cells, List<Gate> gates, int bodyCount,
                           List<BlockPos> seaDoors, Set<BlockPos> ceilingSeaDoors,
                           Map<BlockPos, Direction> sideSeaDoors, Set<BlockPos> closedDoors,
                           Set<BlockPos> hull) {
        this.bodyCount = bodyCount;
        this.ceilingSeaDoors = Set.copyOf(ceilingSeaDoors);
        this.sideSeaDoors = Map.copyOf(sideSeaDoors);
        this.streams = new ShipWaterStreams(this, closedDoors, hull);
        int n = cells.size();
        positions = new BlockPos[n];
        xs = new int[n];
        ys = new int[n];
        zs = new int[n];
        bodyOf = new int[n];
        water = new double[n];
        for (int i = 0; i < n; i++) {
            Cell cell = cells.get(i);
            BlockPos pos = cell.pos();
            positions[i] = pos.immutable();
            xs[i] = pos.getX();
            ys[i] = pos.getY();
            zs[i] = pos.getZ();
            bodyOf[i] = cell.body();
            indexByKey.put(pos.asLong(), i);
        }
        bodyWater = new double[Math.max(0, bodyCount)];
        bodyCapacityUnits = new int[Math.max(0, bodyCount)];
        List<List<Integer>> bodyLists = new ArrayList<>(Math.max(0, bodyCount));
        for (int i = 0; i < bodyCount; i++) {
            bodyLists.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            int body = bodyOf[i];
            if (body >= 0 && body < bodyCount) {
                bodyLists.get(body).add(i);
            }
        }
        bodyCells = new int[bodyCount][];
        bodyCellsByY = new int[bodyCount][];
        for (int i = 0; i < bodyCount; i++) {
            bodyCells[i] = bodyLists.get(i).stream().mapToInt(Integer::intValue).toArray();
            bodyCapacityUnits[i] = bodyCells[i].length * (int) UNITS_PER_BLOCK;
            int[] byY = bodyCells[i].clone();
            // 稳定排序：同 y 的格保持原顺序，均摊结果可复现。
            byY = java.util.Arrays.stream(byY)
                    .boxed()
                    .sorted((a, b) -> Integer.compare(ys[a], ys[b]))
                    .mapToInt(Integer::intValue)
                    .toArray();
            bodyCellsByY[i] = byY;
        }

        // 同 (x,z) 的竖直列：重力下落和渲染的逐列盒都按这个顺序用。
        Map<Long, List<Integer>> columnsByXZ = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long key = packXZ(xs[i], zs[i]);
            columnsByXZ.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        columns = new int[columnsByXZ.size()][];
        int columnIndex = 0;
        for (List<Integer> column : columnsByXZ.values()) {
            column.sort((a, b) -> Integer.compare(ys[a], ys[b]));
            columns[columnIndex++] = column.stream().mapToInt(Integer::intValue).toArray();
        }

        // 门边：只有 body 不同的相邻对才需要，开着的才生成边。
        Map<Long, Boolean> gateOpen = new HashMap<>();
        Set<Integer> seaDoorCells = new HashSet<>();
        for (Gate gate : gates) {
            Integer a = indexByKey.get(gate.a().asLong());
            Integer b = indexByKey.get(gate.b().asLong());
            if (a == null || b == null || bodyOf[a] == bodyOf[b]) {
                continue;
            }
            if (!areNeighbours(a, b)) {
                continue;
            }
            gateOpen.merge(pairKey(a, b), gate.open(), (oldValue, newValue) -> oldValue || newValue);
        }
        for (BlockPos door : seaDoors) {
            Integer idx = indexByKey.get(door.asLong());
            if (idx != null) {
                seaDoorCells.add(idx);
            }
        }
        this.seaDoors = seaDoorCells.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(this.seaDoors);

        List<Edge> edgeList = new ArrayList<>();
        List<Edge> passages = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // 只向 +X / +Z / +Y 找，避免每条边生成两次。
            addEdgeIfPresent(edgeList, passages, gateOpen, i, xs[i] + 1, ys[i], zs[i]);
            addEdgeIfPresent(edgeList, passages, gateOpen, i, xs[i], ys[i], zs[i] + 1);
            addEdgeIfPresent(edgeList, passages, gateOpen, i, xs[i], ys[i] + 1, zs[i]);
        }
        edges = edgeList.toArray(new Edge[0]);
        gateEdges = passages.toArray(new Edge[0]);
        spillGates = new ArrayList<>(bodyCount);
        for (int i = 0; i < bodyCount; i++) {
            spillGates.add(new LinkedHashMap<>());
        }
        for (Edge edge : gateEdges) {
            addSpillGate(edge);
            addSpillGate(new Edge(edge.b(), edge.a()));
        }
        refreshBodyWater();
    }

    /** 测试/工具用：给定格、门和 body 数直接建场。 */
    public static ShipFluidField of(List<Cell> cells, List<Gate> gates, int bodyCount) {
        return new ShipFluidField(cells, gates, bodyCount, List.of(), Set.of(), Map.of(), Set.of(), Set.of());
    }

    /** 从扫描结果建场：密封舱全部格、连海空腔只取船壳内的格，再补上门格。 */
    public static ShipFluidField fromGeometry(ShipGeometry.Result geometry) {
        return fromGeometry(geometry, Set.of());
    }

    /**
     * @param knownInterior 历史确认过的舱内格。切掉外壁舱口/被炸穿后，
     *                      {@code exteriorCells()} 会穿过洞口把 geometry 的
     *                      {@code interiorCells} 清空；这时至少用历史格兜底，
     *                      不能让整间舱从场里消失。
     */
    public static ShipFluidField fromGeometry(ShipGeometry.Result geometry,
                                              Set<BlockPos> knownInterior) {
        List<Cell> cells = new ArrayList<>();
        Map<BlockPos, Integer> bodyByPos = new LinkedHashMap<>();
        List<ShipGeometry.Compartment> sealed = geometry.compartments();
        for (int i = 0; i < sealed.size(); i++) {
            for (BlockPos pos : sealed.get(i).cells()) {
                addCell(cells, bodyByPos, pos, i);
            }
        }
        int pocketOffset = sealed.size();
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        for (int j = 0; j < pockets.size(); j++) {
            Set<BlockPos> interior = pockets.get(j).interiorCells();
            // 破口让外部空气灌进船内时，exteriorCells() 会把破口舱的大部分格
            // 标成"船外"，interiorCells 可能只剩下几格。历史舱内格才是真实房间，
            // 所以只要历史里和这个空腔有交集，就优先用历史格（而不是等 interior 空）。
            Set<BlockPos> known = new HashSet<>();
            if (knownInterior != null) {
                for (BlockPos pos : pockets.get(j).cells()) {
                    if (knownInterior.contains(pos)) {
                        known.add(pos);
                    }
                }
            }
            if (!known.isEmpty()) {
                interior = known;
            } else if (interior == null || interior.isEmpty()) {
                // 没有历史格、也不是圆角船体的纯外部空腔：不建场。
                continue;
            }
            for (BlockPos pos : interior) {
                addCell(cells, bodyByPos, pos, pocketOffset + j);
            }
        }
        // 门格可能落在空腔的 exterior 部分，单独补进来；已经存在的格保持原 body。
        // 但如果这个 body 在场里根本没有舱内格（纯船外空气），不要为了门格
        // 造出一个"船外水盒"。
        Set<Integer> bodiesWithCells = new HashSet<>(bodyByPos.values());
        List<Gate> gates = new ArrayList<>();
        List<BlockPos> seaDoors = new ArrayList<>();
        Set<BlockPos> ceilingSeaDoors = new HashSet<>();
        Map<BlockPos, Direction> sideSeaDoors = new HashMap<>();
        Set<BlockPos> closedDoors = new HashSet<>(geometry.closedDoorways());
        for (ShipGeometry.DoorLink link : geometry.doorLinks()) {
            BlockPos door = link.cell();
            if (!bodyByPos.containsKey(door)) {
                if (!bodiesWithCells.contains(link.body())) {
                    continue;
                }
                addCell(cells, bodyByPos, door, link.body());
            }
            if (!link.open()) {
                closedDoors.add(door);
                continue;
            }
            if (link.other() == ShipGeometry.SEA_BODY) {
                Direction outside = link.seaSide();
                if (outside == null || bodyByPos.containsKey(door.relative(outside))) {
                    // 有破口的邻舱可能被外部搜索标记，却仍有自己的有限水量。
                    continue;
                }
                Direction inward = outside.getOpposite();
                if (!Integer.valueOf(link.body()).equals(bodyByPos.get(door.relative(inward)))) {
                    continue;
                }
                seaDoors.add(door);
                if (outside == Direction.UP) {
                    ceilingSeaDoors.add(door);
                } else if (outside.getAxis().isHorizontal()) {
                    sideSeaDoors.put(door, inward);
                }
                continue;
            }
            // 水只能沿门洞的通行轴流过：门格侧面的邻格可能属于另一侧水体
            // （L 形/绕行几何），按六邻接建边会让水从门侧边流进隔壁舱，
            // 而水盒（doorSurfaces）不认这个方向。没有轴向信息的旧几何保持原行为。
            Direction.Axis axis = geometry.doorwayAxes().get(door);
            for (Direction side : Direction.values()) {
                if (axis != null && side.getAxis() != axis) {
                    continue;
                }
                BlockPos next = door.relative(side);
                Integer other = bodyByPos.get(next);
                if (other != null && other == link.other() && other != link.body()
                        && Integer.valueOf(link.body())
                        .equals(bodyByPos.get(door.relative(side.getOpposite())))) {
                    gates.add(new Gate(door, next, true));
                }
            }
        }
        // 关着的门也要建 gate（open=false），否则两侧会按"不同 body 无门"直接断开；
        // 当前生成边时反正只收 open=true，保留 false 方便以后做门缝渗漏。
        for (ShipGeometry.DoorLink link : geometry.doorLinks()) {
            if (link.open() || link.other() == ShipGeometry.SEA_BODY) {
                continue;
            }
            BlockPos door = link.cell();
            Direction.Axis axis = geometry.doorwayAxes().get(door);
            for (Direction side : Direction.values()) {
                if (axis != null && side.getAxis() != axis) {
                    continue;
                }
                BlockPos next = door.relative(side);
                Integer other = bodyByPos.get(next);
                if (other != null && other == link.other() && other != link.body()
                        && Integer.valueOf(link.body())
                        .equals(bodyByPos.get(door.relative(side.getOpposite())))) {
                    gates.add(new Gate(door, next, false));
                }
            }
        }
        return new ShipFluidField(cells, gates, sealed.size() + pockets.size(), seaDoors,
                ceilingSeaDoors, sideSeaDoors, closedDoors, geometry.hull());
    }

    private void addSpillGate(Edge edge) {
        spillGates.get(bodyOf[edge.a()]).merge(bodyOf[edge.b()], edge,
                (first, next) -> sill(first) <= sill(next) ? first : next);
    }

    private int sill(Edge edge) {
        return Math.max(ys[edge.a()], ys[edge.b()]);
    }

    private static void addCell(List<Cell> cells, Map<BlockPos, Integer> bodyByPos,
                               BlockPos pos, int body) {
        BlockPos immutable = pos.immutable();
        if (bodyByPos.putIfAbsent(immutable, body) == null) {
            cells.add(new Cell(immutable, body));
        }
    }

    private void addEdgeIfPresent(List<Edge> normal, List<Edge> passages, Map<Long, Boolean> gateOpen,
                                  int from, int x, int y, int z) {
        Integer to = indexByKey.get(BlockPos.asLong(x, y, z));
        if (to == null) {
            return;
        }
        if (bodyOf[from] == bodyOf[to]) {
            // 竖直同 body 交给 settle()；水平同 body 才是普通压力边。
            if (ys[from] != ys[to]) {
                return;
            }
            normal.add(new Edge(from, to));
            return;
        }
        Boolean open = gateOpen.get(pairKey(from, to));
        if (Boolean.TRUE.equals(open)) {
            passages.add(new Edge(from, to));
        }
    }

    // ------------------------------------------------------------------ 推进

    /** 重力：同一列、同一 body 的水从上往下补，反复扫到不再移动。 */
    public void settle() {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int[] column : columns) {
                for (int i = column.length - 1; i > 0; i--) {
                    int lower = column[i - 1];
                    int upper = column[i];
                    if (bodyOf[lower] != bodyOf[upper] || ys[upper] != ys[lower] + 1) {
                        continue;
                    }
                    double room = MAX_CELL_WATER - water[lower];
                    if (room <= 0.0D || water[upper] <= 0.0D) {
                        continue;
                    }
                    double move = Math.min(room, water[upper]);
                    if (move <= 1.0E-9D) {
                        continue;
                    }
                    water[upper] -= move;
                    water[lower] += move;
                    moved = true;
                }
            }
        }
        refreshBodyWater();
    }

    /** 开向海的开口注水后，推进各舱水位及门洞流量。 */
    public void tick(double dt) {
        if (dt <= 0.0D) {
            streams.beginStep(dt);
            return;
        }
        for (int index : seaDoors) {
            BlockPos door = positions[index];
            double added = injectFlood(bodyOf[index], DOOR_FLOW_PER_CELL * dt,
                    DOOR_FLOW_PER_CELL * dt);
            if (added > 0.0D) {
                Direction inward = sideSeaDoors.get(door);
                if (inward != null) {
                    streams.recordSideInflow(door.relative(inward.getOpposite()), door, door.getY() + 1.0D, added);
                } else {
                    BlockPos receiver = ceilingSeaDoors.contains(door) ? door.below() : door;
                    streams.recordInflow(door, receiver, added);
                }
            }
        }
        streams.beginStep(dt);
        level();
        flowThroughGates(dt);
    }

    /** 压力平衡，不从外海注水（测试用）。 */
    public void flow(double dt) {
        streams.beginStep(dt);
        flowWithinBodies(dt);
        flowThroughGates(dt);
    }

    /**
     * 舱内水平压力平衡。
     *
     * <p>每条边先按当前水位算流量，再按"每格最大流出"统一缩放；这样一次 tick
     * 里既有双向流动，也不会把一格的水抽成负数。两轮迭代让大舱的水更快拉平。</p>
     */
    private void flowWithinBodies(double dt) {
        if (dt <= 0.0D || edges.length == 0) {
            return;
        }
        double[] edgeFlow = new double[edges.length];
        double[] outSum = new double[water.length];
        double[] inSum = new double[water.length];
        double[] inRoom = new double[water.length];
        for (int iteration = 0; iteration < FLOW_ITERATIONS; iteration++) {
            Arrays.fill(edgeFlow, 0.0D);
            Arrays.fill(outSum, 0.0D);
            Arrays.fill(inSum, 0.0D);
            for (int i = 0; i < water.length; i++) {
                inRoom[i] = Math.max(0.0D, MAX_CELL_WATER - water[i]);
            }
            for (int e = 0; e < edges.length; e++) {
                Edge edge = edges[e];
                double headA = ys[edge.a()] + water[edge.a()] / UNITS_PER_BLOCK;
                double headB = ys[edge.b()] + water[edge.b()] / UNITS_PER_BLOCK;
                double diff = headA - headB;
                int from;
                int to;
                if (diff > FLOW_DEADBAND) {
                    from = edge.a();
                    to = edge.b();
                } else if (diff < -FLOW_DEADBAND) {
                    from = edge.b();
                    to = edge.a();
                    diff = -diff;
                } else {
                    continue;
                }
                // 两个 1 格截面的格把水头差抹平只需要 diff×50 units（各让一半）；
                // 超过这个量就会来回过冲。流量系数再大也只能到这个上限。
                double amount = Math.min(NORMAL_FLOW_COEFF * diff * dt,
                        diff * MAX_CELL_WATER * 0.5D);
                amount = Math.min(amount, water[from]);
                amount = Math.min(amount, inRoom[to]);
                if (amount <= 1.0E-4D) {
                    continue;
                }
                edgeFlow[e] = from == edge.a() ? amount : -amount;
                outSum[from] += amount;
                inSum[to] += amount;
            }
            // 一格的总流出不能超过它当前的水量；总流入也不能超过它的剩余容量。
            // 多条边同时灌同一格时必须按比例一起砍，否则会灌出 200/100 的格。
            for (int e = 0; e < edges.length; e++) {
                double flow = edgeFlow[e];
                if (flow == 0.0D) {
                    continue;
                }
                Edge edge = edges[e];
                int from = flow > 0.0D ? edge.a() : edge.b();
                int to = flow > 0.0D ? edge.b() : edge.a();
                if (outSum[from] > water[from] + 1.0E-9D) {
                    flow *= water[from] / outSum[from];
                }
                if (inSum[to] > inRoom[to] + 1.0E-9D) {
                    flow *= inRoom[to] / inSum[to];
                }
                edgeFlow[e] = flow;
            }
            for (int e = 0; e < edges.length; e++) {
                double flow = edgeFlow[e];
                if (flow == 0.0D) {
                    continue;
                }
                Edge edge = edges[e];
                water[edge.a()] -= flow;
                water[edge.b()] += flow;
            }
        }
        refreshBodyWater();
    }

    /**
     * 门洞压力由两侧舱室的水头决定。同一扇门两侧的格都灌满时，水位更高的
     * 一侧仍能把水挤到接收舱的空余容积里。
     */
    private void flowThroughGates(double dt) {
        if (dt <= 0.0D) {
            return;
        }
        for (Edge edge : gateEdges) {
            int a = edge.a();
            int b = edge.b();
            int sill = sill(edge);
            int from = a;
            int to = b;
            double source = Math.max(sill, bodyLevel(bodyOf[a]));
            double target = Math.max(sill, bodyLevel(bodyOf[b]));
            if (source < target) {
                from = b;
                to = a;
            }
            double head = Math.abs(source - target);
            if (head <= FLOW_DEADBAND) {
                continue;
            }
            int donor = bodyOf[from];
            int receiver = bodyOf[to];
            double aboveSill = bodyWater[donor] - capacityBelow(donor, sill);
            double room = bodyCapacityUnits[receiver] - bodyWater[receiver];
            double amount = Math.min(DOOR_FLOW_COEFF * head * dt, DOOR_FLOW_PER_CELL * dt);
            // 每侧水面至少有一格面积，限制单次水头变化以免过冲。
            amount = Math.min(amount, head * UNITS_PER_BLOCK * 0.5D);
            amount = Math.min(amount, Math.min(aboveSill, room));
            if (amount <= 1.0E-6D) {
                continue;
            }
            double removed = removeBody(donor, amount);
            double moved = addBody(receiver, removed, Double.POSITIVE_INFINITY);
            if (removed - moved > 1.0E-6D) {
                addBody(donor, removed - moved, Double.POSITIVE_INFINITY);
            }
            if (moved > 0.0D) {
                if (ys[from] == ys[to]) {
                    streams.sideOpening(positions[from], positions[to], Math.max(source, target), moved);
                } else {
                    streams.opening(positions[from], positions[to], moved / dt);
                }
            }
        }
    }

    /** 破口紧邻的实际舱内格，与渲染数据共用同一个开口。 */
    public BlockPos breachReceiver(BlockPos breach, int body, Set<BlockPos> breachCells) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = breach.relative(direction);
            if (bodyOf(side) == body && !breachCells.contains(side)) {
                return side;
            }
        }
        BlockPos below = breach.below();
        if (bodyOf(below) == body && !breachCells.contains(below)) {
            return below;
        }
        BlockPos above = breach.above();
        return bodyOf(above) == body && !breachCells.contains(above) ? above : null;
    }

    public void recordInflow(BlockPos opening, BlockPos receiver, double amount) {
        streams.recordInflow(opening, receiver, amount);
    }

    public List<ShipWaterStreams.Segment> waterStreams() {
        return streams.segments();
    }

    public List<ShipWaterStreams.SideFlow> sideFlows() {
        return streams.sideFlows();
    }

    /**
     * 同一水体的水立刻找到水平面：从最低格往上按层均摊。
     *
     * <p>原作 {@code Hull} 整舱只有一个 {@code WaterVolume}，水面是瞬时水平的；
     * 破口进水时看到的是"整间房一起涨"。逐格场要保留每格水量（渲染、多层甲板、
     * 门洞限流），但只靠压力扩散的话，一小股水会在破口处堆成一坨、几十秒都铺不开
     * （用户实测）。所以每个 tick 在扩散之后做一次整舱归平：水体内部不再有局部
     * 水堆，跨 body 的流动仍然只走门/舱口。</p>
     */
    public void level() {
        boolean changed = false;
        for (int body = 0; body < bodyCount; body++) {
            int[] cells = bodyCellsByY[body];
            if (cells.length == 0) {
                continue;
            }
            double total = 0.0D;
            for (int idx : cells) {
                total += water[idx];
            }
            if (total <= 1.0E-9D) {
                continue;
            }
            double capacity = cells.length * MAX_CELL_WATER;
            if (total > capacity) {
                total = capacity;
            }
            for (int idx : cells) {
                water[idx] = 0.0D;
            }
            int i = 0;
            double remaining = total;
            while (i < cells.length && remaining > 1.0E-6D) {
                int y = ys[cells[i]];
                int start = i;
                while (i < cells.length && ys[cells[i]] == y) {
                    i++;
                }
                int count = i - start;
                double take = Math.min(remaining, count * MAX_CELL_WATER);
                double each = take / count;
                for (int k = start; k < i; k++) {
                    water[cells[k]] = each;
                }
                remaining -= take;
            }
            changed = true;
        }
        if (changed) {
            refreshBodyWater();
        }
    }

    // ------------------------------------------------------------------ 注/抽

    /**
     * 往某格所属的水体注水；水量按整舱水位均摊（等价于往舱里倒水，不是往格子里灌满）。
     *
     * <p>旧实现按 BFS 把相邻格逐格灌到 100，少量进水会在破口处堆出一两个满格；
     * 原作是整舱一个 {@code WaterVolume}，倒进来的水立刻摊平，所以这里直接走
     * {@link #addBody}。</p>
     *
     * <p>本舱装不下时沿开着的门/舱口往相邻水体继续顶（{@link #injectFlood}）：
     * 破口一直灌水、舱里满了以后水位必须沿开着的地板舱口继续涨，不能把水丢掉。</p>
     */
    public double inject(BlockPos pos, double units) {
        if (pos == null || units <= 0.0D) {
            return 0.0D;
        }
        Integer start = indexByKey.get(pos.asLong());
        if (start == null) {
            return 0.0D;
        }
        return injectFlood(bodyOf[start], units);
    }

    /**
     * 按"整舱水位"往 body 注水，满了就通过开着的门/舱口往相邻水体溢出。
     *
     * <p>逐格场没有"水压大于静水头"的表达：一个灌满的舱再进水，光靠
     * {@code flow} 的水头差不会往上一层舱走（两边水头相等）。但破口/海门一直在进水，
     * 水必须能从开着的舱口继续往上涌（用户实测：水平舱门开着，灌满的舱下面
     * 水位就不再涨）。这里把装不下的部分沿 open gate BFS 分给相邻水体。</p>
     */
    public double injectFlood(int body, double units) {
        return injectFlood(body, units, DOOR_FLOW_PER_CELL);
    }

    /**
     * 带“每步每道门流量预算”的溢流注入。
     *
     * <p>满舱再进水时，溢流也必须遵守开门的流量上限（和 {@link #flow} 里
     * {@code DOOR_FLOW_PER_CELL * dt} 一致），否则一个破口能让水无视舱口直接灌满
     * 隔壁舱。每道门的预算之外的部分留在原舱（表现为这次没进来那么多水）。</p>
     */
    public double injectFlood(int body, double units, double gateBudget) {
        if (body < 0 || body >= bodyCount || units <= 1.0E-9D) {
            return 0.0D;
        }
        double remaining = units;
        double added = 0.0D;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<Integer, Edge> arrival = new HashMap<>();
        // 每个水体这步最多能进多少：起点是全额，跨过一道门后受门的预算限制。
        Map<Integer, Double> allowance = new HashMap<>();
        queue.add(body);
        arrival.put(body, null);
        allowance.put(body, units);
        while (!queue.isEmpty() && remaining > 1.0E-6D) {
            int current = queue.poll();
            double allow = Math.min(allowance.getOrDefault(current, units), remaining);
            double took = addBody(current, allow, Double.POSITIVE_INFINITY);
            remaining -= took;
            added += took;
            if (took > 0.0D) {
                for (Edge edge = arrival.get(current); edge != null; edge = arrival.get(bodyOf[edge.a()])) {
                    if (ys[edge.a()] == ys[edge.b()]) {
                        streams.recordSideInflow(positions[edge.a()], positions[edge.b()],
                                bodyLevel(bodyOf[edge.a()]), took);
                    }
                }
            }
            if (remaining <= 1.0E-6D) {
                break;
            }
            // 本舱没装下的那部分才能继续往外溢，而且每过一道门都要计入门的预算。
            // 多个出口平分“能外溢的量”，不能每个出口都拿一份完整的 pass：
            // 否则 A→B 只过了一道门的预算，B 却能把同样多的水分别送进 C 和 D
            // （凭空造水）。单出口仍然能拿到完整的预算。
            double excess = allow - took;
            if (excess <= 1.0E-6D) {
                continue;
            }
            double pass = Math.min(excess, Math.max(0.0D, gateBudget));
            if (pass <= 1.0E-9D) {
                continue;
            }
            var outlets = spillGates.get(current);
            if (outlets == null || outlets.isEmpty()) {
                continue;
            }
            int open = 0;
            for (int next : outlets.keySet()) {
                if (!arrival.containsKey(next)) {
                    open++;
                }
            }
            if (open <= 0) {
                continue;
            }
            double share = pass / open;
            if (share <= 1.0E-9D) {
                continue;
            }
            for (var connection : outlets.entrySet()) {
                int next = connection.getKey();
                if (!arrival.containsKey(next)) {
                    arrival.put(next, connection.getValue());
                    allowance.put(next, share);
                    queue.add(next);
                }
            }
        }
        return added;
    }

    /** 往 body 注水，从最低水位格开始铺平；{@code maxYExclusive} 限制最高格（压载舱门限）。 */
    public double addBody(int body, double units, double maxYExclusive) {
        if (body < 0 || body >= bodyCount || units <= 0.0D) {
            return 0.0D;
        }
        int[] cells = bodyCells[body];
        if (cells.length == 0) {
            return 0.0D;
        }
        List<Integer> candidates = new ArrayList<>(cells.length);
        for (int idx : cells) {
            if (ys[idx] < maxYExclusive) {
                candidates.add(idx);
            }
        }
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(ys[a], ys[b]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        double remaining = units;
        double added = 0.0D;
        int i = 0;
        while (i < candidates.size() && remaining > 1.0E-6D) {
            int y = ys[candidates.get(i)];
            int groupStart = i;
            double groupRoom = 0.0D;
            while (i < candidates.size() && ys[candidates.get(i)] == y) {
                groupRoom += MAX_CELL_WATER - water[candidates.get(i)];
                i++;
            }
            if (groupRoom <= 1.0E-9D) {
                continue;
            }
            double take = Math.min(remaining, groupRoom);
            for (int k = groupStart; k < i; k++) {
                int idx = candidates.get(k);
                double room = MAX_CELL_WATER - water[idx];
                if (room <= 0.0D) {
                    continue;
                }
                double share = take * room / groupRoom;
                water[idx] += share;
                added += share;
            }
            remaining -= take;
        }
        refreshBodyWater();
        return added;
    }

    /** 从 body 抽水，从最高水面格开始扣，保持水面整体下降。 */
    public double removeBody(int body, double units) {
        if (body < 0 || body >= bodyCount || units <= 0.0D) {
            return 0.0D;
        }
        int[] cells = bodyCells[body];
        if (cells.length == 0) {
            return 0.0D;
        }
        List<Integer> candidates = new ArrayList<>(cells.length);
        for (int idx : cells) {
            if (water[idx] > 0.0D) {
                candidates.add(idx);
            }
        }
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(ys[b], ys[a]);
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        double remaining = units;
        double removed = 0.0D;
        int i = 0;
        while (i < candidates.size() && remaining > 1.0E-6D) {
            int y = ys[candidates.get(i)];
            int groupStart = i;
            double groupWater = 0.0D;
            while (i < candidates.size() && ys[candidates.get(i)] == y) {
                groupWater += water[candidates.get(i)];
                i++;
            }
            if (groupWater <= 1.0E-9D) {
                continue;
            }
            double take = Math.min(remaining, groupWater);
            for (int k = groupStart; k < i; k++) {
                int idx = candidates.get(k);
                double amount = water[idx];
                if (amount <= 0.0D) {
                    continue;
                }
                double share = take * amount / groupWater;
                double cut = Math.min(amount, share);
                water[idx] -= cut;
                removed += cut;
            }
            remaining -= take;
        }
        refreshBodyWater();
        return removed;
    }

    // ------------------------------------------------------------------ 查询

    public int bodyCount() {
        return bodyCount;
    }

    public int cellCount() {
        return positions.length;
    }

    /** 当前场覆盖的所有格（重建/平移时当"已知舱内格"用）。 */
    public Set<BlockPos> cellPositions() {
        return new HashSet<>(java.util.Arrays.asList(positions));
    }

    public BlockPos posAt(int index) {
        return positions[index];
    }

    public int bodyAt(int index) {
        return bodyOf[index];
    }

    public double waterAt(int index) {
        return water[index];
    }

    public double waterAt(BlockPos pos) {
        Integer idx = pos == null ? null : indexByKey.get(pos.asLong());
        return idx == null ? 0.0D : water[idx];
    }

    /** 全场水量合计（单位）；给存档标脏等粗粒度比较用。 */
    public double totalWater() {
        double sum = 0.0D;
        for (double value : water) {
            sum += value;
        }
        return sum;
    }

    /** 该格的水面高度；空格返回格底 y，场外返回 0。 */
    public double levelAt(BlockPos pos) {
        Integer idx = pos == null ? null : indexByKey.get(pos.asLong());
        if (idx == null) {
            return 0.0D;
        }
        return ys[idx] + water[idx] / UNITS_PER_BLOCK;
    }

    public int bodyOf(BlockPos pos) {
        Integer idx = pos == null ? null : indexByKey.get(pos.asLong());
        return idx == null ? -1 : bodyOf[idx];
    }

    public double bodyWater(int body) {
        return body >= 0 && body < bodyWater.length ? bodyWater[body] : 0.0D;
    }

    public double bodyCapacity(int body) {
        return body >= 0 && body < bodyCapacityUnits.length ? bodyCapacityUnits[body] : 0.0D;
    }

    /** 水位到 {@code maxYExclusive} 时该 body 最多能装多少 units（按整格容量）。 */
    public double capacityBelow(int body, double maxYExclusive) {
        if (body < 0 || body >= bodyCount) {
            return 0.0D;
        }
        int cells = 0;
        for (int idx : bodyCells[body]) {
            if (ys[idx] < maxYExclusive) {
                cells++;
            }
        }
        return cells * UNITS_PER_BLOCK;
    }

    /** body 的水面：最高一格的 y + 水量比例；干 body 返回最低格 y。 */
    public double bodyLevel(int body) {
        if (body < 0 || body >= bodyCount || bodyCells[body].length == 0) {
            return 0.0D;
        }
        double level = Double.NEGATIVE_INFINITY;
        int minY = Integer.MAX_VALUE;
        for (int idx : bodyCells[body]) {
            minY = Math.min(minY, ys[idx]);
            if (water[idx] > 0.0D) {
                level = Math.max(level, ys[idx] + water[idx] / UNITS_PER_BLOCK);
            }
        }
        return level == Double.NEGATIVE_INFINITY ? minY : level;
    }

    public boolean bodyIsWet(int body) {
        return bodyWater(body) >= 5.0D;
    }

    public double bodyWaterFraction(int body) {
        double capacity = bodyCapacity(body);
        return capacity <= 1.0E-9D ? 0.0D : clamp01(bodyWater(body) / capacity);
    }

    /** 当前每个有水格的 units（按位置）。 */
    public Map<BlockPos, Double> toMap() {
        Map<BlockPos, Double> out = new HashMap<>();
        for (int i = 0; i < water.length; i++) {
            if (water[i] > 1.0E-6D) {
                out.put(positions[i], water[i]);
            }
        }
        return out;
    }

    /** 用"位置 → units"覆盖当前场；场外/负值忽略，超过一格容量的夹紧。 */
    public void seed(Map<BlockPos, Double> byCell) {
        Arrays.fill(water, 0.0D);
        if (byCell != null) {
            for (Map.Entry<BlockPos, Double> entry : byCell.entrySet()) {
                Integer idx = indexByKey.get(entry.getKey().asLong());
                if (idx == null || entry.getValue() == null || entry.getValue() <= 0.0D) {
                    continue;
                }
                water[idx] = Math.min(MAX_CELL_WATER, entry.getValue());
            }
        }
        refreshBodyWater();
    }

    /** body 的格位置，按 y、x、z 排序；渲染/测试用。 */
    public List<BlockPos> cellsOfBody(int body) {
        if (body < 0 || body >= bodyCount) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>(bodyCells[body].length);
        for (int idx : bodyCells[body]) {
            out.add(positions[idx]);
        }
        out.sort((a, b) -> {
            int cmp = Integer.compare(a.getY(), b.getY());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(a.getX(), b.getX());
            return cmp != 0 ? cmp : Integer.compare(a.getZ(), b.getZ());
        });
        return out;
    }

    /** 开向外海的门格（服务端注入用）。 */
    public int[] seaDoorCells() {
        return seaDoors.clone();
    }

    private void refreshBodyWater() {
        Arrays.fill(bodyWater, 0.0D);
        for (int i = 0; i < water.length; i++) {
            int body = bodyOf[i];
            if (body >= 0 && body < bodyWater.length) {
                bodyWater[body] += water[i];
            }
        }
    }

    private boolean areNeighbours(int a, int b) {
        int dx = Math.abs(xs[a] - xs[b]);
        int dy = Math.abs(ys[a] - ys[b]);
        int dz = Math.abs(zs[a] - zs[b]);
        return dx + dy + dz == 1;
    }

    private static long pairKey(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
