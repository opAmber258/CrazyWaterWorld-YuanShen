package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 船体几何：先认出"船"，再把船内部的空腔划分成舱室。
 *
 * <p>两步：</p>
 * <ol>
 *   <li><b>找船</b>：在核心周围半径内收集所有船体方块（装甲板/舷窗/舱门/机器），
 *       做连通分量，取最大的那块作为这艘船，得到它的包围盒；</li>
 *   <li><b>分舱</b>：只在包围盒内对非固体格子做 BFS。碰到包围盒边界 = 连到外海（破洞或没封口），
 *       不算舱室；否则就是一个封闭舱室（门关着自然就被隔开）。</li>
 * </ol>
 *
 * <p>纯逻辑，不依赖 Level，方便以后单测。</p>
 */
public final class ShipGeometry {

    /** 世界探针：由调用方提供方块/水判定。 */
    public interface Probe {
        boolean isSolid(BlockPos pos);

        boolean isWater(BlockPos pos);

        /**
         * 门/舱口格：算舱内（能站人、算干燥区），但<b>水不能穿过</b>。
         *
         * <p>门板只有 1 px 厚，可它按格子算体积；关着门时这一格是水密隔断，
         * 但玩家贴着门站在这一格里不应该被当成"舱外"。所以洪水填充会走进这一格、
         * 把它算进某一侧的舱室，却不会从这一格继续往外扩。</p>
         */
        default boolean isDoorway(BlockPos pos) {
            return false;
        }

        /** 开着的门：两侧不合并舱室，只按限速流量互相补水（见 DoorLink）。 */
        default boolean isOpenDoorway(BlockPos pos) {
            return false;
        }

        /** 舱门沿门面法线通海，舱口和地板格栅沿竖直方向通海。 */
        default @Nullable Direction.Axis doorwayAxis(BlockPos pos) {
            return null;
        }
    }

    /**
     * 开着的门带来的两侧水连通。
     *
     * <p>{@code body} / {@code other} 是"水体下标"：先密闭舱室（0..n-1），再连海空腔
     * （n..n+m-1），和 {@link ShipSimulation} 的数组下标一致。门格本身属于
     * {@code body}，{@code other} 是门另一侧相邻的水体。通海连线记录门外的方向。</p>
     */
    public record DoorLink(BlockPos cell, int body, int other, boolean open, @Nullable Direction seaSide) {
        public DoorLink(BlockPos cell, int body, int other, boolean open) {
            this(cell, body, other, open, null);
        }
    }

    /**
     * "外海"作为门连线的另一侧：船壳外的格可能在整船包围盒内。
     * 船体模拟把这一侧当成无限大的水源，开门就往舱里灌。
     */
    public static final int SEA_BODY = -1;

    /** 一个封闭舱室。 */
    public record Compartment(Set<BlockPos> cells, int waterBlocks, Set<BlockPos> interiorCells) {
        /** 默认封闭舱室：整间都在船壳以内。 */
        public Compartment(Set<BlockPos> cells, int waterBlocks) {
            this(cells, waterBlocks, cells);
        }

        public int volume() {
            return cells.size();
        }

        /**
         * 落在船壳以内的格数。
         *
         * <p>连海空腔会把"包围盒里但在船壳外"的海水也算进同一片空腔（圆角船体
         * 的包围盒四角就是外海）。进水比例和水面渲染只能按船壳内的那部分算，
         * 否则一艘内部全干的船会被算成 60% 进水，物理上直接沉底。</p>
         */
        public int interiorVolume() {
            return interiorCells.size();
        }

        public double waterRatio() {
            return volume() == 0 ? 0.0D : waterBlocks / (double) volume();
        }
    }

    /** 包围盒。 */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public Bounds shifted(int dx, int dy, int dz) {
            return new Bounds(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
        }
    }

