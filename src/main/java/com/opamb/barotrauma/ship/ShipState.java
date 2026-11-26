package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.network.ShipWaterStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一艘船的运行时状态：几何（舱室）+ 机器统计 + 数值模拟。
 */
public final class ShipState {

    private ShipGeometry.Result geometry;
    private final MachineCount machines;
    /** 一个破口：位置 + 朝船体内部（密封舱室质心）的水平单位方向。 */
    public record Breach(BlockPos pos, float dirX, float dirZ) {
    }

    /**
     * 破口（装配/世界坐标）。只记录真正连到外海的洞，客户端在洞口画水流/气泡；
     * 内部隔墙被砸出的洞不算，避免在舱室中间凭空冒泡。
     */
    private final Map<BlockPos, Breach> breaches = new LinkedHashMap<>();
    private ShipSimulation simulation;
    private ShipWater water;
    /** 逐方块船壳耐久（剩余比例）。重扫/平移/卸船都要跟着走。 */
    private final HullDamage hullDamage = new HullDamage();
    /**
     * 船体加固倍率：原版 {@code increasewallhealth} 每级 +20%。
     * T1 最多 2 级、T2 4 级、T3 6 级；现在是预留接口，默认 1.0。
     */
    private float hullHealthMultiplier = 1.0F;
    /** 引擎升级推力倍率（1.0 基准，每级 +0.2，上限 3 级）。 */
    private float engineMultiplier = 1.0F;

    /** 10% 伤害开始的小漏：2.1 格³/秒/处（约等于完整破口 35% 的流量）。 */
    private static final double SMALL_LEAK_UNITS_PER_SECOND = 210.0D;
    /** 70% 伤害开始的大漏：4.5 格³/秒/处（约等于完整破口 75% 的流量）。 */
    private static final double BIG_LEAK_UNITS_PER_SECOND = 450.0D;
    /** 漏水的空间缓存：舱室格 → 舱室下标；以及所有“船内空气格”（含连海空腔）。 */
    private Map<BlockPos, Integer> leakCompartmentByCell;
    private Set<BlockPos> leakInsideCells;

    /**
     * 水面包缓存（{@code shipId=-1}、方块船形态）。
     *
     * <p>干燥区判定每 tick 每个玩家都要问一次“我在船舱里吗”，原来每次都重新
     * {@link ShipWaterStateBuilder#build}——里面要跑贪心矩形分解和连海空腔的 64 轮松弛，
     * 属于纯重复劳动。这里按游戏刻缓存，同一 tick 内无论多少个玩家都只算一次。</p>
     */
    private ShipWaterStatePacket waterBoxCache;
    private long waterBoxCacheTick = Long.MIN_VALUE;

    private int throttleX;
    private int throttleY;
    private int throttleZ;
    /**
     * 物理船手动舵令：+1 前进 / −1 后退 / 0 停车。
     *
     * <p>原作引擎只沿船头 X 轴出力（{@code Engine.Update} 里的
     * {@code Submarine.ApplyForce(new Vector2(num, 0))}），没有横移。VS2 物理船
     * 用这一组舵令；方块船的 {@link #throttleX}/{@link #throttleZ} 仍是世界方向，
     * 两条路径互不影响。</p>
     */
    private int helmForward;
    /**
     * 物理船偏航舵令：+1 左转 / −1 右转 / 0 保持。
     *
     * <p>原作是 2D 侧视，刚体 {@code FixedRotation=true}，根本没有偏航。Minecraft
     * 是 3D，必须留一个转向轴才能开到任意目标，所以 A/D 映射成绕世界 +Y 的偏航
     * （+1 = 正方向，船头 +X 转向 −Z）。</p>
     */
    private int helmYaw;
    /**
     * 偏航舵令的幅度（0..1）：手动 A/D = 1（满舵 15°/s），自动驾驶按航向误差
     * 成比例给，避免满舵在目标航向上左右横跳。
     */
    private double helmYawScale = 1.0D;
    private int moveTimer;
    /** 连续运动模型：记录物理船回写的速度（方块船自己不再移动）。 */
    private ShipPhysics physics = new ShipPhysics();
    /** 推进功率 5..100（%），决定每秒挪几格。 */
    private int power = 100;
    /** 自动驾驶：朝声纳上标记的目标点逼近。 */
    private NavigationMode navigationMode = NavigationMode.MANUAL;
    private int settledChecks;
    private Vec3 navigationAnchor;
    private double driveX;
    private double driveZ;
    private double forwardScale;
    private double verticalInput;
    private static final int MANUAL_LEASE_TICKS = 20;
    private UUID manualPilot;
    private long lastManualInput;
    /** 自动驾驶目标（世界坐标）；null = 还没标记。 */
    private net.minecraft.core.BlockPos autopilotTarget;

    /** 历史确认过的舱内格（只增不减）：切掉外壁舱口后重扫会把空腔 interior 清空。 */
    private java.util.Set<BlockPos> knownInterior = java.util.Set.of();

    public ShipState(ShipGeometry.Result geometry, MachineCount machines) {
        this.geometry = geometry;
        this.machines = machines;
        this.knownInterior = collectInterior(geometry);
    }

