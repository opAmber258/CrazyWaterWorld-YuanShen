package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.ship.water.ShipFluidField;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 船网数值模拟（M3.A3）。
 *
 * <p>单位约定：1 格舱容 = 100 单位（水/氧气都用这个换算），这样 600/s 的水泵抽 273 格的舱室
 * 大约 45 秒，和《潜渊症》的手感接近。所有数值都来自 wiki：</p>
 *
 * <ul>
 *   <li>反应堆：5 000 kW，输出/温度由反应堆方块实体每 tick 上报；</li>
 *   <li>制氧机：700 氧/s 供养 1 人；每人 700/s 消耗</li>
 *   <li>水泵：300 kW，最大 600/s；压载舱容量 600/个</li>
 *   <li>引擎：2 000 kW，推力按供电比例缩放</li>
 * </ul>
 *
 * <p><b>硬核供电</b>：船上没有反应堆或反应堆没开机时 {@link #powerRatio()} 为 0，
 * 推进器和泵都停；有反应堆时按 输出/负载 比例降效。</p>
 *
 * <p>固定步长由调用方给（命令里用 0.1 s），与帧率无关。</p>
 */
public final class ShipSimulation {

    public static final double UNITS_PER_BLOCK = 100.0D;

    /**
     * 原版 {@code SubmarineBody.NeutralBallastPercentage = 0.07f}：全船水量占比
     * 7% 时浮力中性。压载泵把压载舱注到这个相对水位来保持浮力平衡。
     */
    public static final double NEUTRAL_WATER_RATIO = 0.07D;

    /**
     * 制氧机满电压（V=1）时的产氧量，单位/秒。
     *
     * <p>原作 {@code OxygenGenerator}：{@code GeneratedAmount=400}，乘 100 再按电压
     * 钳到 2，即 40,000 原始单位/秒。原作 {@code Hull.Volume} 是像素面积，1 格 ≈
     * 97.8 原始像素边长 → 面积 ≈ 9,564；本模组 1 格 = 100 单位，所以折算系数约
     * 95.64：40,000 / 95.64 ≈ 418。</p>
     */
    public static final double O2_GENERATOR_OUTPUT = 418.0D;
    /**
     * 一个乘员每秒吸走的舱室氧气。
     *
     * <p>原作 {@code Hull.OxygenConsumptionSpeed = 700}/秒，按同一系数折算 ≈ 7.32。</p>
     */
    public static final double O2_BREATH_RATE = 7.32D;
    /** 每个舱室每秒自然损耗（原作 {@code Hull}: {@code Oxygen -= 0.3f * dt}，折算 ≈ 0.0031）。 */
    private static final double O2_DECAY_RATE = 0.0031D;
    private static final double PUMP_POWER = 300.0D;
    /** 声纳台耗电：wiki 的 Navigation Terminal / Sonar Monitor 都是 100。 */

    /** 驾驶台（Nav Terminal 本体）也吃 100；我们把它和声纳合在一台机器上，所以只算一份。 */
    private static final double BALLAST_PER_TANK = 600.0D;

    private final int[] volumes;
    /** 每个密封舱的水量聚合缓存（权威数据在 {@link #fluid}）。 */
    private final double[] water;
    private final double[] oxygen;
    private final MachineCount machines;
    /** 船体几何（破口空腔、舱室体积都从这里读）。 */
    /** 整船平移时会被替换成新坐标的几何（水量/温度数组保留）。 */
    private ShipGeometry.Result geometry;
    /** 逐格水场：密封舱 + 连海空腔船壳内格 + 门格。 */
    private ShipFluidField fluid;
    /** 连海空腔（破口舱室）里的水量聚合缓存，按 openPockets 的顺序。 */
    private final double[] pocketWater;
    /**
     * 外界进水速率：每个破口空腔每秒进水（格³/秒），随深度略增。
     *
     * <p>2 格³/秒对一间 270 格的舱室要两分多钟，玩家只会以为"没进水"；
     * 20 又太快（一艘船十几秒就沉底）。先从 6 起步：一间 270 格的舱约 45 秒灌满，
     * 给玩家堵漏/排水的时间。以后按真实破口面积算时可以再精调。</p>
     */
    private static final double SEA_INFLOW_PER_SECOND = 6.0D;

    private double ballast;
    private double ballastCapacity;
    /** 压载舱中性相对水位（由 {@link #primeBallast()} 按舱容算出）。 */
    private double ballastNeutral = 0.5D;
    /** 压载舱可达的最大水位比例（受舱门限幅影响，可能小于 1）。 */
    private double ballastMax = 1.0D;
    /** 物理船第一次启动时请求一次压载注水。 */
    private boolean ballastPrimePending;
    /** 中性水位已经算好；算好之前压载泵不动作（方块船停靠时也不乱注水）。 */
    private boolean ballastPrimed;
    /** 标定时的压载舱总容量：后加/拆压载泵导致容量变化时按新容量重标中性水位。 */
    private double primedBallastCapacity = -1.0D;
    private boolean pumpOut = true;
    /** 反应堆上报表：格子 → (输出 kW, 额定 kW, 温度, 过热, 熔毁, 最后上报 tick)。 */
    private final java.util.Map<net.minecraft.core.BlockPos, ReactorReport> reactors =
            new java.util.HashMap<>();
    private long reactorTick;
    private static final int REACTOR_STALE_TICKS = 40;
    /** 引擎上报表：格子 → (目标力%, 耗电 kW, 最大推力, 耐久比例 0..1, 最后上报 tick)。 */
    private final java.util.Map<net.minecraft.core.BlockPos, EngineReport> engines =
            new java.util.HashMap<>();
    /** 推进最多吃几台引擎的推力：超出部分不叠加（和 {@code VsThruster} 的上限一致）。 */
    public static final int MAX_ENGINES = 8;
    public static final double ENGINE_POWER_EXPONENT = 1.5D;
    private long engineTick;
    private static final int ENGINE_STALE_TICKS = 40;
    private int lastEngineCount;
    private double lastEngineDemand;
    private double lastEngineMaxForce;
    private double availableDrivePowerKw;
    private double nonEngineDemandKw;

    private record EngineReport(double force, double demandKw, double maxForce,
                                double condition, double ratedDemandKw, long lastSeen) {
    }

    public record EngineCapacity(double throttle, double rawForce) {}

    private record ReactorReport(double minOutput, double maxOutput, double capacity,
                                 double temperature, boolean overheated,
                                 boolean meltingDown, long lastSeen) {
    }
    /**
     * 排水泵上报表：格子 → (速率, 最后上报 tick)。
     *
     * <p>船上可能同时有小型泵、排水口、普通泵；每台泵只管自己所在的舱室，同一舱室的
     * 多台泵按速率相加。方块实体每 tick 上报一次，{@link #PUMP_STALE_TICKS} tick
     * 没上报的视为已经拆掉。</p>
     */
    private final java.util.Map<net.minecraft.core.BlockPos, PumpReport> pumps = new java.util.HashMap<>();
    private long pumpTick;
    private static final int PUMP_STALE_TICKS = 40;

    /**
     * 制氧机上报表：格子 → 当前输出（单位/秒，已含电压与耐久）。
     *
     * <p>方块实体自己算 {@code min(电压,2) × GeneratedAmount × 100 × (耐久比)²} 并折算成
     * 本模组单位；这里只负责把多台机器的流量加起来。</p>
     */
    private final java.util.Map<net.minecraft.core.BlockPos, OxygenSourceReport> oxygenSources =
            new java.util.HashMap<>();
    private long oxygenSourceTick;
    private static final int OXYGEN_SOURCE_STALE_TICKS = 40;

    private record OxygenSourceReport(double flow, long lastSeen) {
    }

    /**
     * 通风口上报表：格子 → (所在舱室下标, 是否在工作)。
     *
     * <p>对应原作 {@code Vent}：泡在水里就不送氧（{@code item.InWater} 时不开）。制氧机
     * 按"有通风口的舱室"体积分配流量，和原作 {@code GetVents} 按 hull volume 分一致。</p>
     */
    private final java.util.Map<net.minecraft.core.BlockPos, VentReport> vents =
            new java.util.HashMap<>();
    private long ventTick;
    private static final int VENT_STALE_TICKS = 40;

    private record VentReport(int compartment, boolean active, long lastSeen) {
    }

    private record PumpReport(double rate, double requestedRate, long lastSeen, boolean ballast,
                              boolean keepBelowDoor, double ballastTarget) {
    }

    private static final class PumpRates {
        double manualOut;
        double manualIn;
        double autoOut;
        double autoIn;
        double target = -1.0D;
        boolean ballast;
        boolean keepBelowDoor;

        void add(PumpReport report) {
            ballast |= report.ballast();
            keepBelowDoor |= report.keepBelowDoor();
            double rate = report.rate();
            if (report.ballast() && report.ballastTarget() >= 0.0D) {
                target = report.ballastTarget();
                autoOut += Math.max(0.0D, rate);
                autoIn += Math.max(0.0D, -rate);
            } else {
                manualOut += Math.max(0.0D, rate);
                manualIn += Math.max(0.0D, -rate);
            }
        }
    }

    /**
     * 用电设备上报表：设备格 → (耗电 kW, 最低电压, 绑定的接线盒, 最后上报 tick)。
     *
     * <p>对应原作 {@code Powered}。设备每 tick 上报自己的需求；接线盒只是把设备
     * 归组，电压仍按全船 {@code 总供电 / 总负载} 算。</p>
     */
    private final java.util.Map<net.minecraft.core.BlockPos, ConsumerReport> consumers =
            new java.util.HashMap<>();
    private long consumerTick;
    private static final int CONSUMER_STALE_TICKS = 40;
    /** 终端（船核心）上一次作为消费者上报的 tick；没上报时退回旧的固定声纳负载。 */
    private long lastTerminalTick = Long.MIN_VALUE;
    private static final double SONAR_POWER = 100.0D;

    /** 接线盒上报表：格子 → (耐久 0..100, 最后上报 tick)。 */
    private final java.util.Map<net.minecraft.core.BlockPos, BoxReport> boxes =
            new java.util.HashMap<>();
    private long boxTick;
    private static final int BOX_STALE_TICKS = 40;

    /** 上一次 tick 的全船电压（供电/负载，不封顶）。 */
    private double lastVoltage;
    /** Positive while charging, negative while supplying the bus. */
    private final java.util.Map<net.minecraft.core.BlockPos, Double> storageFlowKw = new java.util.HashMap<>();
    /** 上一次 tick 的全船供电（kW）。 */
    private double lastSupply;
    /** 上一次 tick 电池总共放出的功率（kW）。 */
    private double lastBatteryOutput;
    /** 上一次 tick 电池总共吸收的充电功率（kW，未计效率）。 */
    private double lastBatteryCharge;
    /** 上一次 tick 结束后全船电池总存电量/总容量（kW·min）。 */
    private double lastStoredKwm;
    private double lastStorageCapacityKwm;

    private record ConsumerReport(double demandKw, double minVoltage,
                                  net.minecraft.core.BlockPos box, long lastSeen) {
    }

    private record BoxReport(double condition, long lastSeen) {
    }

    /**
     * 电池上报表：格子 → (存电量 kW·min, 容量 kW·min, 最大输出 kW, 最大充电 kW,
     * 绑定的接线盒, 耐久 0..100, 最后上报 tick)。
     *
     * <p>对应原作 {@code PowerContainer}。充放电由 {@link #tick(double)} 统一结算，
     * 方块实体每 tick 把结算后的电量读回去存档。</p>
     */
    private final java.util.Map<net.minecraft.core.BlockPos, StorageReport> storages =
            new java.util.HashMap<>();
    private long storageTick;
    private static final int STORAGE_STALE_TICKS = 40;
    /**
     * 原作电池充电效率 0.95：充进去 100 kW 只有 95 kW 存下来。
     * 超级电容器是 0.1，走 {@link #reportStorage(BlockPos, double, double, double, double, BlockPos, double, double)}
     * 的 efficiency 参数单独上报。
     */
    private static final double BATTERY_EFFICIENCY = 0.95D;

    private record StorageReport(double chargeKwm, double capacityKwm, double maxOutputKw,
                                 double maxRechargeKw, net.minecraft.core.BlockPos box,
                                 double condition, double efficiency, long lastSeen) {
    }

    /** 电池快照，给电力面板/诊断用。 */
    public record StorageInfo(net.minecraft.core.BlockPos pos, double chargeKwm,
                              double capacityKwm, double maxOutputKw, double maxRechargeKw,
                              net.minecraft.core.BlockPos box, double condition) {
    }

    /** 供电设备快照，给电力面板用。 */
    public record ConsumerInfo(net.minecraft.core.BlockPos pos, double demandKw,
                               double minVoltage, net.minecraft.core.BlockPos box) {
    }

    /** 格子 → 水体下标的缓存；换几何时整个 simulated 对象会重建，缓存自然失效。 */
    private java.util.Map<net.minecraft.core.BlockPos, Integer> bodyIndexOfCell;
    /**
     * 破口格数。洞越大进水越快——潜渊症按 Gap 的开口面积算，
     * 我们先按"被砸掉的船体格数"线性放大，至少 1 格。
     */
    private int breachSize = 1;

    private double lastOutput;
    private double lastDemand;
    private double lastRatio;
    private double reactorTemperature;
    private boolean reactorOverheated;
    private boolean reactorMeltingDown;

    /** 气闸舱室容积（27 格 = 2700 单位）。 */
    private static final double AIRLOCK_VOLUME = 2700.0D;
    private static final double AIRLOCK_POWER = 300.0D;
    private static final double AIRLOCK_RATE = 600.0D;

    /** 气闸循环阶段。 */
    public enum AirlockPhase {
        CLOSED, DRAINING, FLOODING
    }

    private AirlockPhase airlockPhase = AirlockPhase.CLOSED;
    private double airlockWater = AIRLOCK_VOLUME;
    private int airlockCycles;

    public ShipSimulation(ShipGeometry.Result geometry, MachineCount machines) {
        this(geometry, machines, java.util.Set.of());
    }

    public ShipSimulation(ShipGeometry.Result geometry, MachineCount machines,
                          java.util.Set<net.minecraft.core.BlockPos> knownInterior) {
        List<ShipGeometry.Compartment> compartments = geometry.compartments();
        int size = compartments.size();
        volumes = new int[size];
        water = new double[size];
        oxygen = new double[size];
        for (int i = 0; i < size; i++) {
            volumes[i] = compartments.get(i).volume();
            // 满氧起手是刻意对齐原作：Hull.cs 里没有 oxygen 属性时
            // `hull.Oxygen = hull.Volume`（100%）。所以"不装制氧机也有氧气"不是漏氧，
            // 只有乘员呼吸（7.32/秒/人）和自然损耗（0.0031/秒）会让它下降；
            // 需要无氧开局的硬核玩法时再改这里（用户 2026-11-17 选定保持原作）。
            oxygen[i] = compartments.get(i).volume() * UNITS_PER_BLOCK;
        }
        this.machines = machines;
        this.ballastCapacity = machines.ballast() * BALLAST_PER_TANK;
        this.geometry = geometry;
        this.pocketWater = new double[geometry.openPockets().size()];
        this.fluid = ShipFluidField.fromGeometry(geometry, knownInterior);
        // 世界里的真实水块只在方块船扫描时存在；物理船 probe.isWater 恒 false。
        // 把它按底往上铺进逐格场，空腔也不再像旧版那样固定从 0 开始。
        for (int i = 0; i < size; i++) {
            double units = compartments.get(i).waterBlocks() * UNITS_PER_BLOCK;
            if (units > 0.0D) {
                fluid.addBody(i, units, Double.POSITIVE_INFINITY);
            }
        }
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        for (int j = 0; j < pockets.size(); j++) {
            double units = pockets.get(j).waterBlocks() * UNITS_PER_BLOCK;
            if (units > 0.0D) {
                fluid.addBody(size + j, units, Double.POSITIVE_INFINITY);
            }
        }
        refreshWaterAggregates();
    }

    /**
     * 破口进水：只从真实破口格往相邻舱内格注水，再由逐格场向四周流动。
     *
     * <p>旧版是按整个连海空腔的容量直接灌一个标量，破口在哪一格没有意义。现在每个
     * 空腔的总速率仍按原作 {@code 500 * Size * open² * dt} 的量级折算。水量计入
     * 接水的舱，实际注入量再分给各个破口用于绘制来水路径。</p>
     */
    public void tickSeaFlood(double dt, double depthMeters) {
        tickSeaFlood(dt, depthMeters, List.of());
    }

    public void tickSeaFlood(double dt, double depthMeters,
                             java.util.Collection<net.minecraft.core.BlockPos> breachCells) {
        if (dt <= 0.0D || fluid == null) {
            return;
        }
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        if (pockets.isEmpty()) {
            return;
        }
        int sealedCount = geometry.compartments().size();
        double depthFactor = 1.0D + Math.max(0.0D, depthMeters) / 100.0D;
        double pocketRate = SEA_INFLOW_PER_SECOND * depthFactor * Math.max(1, breachSize);
        Set<BlockPos> openings = breachCells == null ? Set.of() : new LinkedHashSet<>(breachCells);
        // 破口格本身通常属于"船外空气"那个大空腔（被打掉的墙格），按格归属会把
        // 整股进水灌到船外。这里按破口四周的格子找真正接水的舱内空腔，并优先
        // 取体量最小的那个（房间而不是包住它的外部空气）。
        Map<Integer, List<BlockPos>> holesByPocket = new HashMap<>();
        for (BlockPos breach : openings) {
            int pocket = pocketForBreach(breach);
            if (pocket >= 0) {
                holesByPocket.computeIfAbsent(pocket, key -> new ArrayList<>()).add(breach);
            }
        }
        java.util.Set<Integer> seaDoorBodies = new java.util.HashSet<>();
        for (int index : fluid.seaDoorCells()) {
            int body = fluid.bodyOf(fluid.posAt(index));
            if (body >= 0) {
                seaDoorBodies.add(body);
            }
        }
        for (int pocket = 0; pocket < pockets.size(); pocket++) {
            int body = sealedCount + pocket;
            // 场里没有这个 body（纯船外空气）就不接水，否则只是把水灌进海里。
            if (fluid.bodyCapacity(body) <= 0.0D) {
                continue;
            }
            List<BlockPos> holes = holesByPocket.getOrDefault(pocket, List.of());
            if (holes.isEmpty()) {
                // 有破口信息时，没有洞的空腔不进水；只有调用方完全没给破口
                // （旧存档/测试）才按空腔底部兜底灌水。
                if (openings.isEmpty() && !seaDoorBodies.contains(body)) {
                    fluid.injectFlood(body, pocketRate * UNITS_PER_BLOCK * dt,
                            DOOR_FLOW_PER_CELL * dt);
                }
                continue;
            }
            // 本舱灌满后沿开着的舱门/舱口继续往相邻舱溢：破口一直进水时，
            // 水位必须能沿开着的水平舱口继续往上涌，不能把多余的水丢掉。
            // 溢流也受每道门每步的流量预算限制（DOOR_FLOW_PER_CELL * dt）。
            // 注意：pocketRate 已经乘过破口数（breachSize），这里不能再除一次，
            // 否则“破口越多进得越快”会被抵消；只有绘制来水路径时才按洞平分。
            double added = fluid.injectFlood(body, pocketRate * UNITS_PER_BLOCK * dt,
                    DOOR_FLOW_PER_CELL * dt);
            double amountPerHole = added / holes.size();
            for (BlockPos breach : holes) {
                BlockPos receiver = fluid.breachReceiver(breach, body, openings);
                fluid.recordInflow(breach, receiver, amountPerHole);
            }
        }
        refreshWaterAggregates();
    }

    /**
     * 破口往哪个连海空腔灌水：看破口自己和上下左右六个邻居落在哪个空腔里。
     *
     * <p>被打掉的船壳格连接外海，几何上属于船外空腔；真正的舱内格在洞口内侧。
     * 所以优先选"包含破口邻居、但不包含破口本身"的空腔；同为候选时再优先
     * 船壳内有舱内格的、体量最小的那个（房间而不是包住它的外部空气）。</p>
     */
    int pocketForBreach(BlockPos breach) {
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        int sealedCount = geometry.compartments().size();
        int best = -1;
        int bestRank = Integer.MAX_VALUE;
        int bestVolume = Integer.MAX_VALUE;
        net.minecraft.core.BlockPos[] around = {
                breach.above(), breach.below(),
                breach.north(), breach.south(), breach.east(), breach.west()};
        for (int j = 0; j < pockets.size(); j++) {
            if (fluid.bodyCapacity(sealedCount + j) <= 0.0D) {
                continue;
            }
            ShipGeometry.Compartment pocket = pockets.get(j);
            boolean neighbourHit = false;
            for (net.minecraft.core.BlockPos cell : around) {
                if (pocket.cells().contains(cell)) {
                    neighbourHit = true;
                    break;
                }
            }
            if (!neighbourHit) {
                continue;
            }
            int rank;
            if (pocket.cells().contains(breach)) {
                // 破口格自己也在里面：这是船外那一侧的空腔，最后才选。
                rank = 2;
            } else if (pocket.interiorVolume() > 0) {
                rank = 0;
            } else {
                rank = 1;
            }
            int volume = pocket.cells().size();
            if (rank < bestRank || (rank == bestRank && volume < bestVolume)) {
                best = j;
                bestRank = rank;
                bestVolume = volume;
            }
        }
        return best;
    }

    /**
     * 船体破损后重新分舱：保留压载/气闸这些"玩家调过的设置"，
     * 只重建舱室水量数组。反应堆状态在方块实体里，不跟着 ShipSimulation 重建丢失。
     */
    public void copySettingsFrom(ShipSimulation old) {
        if (old == null) {
            return;
        }
        this.ballast = old.ballast;
        this.ballastCapacity = old.ballastCapacity;
        this.ballastNeutral = old.ballastNeutral;
        this.ballastPrimed = old.ballastPrimed;
        this.ballastPrimePending = old.ballastPrimePending;
        this.ballastMax = old.ballastMax;
        this.pumpOut = old.pumpOut;
        this.airlockPhase = old.airlockPhase;
        this.airlockWater = old.airlockWater;
        this.airlockCycles = old.airlockCycles;
    }

    /**
     * 破损重分舱时把旧水量带进新几何。
     *
     * <p>每砸一个新洞都会重建 `ShipSimulation`；如果水量从 0 开始，
     * 玩家连续砸几格就会看到水位每次归零（"进水效果不对"）。
     * 密闭舱室和连海空腔各按容积比例分配；某一类在新船里不存在时并入另一类。</p>
     */
    public void seedWater(double sealedUnits, double pocketUnits) {
        if (fluid == null) {
            return;
        }
        fluid.seed(java.util.Map.of());
        double sealedCapacity = 0.0D;
        for (int volume : volumes) {
            sealedCapacity += volume * UNITS_PER_BLOCK;
        }
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        double pocketCapacity = 0.0D;
        for (int j = 0; j < pockets.size(); j++) {
            pocketCapacity += fluid.bodyCapacity(volumes.length + j);
        }
        double sealed = Math.max(0.0D, sealedUnits);
        double pocket = Math.max(0.0D, pocketUnits);
        if (pocketCapacity <= 0.0D) {
            sealed += pocket;
            pocket = 0.0D;
        }
        if (sealedCapacity <= 0.0D) {
            pocket += sealed;
            sealed = 0.0D;
        }
        if (sealedCapacity > 0.0D && sealed > 0.0D) {
            double amount = Math.min(sealed, sealedCapacity);
            for (int i = 0; i < water.length; i++) {
                fluid.addBody(i, amount * (volumes[i] * UNITS_PER_BLOCK / sealedCapacity),
                        Double.POSITIVE_INFINITY);
            }
        }
        if (pocketCapacity > 0.0D && pocket > 0.0D) {
            double amount = Math.min(pocket, pocketCapacity);
            for (int j = 0; j < pockets.size(); j++) {
                int body = volumes.length + j;
                fluid.addBody(body, amount * (fluid.bodyCapacity(body) / pocketCapacity),
                        Double.POSITIVE_INFINITY);
            }
        }
        refreshWaterAggregates();
    }

    /**
     * 开着的门每秒能过多水（单位/秒/格）。
     *
     * <p>原版 {@code Gap.GetWaterFlowFromOutside} 是 {@code 500 * Size * open²}
     * （Size 是门缝像素宽），房间之间的过水量则是 {@code Δ压力 * 300 * (Size/100) * dt}。
     * 换算成我们的单位（1 格 = 100）后，一扇 1×2 的门每秒大约能过几十格³，开门后
     * 水是"涌"进来的，几秒钟就拉平，而不是细水长流。3000 单位/格/秒 = 30 格³/秒/格。</p>
     */
    private static final double DOOR_FLOW_PER_CELL = 3000.0D;

    /** 第 index 个连海空腔当前水量（单位）。 */
    public double pocketWater(int index) {
        return index >= 0 && index < pocketWater.length ? pocketWater[index] : 0.0D;
    }

    /** 所有连海空腔的总水量（单位）。 */
    public double pocketWaterTotal() {
        double total = 0.0D;
        for (double value : pocketWater) {
            total += value;
        }
        return total;
    }

    /** 全船逐格水量合计（单位）；给存档标脏等粗粒度比较用。 */
    public double waterTotal() {
        return fluid == null ? 0.0D : fluid.totalWater();
    }

    /** 推进一个时间步（秒）。 */
    public void tick(double dt) {
        // 反应堆：方块实体每 tick 上报；超过 40 tick 没上报视为已经拆掉。
        reactorTick++;
        double reactorCapacity = 0.0D;
        double availablePowerKw = 0.0D;
        double temperature = 0.0D;
        boolean overheated = false;
        boolean meltingDown = false;
        java.util.List<java.util.Map.Entry<net.minecraft.core.BlockPos, ReactorReport>>
                activeReactors = new java.util.ArrayList<>();
        var reactorIterator = reactors.entrySet().iterator();
        while (reactorIterator.hasNext()) {
            var entry = reactorIterator.next();
            ReactorReport report = entry.getValue();
            if (reactorTick - report.lastSeen() > REACTOR_STALE_TICKS) {
                reactorIterator.remove();
                continue;
            }
            activeReactors.add(entry);
            reactorCapacity += report.capacity();
            availablePowerKw += report.maxOutput();
            temperature = Math.max(temperature, report.temperature());
            overheated |= report.overheated();
            meltingDown |= report.meltingDown();
        }
        reactorTemperature = temperature;
        reactorOverheated = overheated;
        reactorMeltingDown = meltingDown;

        // 引擎：方块实体每 tick 上报；多台相加。没有引擎时 engineCount=0，推进由 VsThruster 关掉。
        engineTick++;
        int engineCount = 0;
        double engineDemand = 0.0D;
        double engineMaxForce = 0.0D;
        var engineIterator = engines.entrySet().iterator();
        while (engineIterator.hasNext()) {
            var entry = engineIterator.next();
            EngineReport report = entry.getValue();
            if (engineTick - report.lastSeen() > ENGINE_STALE_TICKS) {
                engineIterator.remove();
                continue;
            }
            engineCount++;
            engineDemand += report.demandKw();
            engineMaxForce += report.maxForce();
        }
        lastEngineCount = engineCount;
        lastEngineDemand = engineDemand;
        lastEngineMaxForce = engineMaxForce;

        // 只在真正抽/注水的泵算负载；停机待命的泵不吃电。
        int activePumps = 0;
        for (PumpReport pump : pumps.values()) {
            if (Math.abs(pump.requestedRate()) > 1.0E-6D) {
                activePumps++;
            }
        }

        // 接线盒上报（耐久）：超过 40 tick 没上报视为拆掉。先清理，再算负载。
        boxTick++;
        var boxIterator = boxes.entrySet().iterator();
        while (boxIterator.hasNext()) {
            var entry = boxIterator.next();
            if (boxTick - entry.getValue().lastSeen() > BOX_STALE_TICKS) {
                boxIterator.remove();
            }
        }

        // 设备上报的电网负载（原作 Powered）：每台设备有自己的功率和最低电压。
        consumerTick++;
        double consumerDemand = 0.0D;
        double connectedEngineDemand = 0.0D;
        var consumerIterator = consumers.entrySet().iterator();
        while (consumerIterator.hasNext()) {
            var entry = consumerIterator.next();
            ConsumerReport report = entry.getValue();
            if (consumerTick - report.lastSeen() > CONSUMER_STALE_TICKS) {
                consumerIterator.remove();
                continue;
            }
            // 支路断开（接线盒拆掉/耐久 0）的设备不再吃全船供电：
            // 把它的需求算进来会把正常支路也拖到欠压停机。
            if (report.box() != null) {
                BoxReport box = boxes.get(report.box());
                if (box == null || box.condition() <= 0.0D) {
                    continue;
                }
            }
            consumerDemand += report.demandKw();
            if (engines.containsKey(entry.getKey())) {
                connectedEngineDemand += report.demandKw();
            }
        }

        // 电池上报：超过 40 tick 没上报视为拆掉。电量在下面的充放电结算里更新。
        storageTick++;
        var storageIterator = storages.entrySet().iterator();
        while (storageIterator.hasNext()) {
            var entry = storageIterator.next();
            if (storageTick - entry.getValue().lastSeen() > STORAGE_STALE_TICKS) {
                storageIterator.remove();
            }
        }

        // 还没改造完的设备走旧的全船统计；全部改造后 consumers 非空，用设备上报值。
        double legacyDemand = activePumps * PUMP_POWER + engineDemand;
        // 终端（船核心）已经作为消费者上报 110 kW 时不能再按 machines.sonar()
        // 计一遍（会变成 210 kW）；还没接入上报的旧船才用固定负载兜底。
        boolean terminalReporting = lastTerminalTick != Long.MIN_VALUE
                && consumerTick - lastTerminalTick <= CONSUMER_STALE_TICKS;
        // 制氧机自己作为 PowerConsumer 上报 1000 kW
        // （OxygenGeneratorBlockEntity.POWER_KW），这里不能再按 machines.oxygen()
        // 计一遍——两边都算就是双计。
        double deviceDemand = (consumers.isEmpty() ? legacyDemand : consumerDemand)
                + (terminalReporting ? 0.0D : machines.sonar() * SONAR_POWER)
                + (airlockPhase == AirlockPhase.CLOSED ? 0.0D : AIRLOCK_POWER);

        // 电池“请求”的充电功率：即使当前没有富余也要报给反应堆（原作
        // Grid.Load 包含 PowerContainer 的充电需求），让自动控堆把目标负载
        // 抬到 设备负载 + 充电请求；否则堆永远只发刚好够设备的电，电池永远
        // 等不到富余（用户实测“电池不会充电”）。
        double rechargeRequest = 0.0D;
        java.util.Map<net.minecraft.core.BlockPos, Double> rechargeDemand =
                new java.util.HashMap<>();
        if (!storages.isEmpty()) {
            double step = Math.max(dt, 1.0E-9D);
            for (var entry : storages.entrySet()) {
                StorageReport report = entry.getValue();
                if (!storageUsable(report)) {
                    continue;
                }
                double room = report.capacityKwm() - report.chargeKwm();
                if (room <= 1.0E-9D) {
                    continue;
                }
                // room 是 kW·min，换成本步充满所需的平均功率，再和额定充电功率取小。
                double demand = Math.min(report.maxRechargeKw() * Math.min(1.0D, room), room * 60.0D / step);
                rechargeDemand.put(entry.getKey(), demand);
                rechargeRequest += demand;
            }
        }

        // 反应堆出力（原作 GetConnectionPowerOut）：按“剩余需求”出力，并夹在
        // 本机出力带 [min, max] 里；多台堆按装配坐标顺序依次吃需求。负载含
        // 电池充电请求，和原作 Grid.Load 一致。负载在带内时输出正好等于负载
        // （电压 1.0）；负载低于带底时反应堆仍会发到带底，这才是过压的来源。
        double reactorOutput = 0.0D;
        double remainingDemand = deviceDemand + rechargeRequest;
        activeReactors.sort(java.util.Map.Entry.comparingByKey());
        for (var entry : activeReactors) {
            ReactorReport report = entry.getValue();
            double out = Mth.clamp(remainingDemand, report.minOutput(), report.maxOutput());
            reactorOutput += out;
            remainingDemand = Math.max(0.0D, remainingDemand - out);
        }

        // PowerContainer: parallel supplies share the deficit in proportion to available output.
        storageFlowKw.clear();
        double batteryOutput = 0.0D;
        if (deviceDemand > reactorOutput + 1.0E-9D) {
            double batteryCapacity = 0.0D;
            for (var entry : storages.entrySet()) {
                StorageReport report = entry.getValue();
                if (!storageUsable(report)) continue;
                double available = PowerGrid.batteryOutputAvailable(
                        report.chargeKwm(), report.capacityKwm(), report.maxOutputKw(), dt);
                if (available <= 0.0D) continue;
                storageFlowKw.put(entry.getKey(), -available);
                batteryCapacity += available;
            }
            if (batteryCapacity > 0.0D) {
                batteryOutput = Math.min(deviceDemand - reactorOutput, batteryCapacity);
                double share = batteryOutput / batteryCapacity;
                storageFlowKw.replaceAll((cell, available) -> available * share);
            }
        }

        // Model the battery charge side with the reactor surplus on our shared bus.
        double rechargeInput = 0.0D;
        if (reactorOutput > deviceDemand && rechargeRequest > 0.0D) {
            rechargeInput = Math.min(reactorOutput - deviceDemand, rechargeRequest);
            double share = rechargeInput / rechargeRequest;
            for (var entry : rechargeDemand.entrySet()) {
                storageFlowKw.put(entry.getKey(), entry.getValue() * share);
            }
        }

        // 结算电池电量（正=充，负=放）；充电乘效率。
        if (!storageFlowKw.isEmpty()) {
            for (var entry : storages.entrySet()) {
                double flow = storageFlowKw.getOrDefault(entry.getKey(), 0.0D);
                double delta = flow >= 0.0D
                        ? PowerGrid.batteryChargeDeltaKwm(flow, dt, entry.getValue().efficiency())
                        : -PowerGrid.batteryChargeDeltaKwm(-flow, dt, 1.0D);
                if (Math.abs(delta) <= 1.0E-9D) {
                    continue;
                }
                StorageReport report = entry.getValue();
                double charge = Mth.clamp(report.chargeKwm() + delta, 0.0D,
                        report.capacityKwm());
                entry.setValue(new StorageReport(charge, report.capacityKwm(),
                        report.maxOutputKw(), report.maxRechargeKw(), report.box(),
                        report.condition(), report.efficiency(), report.lastSeen()));
            }
        }

        double storedKwm = 0.0D;
        double storageCapacityKwm = 0.0D;
        for (StorageReport report : storages.values()) {
            storedKwm += report.chargeKwm();
            storageCapacityKwm += report.capacityKwm();
            if (storageUsable(report)) {
                availablePowerKw += PowerGrid.batteryOutputAvailable(
                        report.chargeKwm(), report.capacityKwm(), report.maxOutputKw(), dt);
            }
        }
        availableDrivePowerKw = availablePowerKw;
        nonEngineDemandKw = Math.max(0.0D, deviceDemand - (consumers.isEmpty() ? engineDemand : connectedEngineDemand));

        double supply = reactorOutput + batteryOutput;
        double demand = deviceDemand + rechargeInput;
        // 硬核：没堆 / 没开机 / 总供电为 0 → 全船没电；电池有电也算供电。
        boolean hasReactor = reactorCapacity > 0.0D;
        lastSupply = supply;
        lastVoltage = hasReactor || supply > 0.0D
                ? PowerGrid.voltage(supply, demand) : 0.0D;
        lastOutput = reactorOutput;
        // 给反应堆自动控制看的“目标负载”要含电池充电请求，否则堆只追设备负载，
        // 电池永远没有富余可充。电压仍按实际吸收的充电功率算（见上面的 demand）。
        lastDemand = deviceDemand + rechargeRequest;
        lastBatteryOutput = batteryOutput;
        lastBatteryCharge = rechargeInput;
        lastStoredKwm = storedKwm;
        lastStorageCapacityKwm = storageCapacityKwm;
        // 兼容旧代码：0..1 的“够不够电”，过压部分由 lastVoltage 单独表达。
        lastRatio = Math.min(1.0D, lastVoltage);
        double ratio = lastRatio;

        // 制氧：制氧机把流量按"有通风口的舱室"体积分配（原作 GetVents 按 hull volume 分）。
        // 没有通风口的舱室拿不到氧——这就是原作"制氧机停机/管路断了 → 舱室变闷"的来源。
        oxygenSourceTick++;
        double oxygenFlow = 0.0D;
        var oxygenSourceIterator = oxygenSources.entrySet().iterator();
        while (oxygenSourceIterator.hasNext()) {
            var entry = oxygenSourceIterator.next();
            if (oxygenSourceTick - entry.getValue().lastSeen() > OXYGEN_SOURCE_STALE_TICKS) {
                oxygenSourceIterator.remove();
                continue;
            }
            oxygenFlow += entry.getValue().flow();
        }
        ventTick++;
        int ventedVolume = 0;
        int[] ventedVolumes = new int[oxygen.length];
        var ventIterator = vents.entrySet().iterator();
        while (ventIterator.hasNext()) {
            var entry = ventIterator.next();
            VentReport report = entry.getValue();
            if (ventTick - report.lastSeen() > VENT_STALE_TICKS) {
                ventIterator.remove();
                continue;
            }
            int compartment = report.compartment();
            if (!report.active() || compartment < 0 || compartment >= oxygen.length
                    || ventedVolumes[compartment] > 0) {
                continue;
            }
            ventedVolumes[compartment] = volumes[compartment];
            ventedVolume += volumes[compartment];
        }
        if (oxygenFlow > 0.0D) {
            if (ventedVolume > 0) {
                for (int i = 0; i < oxygen.length; i++) {
                    if (ventedVolumes[i] <= 0) {
                        continue;
                    }
                    oxygen[i] += oxygenFlow * dt * ventedVolumes[i] / (double) ventedVolume;
                }
            }
            // 没有（可用的）通风口 → 这艘船拿不到制氧机的氧，对应原作"管路没接"。
        }
        // 自然损耗 + 容量钳制（原作 Hull.Oxygen 钳在 0..Volume）。
        for (int i = 0; i < oxygen.length; i++) {
            double capacity = volumes[i] * UNITS_PER_BLOCK;
            oxygen[i] = Math.max(0.0D, Math.min(capacity, oxygen[i] - O2_DECAY_RATE * dt));
        }

        tickPumps(dt);

        // 气闸：抽干 / 注水，完成后自动回到 CLOSED（此时另一侧门才允许开）
        if (airlockPhase == AirlockPhase.DRAINING) {
            airlockWater = Math.max(0.0D, airlockWater - AIRLOCK_RATE * ratio * dt);
            if (airlockWater <= 0.0D) {
                airlockPhase = AirlockPhase.CLOSED;
                airlockCycles++;
            }
        } else if (airlockPhase == AirlockPhase.FLOODING) {
            airlockWater = Math.min(AIRLOCK_VOLUME, airlockWater + AIRLOCK_RATE * ratio * dt);
            if (airlockWater >= AIRLOCK_VOLUME) {
                airlockPhase = AirlockPhase.CLOSED;
                airlockCycles++;
            }
        }
    }

    private void tickPumps(double dt) {
        pumpTick++;
        Map<Integer, PumpRates> ratesByBody = new HashMap<>();
        var pumpIterator = pumps.entrySet().iterator();
        while (pumpIterator.hasNext()) {
            var entry = pumpIterator.next();
            PumpReport report = entry.getValue();
            if (pumpTick - report.lastSeen() > PUMP_STALE_TICKS) {
                pumpIterator.remove();
                continue;
            }
            int body = pumpBody(entry.getKey());
            if (body >= 0) {
                ratesByBody.computeIfAbsent(body, ignored -> new PumpRates()).add(report);
            }
        }

        Set<Integer> ballastBodies = new LinkedHashSet<>();
        double usable = 0.0D;
        ballast = 0.0D;
        ballastCapacity = 0.0D;
        for (var entry : ratesByBody.entrySet()) {
            int body = entry.getKey();
            PumpRates rates = entry.getValue();
            double limit = rates.keepBelowDoor ? doorBottomLevel(body) : Double.MAX_VALUE;
            if (rates.manualOut > 0.0D) {
                removeBodyWater(body, rates.manualOut * dt);
            }
            if (rates.manualIn > 0.0D) {
                addPumpedWater(body, rates.manualIn * dt, limit);
            }
            if (rates.target >= 0.0D) {
                double difference = rates.target * ballastUsableCapacity(body) - bodyWater(body);
                if (difference > 0.0D && rates.autoIn > 0.0D) {
                    addPumpedWater(body, Math.min(difference, rates.autoIn * dt), limit);
                } else if (difference < 0.0D && rates.autoOut > 0.0D) {
                    removeBodyWater(body, Math.min(-difference, rates.autoOut * dt));
                }
            }

            // 停机或没电的压载泵仍代表所在舱，浮力继续计入舱里的水。
            if (rates.ballast) {
                ballastBodies.add(body);
                ballast += bodyWater(body);
                ballastCapacity += bodyCapacity(body);
                usable += ballastUsableCapacity(body);
            }
        }

        ballastMax = ballastCapacity > 0.0D ? usable / ballastCapacity : 1.0D;
        if (ballastPrimed && Math.abs(ballastCapacity - primedBallastCapacity) > 1.0E-6D) {
            // 压载舱集合变了（后加/拆了压载泵）：中性水位按新容量重算，
            // 但不再注水，避免“放一台泵就往舱里灌水”。
            recalibrateBallast(usable);
            primedBallastCapacity = ballastCapacity;
        }
        if (ballastPrimePending && ballastCapacity > 0.0D) {
            primeBallast(ballastBodies, usable);
            ballastPrimePending = false;
            primedBallastCapacity = ballastCapacity;
        }
    }

    private void addPumpedWater(int body, double units, double limit) {
        double capacity = limit < Double.MAX_VALUE ? unitsBelowLevel(body, limit) : bodyCapacity(body);
        double amount = Math.min(units, capacity - bodyWater(body));
        if (amount > 0.0D) {
            addBodyWater(body, amount, limit);
        }
    }

    /** 请求抽干气闸（出舱）。没有气闸或已经是空的返回 false。 */
    public boolean requestDrain() {
        if (machines.airlock() <= 0 || airlockPhase != AirlockPhase.CLOSED || airlockWater <= 0.0D) {
            return false;
        }
        airlockPhase = AirlockPhase.DRAINING;
        return true;
    }

    /** 请求给气闸注水（回船）。没有气闸或已经满了返回 false。 */
    public boolean requestFlood() {
        if (machines.airlock() <= 0 || airlockPhase != AirlockPhase.CLOSED
                || airlockWater >= AIRLOCK_VOLUME) {
            return false;
        }
        airlockPhase = AirlockPhase.FLOODING;
        return true;
    }

    public AirlockPhase airlockPhase() {
        return airlockPhase;
    }

    public double airlockWater() {
        return airlockWater;
    }

    public int airlockCycles() {
        return airlockCycles;
    }

    /** 设置破口格数（>=1）；洞越多进水越快。 */
    public void setBreachSize(int size) {
        this.breachSize = Math.max(1, size);
    }

    /** 直接往第 index 个舱室灌水（测试/事件用）。 */
    public void flood(int index, double units) {
        addWater(index, units);
    }

    // ------------------------------------------------------------------ 门两侧的水

    /**
     * 水体下标（{@link ShipGeometry.DoorLink} 用的那套）：先密闭舱室 0..n-1，
     * 再连海空腔 n..n+m-1。
     */
    public int bodyCount() {
        return water.length + pocketWater.length;
    }

    /** 第 body 个水体的水位（格）：场里最高的水面；干 body 取最低格底。 */
    public double bodyLevel(int body) {
        return fluid == null ? 0.0D : fluid.bodyLevel(body);
    }

    /**
     * 连海空腔里船壳格的实际水量（单位）。
     *
     * <p>逐格场本身就只包含船壳内格，所以直接取该 body 的聚合；不会再出现
     * "包围盒四角的外海水被算进进水比例"。</p>
     */
    public double interiorWater(int pocketIndex, java.util.Set<net.minecraft.core.BlockPos> interior,
                                java.util.Collection<net.minecraft.core.BlockPos> breachSources) {
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        if (pocketIndex < 0 || pocketIndex >= pockets.size() || fluid == null) {
            return 0.0D;
        }
        return fluid.bodyWater(geometry.compartments().size() + pocketIndex);
    }

    /** 第 body 个水体的格数（以逐格场为准，空腔不含船壳外格）。 */
    public int bodyVolume(int body) {
        return fluid == null ? 0 : (int) Math.round(fluid.bodyCapacity(body) / UNITS_PER_BLOCK);
    }

    /**
     * 某一格的实际水位（格）：直接读逐格场。
     *
     * <p>空格返回自己的格底 y，场外返回 0；调用方（机器泡水判定）通常先看
     * {@link #bodyIsWet(int)}，所以不需要再按整舱水位兜底。</p>
     */
    public double waterLevelAt(net.minecraft.core.BlockPos cell,
                               java.util.Collection<net.minecraft.core.BlockPos> breachSources) {
        return fluid == null ? 0.0D : fluid.levelAt(cell);
    }

    /**
     * 整船平移：几何换成新坐标，水量/温度数组保留（舱室数量/体积不变）。
     *
     * <p>不换几何的话 {@code bodyOf()}/{@code waterLevelAt()} 还按旧坐标查，
     * 平移后接线盒会被判成不在任何舱室、泡水判定也拿不到列水位。</p>
     */
    public void shiftGeometry(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        java.util.Map<net.minecraft.core.BlockPos, Double> carried = fluid == null
                ? java.util.Map.of() : fluid.toMap();
        java.util.Map<net.minecraft.core.BlockPos, Double> shifted = new java.util.HashMap<>();
        java.util.Set<net.minecraft.core.BlockPos> shiftedCells = new java.util.HashSet<>();
        if (fluid != null) {
            for (net.minecraft.core.BlockPos pos : fluid.cellPositions()) {
                shiftedCells.add(pos.offset(dx, dy, dz));
            }
        }
        for (var entry : carried.entrySet()) {
            shifted.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        this.geometry = geometry.shifted(dx, dy, dz);
        this.bodyIndexOfCell = null;
        this.fluid = ShipFluidField.fromGeometry(this.geometry, shiftedCells);
        this.fluid.seed(shifted);
        refreshWaterAggregates();
    }

    /**
     * 整船平移后迁移上报表：键和绑定的接线盒都加上偏移，上报 tick 保持不变。
     *
     * <p>模拟实例在 {@code ShipState.shifted()} 里原样复用；不迁移的话旧坐标的
     * 记录会和设备按新坐标上报的记录同时存在，同一台泵在 40 tick 内被计两份需求。</p>
     */
    public void shiftReports(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        java.util.Map<net.minecraft.core.BlockPos, ConsumerReport> movedConsumers =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, ConsumerReport> entry
                : consumers.entrySet()) {
            ConsumerReport report = entry.getValue();
            net.minecraft.core.BlockPos box = report.box() == null ? null
                    : report.box().offset(dx, dy, dz);
            movedConsumers.put(entry.getKey().offset(dx, dy, dz),
                    new ConsumerReport(report.demandKw(), report.minVoltage(), box,
                            report.lastSeen()));
        }
        consumers.clear();
        consumers.putAll(movedConsumers);
        java.util.Map<net.minecraft.core.BlockPos, BoxReport> movedBoxes =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, BoxReport> entry
                : boxes.entrySet()) {
            movedBoxes.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        boxes.clear();
        boxes.putAll(movedBoxes);
        // 引擎/反应堆报告也要跟着走：engineSupplyFactor 用引擎格子去查消费者，
        // 旧格子查不到已迁移的消费者会回退到默认总线电压，损坏支路因此产生推力。
        java.util.Map<net.minecraft.core.BlockPos, EngineReport> movedEngines =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, EngineReport> entry
                : engines.entrySet()) {
            movedEngines.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        engines.clear();
        engines.putAll(movedEngines);
        java.util.Map<net.minecraft.core.BlockPos, ReactorReport> movedReactors =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, ReactorReport> entry
                : reactors.entrySet()) {
            movedReactors.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        reactors.clear();
        reactors.putAll(movedReactors);
        // 泵上报表也要平移：旧位置的泵记录会继续按旧舱室抽水，和新位置记录叠加。
        java.util.Map<net.minecraft.core.BlockPos, PumpReport> movedPumps =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, PumpReport> entry
                : pumps.entrySet()) {
            movedPumps.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        pumps.clear();
        pumps.putAll(movedPumps);
        // 电池上报表也要跟着走：旧位置的电池会继续充放电 40 tick，
        // 同一块电池被重复计入容量/供电。
        java.util.Map<net.minecraft.core.BlockPos, StorageReport> movedStorages =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, StorageReport> entry
                : storages.entrySet()) {
            StorageReport report = entry.getValue();
            net.minecraft.core.BlockPos box = report.box() == null ? null
                    : report.box().offset(dx, dy, dz);
            movedStorages.put(entry.getKey().offset(dx, dy, dz),
                    new StorageReport(report.chargeKwm(), report.capacityKwm(),
                            report.maxOutputKw(), report.maxRechargeKw(), box,
                            report.condition(), report.efficiency(), report.lastSeen()));
        }
        storages.clear();
        storages.putAll(movedStorages);
        storageFlowKw.clear();
        // 制氧机/通风口上报表也要平移：旧坐标的报告会和同一台设备的新坐标报告
        // 同时计入，产氧量和通风口数量会翻倍，直到旧报告 40 tick 后过期。
        java.util.Map<net.minecraft.core.BlockPos, OxygenSourceReport> movedOxygen =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, OxygenSourceReport> entry
                : oxygenSources.entrySet()) {
            movedOxygen.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        oxygenSources.clear();
        oxygenSources.putAll(movedOxygen);
        java.util.Map<net.minecraft.core.BlockPos, VentReport> movedVents =
                new java.util.HashMap<>();
        for (java.util.Map.Entry<net.minecraft.core.BlockPos, VentReport> entry
                : vents.entrySet()) {
            movedVents.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
        }
        vents.clear();
        vents.putAll(movedVents);
    }

    /**
     * 这个水体里最低的舱门/舱口格的下沿 Y（水面不能高过它）。
     *
     * <p>压载舱注水上限：水面到"舱门下方那一格"的顶面为止，也就是舱门所在格
     * 的底面 Y。没有门就返回 {@link Double#MAX_VALUE}（不限制）。</p>
     */
    private double doorBottomLevel(int body) {
        double limit = Double.MAX_VALUE;
        for (ShipGeometry.DoorLink link : geometry.doorLinks()) {
            if (link.body() == body || link.other() == body) {
                limit = Math.min(limit, link.cell().getY());
            }
        }
        return limit;
    }

    /** 水面到 {@code surfaceY} 时最多能装多少单位水（只累计该水位以下的整格容量）。 */
    private double unitsBelowLevel(int body, double surfaceY) {
        return fluid == null ? 0.0D : fluid.capacityBelow(body, surfaceY);
    }

    /** 压载舱在舱门下沿以下的可注水容积。 */
    public double ballastUsableCapacity(int body) {
        double limit = doorBottomLevel(body);
        return limit < Double.MAX_VALUE ? unitsBelowLevel(body, limit) : bodyCapacity(body);
    }

    /** 第 body 个水体现在的水量（单位）。 */
    public double bodyWater(int body) {
        return fluid == null ? 0.0D : fluid.bodyWater(body);
    }

    /**
     * 自动泵的水传感器语义：这个水体里有没有积水。
     *
     * <p>不能拿 {@link #bodyLevel(int)} 和 0.05 比——它返回的是**绝对 Y 坐标**
     * （深海是 -70 以下），永远小于 0.05，自动泵就永远不会启动。用"水量 &gt; 5 单位
     * （0.05 格）"判断才和"舱里有水"一致。</p>
     */
    public boolean bodyIsWet(int body) {
        return fluid != null && fluid.bodyIsWet(body);
    }

    /** 水体当前进水比例（0..1）：自动化门/泵用。 */
    public double bodyWaterFraction(int body) {
        return fluid == null ? 0.0D : fluid.bodyWaterFraction(body);
    }

    /**
     * 逐格水场推进：外海开着的门先注水，然后整舱归平、重力下落、水平压力平衡。
     *
     * <p>归平（{@link ShipFluidField#level()}）对应原作整舱一个 {@code WaterVolume}
     * 的瞬时水平面：同一间舱里的水不会堆在破口一小坨，而是整间一起涨。
     * 门/舱口两侧按舱室水头限速交换。</p>
     */
    public void tickWaterFlow(double dt) {
        if (dt <= 0.0D || fluid == null) {
            return;
        }
        fluid.tick(dt);
        refreshWaterAggregates();
    }

    /** 兼容旧调用点；语义已改成逐格场推进。 */
    @Deprecated
    public void tickDoorFlow(double dt) {
        tickWaterFlow(dt);
    }

    private void removeBodyWater(int body, double units) {
        if (fluid != null) {
            fluid.removeBody(body, units);
            refreshWaterAggregates();
        }
    }

    private void addBodyWater(int body, double units) {
        addBodyWater(body, units, Double.POSITIVE_INFINITY);
    }

    private void addBodyWater(int body, double units, double maxYExclusive) {
        if (fluid != null) {
            fluid.addBody(body, units, maxYExclusive);
            refreshWaterAggregates();
        }
    }

    /** 第 body 个水体的容量（单位）；泵入时用来判断还能灌多少。 */
    private double bodyCapacity(int body) {
        return fluid == null ? 0.0D : fluid.bodyCapacity(body);
    }

    /**
     * 按"每一格里的水"重建水量。
     *
     * <p>重扫（砸洞、开关门）会重建舱室数组。老的 {@link #seedWater} 只把总水量按
     * 容积比例重新分配，于是：开门（两舱合并）再关门（重新拆开）时，全部水会被
     * 算到"漏水侧"、完好侧被清空。这里改成按格子搬运——每格保留自己那一份，
     * 拆舱/并舱都不会凭空搬水。</p>
     */
    public void seedWaterByCell(java.util.Map<net.minecraft.core.BlockPos, Double> byCell,
                                ShipGeometry.Result next) {
        if (byCell == null || byCell.isEmpty() || fluid == null) {
            return;
        }
        // 逐格场本身就是按位置存的：重扫后同坐标的格直接拿回自己的水，
        // 并舱/拆舱/开门都不会把水按容积比例整体搬到漏水侧。
        fluid.seed(byCell);
        refreshWaterAggregates();
    }

    public void setPumpOut(boolean pumpOut) {
        this.pumpOut = pumpOut;
    }

    /**
     * 换几何后按格子恢复舱室氧气。
     *
     * <p>和 {@link #seedWaterByCell} 同一个用途：开门/破洞/拆方块都会重扫并重建
     * {@link ShipSimulation}，不搬氧气就会回满。空 map（新船/旧船没有记录）时保留
     * 构造函数给的满氧。</p>
     */
    public void seedOxygenByCell(java.util.Map<net.minecraft.core.BlockPos, Double> byCell,
                                 ShipGeometry.Result next) {
        if (byCell == null || byCell.isEmpty() || next == null) {
            return;
        }
        List<ShipGeometry.Compartment> sealed = next.compartments();
        for (int i = 0; i < oxygen.length && i < sealed.size(); i++) {
            double sum = 0.0D;
            for (net.minecraft.core.BlockPos cell : sealed.get(i).cells()) {
                sum += byCell.getOrDefault(cell, 0.0D);
            }
            oxygen[i] = Math.max(0.0D, Math.min(volumes[i] * UNITS_PER_BLOCK, sum));
        }
    }

    /**
     * 方块实体每 tick 上报：这个泵（装配坐标）的流量，单位/秒。
     *
     * <p>符号对齐原版 {@code FlowPercentage}：正值泵出（抽本舱水），负值泵入
     * （往本舱注水），0 停机。</p>
     */
    public void reportPump(net.minecraft.core.BlockPos cell, double rate) {
        reportPump(cell, rate, false);
    }

    /**
     * 同上；{@code ballast=true} 时把这台泵所在的舱室登记成压载舱，
     * 舱内水量进 {@link #ballastPercent()}（浮力因子），并从进水比例里扣除。
     */
    public void reportPump(net.minecraft.core.BlockPos cell, double rate, boolean ballast) {
        reportPump(cell, rate, ballast, rate);
    }

    /**
     * 带"请求流量"的版本：{@code rate} 是供电降效后的实际流量，{@code requestedRate}
     * 是泵想跑的流量——电力负载按请求值算，避免"没电 → 泵不转 → 负载归零 → 又有电"
     * 的循环抖动。
     */
    public void reportPump(net.minecraft.core.BlockPos cell, double rate, boolean ballast,
                           double requestedRate) {
        reportPump(cell, rate, ballast, requestedRate, false);
    }

    /**
     * @param keepBelowDoor 注水时最多注到最近的舱门/舱口下方那一格（压载舱用，
     *                      避免水漫过舱门灌进上层舱室）
     */
    public void reportPump(net.minecraft.core.BlockPos cell, double rate, boolean ballast,
                           double requestedRate, boolean keepBelowDoor) {
        reportPump(cell, rate, ballast, requestedRate, keepBelowDoor, -1.0D);
    }

    /**
     * @param ballastTarget 自动目标占本舱可注水容积的比例（0..1）；-1 = 不做目标钳制。
     */
    public void reportPump(net.minecraft.core.BlockPos cell, double rate, boolean ballast,
                           double requestedRate, boolean keepBelowDoor, double ballastTarget) {
        if (cell == null) {
            return;
        }
        pumps.put(cell.immutable(), new PumpReport(
                // 单台普通泵按原版换算后是 ~14062 单位/秒，旧上限 3000 会把它削到 1/5
                Mth.clamp(rate, -30000.0D, 30000.0D),
                Mth.clamp(requestedRate, -30000.0D, 30000.0D),
                pumpTick, ballast, keepBelowDoor, ballastTarget));
    }

    /**
     * 泵所在的水体：泵本身是实心方块，取它旁边那一格舱室。
     *
     * <p>自动控制（水传感器）也走这里，否则泵格子永远不在舱室里、判定成"没水"。</p>
     */
    public int pumpBody(net.minecraft.core.BlockPos cell) {
        int body = bodyOf(cell);
        if (body >= 0 || cell == null) {
            return body;
        }
        for (net.minecraft.core.BlockPos next : new net.minecraft.core.BlockPos[]{
                cell.above(), cell.below(), cell.north(),
                cell.south(), cell.east(), cell.west()}) {
            body = bodyOf(next);
            if (body >= 0) {
                return body;
            }
        }
        return -1;
    }

    /**
     * 实心机器格用：找相邻的舱室格（机器自己不在水体里，bodyOf(自己) 恒为 -1）。
     *
     * @return 相邻的舱室格；四周都不在舱室里返回 null
     */
    public net.minecraft.core.BlockPos bodyCellNear(net.minecraft.core.BlockPos cell) {
        if (cell == null) {
            return null;
        }
        for (net.minecraft.core.BlockPos next : new net.minecraft.core.BlockPos[]{
                cell.above(), cell.below(), cell.north(), cell.south(),
                cell.east(), cell.west()}) {
            if (bodyOf(next) >= 0) {
                return next;
            }
        }
        return null;
    }

    /**
     * 实心机器格是否泡水：任一相邻舱室格的水位达到机器格就算。
     *
     * <p>只看第一个相邻格会漏：北侧干舱、南侧积水舱时机器南面已经淹了。</p>
     */
    public boolean submergedAt(net.minecraft.core.BlockPos cell,
                               java.util.Collection<net.minecraft.core.BlockPos> breachSources) {
        if (cell == null) {
            return false;
        }
        for (net.minecraft.core.BlockPos next : new net.minecraft.core.BlockPos[]{
                cell.above(), cell.below(), cell.north(), cell.south(),
                cell.east(), cell.west()}) {
            int body = bodyOf(next);
            if (body < 0 || !bodyIsWet(body)) {
                continue;
            }
            // 水位要跟机器自己的底面比：相邻格在机器下方时，水位没过邻居
            // 不代表淹到机器。空格没有水，levelAt 返回的是格底高度，不能当水位用，
            // 否则舱里任何地方有 5 单位水，干燥的机器也会被判成浸水。
            double level = waterLevelAt(next, breachSources);
            if (level <= next.getY() + 1.0E-6D) {
                continue;
            }
            if (cell.getY() < level) {
                return true;
            }
        }
        return false;
    }

    /** 这一格在第几个水体里（0..n-1 密闭舱室，n.. 连海空腔）；不在任何舱里返回 -1。 */
    public int bodyOf(net.minecraft.core.BlockPos cell) {
        if (cell == null) {
            return -1;
        }
        if (fluid != null) {
            int fieldBody = fluid.bodyOf(cell);
            if (fieldBody >= 0) {
                return fieldBody;
            }
        }
        if (bodyIndexOfCell == null) {
            bodyIndexOfCell = new java.util.HashMap<>();
            List<ShipGeometry.Compartment> sealed = geometry.compartments();
            for (int i = 0; i < sealed.size(); i++) {
                for (net.minecraft.core.BlockPos pos : sealed.get(i).cells()) {
                    bodyIndexOfCell.putIfAbsent(pos, i);
                }
            }
            List<ShipGeometry.Compartment> pockets = geometry.openPockets();
            for (int j = 0; j < pockets.size(); j++) {
                for (net.minecraft.core.BlockPos pos : pockets.get(j).cells()) {
                    bodyIndexOfCell.putIfAbsent(pos, sealed.size() + j);
                }
            }
        }
        return bodyIndexOfCell.getOrDefault(cell, -1);
    }

    public ShipGeometry.Result geometry() {
        return geometry;
    }

    /** 当前登记的泵数量（诊断/测试用）。 */
    public int pumpCount() {
        return pumps.size();
    }

    /** 当前所有泵的流量总和（单位/秒，按绝对值累加，诊断/测试用）。 */
    public double pumpTotalRate() {
        double total = 0.0D;
        for (PumpReport report : pumps.values()) {
            total += Math.abs(report.rate());
        }
        return total;
    }

    /** 反应堆方块实体每 tick 上报一次（出力固定，等价于 min=max=outputKw）。 */
    public void reportReactor(net.minecraft.core.BlockPos cell, double outputKw, double capacityKw,
                              double temperature, boolean overheated, boolean meltingDown) {
        reportReactor(cell, outputKw, outputKw, capacityKw, temperature,
                overheated, meltingDown);
    }

    /**
     * 反应堆方块实体每 tick 上报一次出力带（原作 {@code MinMaxPowerOut}）。
     * 实际的交付出力由 {@link #tick(double)} 按剩余需求夹到 [min, max] 里。
     */
    public void reportReactor(net.minecraft.core.BlockPos cell, double minOutputKw,
                              double maxOutputKw, double capacityKw, double temperature,
                              boolean overheated, boolean meltingDown) {
        if (cell == null) {
            return;
        }
        double min = Math.max(0.0D, minOutputKw);
        reactors.put(cell.immutable(), new ReactorReport(
                min, Math.max(min, maxOutputKw), Math.max(0.0D, capacityKw),
                Math.max(0.0D, temperature), overheated, meltingDown, reactorTick));
    }

    /** 引擎方块实体每 tick 上报一次：实际插值力（−100..100%）、耗电、最大推力、耐久比例。 */
    public void reportEngine(net.minecraft.core.BlockPos cell, double force,
                             double demandKw, double maxForce) {
        reportEngine(cell, force, demandKw, maxForce, 1.0D);
    }

    public void reportEngine(net.minecraft.core.BlockPos cell, double force,
                             double demandKw, double maxForce, double condition) {
        reportEngine(cell, force, demandKw, maxForce, condition,
                com.opamb.barotrauma.blockentity.EngineBlockEntity.MAX_POWER_KW);
    }

    public void reportEngine(net.minecraft.core.BlockPos cell, double force,
                             double demandKw, double maxForce, double condition, double ratedDemandKw) {
        if (cell == null) {
            return;
        }
        engines.put(cell.immutable(), new EngineReport(
                Mth.clamp(force, -100.0D, 100.0D),
                Math.max(0.0D, demandKw),
                Math.max(0.0D, maxForce),
                Mth.clamp(condition, 0.0D, 1.0D),
                Math.max(0.0D, ratedDemandKw),
                engineTick));
    }

    /** Estimate the voltage and force at the largest sustainable automatic command. */
    public EngineCapacity engineCapacity(int powerLimit) {
        double ratedDemand = 0.0D;
        java.util.List<Double> forces = new java.util.ArrayList<>(engines.size());
        for (var entry : engines.entrySet()) {
            EngineReport engine = entry.getValue();
            if (engineTick - engine.lastSeen() > ENGINE_STALE_TICKS || engine.condition() <= 0.0D
                    || !consumerConnected(entry.getKey())) {
                continue;
            }
            ratedDemand += engine.ratedDemandKw();
            forces.add(engineRawForce(engine, 100.0D, 1.0D));
        }
        double requested = Mth.clamp(powerLimit / 100.0D, 0.0D, 1.0D);
        // Leave headroom above the engine and terminal cutoff. Allow some load above the reactor's
        // current output band so its automatic controller can ramp up to meet increased demand.
        double budget = availableDrivePowerKw / 0.80D - nonEngineDemandKw;
        if (requested == 0.0D || ratedDemand <= 0.0D || budget <= 0.0D) {
            return new EngineCapacity(0.0D, 0.0D);
        }
        double powered = Math.pow(budget / ratedDemand, 1.0D / ENGINE_POWER_EXPONENT);
        double fraction = Math.min(requested, powered);
        double demand = nonEngineDemandKw + ratedDemand * Math.pow(fraction, ENGINE_POWER_EXPONENT);
        double voltage = Math.min(1.0D, availableDrivePowerKw / demand);
        double effectiveFraction = fraction * Math.pow(voltage, 1.0D / ENGINE_POWER_EXPONENT);
        return new EngineCapacity(fraction / requested, strongestEngineForces(forces) * effectiveFraction);
    }

    /** 上一 tick 登记的引擎数量（诊断/推进倍率用）。 */
    public int engineCount() {
        return lastEngineCount;
    }

    /**
     * 逐引擎有效供电系数之和：每台引擎按它实际拿到的电压算
     * {@code min(Voltage,2)^(2/3)}，低于 MinVoltage 0.5 记 0。
     *
     * <p>物理推进不能直接用"全船电压 × 引擎数"：某台引擎绑的接线盒坏了/缺失时
     * 它实际拿不到电，必须单独扣掉。</p>
     *
     * <p>最多只算 {@link #MAX_ENGINES} 台：9 台引擎在额定电压下加起来是 9，
     * 远低于系数上限（8×2^(2/3)≈12.7），不按台数截断就会凭空多出一台推力。</p>
     */
    public double engineSupplyFactor() {
        java.util.List<Double> powered = new java.util.ArrayList<>(engines.size());
        for (var entry : engines.entrySet()) {
            EngineReport report = entry.getValue();
            if (engineTick - report.lastSeen() > ENGINE_STALE_TICKS || Math.abs(report.force()) <= 0.1D) {
                continue;
            }
            double voltage = consumerVoltage(entry.getKey());
            if (voltage >= 0.5D) {
                powered.add(Math.pow(Math.min(voltage, 2.0D), 2.0D / 3.0D));
            }
        }
        // 按系数从大到小取前 8 台：结果和 HashMap 遍历顺序无关。
        powered.sort(java.util.Comparator.reverseOrder());
        double sum = 0.0D;
        for (int i = 0; i < powered.size() && i < MAX_ENGINES; i++) {
            sum += powered.get(i);
        }
        return sum;
    }

    /**
     * 原作 {@code Engine.Update} 的逐引擎推力之和（原始力单位，未乘 0.1 驾驶系数）。
     *
     * <pre>
     * 每台 = maxforce × 目标推力% × min(电压,2)^(2/3) × lerp(0.5,2,耐久比例)
     * </pre>
     *
     * <p>目标推力用引擎实际插值后的力（{@link EngineReport#force()}），不是面板
     * 上的目标值；耗电仍按目标值算（和原作 {@code GetCurrentPowerConsumption}
     * 一致）。电压低于 MinVoltage 0.5 的引擎完全不贡献。最多算 8 台。</p>
     */
    public double engineForceRaw() {
        java.util.List<Double> forces = new java.util.ArrayList<>(engines.size());
        for (var entry : engines.entrySet()) {
            EngineReport report = entry.getValue();
            if (engineTick - report.lastSeen() > ENGINE_STALE_TICKS || Math.abs(report.force()) <= 0.1D) {
                continue;
            }
            double voltage = consumerVoltage(entry.getKey());
            if (voltage <= 0.0D) {
                continue;
            }
            // 原作不因为电压低于 MinVoltage 就把这一帧的出力直接清零：
            // Engine.Update 让 Force 以 10/s 衰减，同时仍然乘 min(Voltage,2)^(2/3)。
            // 所以这里保留 0.5 以下的电压系数，衰减交给 EngineBlockEntity.currentForce。
            forces.add(engineRawForce(report, report.force(), voltage));
        }
        return strongestEngineForces(forces);
    }

    private static double engineRawForce(EngineReport engine, double commandPercent, double voltage) {
        double voltageFactor = Math.pow(Math.min(voltage, 2.0D), 1.0D / ENGINE_POWER_EXPONENT);
        double conditionFactor = 0.5D + 1.5D * engine.condition();
        return engine.maxForce() * commandPercent * voltageFactor * conditionFactor;
    }

    private static double strongestEngineForces(java.util.List<Double> forces) {
        forces.sort(java.util.Comparator.comparingDouble((Double force) -> Math.abs(force)).reversed());
        double sum = 0.0D;
        for (int i = 0; i < forces.size() && i < MAX_ENGINES; i++) {
            sum += forces.get(i);
        }
        return sum;
    }

    /**
     * 已安装引擎在装配坐标里的平均位置；没有引擎返回 null。
     *
     * <p>物理船的船头方向用它和船核心反推：引擎在船尾，核心指向引擎的
     * 反方向就是船头。这样换一艘船、或者以后把船横过来搭也能自洽，
     * 不用在代码里写死 +X。</p>
     */
    @javax.annotation.Nullable
    public double[] engineCentroid() {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (var entry : engines.entrySet()) {
            if (engineTick - entry.getValue().lastSeen() > ENGINE_STALE_TICKS) {
                continue;
            }
            net.minecraft.core.BlockPos cell = entry.getKey();
            x += cell.getX() + 0.5D;
            y += cell.getY() + 0.5D;
            z += cell.getZ() + 0.5D;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return new double[]{x / count, y / count, z / count};
    }

    /** 上一 tick 的引擎总耗电（kW）。 */
    public double engineDemandKw() {
        return lastEngineDemand;
    }

    /** 上一 tick 的引擎总最大推力（诊断用）。 */
    public double engineMaxForce() {
        return lastEngineMaxForce;
    }

    /** 全船供电比例（0..1）：0 = 没堆/没开机，泵和推进器都不动。 */
    public double powerRatio() {
        return lastRatio;
    }

    /** 原版电压：总供电 / 总负载，不封顶 1；0 = 没电，>1 = 富余，>1.7 = 过压。 */
    public double voltage() {
        return lastVoltage;
    }

    /** 上一 tick 的全船供电（kW）。 */
    public double supplyKw() {
        return lastSupply;
    }

    /** 设备每 tick 上报自己的电网需求；{@code box == null} 表示挂在默认总线上。 */
    /** 终端（船核心）上报：和普通消费者一样登记，同时标记旧声纳负载不用再补。 */
    public void reportTerminal(net.minecraft.core.BlockPos cell, double demandKw,
                               double minVoltage, net.minecraft.core.BlockPos box) {
        reportConsumer(cell, demandKw, minVoltage, box);
        lastTerminalTick = consumerTick;
    }

    public void reportConsumer(net.minecraft.core.BlockPos cell, double demandKw,
                               double minVoltage, net.minecraft.core.BlockPos box) {
        if (cell == null) {
            return;
        }
        consumers.put(cell.immutable(), new ConsumerReport(
                Math.max(0.0D, demandKw), Math.max(0.0D, minVoltage),
                box == null ? null : box.immutable(), consumerTick));
    }

    /** 接线盒每 tick 上报耐久。 */
    public void reportJunctionBox(net.minecraft.core.BlockPos cell, double condition) {
        if (cell == null) {
            return;
        }
        boxes.put(cell.immutable(), new BoxReport(
                Math.max(0.0D, Math.min(100.0D, condition)), boxTick));
    }

    /**
     * 电池每 tick 上报存电量和额定参数；充放电由 {@link #tick(double)} 统一结算。
     *
     * <p>方块实体在 tick 开头应先用 {@link #storageCharge(BlockPos)} 读回模拟里的
     * 电量再上报，避免把上一 tick 的旧值写回去。</p>
     */
    public void reportStorage(net.minecraft.core.BlockPos cell, double chargeKwm,
                              double capacityKwm, double maxOutputKw, double maxRechargeKw,
                              net.minecraft.core.BlockPos box, double condition) {
        reportStorage(cell, chargeKwm, capacityKwm, maxOutputKw, maxRechargeKw, box,
                condition, BATTERY_EFFICIENCY);
    }

    /**
     * 带充电效率的上报：电池 0.95，超级电容器 0.1（原作 PowerContainer.efficiency）。
     */
    public void reportStorage(net.minecraft.core.BlockPos cell, double chargeKwm,
                              double capacityKwm, double maxOutputKw, double maxRechargeKw,
                              net.minecraft.core.BlockPos box, double condition,
                              double efficiency) {
        if (cell == null) {
            return;
        }
        double capacity = Math.max(1.0D, capacityKwm);
        storages.put(cell.immutable(), new StorageReport(
                Math.max(0.0D, Math.min(chargeKwm, capacity)),
                capacity,
                Math.max(0.0D, maxOutputKw),
                Math.max(0.0D, maxRechargeKw),
                box == null ? null : box.immutable(),
                Math.max(0.0D, Math.min(100.0D, condition)),
                Math.max(0.0D, Math.min(1.0D, efficiency)),
                storageTick));
    }

    /** 模拟里某块电池当前的电量（kW·min）；没上报返回 null。 */
    public Double storageCharge(net.minecraft.core.BlockPos cell) {
        StorageReport report = cell == null ? null : storages.get(cell);
        return report == null ? null : report.chargeKwm();
    }

    public double storageFlowKw(net.minecraft.core.BlockPos cell) {
        return storageFlowKw.getOrDefault(cell, 0.0D);
    }

    public boolean storageConnected(net.minecraft.core.BlockPos cell) {
        StorageReport report = storages.get(cell);
        return report != null && storageUsable(report);
    }

    /** 电池是否在电网里可用：耐久 > 0 且绑定的接线盒没坏。 */
    private boolean storageUsable(StorageReport report) {
        return report.condition() > 0.0D && boxHealthy(report.box());
    }

    /** 接线盒是否有效；null 表示默认总线，视为有效。 */
    private boolean boxHealthy(net.minecraft.core.BlockPos box) {
        if (box == null) {
            return true;
        }
        BoxReport report = boxes.get(box);
        return report != null && report.condition() > 0.0D;
    }

    /* ------------------------------------------------------- 制氧 / 通风 */

    /** 制氧机每 tick 上报当前产氧量（单位/秒，电压和耐久已由方块实体折算）。 */
    public void reportOxygenSource(net.minecraft.core.BlockPos cell, double flow) {
        if (cell == null) {
            return;
        }
        oxygenSources.put(cell.immutable(), new OxygenSourceReport(
                Math.max(0.0D, flow), oxygenSourceTick));
    }

    /**
     * 通风口每 tick 上报：服务哪个舱室、当前能不能送氧。
     *
     * @param active 泡水（原作 {@code item.InWater}）时为 false，此时不往舱室送氧
     */
    public void reportVent(net.minecraft.core.BlockPos cell, int compartment, boolean active) {
        if (cell == null) {
            return;
        }
        vents.put(cell.immutable(), new VentReport(compartment, active, ventTick));
    }

    /** 某个舱室当前的氧气单位数（满 = 舱容×100）。 */
    public double oxygenUnits(int compartment) {
        return compartment < 0 || compartment >= oxygen.length ? 0.0D : oxygen[compartment];
    }

    /** 某个舱室的氧气百分比 0..100。 */
    public double oxygenPercent(int compartment) {
        if (compartment < 0) {
            return 0.0D;
        }
        if (compartment >= oxygen.length) {
            // 连海空腔没有单独的氧气数组：破口舱只要人还在水面以上，
            // 舱内空气就是可呼吸的（水淹到眼睛时 ShipBreathing 会先按 0 处理）。
            return 100.0D;
        }
        if (volumes[compartment] <= 0) {
            return 0.0D;
        }
        return oxygen[compartment] / (volumes[compartment] * UNITS_PER_BLOCK) * 100.0D;
    }

    /**
     * 一个乘员在 {@code compartment} 里呼吸 {@code dtSeconds} 秒，扣该舱室氧气。
     *
     * <p>原作 {@code Character.UpdateOxygen}：头部不在水里、且自身可用氧低于舱室
     * 百分比时，{@code hull.Oxygen -= Hull.OxygenConsumptionSpeed * dt}。</p>
     */
    public void breathe(int compartment, double dtSeconds) {
        if (compartment < 0 || compartment >= oxygen.length || dtSeconds <= 0.0D) {
            return;
        }
        oxygen[compartment] = Math.max(0.0D, oxygen[compartment] - O2_BREATH_RATE * dtSeconds);
    }

    /** 上一 tick 电池总输出（kW）。 */
    public double batteryOutputKw() {
        return lastBatteryOutput;
    }

    /** 上一 tick 电池总充电功率（kW，未计效率）。 */
    public double batteryChargeKw() {
        return lastBatteryCharge;
    }

    /** 全船电池总存电量（kW·min）。 */
    public double storedKwm() {
        return lastStoredKwm;
    }

    /** 全船电池总容量（kW·min）。 */
    public double storageCapacityKwm() {
        return lastStorageCapacityKwm;
    }

    /** 当前登记的所有电池（电力面板用）。 */
    public java.util.List<StorageInfo> storageInfos() {
        java.util.List<StorageInfo> result = new java.util.ArrayList<>(storages.size());
        for (var entry : storages.entrySet()) {
            StorageReport report = entry.getValue();
            result.add(new StorageInfo(entry.getKey(), report.chargeKwm(),
                    report.capacityKwm(), report.maxOutputKw(), report.maxRechargeKw(),
                    report.box(), report.condition()));
        }
        return result;
    }

    /**
     * 某个设备实际拿到的电压。
     *
     * <p>绑定的接线盒坏了（耐久 0 / 没上报）→ 0；否则用全船电压。没绑盒子（旧船）
     * 也走全船默认总线，保证现有存档能继续开。</p>
     */
    public double consumerVoltage(net.minecraft.core.BlockPos cell) {
        return consumerConnected(cell) ? lastVoltage : 0.0D;
    }

    private boolean consumerConnected(net.minecraft.core.BlockPos cell) {
        ConsumerReport report = cell == null ? null : consumers.get(cell);
        if (report != null && report.box() != null) {
            BoxReport box = boxes.get(report.box());
            return box != null && box.condition() > 0.0D;
        }
        return true;
    }

    /** 某个接线盒上绑定的设备总负载（kW）。 */
    public double boxLoad(net.minecraft.core.BlockPos box) {
        if (box == null) {
            return 0.0D;
        }
        double load = 0.0D;
        for (ConsumerReport report : consumers.values()) {
            if (box.equals(report.box())) {
                load += report.demandKw();
            }
        }
        return load;
    }

    /** 某个接线盒当前耐久（0..100）；没上报返回 0。 */
    public double boxCondition(net.minecraft.core.BlockPos box) {
        BoxReport report = box == null ? null : boxes.get(box);
        return report == null ? 0.0D : report.condition();
    }

    /** 当前登记的所有用电设备（电力面板用）。 */
    public java.util.List<ConsumerInfo> consumerInfos() {
        java.util.List<ConsumerInfo> result = new java.util.ArrayList<>(consumers.size());
        for (var entry : consumers.entrySet()) {
            ConsumerReport report = entry.getValue();
            result.add(new ConsumerInfo(entry.getKey(), report.demandKw(),
                    report.minVoltage(), report.box()));
        }
        return result;
    }

    /** 当前登记的所有接线盒位置（电力面板用）。 */
    public java.util.Set<net.minecraft.core.BlockPos> junctionBoxes() {
        return java.util.Set.copyOf(boxes.keySet());
    }


    /** 当前登记的反应堆数量（诊断/测试用）。 */
    public int reactorCount() {
        return reactors.size();
    }

    public boolean pumpOut() {
        return pumpOut;
    }

    public double waterUnits() {
        double sum = 0.0D;
        for (double value : water) {
            sum += value;
        }
        return sum;
    }

    /** 第 index 个舱室的水量（单位）。 */
    public double waterUnits(int index) {
        return index >= 0 && index < water.length ? water[index] : 0.0D;
    }

    /**
     * 往某个密封舱室直接注水（单位）。
     *
     * <p>用于“船壳还没破、但已经裂到渗水”的泄漏：这部分水不经过 openPockets
     * 的海水灌入链路，而是由 {@link ShipState#tickLeaks} 按受损格数量和伤害等级累加。</p>
     */
    public void addWater(int compartmentIndex, double units) {
        if (compartmentIndex < 0 || compartmentIndex >= water.length || units <= 0.0D
                || fluid == null) {
            return;
        }
        fluid.addBody(compartmentIndex, units, Double.POSITIVE_INFINITY);
        refreshWaterAggregates();
    }

    /**
     * 往指定舱室注水（渗漏用）。水量从舱底摊平，所接收的水量用于绘制漏点流入的路径。
     */
    public void addWaterAt(int compartmentIndex, net.minecraft.core.BlockPos near, double units) {
        if (compartmentIndex < 0 || compartmentIndex >= water.length || units <= 0.0D
                || fluid == null) {
            return;
        }
        double added = fluid.addBody(compartmentIndex, units, Double.POSITIVE_INFINITY);
        if (near != null && added > 0.0D) {
            BlockPos receiver = fluid.breachReceiver(near, compartmentIndex, Set.of());
            fluid.recordInflow(near, receiver, added);
        }
        refreshWaterAggregates();
    }

    /** 逐格场只读入口（渲染/存档/测试用）。 */
    public ShipFluidField fluid() {
        return fluid;
    }

    /** 逐格水量快照（位置 → units）。 */
    public java.util.Map<net.minecraft.core.BlockPos, Double> waterCells() {
        return fluid == null ? java.util.Map.of() : fluid.toMap();
    }

    /** 存档用：只写真正有水的格，避免把 6000 个空格全塞进 NBT。 */
    public net.minecraft.nbt.ListTag saveWaterNbt() {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        if (fluid == null) {
            return list;
        }
        for (int i = 0; i < fluid.cellCount(); i++) {
            double units = fluid.waterAt(i);
            if (units <= 0.005D) {
                continue;
            }
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putLong("Pos", fluid.posAt(i).asLong());
            entry.putFloat("Units", (float) Math.min(ShipFluidField.MAX_CELL_WATER, units));
            list.add(entry);
        }
        return list;
    }

    /** 读存档里的逐格水量；坏条目/负值跳过。 */
    public static java.util.Map<net.minecraft.core.BlockPos, Double> readWaterNbt(
            net.minecraft.nbt.ListTag list) {
        java.util.Map<net.minecraft.core.BlockPos, Double> out = new java.util.HashMap<>();
        if (list == null) {
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof net.minecraft.nbt.CompoundTag entry)
                    || !entry.contains("Pos")) {
                continue;
            }
            double units = entry.getFloat("Units");
            if (units > 0.0D) {
                out.put(net.minecraft.core.BlockPos.of(entry.getLong("Pos")), units);
            }
        }
        return out;
    }

    /** 读档时恢复压载标定，避免已注水的压载舱被再次 prime 注满。 */
    public void restoreBallastState(boolean primed, double neutral, double max) {
        this.ballastPrimed = primed;
        this.ballastPrimePending = false;
        this.ballastNeutral = Math.max(0.0D, Math.min(1.0D, neutral));
        this.ballastMax = Math.max(0.0D, Math.min(1.0D, max));
    }

    /** 把逐格场的水量同步到 water[]/pocketWater[] 聚合缓存。 */
    private void refreshWaterAggregates() {
        java.util.Arrays.fill(water, 0.0D);
        java.util.Arrays.fill(pocketWater, 0.0D);
        if (fluid == null) {
            return;
        }
        for (int i = 0; i < fluid.cellCount(); i++) {
            int body = fluid.bodyAt(i);
            double units = fluid.waterAt(i);
            if (body < 0) {
                continue;
            }
            if (body < water.length) {
                water[body] += units;
            } else {
                int pocket = body - water.length;
                if (pocket >= 0 && pocket < pocketWater.length) {
                    pocketWater[pocket] += units;
                }
            }
        }
    }

    /** 第 index 个舱室的容积（格）。 */
    public int compartmentVolume(int index) {
        return index >= 0 && index < volumes.length ? volumes[index] : 0;
    }

    public double oxygenPercent() {
        double total = 0.0D;
        double totalVolume = 0.0D;
        for (int i = 0; i < oxygen.length; i++) {
            total += oxygen[i];
            totalVolume += volumes[i] * UNITS_PER_BLOCK;
        }
        return totalVolume <= 0.0D ? 0.0D : total / totalVolume;
    }

    public double ballastPercent() {
        return ballastCapacity <= 0.0D ? 0.0D : ballast / ballastCapacity;
    }

    /**
     * 压载舱的"中性相对水位"（0..1）：让全船水量刚好等于 7% 时，压载舱应有的
     * 填充比例；由 {@link #primeBallast()} 按实际舱容算出来。舵台空闲时压载泵
     * 保持这个水位，上浮/下潜才改成 0/1。
     */
    public double ballastNeutral() {
        return ballastNeutral;
    }

    /** 把存档记录的全压载容积比例换成各舱可注水容积的共同目标。 */
    public double neutralBallastFill() {
        return ballastMax > 0.0D ? Math.min(1.0D, ballastNeutral / ballastMax) : 0.0D;
    }

    /** 压载舱可达的最大水位比例（舱门限幅后）。下潜目标不能超过它，否则泵会一直空转耗电。 */
    public double ballastMaxPercent() {
        return ballastMax;
    }

    /** 物理船第一次启动时调用一次：下一 tick 把压载舱注到中性水位。 */
    public void requestBallastPrime() {
        ballastPrimePending = true;
    }

    /** 中性水位是否已经标定；没标定前压载泵不自动动作。 */
    public boolean ballastPrimed() {
        return ballastPrimed;
    }

    private void primeBallast(Set<Integer> ballastBodies, double usable) {
        recalibrateBallast(usable);
        double target = ballastNeutral * ballastCapacity;
        // 按各舱剩余可注水空间同比例注入，保留存档或手动预注的水。
        double available = 0.0D;
        for (int body : ballastBodies) {
            available += Math.max(0.0D, ballastUsableCapacity(body) - bodyWater(body));
        }
        if (target > ballast && available > 0.0D) {
            double fill = Math.min(1.0D, (target - ballast) / available);
            for (int body : ballastBodies) {
                double before = bodyWater(body);
                double add = (ballastUsableCapacity(body) - before) * fill;
                if (add > 0.0D) {
                    addBodyWater(body, add, doorBottomLevel(body));
                    ballast += bodyWater(body) - before;
                }
            }
        }
        ballastPrimed = true;
    }

    /** 只按当前舱容重算中性水位，不注水；压载舱集合变化时用。 */
    private void recalibrateBallast(double usable) {
        if (ballastCapacity <= 0.0D) {
            return;
        }
        double total = 0.0D;
        for (int body = 0; body < volumes.length + pocketWater.length; body++) {
            total += bodyCapacity(body);
        }
        double nonBallastWater = Math.max(0.0D, waterUnits() + pocketWaterTotal() - ballast);
        double target = Mth.clamp(total * NEUTRAL_WATER_RATIO - nonBallastWater, 0.0D, usable);
        ballastNeutral = target / ballastCapacity;
    }

    /** 压载舱当前水量（单位）。 */
    public double ballastUnits() {
        return ballast;
    }

    /** 压载舱总容量（单位）。 */
    public double ballastCapacityUnits() {
        return ballastCapacity;
    }

    public Report report() {
        return new Report(reactorTemperature, lastOutput, lastDemand, lastRatio,
                oxygenPercent(), waterUnits(), ballastPercent(), ballast, ballastCapacity,
                airlockPhase, airlockWater / AIRLOCK_VOLUME, airlockCycles,
                reactorOverheated, reactorMeltingDown);
    }

    /** 一次模拟快照。 */
    public record Report(double temperature, double powerOutput, double powerDemand, double powerRatio,
                         double oxygenPercent, double waterUnits, double ballastPercent,
                         double ballastUnits, double ballastCapacity,
                         AirlockPhase airlockPhase, double airlockPercent, int airlockCycles,
                         boolean overheated, boolean meltingDown) {
    }
}