    /**
     * 扫描结果。{@code openPockets} 是连到外海的空腔。
     *
     * @param hull      选中的船壳连通块
     * @param equipment 船壳包围盒内的独立设备格（接线盒/小型泵等）：它们不参与船壳
     *                  选择，但仍属于这艘船，见 {@link #owns(BlockPos)}
     */
    public record Result(List<Compartment> compartments, List<Compartment> openPockets,
                         int hullBlocks, int scannedCells, Bounds bounds, Set<BlockPos> hull,
                         List<DoorLink> doorLinks, Set<BlockPos> equipment,
                         Map<BlockPos, Direction.Axis> doorwayAxes,
                         Set<BlockPos> closedDoorways) {
        public Result(List<Compartment> compartments, List<Compartment> openPockets,
                      int hullBlocks, int scannedCells, Bounds bounds, Set<BlockPos> hull,
                      List<DoorLink> doorLinks, Set<BlockPos> equipment) {
            this(compartments, openPockets, hullBlocks, scannedCells, bounds, hull, doorLinks,
                    equipment, Map.of(), Set.of());
        }

        public Result(List<Compartment> compartments, List<Compartment> openPockets,
                      int hullBlocks, int scannedCells, Bounds bounds, Set<BlockPos> hull,
                      List<DoorLink> doorLinks, Set<BlockPos> equipment,
                      Map<BlockPos, Direction.Axis> doorwayAxes) {
            this(compartments, openPockets, hullBlocks, scannedCells, bounds, hull, doorLinks,
                    equipment, doorwayAxes, Set.of());
        }

        public int totalVolume() {
            return compartments.stream().mapToInt(Compartment::volume).sum();
        }

        public boolean sealed() {
            // 包围盒四角/桅杆外的空气会是 interiorVolume=0 的“连海空腔”，
            // 它们不代表船体破口，不能把整艘船判成不水密。
            return openPockets.stream().noneMatch(pocket -> pocket.interiorVolume() > 0);
        }

        /**
         * 这一格是否属于这艘船：船壳，或船内的独立设备。
         *
         * <p>{@code tracksHullBlock()} 用它：独立接线盒/小型泵不和船壳连通，
         * 不能因此被判成"不是我的船"，否则接线盒不上报、绑定设备断电。</p>
         */
        public boolean owns(BlockPos pos) {
            return hull.contains(pos) || equipment.contains(pos);
        }

        /**
         * 整艘船平移后的几何。
         *
         * <p>船是刚体：挪一格之后舱室划分、容积、破口分布全都不变，只是坐标变了。
         * 所以推进时不需要重新扫描（一次重扫要 13 万次方块查询），直接平移即可，
         * 顺带也让温度 / 水位这些模拟状态不会因为"换了艘新船"被重置。</p>
         */
        public Result shifted(int dx, int dy, int dz) {
            Set<BlockPos> movedHull = new HashSet<>(Math.max(16, hull.size() * 2));
            for (BlockPos pos : hull) {
                movedHull.add(pos.offset(dx, dy, dz));
            }
            Set<BlockPos> movedEquipment = new HashSet<>(Math.max(4, equipment.size() * 2));
            for (BlockPos pos : equipment) {
                movedEquipment.add(pos.offset(dx, dy, dz));
            }
            List<DoorLink> movedDoors = new ArrayList<>(doorLinks.size());
            for (DoorLink link : doorLinks) {
                movedDoors.add(new DoorLink(link.cell().offset(dx, dy, dz),
                        link.body(), link.other(), link.open(), link.seaSide()));
            }
            Map<BlockPos, Direction.Axis> movedAxes = new HashMap<>();
            doorwayAxes.forEach((cell, axis) -> movedAxes.put(cell.offset(dx, dy, dz), axis));
            Set<BlockPos> movedClosed = new HashSet<>(Math.max(4, closedDoorways.size() * 2));
            for (BlockPos pos : closedDoorways) {
                movedClosed.add(pos.offset(dx, dy, dz));
            }
            return new Result(shift(compartments, dx, dy, dz), shift(openPockets, dx, dy, dz),
                    hullBlocks, scannedCells, bounds.shifted(dx, dy, dz), movedHull, movedDoors,
                    movedEquipment, Map.copyOf(movedAxes), Set.copyOf(movedClosed));
        }

        private static List<Compartment> shift(List<Compartment> source, int dx, int dy, int dz) {
            List<Compartment> out = new ArrayList<>(source.size());
            for (Compartment compartment : source) {
                Set<BlockPos> cells = new HashSet<>(Math.max(16, compartment.cells().size() * 2));
                for (BlockPos pos : compartment.cells()) {
                    cells.add(pos.offset(dx, dy, dz));
                }
                Set<BlockPos> interior = compartment.interiorCells() == compartment.cells()
                        ? cells : new HashSet<>(compartment.interiorVolume());
                if (interior != cells) {
                    for (BlockPos pos : compartment.interiorCells()) {
                        interior.add(pos.offset(dx, dy, dz));
                    }
                }
                out.add(new Compartment(cells, compartment.waterBlocks(), interior));
            }
            return out;
        }
    }