    private static java.util.Set<BlockPos> collectInterior(ShipGeometry.Result geometry) {
        if (geometry == null) {
            return java.util.Set.of();
        }
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        for (ShipGeometry.Compartment compartment : geometry.compartments()) {
            out.addAll(compartment.interiorCells());
        }
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            out.addAll(pocket.interiorCells());
        }
        return java.util.Set.copyOf(out);
    }

    /**
     * 空腔的壳内格：几何给空时用历史确认过的舱内格兜底。
     *
     * <p>切掉外壁舱口后 {@code exteriorCells()} 会穿过洞口，房间的 interiorCells
     * 可能变空；不兜底的话进水体积/水面都会变成 0，船灌满也不沉。</p>
     */
    public java.util.Set<BlockPos> interiorCellsOf(ShipGeometry.Compartment pocket) {
        // 先看历史舱内格：破口/切外壁舱口后 exteriorCells() 会穿过洞口，
        // interiorCells 可能只剩几格，但历史里仍是完整房间。历史为空才信几何，
        // 纯船外空气（圆角包围盒四角）两边都为空，不能算舱内。
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        for (BlockPos cell : pocket.cells()) {
            if (knownInterior.contains(cell)) {
                out.add(cell);
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        return pocket.interiorCells();
    }

    /** 历史舱内格（核心 NBT 保存用）。 */
    public long[] knownInteriorArray() {
        long[] out = new long[knownInterior.size()];
        int i = 0;
        for (BlockPos pos : knownInterior) {
            out[i++] = pos.asLong();
        }
        return out;
    }

    /** 世界重开后的首次重扫：恢复 NBT 里保存的历史舱内格。 */
    public void restoreKnownInterior(long[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        java.util.Set<BlockPos> merged = new java.util.HashSet<>(knownInterior);
        for (long value : values) {
            merged.add(BlockPos.of(value));
        }
        knownInterior = java.util.Set.copyOf(merged);
    }

    /** 认领物理船时从记录恢复历史舱内格（在第一次 simulation() 之前调用）。 */
    public void restoreKnownInterior(java.util.Set<BlockPos> cells) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        java.util.Set<BlockPos> merged = new java.util.HashSet<>(knownInterior);
        merged.addAll(cells);
        knownInterior = java.util.Set.copyOf(merged);
    }

    /** 重扫换新对象时合并历史舱内格（只增不减）。 */
    public void inheritInteriorFrom(ShipState previous) {
        if (previous == null) {
            return;
        }
        java.util.Set<BlockPos> merged = new java.util.HashSet<>(knownInterior);
        merged.addAll(previous.knownInterior);
        knownInterior = java.util.Set.copyOf(merged);
    }

    public ShipGeometry.Result geometry() {
        return geometry;
    }

    public HullDamage hullDamage() {
        return hullDamage;
    }

    /** 该方块的最大血量（基础值 × 船体加固倍率）；不可破坏的方块返回 0。 */
    public float hullMaxHealth(net.minecraft.world.level.block.state.BlockState state) {
        return com.opamb.barotrauma.registry.ModShipBlocks.maxHealth(state) * hullHealthMultiplier;
    }

    public float hullHealthMultiplier() {
        return hullHealthMultiplier;
    }

    public void setHullHealthMultiplier(float multiplier) {
        this.hullHealthMultiplier = net.minecraft.util.Mth.clamp(multiplier, 0.25F, 5.0F);
    }

    /** 引擎升级步长（每级 +20% 推力）。 */
    public static final float ENGINE_STEP = 0.2F;
    /** 引擎升级最大级数。 */
    public static final int ENGINE_MAX_LEVEL = 3;

    public float engineMultiplier() {
        return engineMultiplier;
    }

    public void setEngineMultiplier(float multiplier) {
        // 存档里的 float 可能被写坏成 NaN；Mth.clamp 对 NaN 原样返回，
        // 再传给推进器会让加速度整体变成 NaN。坏值按未升级处理。
        if (Float.isNaN(multiplier)) {
            multiplier = 1.0F;
        }
        this.engineMultiplier = net.minecraft.util.Mth.clamp(multiplier,
                1.0F, 1.0F + ENGINE_STEP * ENGINE_MAX_LEVEL);
    }

    /** 由倍率反推升级级数（0..{@link #ENGINE_MAX_LEVEL}）。 */
    public static int engineLevel(float multiplier) {
        return Math.round((multiplier - 1.0F) / ENGINE_STEP);
    }

    /** 由级数算倍率（1.0 + 0.2×级数）。 */
    public static float engineMultiplierForLevel(int level) {
        return 1.0F + ENGINE_STEP * net.minecraft.util.Mth.clamp(level, 0, ENGINE_MAX_LEVEL);
    }

    /**
     * 这个方块是不是当前船体几何里的一格结构（用于拒绝空气/隔墙上的伤害）。
     *
     * <p>船内独立设备（接线盒、小型泵）不和船壳连通，但同样属于这艘船，
     * 走 {@link ShipGeometry.Result#owns(BlockPos)} 一起认。</p>
     */
    public boolean tracksHullBlock(net.minecraft.core.BlockPos pos) {
        return geometry.owns(pos);
    }

    public MachineCount machines() {
        return machines;
    }

    public List<ShipGeometry.Compartment> compartments() {
        return geometry.compartments();
    }

    /**
     * 这个格子属于哪个舱室/空腔（下标与 {@link ShipSimulation} 的氧气数组一致）。
     *
     * <p>连海空腔的下标在密闭舱室之后，它们的氧气按 0 处理；不在任何舱里返回 -1。</p>
     */
    public int compartmentAt(BlockPos cell) {
        return simulation().bodyOf(cell);
    }

    /**
     * 船体破损后替换几何（舱室 / 连海空腔全部重算）。
     *
     * <p>模拟对象要重建（舱室数组长度变了），但反应堆、压载、水泵这些设置
     * 从旧实例拷过来；密封舱室里的旧水不保留——破口舱室会从 0 开始由海水灌入，
     * 这比"把旧水位硬塞进新舱室"的行为可预期。</p>
     */
    public void replaceGeometry(ShipGeometry.Result next) {
        ShipSimulation old = this.simulation;
        double oldSealedWater = old == null ? 0.0D : old.waterUnits();
        double oldPocketWater = old == null ? 0.0D : old.pocketWaterTotal();
        // 先按"每格的水"把旧水收下来，再换几何；直接按容积比例重分会让关门的
        // 那一瞬间水整体跑到漏水侧（用户实测：完好侧被清空）。
        Map<BlockPos, Double> carried = old == null ? Map.of() : waterByCell();
        Map<BlockPos, Double> carriedOxygen = old == null ? Map.of() : oxygenByCell();
        this.geometry = next;
        // 新几何确认的舱内格也要并入历史：只增不减，破损后仍能兜底。
        java.util.Set<BlockPos> mergedInterior = new java.util.HashSet<>(knownInterior);
        mergedInterior.addAll(collectInterior(next));
        knownInterior = java.util.Set.copyOf(mergedInterior);
        // 已经打没的方块不再属于新几何；把它们的耐久记录清掉，客户端不会在空气里画血条。
        // 船内独立设备（接线盒/小型泵）也吃船壳伤害，重扫后不能把它们的耐久记录清掉。
        this.hullDamage.retain(next::owns);
        this.leakCompartmentByCell = null;
        this.leakInsideCells = null;
        this.waterBoxCache = null;
        this.waterBoxCacheTick = Long.MIN_VALUE;
        this.simulation = new ShipSimulation(next, machines, knownInterior);
        this.simulation.copySettingsFrom(old);
        // 连续砸洞时水位不能归零：旧水量按格子搬进新几何（并舱/拆舱都守恒）。
        if (carried.isEmpty()) {
            this.simulation.seedWater(oldSealedWater, oldPocketWater);
        } else {
            this.simulation.seedWaterByCell(carried, next);
        }
        // 氧气同样按格搬过去：开门/换几何会重扫，不能因为重扫就"回满"。
        this.simulation.seedOxygenByCell(carriedOxygen, next);
        this.water = null;
    }

    /**
     * 当前每个格子里的水量（单位）。重扫/换几何前用它把水"按位置"保存下来。
     *
     * <p>逐格场本身就是按位置存的；并舱/拆舱/开关门后再按格注回，不会把水
     * 按容积比例整体搬到漏水侧。</p>
     */
    public Map<BlockPos, Double> waterByCell() {
        ShipSimulation sim = this.simulation;
        return sim == null ? Map.of() : sim.waterCells();
    }

    /** 方块船重扫会新建一个 {@link ShipState}：把旧船的水/氧气（按格子）和设置接过来。 */
    public void inheritWaterFrom(ShipState previous) {
        if (previous == null) {
            return;
        }
        simulation().copySettingsFrom(previous.simulation);
        Map<BlockPos, Double> carried = previous.waterByCell();
        if (!carried.isEmpty()) {
            simulation().seedWaterByCell(carried, geometry);
        }
        // 氧气也不能因重扫回满：按格搬（见 replaceGeometry 的同一处理）。
        simulation().seedOxygenByCell(previous.oxygenByCell(), geometry);
    }

    /**
     * 当前每个舱内格的氧气（单位，按舱室均分）。
     *
     * <p>重扫/换几何前用它把氧气按位置保存下来；开门、破洞、拆方块都会触发重扫，
     * 不搬的话舱室氧气会被 {@link ShipSimulation} 的初始值（满氧）覆盖。</p>
     */
    public Map<BlockPos, Double> oxygenByCell() {
        Map<BlockPos, Double> out = new HashMap<>();
        ShipSimulation sim = this.simulation;
        if (sim == null) {
            return out;
        }
        List<ShipGeometry.Compartment> sealed = geometry.compartments();
        for (int i = 0; i < sealed.size(); i++) {
            double units = sim.oxygenUnits(i);
            java.util.Set<BlockPos> cells = sealed.get(i).cells();
            if (units <= 1.0E-6D || cells.isEmpty()) {
                continue;
            }
            double per = units / cells.size();
            for (BlockPos cell : cells) {
                out.merge(cell, per, Double::sum);
            }
        }
        return out;
    }


    public ShipSimulation simulation() {
        if (simulation == null) {
            simulation = new ShipSimulation(geometry, machines, knownInterior);
        }
        return simulation;
    }

    /**
     * 当前游戏刻的水面包（方块船形态），同一 tick 内复用。
     *
     * <p>干燥区判定每个玩家每 tick 都要用一次，而 {@code ShipWaterStateBuilder.build}
     * 里有贪心矩形分解和连海空腔松弛，代价远高于一次包围盒判定。这里按 tick 缓存：
     * 同一 tick 的所有调用共用一份结果；下一个 tick 自然重算，水位变化最多延迟 1 tick
     * （20 Hz），和 10 Hz 的网络同步相比看不出差别。</p>
     *
     * <p>调用方必须只在服务端持有 {@link ShipState} 的时候用它——客户端没有这个对象。</p>
     */
    public ShipWaterStatePacket waterBoxesFor(long gameTime) {
        if (waterBoxCache != null && waterBoxCacheTick == gameTime) {
            return waterBoxCache;
        }
        waterBoxCache = ShipWaterStateBuilder.build(this, -1L, false, 0.0F, 0.0F, 0.0F);
        waterBoxCacheTick = gameTime;
        return waterBoxCache;
    }

    /**
     * 带整格方块判定的版本：方块船干燥区判定用。只有非整格的家具/机器需要补
     * 水面，船壳/地板/墙补水会把水画进墙里。
     */
    public ShipWaterStatePacket waterBoxesFor(net.minecraft.world.level.Level level,
                                              long gameTime) {
        if (waterBoxCache != null && waterBoxCacheTick == gameTime) {
            return waterBoxCache;
        }
        java.util.function.Predicate<net.minecraft.core.BlockPos> fullCube = level == null
                ? null
                : pos -> level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
        waterBoxCache = ShipWaterStateBuilder.build(this, -1L, false, 0.0F, 0.0F, 0.0F, fullCube);
        waterBoxCacheTick = gameTime;
        return waterBoxCache;
    }

    /** 连到外海的破口；随水包发给客户端，用来在洞口画水花/气泡。 */
    public Collection<Breach> breaches() {
        return breaches.values();
    }

    /** 方块焊回去之后，把对应破口从列表里移除（否则客户端会在补好的位置继续画水花）。 */
    public void removeBreaches(Collection<BlockPos> positions) {
        if (positions == null) {
            return;
        }
        for (BlockPos pos : positions) {
            breaches.remove(pos);
        }
    }

    public void removeBreach(BlockPos pos) {
        if (pos != null) {
            breaches.remove(pos);
        }
    }

    /**
     * 记录新破口。只有落在连海空腔（{@code openPockets}）里的洞才会真正进水，
     * 内部隔墙的洞不记录，避免客户端在舱室中间画错位置。
     *
     * <p>方向取“洞口 → 密封舱室质心”的水平单位向量：客户端用它把水花/气泡朝舱内推，
     * 不再靠 bbox 中心猜（连海空腔的 bbox 会包含船外空气，容易指向舱外）。</p>
     */
    public void addBreaches(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (BlockPos pos : positions) {
            if (!isSeaConnected(pos) || breaches.containsKey(pos)) {
                continue;
            }
            // 优先用破口所在空腔的质心：整个船的质心可能指向别的密封舱，
            // 客户端水花/气泡就会朝错方向喷。
            double[] center = pocketCenter(pos);
            if (center == null) {
                center = interiorCenter();
            }
            double dx = center[0] - (pos.getX() + 0.5D);
            double dz = center[1] - (pos.getZ() + 0.5D);
            double length = Math.sqrt(dx * dx + dz * dz);
            float dirX = length > 1.0E-4D ? (float) (dx / length) : 0.0F;
            float dirZ = length > 1.0E-4D ? (float) (dz / length) : 0.0F;
            BlockPos immutable = pos.immutable();
            breaches.put(immutable, new Breach(immutable, dirX, dirZ));
        }
    }

    /** 破口所在连海空腔的水平质心；不在任何空腔里返回 null。 */
    private double[] pocketCenter(BlockPos pos) {
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            if (!pocket.cells().contains(pos)) {
                continue;
            }
            double x = 0.0D;
            double z = 0.0D;
            long count = 0L;
            for (BlockPos cell : pocket.cells()) {
                x += cell.getX() + 0.5D;
                z += cell.getZ() + 0.5D;
                count++;
            }
            if (count > 0L) {
                return new double[]{x / count, z / count};
            }
        }
        return null;
    }

    /** 密封舱室在水平面上的质心；没有密封舱室时退回整船包围盒中心。 */
    private double[] interiorCenter() {
        double x = 0.0D;
        double z = 0.0D;
        long count = 0L;
        for (ShipGeometry.Compartment compartment : geometry.compartments()) {
            for (BlockPos pos : compartment.cells()) {
                x += pos.getX() + 0.5D;
                z += pos.getZ() + 0.5D;
                count++;
            }
        }
        if (count == 0L) {
            ShipGeometry.Bounds bounds = geometry.bounds();
            x = (bounds.minX() + bounds.maxX() + 1) * 0.5D;
            z = (bounds.minZ() + bounds.maxZ() + 1) * 0.5D;
            count = 1L;
        }
        return new double[]{x / count, z / count};
    }

    private boolean isSeaConnected(BlockPos pos) {
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            if (pocket.cells().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** 船体是否还完整（有方块被破坏 → 需要重新分舱）。 */
    public boolean hullIntact(net.minecraft.world.level.Level level) {
        for (net.minecraft.core.BlockPos pos : geometry.hull()) {
            if (!com.opamb.barotrauma.registry.ModShipBlocks.isShipBlock(level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 收集这艘船在世界上占用的**全部**方块。
     *
     * <p>不能用 {@code geometry.hull()}：那是"最大连通分量"，船上有独立的小方块组
     * （门、玻璃、单独挂着的机器）时会漏掉，搬船/装船之后那些方块就**留在原地**——
     * 表现出来就是"船开走了，原地还留着一截"。所以扫包围盒里的所有船方块。</p>
     */
    public java.util.Set<net.minecraft.core.BlockPos> collectBlocks(net.minecraft.world.level.Level level) {
        return collectBlocks(level, net.minecraft.core.BlockPos.ZERO);
    }

    /**
     * 按坐标平移读船体方块。装配后方块被 VS2 搬到船坞，几何包围盒仍是装配时的
     * 世界坐标；传 {@code translate = 船坞偏移} 才能读到船坞里的真实方块。
     */
    public java.util.Set<net.minecraft.core.BlockPos> collectBlocks(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos translate) {
        java.util.Set<net.minecraft.core.BlockPos> out = new java.util.HashSet<>();
        ShipGeometry.Bounds b = geometry.bounds();
        for (net.minecraft.core.BlockPos raw : net.minecraft.core.BlockPos.betweenClosed(
                b.minX() - 1, b.minY() - 1, b.minZ() - 1,
                b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1)) {
            net.minecraft.core.BlockPos pos = raw.offset(translate);
            if (com.opamb.barotrauma.registry.ModShipBlocks.isShipBlock(level.getBlockState(pos))
                    && !com.opamb.barotrauma.world.DockingBay.isStationSideDoor(raw)
                    && !com.opamb.barotrauma.world.DockingBay.isStationDockHatch(raw)
                    // 对接时 padUp=1 把扫描上界抬到 -68，再向外扩 1 会够到 -67 的站内梯子；
                    // 那不是船体，收进去会让装配把站里的梯子一起带走。
                    && !com.opamb.barotrauma.world.DockingBay.isStationLadder(raw)) {
                out.add(pos.immutable());
            }
        }
        return out;
    }

    /** 设定方块船推进方向（每轴 −1/0/1，世界坐标）。物理船请用 {@link #setHelm}。 */
    public void setThrottle(int x, int y, int z) {
        double scale = Math.max(1.0D, Math.hypot(x, z));
        setManualVector(x / scale, clampAxis(y), z / scale, false);
    }

    public void setHelm(int forward, int yaw, int vertical) {
        setManualVector(clampAxis(forward), clampAxis(vertical), clampAxis(yaw), true);
    }

    public void setManualVector(double x, double y, double z, boolean physical) {
        // A late neutral key release must not cancel an automatic controller.
        if (x == 0.0D && y == 0.0D && z == 0.0D && autopilotHold()) {
            return;
        }
        navigationMode = NavigationMode.MANUAL;
        settledChecks = 0;
        autopilotTarget = null;
        navigationAnchor = null;
        x = clamp(x, -1.0D, 1.0D);
        z = clamp(z, -1.0D, 1.0D);
        verticalInput = clamp(y, -1.0D, 1.0D);
        throttleY = (int) Math.signum(verticalInput);
        double horizontalScale = Math.max(1.0D, Math.hypot(x, z));
        driveX = physical ? 0.0D : x / horizontalScale;
        driveZ = physical ? 0.0D : z / horizontalScale;
        throttleX = (int) Math.signum(driveX);
        throttleZ = (int) Math.signum(driveZ);
        helmForward = physical ? (int) Math.signum(x) : 0;
        forwardScale = physical ? Math.abs(x) : 0.0D;
        helmYaw = physical ? (int) Math.signum(z) : 0;
        helmYawScale = physical ? Math.abs(z) : 0.0D;
        moveTimer = 0;
    }

    public boolean canTakeHelm(UUID player, long tick) {
        return manualPilot == null || manualPilot.equals(player) || tick - lastManualInput >= MANUAL_LEASE_TICKS;
    }

    public void acceptManualInput(UUID player, long tick, double x, double y, double z, boolean physical) {
        if (autopilotHold() || !canTakeHelm(player, tick)) {
            return;
        }
        setManualVector(x, y, z, physical);
        manualPilot = throttled() ? player : null;
        lastManualInput = tick;
    }

    public boolean expireManualInput(long tick) {
        if (manualPilot == null || tick - lastManualInput < MANUAL_LEASE_TICKS) {
            return false;
        }
        manualPilot = null;
        neutralizeControls();
        return true;
    }

    private static int clampAxis(int value) {
        return value < 0 ? -1 : Math.min(value, 1);
    }

    /** 物理船前进舵令（−1/0/1）。 */
    public int helmForward() {
        return helmForward;
    }

    /** 物理船偏航舵令（−1/0/1）。 */
    public int helmYaw() {
        return helmYaw;
    }

    /** 偏航舵令幅度（0..1）；手动满舵 = 1，自动驾驶按航向误差成比例。 */
    public double helmYawScale() {
        return helmYawScale;
    }

    public void setHelmYawScale(double scale) {
        helmYawScale = Double.isFinite(scale) ? clamp(scale, 0.0D, 1.0D) : 0.0D;
    }

    public double engineThrottle(boolean physical) {
        return physical ? forwardScale : Math.min(1.0D, Math.hypot(driveX, driveZ));
    }

    public double verticalInput() {
        return verticalInput;
    }

    /** Enabling without a position captures the current core pose on the next simulation step. */
    public void setAutopilotHold(boolean hold) {
        manualPilot = null;
        navigationMode = hold ? NavigationMode.SETTLING : NavigationMode.MANUAL;
        settledChecks = 0;
        autopilotTarget = null;
        navigationAnchor = null;
        neutralizeControls();
    }

    public void holdAt(Vec3 position) {
        setAutopilotHold(true);
        navigationAnchor = position;
        autopilotTarget = BlockPos.containing(position);
    }

    public void setAutopilotTarget(BlockPos target) {
        manualPilot = null;
        autopilotTarget = target.immutable();
        navigationAnchor = Vec3.atCenterOf(target);
        navigationMode = NavigationMode.TRANSIT;
        settledChecks = 0;
    }

    public void clearAutopilotTarget() {
        setAutopilotHold(autopilotHold());
    }

    public void neutralizeControls() {
        throttleX = throttleY = throttleZ = 0;
        helmForward = helmYaw = 0;
        driveX = driveZ = forwardScale = verticalInput = helmYawScale = 0.0D;
        moveTimer = 0;
    }

    @javax.annotation.Nullable
    public BlockPos autopilotTarget() {
        return autopilotTarget;
    }

    public boolean autopilotHold() {
        return navigationMode.automatic();
    }

    public NavigationMode navigationMode() {
        return navigationMode;
    }

    public int autopilotDistance(net.minecraft.core.BlockPos corePos) {
        return autopilotTarget == null ? -1 : (int) Math.round(Math.sqrt(autopilotTarget.distSqr(corePos)));
    }

    /** 自动驾驶：按目标点设推力；到点后推力归零即"保持"。 */
    private void aimAtTarget(net.minecraft.core.BlockPos corePos) {        if (autopilotTarget == null) {
            throttleX = 0;
            throttleY = 0;
            throttleZ = 0;
            return;
        }
        int dx = autopilotTarget.getX() - corePos.getX();
        int dy = autopilotTarget.getY() - corePos.getY();
        int dz = autopilotTarget.getZ() - corePos.getZ();
        // 差 3 格以内不再纠偏，避免在目标点附近来回抖
        throttleX = Math.abs(dx) >= 3 ? Integer.signum(dx) : 0;
        throttleY = Math.abs(dy) >= 3 ? Integer.signum(dy) : 0;
        throttleZ = Math.abs(dz) >= 3 ? Integer.signum(dz) : 0;
    }

    /** Called with the transformed core center and actual rigid-body velocity. */
    public void autopilotStep(Vec3 core, Vec3 velocity, Vec3 forward, double availableAcceleration,
                              double commandLimit, double engineAcceleration, double verticalAcceleration,
                              double yawRate) {
        if (!autopilotHold()) {
            return;
        }
        if (navigationMode == NavigationMode.BLOCKED) {
            neutralizeControls();
            return;
        }
        if (navigationAnchor == null) {
            holdAt(core);
        }
        double length = Math.hypot(forward.x, forward.z);
        double ux = length > 1.0E-6D ? forward.x / length : 1.0D;
        double uz = length > 1.0E-6D ? forward.z / length : 0.0D;
        NavigationMotion.PhysicalCommand command = NavigationMotion.physical(
                navigationAnchor.x - core.x, navigationAnchor.y - core.y, navigationAnchor.z - core.z,
                velocity.x, velocity.y, velocity.z, ux, uz, 6.0D * Math.sqrt(power / 100.0D),
                availableAcceleration, verticalAcceleration);
        updateStationKeeping(command.stopped() && Math.abs(engineAcceleration) < 0.02D
                && Math.abs(yawRate) < 0.05D, 10);
        helmForward = (int) Math.signum(command.forward());
        forwardScale = Math.abs(command.forward()) * commandLimit;
        verticalInput = command.vertical();
        throttleY = (int) Math.signum(verticalInput);
        applyAutopilotYaw(command.yawError());
    }

    public void pauseNavigation() {
        neutralizeControls();
        updateStationKeeping(false, 10);
    }

    private void updateStationKeeping(boolean settled, int requiredChecks) {
        if (!settled) {
            settledChecks = 0;
            if (navigationMode == NavigationMode.HOLD) {
                navigationMode = NavigationMode.SETTLING;
            }
        } else if (settledChecks < requiredChecks && ++settledChecks >= requiredChecks) {
            navigationMode = NavigationMode.HOLD;
        }
    }

    /**
     * 自动驾驶偏航：把航向误差映射成 0..1 的偏航速率，而不是 ±1 满舵。
     *
     * <p>满舵 + 15°/s 的舵效会让船在目标航向上左右横跳：刚过线就满舵反打，
     * 永远在过冲。按误差比例给速率后，航向近似指数收敛，不会来回摇。
     * 3.4° 以内直接回中，避免在噪声上抖；误差超过 35° 才给满舵。</p>
     */
    private void applyAutopilotYaw(double angle) {
        double abs = Math.abs(angle);
        if (abs < 0.06D) {
            helmYaw = 0;
            helmYawScale = 0.0D;
            return;
        }
        helmYaw = angle > 0.0D ? 1 : -1;
        helmYawScale = clamp((abs - 0.06D) / 0.54D, 0.15D, 1.0D);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public boolean throttled() {
        return throttleX != 0 || throttleY != 0 || throttleZ != 0
                || helmForward != 0 || helmYaw != 0;
    }

    /**
     * 观察用：按当前目标点算一遍推力，不移动船、不改变现有推力。
     *
     * <p>自检拿它验证"目标在正东 → 推力 +X""已经在目标点 2 格内 → 推力为零"。</p>
     */
    public int[] previewAutopilot(net.minecraft.core.BlockPos corePos) {
        int sx = throttleX;
        int sy = throttleY;
        int sz = throttleZ;
        aimAtTarget(corePos);
        int[] out = {throttleX, throttleY, throttleZ};
        throttleX = sx;
        throttleY = sy;
        throttleZ = sz;
        return out;
    }

    public int throttleX() {
        return throttleX;
    }

    public int throttleY() {
        return throttleY;
    }

    public int throttleZ() {
        return throttleZ;
    }

    /** 推进功率（%）。 */
    public void setPower(int percent) {
        this.power = net.minecraft.util.Mth.clamp(percent, 5, 100);
    }

    public int power() {
        return power;
    }

    /** 当前推力对应的航向名（水平八向；物理船回舵令描述）。 */
    public String headingName() {
        if (autopilotHold()) {
            if (autopilotTarget == null) {
                return "自动待命";
            }
            return navigationLabel();
        }
        if (helmForward != 0 || helmYaw != 0) {
            return helmName();
        }
        if (throttleX == 0 && throttleZ == 0) {
            return throttleY > 0 ? "上浮" : throttleY < 0 ? "下潜" : "停车";
        }
        String vertical = throttleY > 0 ? " + 上浮" : throttleY < 0 ? " + 下潜" : "";
        if (throttleZ < 0) {
            return (throttleX == 0 ? "北" : throttleX > 0 ? "东北" : "西北") + vertical;
        }
        if (throttleZ > 0) {
            return (throttleX == 0 ? "南" : throttleX > 0 ? "东南" : "西南") + vertical;
        }
        return (throttleX > 0 ? "东" : "西") + vertical;
    }

    /** 物理船舵令的文字描述。 */
    public String helmName() {
        if (autopilotHold()) {
            if (autopilotTarget == null) {
                return "自动待命";
            }
            return navigationLabel();
        }
        StringBuilder text = new StringBuilder();
        if (helmForward > 0) {
            text.append("前进");
        } else if (helmForward < 0) {
            text.append("后退");
        }
        if (helmYaw > 0) {
            text.append(text.length() > 0 ? " + 左转" : "左转");
        } else if (helmYaw < 0) {
            text.append(text.length() > 0 ? " + 右转" : "右转");
        }
        if (throttleY > 0) {
            text.append(text.length() > 0 ? " + 上浮" : "上浮");
        } else if (throttleY < 0) {
            text.append(text.length() > 0 ? " + 下潜" : "下潜");
        }
        return text.length() == 0 ? "停车" : text.toString();
    }

    private String navigationLabel() {
        return switch (navigationMode) {
            case TRANSIT -> "自动·前往标记";
            case HOLD -> "自动·定点保持";
            case SETTLING -> "自动·正在稳船";
            case BLOCKED -> "自动·航行受阻";
            case MANUAL -> "手动";
        };
    }

    public String throttleName() {
        if (!throttled()) {
            return "停车";
        }
        if (helmForward != 0 || helmYaw != 0) {
            return helmName() + "  (前 " + helmForward + ", 舵 " + helmYaw + ", 垂 " + throttleY + ")";
        }
        return headingName() + "  (" + throttleX + "," + throttleY + "," + throttleZ + ")";
    }

    /** 运动模型（只读，给面板/自检看速度和航向）。 */
    public ShipPhysics physics() {
        return physics;
    }

    /** 当前航速（格/秒，1 格 = 1 米）。有惯性，收油门后还会滑一段。 */
    public double blocksPerSecond() {
        return physics.speed();
    }

    /** 舱室总进水比例（0..1），用于让灌了水的船往下沉。 */
    public double floodRatio() {
        return waterRatio(true);
    }

    /**
     * 原版 {@code SubmarineBody.CalculateBuoyancy()} 的输入：全船水量 / 全船容积。
     *
     * <p>和 {@link #floodRatio()} 的区别是<b>包含压载水</b>——原作压载舱也是
     * Hull，压载水本来就计入浮力；中性点是 {@code NeutralBallastPercentage = 0.07}
     * （全船 7% 的水），不是压载泵所在舱的百分比。</p>
     */
    public double buoyancyWaterRatio() {
        return waterRatio(false);
    }

    private double waterRatio(boolean excludeBallast) {
        // 分母必须把"连海空腔"也算上：舱室破洞后会从 compartments 移到 openPockets，
        // 只数密闭舱室的话，整艘船灌满水时比例反而是 0，物理上不会下沉。
        // 但只算**船壳以内**的格：圆角船体的包围盒四角也在同一片连海空腔里，
        // 那部分外海海水不能算成船内进水（否则内部全干的船会显示 60% 进水直接沉底）。
        double volume = 0.0D;
        for (ShipGeometry.Compartment compartment : compartments()) {
            volume += compartment.volume();
        }
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            volume += interiorCellsOf(pocket).size();
        }
        if (volume <= 0.0D) {
            return 0.0D;
        }
        // 连海空腔按逐列水位累加“船壳内格”的实际水量：各列底高不同，
        // 按格数比例折算会让进水比例偏离真实水量（下沉判断跟着错）。
        double pocketWater = 0.0D;
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        for (int i = 0; i < pockets.size(); i++) {
            ShipGeometry.Compartment pocket = pockets.get(i);
            java.util.Set<BlockPos> interior = interiorCellsOf(pocket);
            if (interior.isEmpty()) {
                continue;
            }
            java.util.List<BlockPos> sources = new java.util.ArrayList<>();
            for (Breach breach : breaches.values()) {
                if (pocket.cells().contains(breach.pos())) {
                    sources.add(breach.pos());
                }
            }
            pocketWater += simulation().interiorWater(i, interior, sources);
        }
        double units = simulation().waterUnits() + pocketWater;
        double capacity = volume * ShipSimulation.UNITS_PER_BLOCK;
        if (excludeBallast) {
            // 压载舱的水是有意灌进去的，不能同时算成"进水"——否则浮力会被
            // 压载因子和进水因子各扣一次。
            units -= simulation().ballastUnits();
            capacity -= simulation().ballastCapacityUnits();
        }
        return capacity <= 0.0D ? 0.0D : Math.min(1.0D, Math.max(0.0D, units / capacity));
    }

    /**
     * 给客户端水花用的泄漏强度（0 = 不漏，0.35 = 小漏，1 = 大漏/已破口）。
     *
     * <p>只对“有一面朝船外、相邻还有密封舱”的受损格返回非零；0% 的 breached
     * 也返回 1，让客户端继续在洞口喷水花（真正的水量由 tickSeaFlood 管）。</p>
     */
    public float leakIntensity(BlockPos pos) {
        if (pos == null) {
            return 0.0F;
        }
        float health = hullDamage.health(pos);
        if (health > 0.9F) {
            return 0.0F;
        }
        ensureLeakCache();
        if (!touchesOutside(pos)) {
            return 0.0F;
        }
        if (health <= 0.0F) {
            return 1.0F;
        }
        if (adjacentCompartment(pos) < 0) {
            return 0.0F;
        }
        return health <= 0.3F ? 1.0F : 0.35F;
    }

    /**
     * 船壳渗漏：剩余耐久 90% 以下（伤害 ≥10%）开始小漏，30% 以下（伤害 ≥70%）变大漏。
     *
     * <p>0% 不走这里：那会把方块切成 {@code breached}，几何扫描直接把它当空气，
     * 舱室变成连海空腔，由 {@link ShipSimulation#tickSeaFlood} 按洞口灌水。</p>
     */
    public void tickLeaks(double dt) {
        if (dt <= 0.0D || hullDamage.isEmpty()) {
            return;
        }
        ensureLeakCache();
        ShipSimulation sim = simulation();
        for (Map.Entry<BlockPos, Float> entry : hullDamage.entries().entrySet()) {
            float health = entry.getValue();
            if (health <= 0.0F || health > 0.9F) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (!touchesOutside(pos)) {
                continue;
            }
            int compartment = adjacentCompartment(pos);
            if (compartment < 0) {
                continue;
            }
            double rate = health <= 0.3F ? BIG_LEAK_UNITS_PER_SECOND
                    : SMALL_LEAK_UNITS_PER_SECOND;
            // 漏点随实际接水量发流路，水量计入相邻舱室。
            sim.addWaterAt(compartment, pos, rate * dt);
        }
    }

    /** 舱室格 → 下标 + 船内空气格集合；几何变化时由 replaceGeometry 清空。 */
    private void ensureLeakCache() {
        if (leakCompartmentByCell != null && leakInsideCells != null) {
            return;
        }
        Map<BlockPos, Integer> byCell = new HashMap<>();
        Set<BlockPos> inside = new HashSet<>();
        List<ShipGeometry.Compartment> rooms = compartments();
        for (int i = 0; i < rooms.size(); i++) {
            for (BlockPos cell : rooms.get(i).cells()) {
                byCell.put(cell, i);
                inside.add(cell);
            }
        }
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            inside.addAll(pocket.cells());
        }
        leakCompartmentByCell = byCell;
        leakInsideCells = inside;
    }

    /** 这一格船壳是否有一面直接朝船外（既不是船方块，也不是船内空气）。 */
    private boolean touchesOutside(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!leakInsideCells.contains(neighbor) && !geometry.hull().contains(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private int adjacentCompartment(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            Integer index = leakCompartmentByCell.get(pos.relative(direction));
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 重扫换了新 ShipState 对象时，把航行控制状态从旧实例带过来。
     *
     * <p>不迁的话开合一次门/放一台机器就会把油门、功率和自动驾驶目标重置成默认值。</p>
     */
    public void inheritControlFrom(ShipState previous) {
        if (previous == null) {
            return;
        }
        this.throttleX = previous.throttleX;
        this.throttleY = previous.throttleY;
        this.throttleZ = previous.throttleZ;
        this.helmForward = previous.helmForward;
        this.helmYaw = previous.helmYaw;
        this.helmYawScale = previous.helmYawScale;
        this.power = previous.power;
        navigationMode = previous.navigationMode;
        settledChecks = previous.settledChecks;
        navigationAnchor = previous.navigationAnchor;
        autopilotTarget = previous.autopilotTarget;
        driveX = previous.driveX;
        driveZ = previous.driveZ;
        forwardScale = previous.forwardScale;
        verticalInput = previous.verticalInput;
        manualPilot = previous.manualPilot;
        lastManualInput = previous.lastManualInput;
    }

    /**
     * 平移后的船状态：几何整体偏移，模拟实例原样带过去。
     *
     * <p>船是刚体，挪一格不改变舱室，所以不需要重扫；温度和舱内水位也跟着保留——
     * 否则每推进一格反应堆都会从 3000°C 重新开始。</p>
     */
    public ShipState shifted(int dx, int dy, int dz) {
        ShipState next = new ShipState(geometry.shifted(dx, dy, dz), machines);
        next.simulation = this.simulation;
        if (next.simulation != null) {
            // 模拟实例原样带过去，但几何/上报表的键还是平移前的坐标：
            // 几何不换的话 bodyOf()/waterLevelAt() 查不到新位置；
            // 上报表不换的话设备按新坐标上报后旧记录还会重复计入需求。
            next.simulation.shiftGeometry(dx, dy, dz);
            next.simulation.shiftReports(dx, dy, dz);
        }
        // 历史舱内格也要平移，否则破洞兜底会指向旧坐标。
        java.util.Set<BlockPos> movedInterior = new java.util.HashSet<>();
        for (BlockPos cell : knownInterior) {
            movedInterior.add(cell.offset(dx, dy, dz));
        }
        next.knownInterior = java.util.Set.copyOf(movedInterior);
        // 破口入口也要平移：waterLevelAt 用破口位置算列水位，breaches 为空时
        // 会退回整块统一水位，平移后干燥侧又会被误判成泡水。
        next.breaches.clear();
        for (java.util.Map.Entry<BlockPos, Breach> entry : this.breaches.entrySet()) {
            Breach breach = entry.getValue();
            BlockPos moved = breach.pos().offset(dx, dy, dz);
            next.breaches.put(moved, new Breach(moved, breach.dirX(), breach.dirZ()));
        }
        // 船壳耐久是逐格记录的，整船平移时必须跟着坐标一起挪。
        next.hullDamage.copyFrom(this.hullDamage);
        next.hullDamage.shift(dx, dy, dz);
        next.hullHealthMultiplier = this.hullHealthMultiplier;
        next.engineMultiplier = this.engineMultiplier;
        // 速度/惯性必须跟着船走，否则每挪一格船就"停"一次
        next.physics = this.physics;
        next.throttleX = throttleX;
        next.throttleY = throttleY;
        next.throttleZ = throttleZ;
        next.helmForward = helmForward;
        next.helmYaw = helmYaw;
        next.helmYawScale = helmYawScale;
        next.power = power;
        next.navigationMode = navigationMode;
        next.settledChecks = settledChecks;
        next.navigationAnchor = navigationAnchor;
        next.autopilotTarget = autopilotTarget;
        next.driveX = driveX;
        next.driveZ = driveZ;
        next.forwardScale = forwardScale;
        next.verticalInput = verticalInput;
        next.manualPilot = manualPilot;
        next.lastManualInput = lastManualInput;
        return next;
    }

    /**
     * 方块船推进：先积分连续速度，攒够一格才真的挪一格。
     *
     * <p>2026-11-02 恢复：用户选择"方块留在世界里"的路线——船壳真挡水、能交互、能做破损。
     * VS2 物理船代码保留但不再作为主路径。</p>
     */
    public net.minecraft.core.BlockPos tickMovement(net.minecraft.server.level.ServerLevel level,
                                                    net.minecraft.core.BlockPos corePos) {
        expireManualInput(level.getGameTime());
        double dt = 0.1D;
        double engineSupply = Math.max(0.0D, simulation().engineSupplyFactor());
        boolean terminalPowered = level.getBlockEntity(corePos) instanceof ShipCoreBlockEntity terminal
                && terminal.condition() > 0.0D
                && simulation().consumerVoltage(corePos) >= ShipCoreBlockEntity.TERMINAL_MIN_VOLTAGE;
        if (!terminalPowered || navigationMode == NavigationMode.BLOCKED) {
            pauseNavigation();
            physics.tickVector(dt, 0.0D, 0.0D, 0.0D, 0,
                    simulation().ballastPercent(), floodRatio());
        } else if (autopilotHold()) {
            Vec3 pos = physics.positionAt(corePos);
            if (navigationAnchor == null) {
                holdAt(pos);
            }
            Vec3 delta = navigationAnchor.subtract(pos);
            double horizontal = Math.hypot(delta.x, delta.z);
            double max = Math.min(1.0D, engineSupply * power / 100.0D);
            double speed = NavigationMotion.desiredSpeed(horizontal, ShipPhysics.MAX_SPEED * max, 1.5D, 0.20D);
            double vertical = NavigationMotion.desiredSpeed(delta.y, ShipPhysics.MAX_RISE * max, 0.6D, 0.15D);
            driveX = horizontal > 1.0E-6D ? speed / ShipPhysics.MAX_SPEED * delta.x / horizontal : 0.0D;
            driveZ = horizontal > 1.0E-6D ? speed / ShipPhysics.MAX_SPEED * delta.z / horizontal : 0.0D;
            verticalInput = vertical / ShipPhysics.MAX_RISE;
            throttleX = (int) Math.signum(driveX);
            throttleY = (int) Math.signum(verticalInput);
            throttleZ = (int) Math.signum(driveZ);
            updateStationKeeping(horizontal <= NavigationMotion.HORIZONTAL_STOP
                    && Math.abs(delta.y) <= NavigationMotion.VERTICAL_STOP && physics.totalSpeed() < 0.12D, 5);
            if (max > 0.0D) {
                physics.tickAuto(dt, driveX * ShipPhysics.MAX_SPEED, vertical, driveZ * ShipPhysics.MAX_SPEED);
            } else {
                physics.tickVector(dt, 0.0D, 0.0D, 0.0D, 0,
                        simulation().ballastPercent(), floodRatio());
            }
        } else {
            int effectivePower = (int) Math.round(power * engineSupply);
            physics.tickVector(dt, driveX, verticalInput, driveZ, effectivePower,
                    simulation().ballastPercent(), floodRatio());
        }
        return applyStep(level, corePos);
    }

    private net.minecraft.core.BlockPos applyStep(net.minecraft.server.level.ServerLevel level,
                                                  net.minecraft.core.BlockPos corePos) {
        int dx = physics.stepX();
        int dy = physics.stepY();
        int dz = physics.stepZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return corePos;
        }
        if (ShipMover.move(level, corePos, dx, dy, dz).ok()) {
            physics.consume(dx, dy, dz);
            return corePos.offset(dx, dy, dz);
        }
        // 整步被挡：拆成三个单轴分别试，撞上的那轴停车，其余轴照走（贴墙滑行）
        net.minecraft.core.BlockPos pos = corePos;
        pos = slide(level, pos, dx, 0, 0);
        pos = slide(level, pos, 0, dy, 0);
        pos = slide(level, pos, 0, 0, dz);
        if (navigationMode == NavigationMode.BLOCKED && !pos.equals(corePos)
                && level.getBlockEntity(pos) instanceof ShipCoreBlockEntity moved && moved.state() != null) {
            moved.state().navigationMode = NavigationMode.BLOCKED;
            moved.state().neutralizeControls();
        }
        return pos;
    }

    private net.minecraft.core.BlockPos slide(net.minecraft.server.level.ServerLevel level,
                                              net.minecraft.core.BlockPos corePos, int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return corePos;
        }
        if (ShipMover.move(level, corePos, dx, dy, dz).ok()) {
            physics.consume(dx, dy, dz);
            return corePos.offset(dx, dy, dz);
        }
        int axis = dx != 0 ? 0 : dy != 0 ? 1 : 2;
        double hitSpeed = Math.abs(physics.speedOn(axis));
        physics.blocked(axis);
        if (hitSpeed > 1.0D) {
            damageFront(level, dx, dy, dz, hitSpeed);
        }
        if (axis == 0) {
            throttleX = 0;
        } else if (axis == 1) {
            throttleY = 0;
        } else {
            throttleZ = 0;
        }
        if (autopilotHold()) {
            navigationMode = NavigationMode.BLOCKED;
            neutralizeControls();
        }
        return corePos;
    }

    /** 撞墙：迎面船壳按撞击速度扣耐久，0 耐久才破口（不再一击秒掉）。 */
    private void damageFront(net.minecraft.server.level.ServerLevel level, int dx, int dy, int dz,
                             double hitSpeed) {
        if (geometry.hull().isEmpty()) {
            return;
        }
        int best = Integer.MIN_VALUE;
        net.minecraft.core.BlockPos target = null;
        for (net.minecraft.core.BlockPos pos : geometry.hull()) {
            // 声纳是船脑，不能当迎撞面被撞掉
            if (level.getBlockState(pos).is(com.opamb.barotrauma.registry.ModShipBlocks.SONAR.get())
                    || level.getBlockState(pos).is(
                            com.opamb.barotrauma.registry.ModShipBlocks.NAVIGATION_TERMINAL.get())) {
                continue;
            }
            int score = pos.getX() * dx + pos.getY() * dy + pos.getZ() * dz;
            if (score > best) {
                best = score;
                target = pos;
            }
        }
        if (target == null) {
            return;
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(level, target, 64.0D);
        float amount = (float) (15.0D + hitSpeed * 10.0D);
        if (core != null) {
            // 有船核心：只走耐久，0 耐久才由核心破口；不能在这里直接删方块。
            core.damageBlock(level, target, amount, null);
            return;
        }
        // 没有船核心（老存档/独立结构）才退回直接删除，避免完全失去碰撞破损。
        level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    /** 舱室水位 → 真实水方块。 */
    public int applyWater(net.minecraft.world.level.Level level) {
        if (water == null) {
            water = new ShipWater(geometry, simulation());
        }
        return water.apply(level);
    }

    /**
     * 把舱内（和连海空腔）的水方块全部清掉。
     *
     * <p>装成物理船之前必须调一次：VS2 的装配集合只有"船体方块"，舱内的水不在里面，
     * 会原地剩下一坨水——看起来就是"船没了、水灌进来了"。拆船的时候也不该带着水回来。</p>
     */
    public int clearWater(net.minecraft.world.level.Level level) {
        return clearWater(level, 0, 0, 0);
    }

    /** 带整数位移的舱室排水（拆船铺回方块后，位置 = 原坐标 + 船位移）。 */
    public int clearWater(net.minecraft.world.level.Level level, int dx, int dy, int dz) {
        if (water == null) {
            water = new ShipWater(geometry, simulation());
        }
        return water.clear(level, dx, dy, dz);
    }

    /** 静态摘要：舱室 + 设备。机器方块已删除，只报声纳和舱室。 */
    public Component summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("船体扫描：").append(geometry.compartments().size()).append(" 个舱室");
        sb.append("，船体方块 ").append(geometry.hullBlocks());
        sb.append("，舱内容积 ").append(geometry.totalVolume()).append(" 格");
        sb.append(geometry.sealed() ? "，密闭正常" : "，有 " + geometry.openPockets().size() + " 处连海");
        Component head = Component.literal(sb.toString())
                .withStyle(geometry.sealed() ? ChatFormatting.AQUA : ChatFormatting.GOLD);

        Component body = Component.literal("\n  设备：" + machines.describe()).withStyle(ChatFormatting.GRAY);
        int index = 1;
        for (ShipGeometry.Compartment compartment : compartments()) {
            double capacity = Math.max(1.0D, compartment.volume() * ShipSimulation.UNITS_PER_BLOCK);
            int waterPercent = (int) Math.round(
                    simulation().waterUnits(index - 1) / capacity * 100.0D);
            body = body.copy().append(Component.literal("\n  #" + index + " 容积 " + compartment.volume()
                    + " 格，进水 " + waterPercent + "%").withStyle(ChatFormatting.DARK_GRAY));
            index++;
        }
        return head.copy().append(body);
    }

    /** 当前实时数值（不推进时间），用于面板/命令。机器删了，只报水、航向和推力。 */
    public Component liveReport() {
        ShipSimulation.Report report = simulation().report();
        String text = String.format(Locale.ROOT,
                "实时｜舱内水 %.0f 单位｜进水 %d%%｜%s %d%%",
                report.waterUnits(), (int) Math.round(floodRatio() * 100.0D),
                headingName(), power);
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    /** 快进 {@code seconds} 秒（固定 0.1 s 步长），返回一行报告。 */
    public Component simulate(double seconds) {
        ShipSimulation sim = simulation();
        int steps = Math.max(1, (int) Math.round(seconds * 10.0D));
        for (int i = 0; i < steps; i++) {
            sim.tick(0.1D);
        }
        ShipSimulation.Report report = sim.report();

        ChatFormatting color = report.meltingDown() ? ChatFormatting.RED
                : report.overheated() ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        String phase = switch (report.airlockPhase()) {
            case DRAINING -> "抽干中";
            case FLOODING -> "注水中";
            default -> "关闭";
        };
        String text = String.format(Locale.ROOT,
                "模拟 %.1fs｜温度 %.0f°C｜功率 %.0f/%.0f kW (%d%%)｜氧气 %d%%｜舱内水 %.0f 单位｜压载 %d%%｜气闸 %d%%(%s×%d)",
                seconds, report.temperature(), report.powerOutput(), report.powerDemand(),
                (int) Math.round(report.powerRatio() * 100.0D), (int) Math.round(report.oxygenPercent() * 100.0D),
                report.waterUnits(), (int) Math.round(report.ballastPercent() * 100.0D),
                (int) Math.round(report.airlockPercent() * 100.0D), phase, report.airlockCycles());
        Component line = Component.literal(text).withStyle(color);
        if (report.meltingDown()) {
            line = line.copy().append(Component.literal("  ⚠ 熔毁！").withStyle(ChatFormatting.RED));
        } else if (report.overheated()) {
            line = line.copy().append(Component.literal("  ⚠ 过热").withStyle(ChatFormatting.GOLD));
        }
        return line;
    }
}