    /** 单次分舱 BFS 的格子上限。 */
    private static final int MAX_CELLS_PER_FILL = 30_000;

    /**
     * 显式扫描范围的单轴 / 总格数上限。
     *
     * <p>{@code collectHullWithEquipment}、分舱和 exteriorCells 都会逐格遍历整个
     * 包围盒；损坏的 NBT（比如 min=Integer.MIN_VALUE / max=Integer.MAX_VALUE）会让
     * {@code for (int x = min; x <= max; x++)} 溢出成死循环。所有外部传入的 bounds
     * 都必须先过 {@link #validBounds(Bounds)} / {@link #validScanArea(Bounds, BlockPos)}。</p>
     */
    public static final int MAX_SCAN_BOUNDS_SPAN = 512;
    public static final long MAX_SCAN_BOUNDS_CELLS = 16_000_000L;

    private ShipGeometry() {
    }

    /** 包围盒是否合法且有界：先挡住 min>max 和溢出，再限制逐格循环的规模。 */
    public static boolean validBounds(@Nullable Bounds bounds) {
        if (bounds == null) {
            return false;
        }
        long sx = (long) bounds.maxX() - bounds.minX() + 1L;
        long sy = (long) bounds.maxY() - bounds.minY() + 1L;
        long sz = (long) bounds.maxZ() - bounds.minZ() + 1L;
        if (sx < 1 || sy < 1 || sz < 1
                || sx > MAX_SCAN_BOUNDS_SPAN || sy > MAX_SCAN_BOUNDS_SPAN
                || sz > MAX_SCAN_BOUNDS_SPAN) {
            return false;
        }
        return sx * sy * sz <= MAX_SCAN_BOUNDS_CELLS;
    }

    /**
     * bounds 与核心取并集后是否仍有界。
     *
     * <p>船被搬走 / 跨维度还原后，旧的绝对坐标 bounds 和新核心可能相隔一百万格；
     * 即使 bounds 本身合法，取并集后也会变成一张上百万格跨度的扫描区。这里把它
     * 当成陈旧数据拒绝掉，调用方应回退到核心 ±radius。</p>
     */
    public static boolean validScanArea(@Nullable Bounds bounds, BlockPos core) {
        if (!validBounds(bounds)) {
            return false;
        }
        return validBounds(new Bounds(
                Math.min(bounds.minX(), core.getX()), Math.min(bounds.minY(), core.getY()),
                Math.min(bounds.minZ(), core.getZ()),
                Math.max(bounds.maxX(), core.getX()), Math.max(bounds.maxY(), core.getY()),
                Math.max(bounds.maxZ(), core.getZ())));
    }

    /** 空结果（扫描范围非法 / 没扫到船壳时用），避免把巨型 bounds 交给逐格循环。 */
    private static Result emptyAt(BlockPos core) {
        return new Result(List.of(), List.of(), 0, 0,
                new Bounds(core.getX(), core.getY(), core.getZ(),
                        core.getX(), core.getY(), core.getZ()),
                Set.of(), List.of(), Set.of());
    }

    /**
     * @param core        船核心位置
     * @param radius      收集船体方块的半径
     * @param isShipBlock 判定某格是不是船体方块
     * @param probe       实心/水判定
     */
    public static Result scanShip(BlockPos core, int radius, Predicate<BlockPos> isShipBlock, Probe probe) {
        return scanShip(core, radius, isShipBlock, probe, 0, null);
    }

    /**
     * @param padUp 包围盒向上额外扩几格。对接时用 1，把潜艇头顶的站侧舱口/平台
     *              包进扫描，配合 Probe 的虚拟密封盖防止“拆艇顶舱口就进水”。
     */
    public static Result scanShip(BlockPos core, int radius, Predicate<BlockPos> isShipBlock,
                                  Probe probe, int padUp) {
        return scanShip(core, radius, isShipBlock, probe, padUp, null);
    }

    /**
     * @param isShell 船壳格判定（船壳板/舷窗/门/舱口，破口也算），用来区分
     *                "船壳包住的舱内空气"和"包围盒四角的外海空气"。传 null 表示
     *                不做区分（旧行为，整片连海空腔都算船内进水）。
     */
    public static Result scanShip(BlockPos core, int radius, Predicate<BlockPos> isShipBlock,
                                  Probe probe, int padUp, @Nullable Predicate<BlockPos> isShell) {
        return scanShip(core, radius, isShipBlock, probe, padUp, isShell, pos -> false);
    }

    /**
     * @param isEquipment 纯设备格：单独成块时不参与"最近船体"选择（接线盒等），
     *                    但落在船壳包围盒内时仍记进 {@link Result#equipment()}。
     */
    public static Result scanShip(BlockPos core, int radius, Predicate<BlockPos> isShipBlock,
                                  Probe probe, int padUp, @Nullable Predicate<BlockPos> isShell,
                                  Predicate<BlockPos> isEquipment) {
        return scanShip(new Bounds(core.getX() - radius, core.getY() - radius, core.getZ() - radius,
                        core.getX() + radius, core.getY() + radius, core.getZ() + radius),
                core, isShipBlock, probe, padUp, isShell, isEquipment);
    }

    /**
     * 显式 bounds 版本：船厂/加载蓝图时船体可以超出核心 ±24。
     *
     * <p>bounds 会先与核心位置取并集，防止核心被放在边界外导致 hull 为空。</p>
     */
    public static Result scanShip(Bounds scanBounds, BlockPos core, Predicate<BlockPos> isShipBlock,
                                  Probe probe, int padUp, @Nullable Predicate<BlockPos> isShell,
                                  Predicate<BlockPos> isEquipment) {
        // 损坏 NBT / 船搬走后留下的旧绝对坐标都在这里挡掉：绝不能把上百万格的
        // 包围盒交给下面三段逐格循环（freeze / OOM）。
        if (!validScanArea(scanBounds, core)) {
            return emptyAt(core);
        }
        Bounds boundsWithCore = new Bounds(
                Math.min(scanBounds.minX(), core.getX()), Math.min(scanBounds.minY(), core.getY()),
                Math.min(scanBounds.minZ(), core.getZ()),
                Math.max(scanBounds.maxX(), core.getX()), Math.max(scanBounds.maxY(), core.getY()),
                Math.max(scanBounds.maxZ(), core.getZ()));
        HullPick pick = collectHullWithEquipment(core, boundsWithCore, isShipBlock, isEquipment);
        Set<BlockPos> hull = pick.hull();
        if (hull.isEmpty()) {
            return emptyAt(core);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : hull) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (padUp > 0) {
            maxY += padUp;
        }

        // ---- 3) 包围盒内分舱 ----
        List<Compartment> compartments = new ArrayList<>();
        List<Compartment> openPockets = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> doorways = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scanned = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockPos pos = cursor.immutable();
                    if (visited.contains(pos) || (probe.isSolid(pos) && !probe.isDoorway(pos))) {
                        continue;
                    }
                    Flood flood = flood(pos, minX, minY, minZ, maxX, maxY, maxZ, probe, visited, doorways);
                    if (flood == null) {
                        continue;
                    }
                    scanned += flood.cells.size();
                    Compartment compartment = new Compartment(flood.cells, flood.waterBlocks);
                    if (flood.touchesBoundary) {
                        openPockets.add(compartment);
                    } else {
                        compartments.add(compartment);
                    }
                }
            }
        }
        // 门格优先归"密闭舱室"：贴着门站的那一侧得算干燥区，不能归到进水空腔里。
        preferSealedDoorOwners(compartments, openPockets, doorways);
        // 船壳包住的空气才算船内：圆角船体的包围盒四角是外海，连海空腔会把
        // 那些海水一起吞进去。先用"船壳当墙"从包围盒边界灌一遍标出船外空气，
        // 再从每个连海空腔里扣掉这部分。
        Set<BlockPos> exterior = isShell == null
                ? Set.of() : exteriorCells(minX, minY, minZ, maxX, maxY, maxZ, isShell);
        if (isShell != null) {
            List<Compartment> clipped = new ArrayList<>(openPockets.size());
            for (Compartment pocket : openPockets) {
                Set<BlockPos> interior = new HashSet<>();
                for (BlockPos cell : pocket.cells()) {
                    if (!exterior.contains(cell)) {
                        interior.add(cell);
                    }
                }
                clipped.add(new Compartment(pocket.cells(), pocket.waterBlocks(), interior));
            }
            openPockets = clipped;
        }
        Bounds bounds = new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        List<DoorLink> doorLinks = collectDoorLinks(doorways, compartments, openPockets, probe, bounds, exterior);
        Map<BlockPos, Direction.Axis> axes = new HashMap<>();
        Set<BlockPos> closedDoorways = new HashSet<>();
        for (BlockPos cell : doorways) {
            Direction.Axis axis = probe.doorwayAxis(cell);
            if (axis != null) {
                axes.put(cell, axis);
            }
            // 关闭的门/舱口全部记下来（不只跨水体的 DoorLink）：同一水体里经楼梯
            // 连通的关闭舱口，落水路线不能穿过去。
            if (!probe.isOpenDoorway(cell)) {
                closedDoorways.add(cell);
            }
        }
        return new Result(compartments, openPockets, hull.size(), scanned, bounds, hull, doorLinks,
                pick.equipment(), Map.copyOf(axes), Set.copyOf(closedDoorways));
    }

    /** 把被连海空腔占走的门格挪给相邻的密闭舱室（先按邻居找，找不到就留着）。 */
    private static void preferSealedDoorOwners(List<Compartment> compartments,
                                               List<Compartment> pockets, Set<BlockPos> doorways) {
        if (doorways.isEmpty() || compartments.isEmpty() || pockets.isEmpty()) {
            return;
        }
        Map<BlockPos, Compartment> sealedOf = new HashMap<>();
        for (Compartment compartment : compartments) {
            for (BlockPos cell : compartment.cells()) {
                sealedOf.putIfAbsent(cell, compartment);
            }
        }
        for (BlockPos door : doorways) {
            Compartment owner = null;
            for (Compartment pocket : pockets) {
                if (pocket.cells().contains(door)) {
                    owner = pocket;
                    break;
                }
            }
            if (owner == null) {
                continue;
            }
            Compartment sealed = null;
            for (BlockPos next : neighbours(door)) {
                Compartment candidate = sealedOf.get(next);
                if (candidate != null) {
                    sealed = candidate;
                    break;
                }
            }
            if (sealed != null) {
                owner.cells().remove(door);
                sealed.cells().add(door);
                sealedOf.put(door, sealed);
            }
        }
    }

    /** 门格两侧的水体连线；关着的门也记下来（open=false），开合只改这一位。 */
    private static List<DoorLink> collectDoorLinks(Set<BlockPos> doorways,
                                                   List<Compartment> compartments,
                                                   List<Compartment> pockets, Probe probe,
                                                   Bounds bounds, Set<BlockPos> exterior) {
        if (doorways.isEmpty()) {
            return List.of();
        }
        Map<BlockPos, Integer> bodyOf = new HashMap<>();
        for (int i = 0; i < compartments.size(); i++) {
            for (BlockPos cell : compartments.get(i).cells()) {
                bodyOf.putIfAbsent(cell, i);
            }
        }
        int pocketOffset = compartments.size();
        for (int j = 0; j < pockets.size(); j++) {
            for (BlockPos cell : pockets.get(j).cells()) {
                bodyOf.putIfAbsent(cell, pocketOffset + j);
            }
        }
        List<DoorLink> links = new ArrayList<>();
        for (BlockPos door : doorways) {
            Integer owner = bodyOf.get(door);
            if (owner == null) {
                continue;
            }
            // 一个门格可能同时贴着两个水体（三个格子宽的舱口下方是两个舱），都要连。
            Set<Integer> others = new java.util.LinkedHashSet<>();
            boolean open = probe.isOpenDoorway(door);
            Direction.Axis axis = probe.doorwayAxis(door);
            for (Direction side : Direction.values()) {
                BlockPos next = door.relative(side);
                if ((axis == null || axis == side.getAxis())
                        && (!bounds.contains(next) || exterior.contains(next)) && !probe.isSolid(next)) {
                    // 修剪过的船壳也能在整船包围盒以内直接接到外海。
                    links.add(new DoorLink(door, owner, SEA_BODY, open, side));
                }
                Integer other = bodyOf.get(next);
                if (other != null && !other.equals(owner)) {
                    others.add(other);
                }
            }
            for (Integer other : others) {
                links.add(new DoorLink(door, owner, other, open));
            }
        }
        return links;
    }

    /**
     * 收集船体方块：半径内所有船体方块里，**离核心最近**的那个连通块（不是最大的）。
     *
     * <p>核心自身那一格要被排除。声纳台从这一版起兼任船核心，它同时也是
     * {@code isShipBlock}（这样装船时才会被收进刚体），于是会出现一个很坑的情况：
     * 核心自己构成一个"距离 0"的连通块，被选中，整艘船反而没被识别——装船只收到 1 格。
     * 排除核心自身之后，选中的才是外面那层真正的船壳。</p>
     */
    public static Set<BlockPos> collectHull(BlockPos core, int radius, Predicate<BlockPos> isShipBlock) {
        return collectHull(core, radius, isShipBlock, pos -> false);
    }

    /**
     * @param isEquipment 纯设备格（接线盒等）：自己单独成块时不能抢走"最近船体"的身份；
     *                    接在船体上时仍然算船体的一部分。
     */
    public static Set<BlockPos> collectHull(BlockPos core, int radius,
                                            Predicate<BlockPos> isShipBlock,
                                            Predicate<BlockPos> isEquipment) {
        return collectHullWithEquipment(core, new Bounds(
                core.getX() - radius, core.getY() - radius, core.getZ() - radius,
                core.getX() + radius, core.getY() + radius, core.getZ() + radius),
                isShipBlock, isEquipment).hull();
    }

    /** 船壳连通块 + 船内独立设备格（见 {@link Result#owns(BlockPos)}）。 */
    private record HullPick(Set<BlockPos> hull, Set<BlockPos> equipment) {
    }

    /**
     * 选船壳连通块，并把"船内的独立设备"单独收出来。
     *
     * <p>设备块（接线盒、小型泵）单独成块时不参与船壳选择，否则离核心更近的
     * 设备会抢走整艘船的身份；但它们仍然属于这艘船，所以船壳选完之后，把
     * 落在船壳包围盒内的设备连通块记进 {@code equipment}。只认包围盒内是为了
     * 排除 24 格扫描半径扫到的站上设备。</p>
     */
    private static HullPick collectHullWithEquipment(BlockPos core, Bounds scanBounds,
                                                     Predicate<BlockPos> isShipBlock,
                                                     Predicate<BlockPos> isEquipment) {
        Set<BlockPos> shipBlocks = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = scanBounds.minX(); x <= scanBounds.maxX(); x++) {
            for (int y = scanBounds.minY(); y <= scanBounds.maxY(); y++) {
                for (int z = scanBounds.minZ(); z <= scanBounds.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockPos pos = cursor.immutable();
                    if (pos.equals(core)) {
                        continue;
                    }
                    if (isShipBlock.test(pos)) {
                        shipBlocks.add(pos);
                    }
                }
            }
        }
        if (shipBlocks.isEmpty()) {
            return new HullPick(Set.of(), Set.of());
        }
        Set<BlockPos> best = Set.of();
        double bestDistance = Double.MAX_VALUE;
        // 全是设备的最小连通块：正常船体一个都没有时才退回它。
        Set<BlockPos> equipmentOnly = Set.of();
        double equipmentDistance = Double.MAX_VALUE;
        List<Set<BlockPos>> equipmentComponents = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : shipBlocks) {
            if (!seen.add(start)) {
                continue;
            }
            Set<BlockPos> component = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            double nearest = Double.MAX_VALUE;
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                component.add(pos);
                nearest = Math.min(nearest, pos.distSqr(core));
                for (BlockPos next : neighbours(pos)) {
                    if (shipBlocks.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            boolean onlyEquipment = true;
            for (BlockPos pos : component) {
                if (!isEquipment.test(pos)) {
                    onlyEquipment = false;
                    break;
                }
            }
            if (onlyEquipment) {
                equipmentComponents.add(component);
                if (nearest < equipmentDistance) {
                    equipmentDistance = nearest;
                    equipmentOnly = component;
                }
                continue;
            }
            if (nearest < bestDistance) {
                bestDistance = nearest;
                best = component;
            }
        }
        if (best.isEmpty()) {
            // 只有设备、没有船壳：保持旧行为，把最近的设备块当成这艘船。
            return new HullPick(equipmentOnly, Set.of());
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : best) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        Set<BlockPos> equipment = new HashSet<>();
        for (Set<BlockPos> component : equipmentComponents) {
            for (BlockPos pos : component) {
                if (pos.getX() >= minX && pos.getX() <= maxX
                        && pos.getY() >= minY && pos.getY() <= maxY
                        && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                    equipment.add(pos);
                }
            }
        }
        return new HullPick(best, equipment);
    }

    private static Set<BlockPos> largestComponent(Set<BlockPos> blocks) {
        Set<BlockPos> best = Set.of();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos start : blocks) {
            if (!seen.add(start)) {
                continue;
            }
            Set<BlockPos> component = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                component.add(pos);
                for (BlockPos next : neighbours(pos)) {
                    if (blocks.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (component.size() > best.size()) {
                best = component;
            }
        }
        return best;
    }

    private record Flood(Set<BlockPos> cells, int waterBlocks, boolean touchesBoundary) {
    }

    private static Flood flood(BlockPos start, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                               Probe probe, Set<BlockPos> visited, Set<BlockPos> doorways) {
        Set<BlockPos> cells = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        boolean touches = false;
        int water = 0;

        while (!queue.isEmpty() && cells.size() < MAX_CELLS_PER_FILL) {
            BlockPos pos = queue.poll();
            cells.add(pos);
            if (probe.isWater(pos)) {
                water++;
            }
            // 门格本身是水密的：门板就装在外壳上时，不能因为它贴着包围盒边界
            // 就把整个舱判成"连海"（那样关门也会进水）。
            if (!probe.isDoorway(pos)
                    && (pos.getX() == minX || pos.getX() == maxX
                    || pos.getY() == minY || pos.getY() == maxY
                    || pos.getZ() == minZ || pos.getZ() == maxZ)) {
                touches = true;
            }
            // 门格算舱内，但水/空气都不能从这里继续扩：关着是一条水密隔断，
            // 开着也只按 DoorLink 的限速流量过水，不把两个舱并成一个。
            if (probe.isDoorway(pos)) {
                doorways.add(pos.immutable());
                continue;
            }
            for (BlockPos next : neighbours(pos)) {
                if (next.getX() < minX || next.getX() > maxX
                        || next.getY() < minY || next.getY() > maxY
                        || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                if (visited.contains(next)) {
                    continue;
                }
                if (probe.isSolid(next) && !probe.isDoorway(next)) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
        return new Flood(cells, water, touches);
    }

    /**
     * 从包围盒边界灌一遍，标出船壳**外面**的空气（含圆角船体包围盒四角的空隙）。
     *
     * <p>{@code isShell} 把船壳板/门/舱口（破口也算）当墙：这样有破洞的舱室
     * 仍然留在"船内"这一侧，进水才会算进进水比例；而包围盒四角那种直接连着
     * 外海的空气会被标成船外。</p>
     */
    private static Set<BlockPos> exteriorCells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                               Predicate<BlockPos> isShell) {
        Set<BlockPos> exterior = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // 种子：包围盒六个面上的空气格
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onBoundary = x == minX || x == maxX
                            || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (!onBoundary) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    BlockPos pos = cursor.immutable();
                    if (isShell.test(pos)) {
                        continue;
                    }
                    if (exterior.add(pos)) {
                        queue.add(pos);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (BlockPos next : neighbours(pos)) {
                if (next.getX() < minX || next.getX() > maxX
                        || next.getY() < minY || next.getY() > maxY
                        || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                if (isShell.test(next)) {
                    continue;
                }
                if (exterior.add(next)) {
                    queue.add(next);
                }
            }
        }
        return exterior;
    }

    private static BlockPos[] neighbours(BlockPos pos) {
        return new BlockPos[]{
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()
        };
    }
}
