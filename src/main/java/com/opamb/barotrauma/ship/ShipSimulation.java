package com.opamb.barotrauma.ship;

import net.minecraft.util.Mth;

import java.util.List;

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

    private static final double O2_PER_GENERATOR = 700.0D;
    private static final double CREW_OXYGEN = 700.0D;
    private static final double PUMP_RATE = 600.0D;
    private static final double PUMP_POWER = 300.0D;
    private static final double O2_POWER = 300.0D;
    /** 声纳台耗电：wiki 的 Navigation Terminal / Sonar Monitor 都是 100。 */
    private static final double SONAR_POWER = 100.0D;
    /** 驾驶台（Nav Terminal 本体）也吃 100；我们把它和声纳合在一台机器上，所以只算一份。 */
    private static final double BALLAST_PER_TANK = 600.0D;

    private final int[] volumes;
    private final double[] water;
    private final double[] oxygen;
    private final MachineCount machines;
    /** 船体几何（破口空腔、舱室体积都从这里读）。 */
    private final ShipGeometry.Result geometry;
    /** 连海空腔（破口舱室）里的水量，按 openPockets 的顺序。 */
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
    /** 物理船第一次启动时请求一次压载注水。 */
    private boolean ballastPrimePending;
    /** 中性水位已经算好；算好之前压载泵不动作（方块船停靠时也不乱注水）。 */
    private boolean ballastPrimed;
    private boolean pumpOut = true;
    /** 反应堆上报表：格子 → (输出 kW, 额定 kW, 温度, 过热, 熔毁, 最后上报 tick)。 */
    private final java.util.Map<net.minecraft.core.BlockPos, ReactorReport> reactors =
            new java.util.HashMap<>();
    private long reactorTick;
    private static final int REACTOR_STALE_TICKS = 40;
    /** 引擎上报表：格子 → (目标力%, 耗电 kW, 最大推力, 最后上报 tick)。 */
    private final java.util.Map<net.minecraft.core.BlockPos, EngineReport> engines =
            new java.util.HashMap<>();
    private long engineTick;
    private static final int ENGINE_STALE_TICKS = 40;
    private int lastEngineCount;
    private double lastEngineDemand;
    private double lastEngineMaxForce;

    private record EngineReport(double force, double demandKw, double maxForce, long lastSeen) {
    }

    private record ReactorReport(double output, double capacity, double temperature,
                                 boolean overheated, boolean meltingDown, long lastSeen) {
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

    private record PumpReport(double rate, double requestedRate, long lastSeen, boolean ballast,
                              boolean keepBelowDoor) {
    }

    /** 上一 tick 由压载泵登记为压载舱的水体下标（用于浮力/进水比例）。 */
    private final java.util.Set<Integer> ballastBodies = new java.util.HashSet<>();
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
        List<ShipGeometry.Compartment> compartments = geometry.compartments();
        int size = compartments.size();
        volumes = new int[size];
        water = new double[size];
        oxygen = new double[size];
        for (int i = 0; i < size; i++) {
            ShipGeometry.Compartment compartment = compartments.get(i);
            volumes[i] = compartment.volume();
            water[i] = compartment.waterBlocks() * UNITS_PER_BLOCK;
            oxygen[i] = compartments.get(i).volume() * UNITS_PER_BLOCK;
        }
        this.machines = machines;
        this.ballastCapacity = machines.ballast() * BALLAST_PER_TANK;
        this.geometry = geometry;
        this.pocketWater = new double[geometry.openPockets().size()];
    }

    /**
     * 破口进水（《潜渊症》模型的 3D 简化版）：连海空腔按"开口面积 × 深度"缓慢灌水。
     *
     * <p>原版公式是 {@code flow = 500 * Size * open² * dt}；我们用每个破口空腔每秒
     * 2 格³ 的基础速率、按深度放大，先保证手感（一两个格子的洞几十秒灌满一间舱）。</p>
     */
    public void tickSeaFlood(double dt, double depthMeters) {
        java.util.List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        for (int i = 0; i < pockets.size(); i++) {
            double capacity = pockets.get(i).volume() * UNITS_PER_BLOCK;
            if (pocketWater[i] >= capacity) {
                continue;
            }
            double depthFactor = 1.0D + Math.max(0.0D, depthMeters) / 100.0D;
            double flow = SEA_INFLOW_PER_SECOND * depthFactor * Math.max(1, breachSize);
            pocketWater[i] = Math.min(capacity,
                    pocketWater[i] + flow * UNITS_PER_BLOCK * dt);
        }
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
        double sealedCapacity = 0.0D;
        for (int volume : volumes) {
            sealedCapacity += volume * UNITS_PER_BLOCK;
        }
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        double pocketCapacity = 0.0D;
        for (ShipGeometry.Compartment pocket : pockets) {
            pocketCapacity += pocket.volume() * UNITS_PER_BLOCK;
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
                water[i] = amount * (volumes[i] * UNITS_PER_BLOCK / sealedCapacity);
            }
        }
        if (pocketCapacity > 0.0D && pocket > 0.0D) {
            double amount = Math.min(pocket, pocketCapacity);
            for (int i = 0; i < pocketWater.length; i++) {
                pocketWater[i] = amount * (pockets.get(i).volume() * UNITS_PER_BLOCK / pocketCapacity);
            }
        }
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

    /** 水位差小于这个值就当已经平了，不再过水（避免无限微流）。 */
    private static final double DOOR_FLOW_DEADBAND = 0.02D;

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

    /** 推进一个时间步（秒）。 */
    public void tick(double dt) {
        // 反应堆：方块实体每 tick 上报；超过 40 tick 没上报视为已经拆掉。
        reactorTick++;
        double reactorOutput = 0.0D;
        double reactorCapacity = 0.0D;
        double temperature = 0.0D;
        boolean overheated = false;
        boolean meltingDown = false;
        var reactorIterator = reactors.entrySet().iterator();
        while (reactorIterator.hasNext()) {
            var entry = reactorIterator.next();
            ReactorReport report = entry.getValue();
            if (reactorTick - report.lastSeen() > REACTOR_STALE_TICKS) {
                reactorIterator.remove();
                continue;
            }
            reactorOutput += report.output();
            reactorCapacity += report.capacity();
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
        double demand = activePumps * PUMP_POWER
                + machines.oxygen() * O2_POWER
                + machines.sonar() * SONAR_POWER
                + (airlockPhase == AirlockPhase.CLOSED ? 0.0D : AIRLOCK_POWER)
                + engineDemand;
        // 硬核：没堆 / 没开机 / 输出为 0 → 全船没电，推进和泵都停。
        boolean hasReactor = reactorCapacity > 0.0D;
        double ratio = !hasReactor || reactorOutput <= 0.0D ? 0.0D
                : demand <= 0.0D ? 1.0D : Math.min(1.0D, reactorOutput / demand);
        lastOutput = reactorOutput;
        lastDemand = demand;
        lastRatio = ratio;

        // 制氧：按舱室体积分配
        int totalVolume = 0;
        for (int volume : volumes) {
            totalVolume += volume;
        }
        if (totalVolume > 0 && machines.oxygen() > 0) {
            double produced = machines.oxygen() * O2_PER_GENERATOR * ratio * dt;
            for (int i = 0; i < oxygen.length; i++) {
                oxygen[i] += produced * volumes[i] / (double) totalVolume;
            }
        }
        // 乘员消耗（一个人，从最大的舱室扣——M3 先不追踪玩家位置）
        int largest = -1;
        for (int i = 0; i < volumes.length; i++) {
            if (largest < 0 || volumes[i] > volumes[largest]) {
                largest = i;
            }
        }
        if (largest >= 0) {
            oxygen[largest] = Math.max(0.0D, oxygen[largest] - CREW_OXYGEN * dt);
        }

        // 水泵：抽舱室的水，或者往压载舱注水
        double moved = machines.pump() * PUMP_RATE * ratio * dt;
        if (pumpOut) {
            for (int i = 0; i < water.length && moved > 0.0D; i++) {
                double take = Math.min(water[i], moved);
                water[i] -= take;
                moved -= take;
            }
        } else {
            double add = Math.min(moved, ballastCapacity - ballast);
            ballast += Math.max(0.0D, add);
        }

        // 排水泵方块：只抽自己所在的那个舱（原版水泵也是这样——关上的门后面
        // 的水泵不归它管）。旧实现是把全船的水按顺序抽一遍，用户实测"门关着水还能
        // 被泵出去"。水泵位置由方块实体每 tick 报上来（物理船是装配坐标）。
        pumpTick++;
        // 带符号流量：**正值泵出、负值泵入、0 停机**（和 ShipDrainPumpTest 一致）。
        // 同舱多台泵分别汇总后再结算，避免"一台泵入一台泵出"互相抵消成不动作。
        java.util.Map<Integer, Double> pumpOutRates = new java.util.HashMap<>();
        java.util.Map<Integer, Double> pumpInRates = new java.util.HashMap<>();
        java.util.Set<Integer> ballastBodies = new java.util.HashSet<>();
        java.util.Set<Integer> keepBelowDoorBodies = new java.util.HashSet<>();
        var pumpIterator = pumps.entrySet().iterator();
        while (pumpIterator.hasNext()) {
            var entry = pumpIterator.next();
            PumpReport report = entry.getValue();
            if (pumpTick - report.lastSeen() > PUMP_STALE_TICKS) {
                pumpIterator.remove();
                continue;
            }
            int body = pumpBody(entry.getKey());
            // 压载泵停机时也要登记舱室，否则水位不变时浮力会突然归零。
            if (report.ballast() && body >= 0) {
                ballastBodies.add(body);
            }
            if (report.keepBelowDoor() && body >= 0) {
                keepBelowDoorBodies.add(body);
            }
            if (report.rate() == 0.0D || body < 0) {
                continue;
            }
            if (report.rate() > 0.0D) {
                pumpOutRates.merge(body, report.rate(), Double::sum);
            } else {
                pumpInRates.merge(body, -report.rate(), Double::sum);
            }
        }
        for (var entry : pumpOutRates.entrySet()) {
            double take = Math.min(bodyWater(entry.getKey()), entry.getValue() * dt);
            if (take > 0.0D) {
                removeBodyWater(entry.getKey(), take);
            }
        }
        for (var entry : pumpInRates.entrySet()) {
            int body = entry.getKey();
            double room = Math.max(0.0D, bodyCapacity(body) - bodyWater(body));
            double add = Math.min(room, entry.getValue() * dt);
            if (keepBelowDoorBodies.contains(body)) {
                // 压载舱注水上限：水面到最近的舱门/舱口下方那一格为止。
                double limit = doorBottomLevel(body);
                if (bodyLevel(body) >= limit) {
                    continue;
                }
                double capUnits = unitsBelowLevel(body, limit);
                add = Math.min(add, Math.max(0.0D, capUnits - bodyWater(body)));
            }
            if (add > 0.0D) {
                addBodyWater(body, add);
            }
        }

        // 压载舱 = 压载泵所在舱；水量/容量直接喂给 VsThruster 的浮力因子。
        // 旧版 machines.pump() 的全局压载分支已经没人计数（MachineCount 恒 0），
        // 这里以实际压载舱为准覆盖它。
        this.ballastBodies.clear();
        this.ballastBodies.addAll(ballastBodies);
        double ballastWater = 0.0D;
        double ballastCapacity = 0.0D;
        for (int body : ballastBodies) {
            ballastWater += bodyWater(body);
            ballastCapacity += bodyCapacity(body);
        }
        this.ballast = ballastWater;
        this.ballastCapacity = ballastCapacity;

        // 原版中性浮力：全船 7% 的水（SubmarineBody.NeutralBallastPercentage）。
        // 物理船第一次启动时把压载舱注到"刚好让全船水量 = 7%"的相对水位，
        // 之后舵台空闲就让压载泵保持这个水位；上浮排空、下潜注满。
        if (ballastPrimePending && ballastCapacity > 0.0D) {
            primeBallast();
            ballastPrimePending = false;
        }

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
        if (index >= 0 && index < water.length) {
            water[index] += units;
        }
    }

    // ------------------------------------------------------------------ 门两侧的水

    /**
     * 水体下标（{@link ShipGeometry.DoorLink} 用的那套）：先密闭舱室 0..n-1，
     * 再连海空腔 n..n+m-1。
     */
    public int bodyCount() {
        return water.length + pocketWater.length;
    }

    /** 第 body 个水体的水位（格）。连海空腔按整块水位的近似值算。 */
    public double bodyLevel(int body) {
        List<ShipGeometry.Compartment> sealed = geometry.compartments();
        if (body >= 0 && body < sealed.size()) {
            return ShipWaterStateBuilder.surfaceOf(sealed.get(body), water[body]);
        }
        int pocket = body - sealed.size();
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        if (pocket >= 0 && pocket < pockets.size()) {
            return ShipWaterStateBuilder.surfaceOf(pockets.get(pocket), pocketWater[pocket]);
        }
        return 0.0D;
    }

    /** 第 body 个水体的格子数。 */
    public int bodyVolume(int body) {
        List<ShipGeometry.Compartment> sealed = geometry.compartments();
        if (body >= 0 && body < sealed.size()) {
            return sealed.get(body).volume();
        }
        int pocket = body - sealed.size();
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        if (pocket >= 0 && pocket < pockets.size()) {
            return pockets.get(pocket).volume();
        }
        return 0;
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

    /** 水面到 {@code surfaceY} 时需要多少单位水（只累计该水位以下的整格）。 */
    private double unitsBelowLevel(int body, double surfaceY) {
        List<ShipGeometry.Compartment> sealed = geometry.compartments();
        java.util.Collection<net.minecraft.core.BlockPos> cells;
        if (body >= 0 && body < sealed.size()) {
            cells = sealed.get(body).cells();
        } else {
            int pocket = body - sealed.size();
            List<ShipGeometry.Compartment> pockets = geometry.openPockets();
            if (pocket < 0 || pocket >= pockets.size()) {
                return 0.0D;
            }
            cells = pockets.get(pocket).cells();
        }
        double units = 0.0D;
        for (net.minecraft.core.BlockPos cell : cells) {
            if (cell.getY() < surfaceY) {
                units += UNITS_PER_BLOCK;
            }
        }
        return units;
    }

    /** 第 body 个水体现在的水量（单位）。 */
    public double bodyWater(int body) {
        if (body >= 0 && body < water.length) {
            return water[body];
        }
        int pocket = body - water.length;
        if (pocket >= 0 && pocket < pocketWater.length) {
            return pocketWater[pocket];
        }
        return 0.0D;
    }

    /**
     * 自动泵的水传感器语义：这个水体里有没有积水。
     *
     * <p>不能拿 {@link #bodyLevel(int)} 和 0.05 比——它返回的是**绝对 Y 坐标**
     * （深海是 -70 以下），永远小于 0.05，自动泵就永远不会启动。用"水量 &gt; 5 单位
     * （0.05 格）"判断才和"舱里有水"一致。</p>
     */
    public boolean bodyIsWet(int body) {
        return body >= 0 && bodyWater(body) >= UNITS_PER_BLOCK * 0.05D;
    }

    /**
     * 开着的门按限速把高水位一侧的水挪到低水位一侧。
     *
     * <p>这一步是"开门之后水慢慢流过去"，而不是把两个舱并成一个（并舱会让水位
     * 瞬间均摊，关回来时水又整体跑到漏水侧）。关门没有 DoorLink，各自的水原地
     * 不动；开门每秒最多搬 {@link #DOOR_FLOW_PER_CELL} × 门格数，直到水位差进死区。</p>
     */
    public void tickDoorFlow(double dt) {
        List<ShipGeometry.DoorLink> links = geometry.doorLinks();
        if (links.isEmpty() || dt <= 0.0D) {
            return;
        }
        java.util.Map<Integer, Double> levels = new java.util.HashMap<>();
        for (ShipGeometry.DoorLink link : links) {
            if (!link.open() || link.body() == link.other()) {
                continue;
            }
            int a = link.body();
            int b = link.other();
            // 门格开在船壳外表面上：另一侧是外海，按无限水源往舱里灌。
            // 之前没有这条连线，打开顶舱口/底舱口完全不进水（用户实测）。
            if (a == ShipGeometry.SEA_BODY || b == ShipGeometry.SEA_BODY) {
                floodFromSea(a == ShipGeometry.SEA_BODY ? b : a, dt);
                continue;
            }
            double la = levels.computeIfAbsent(a, this::bodyLevel);
            double lb = levels.computeIfAbsent(b, this::bodyLevel);
            double diff = la - lb;
            if (Math.abs(diff) <= DOOR_FLOW_DEADBAND) {
                continue;
            }
            boolean aHigh = diff > 0.0D;
            int high = aHigh ? a : b;
            int low = aHigh ? b : a;
            // 想把水位差抹平需要搬的量（按两侧格子数估算）；不够一格时按比例。
            double highCells = Math.max(1, bodyVolume(high));
            double lowCells = Math.max(1, bodyVolume(low));
            double needBlocks = Math.abs(diff) * (highCells * lowCells) / (highCells + lowCells);
            double units = Math.min(needBlocks * UNITS_PER_BLOCK, DOOR_FLOW_PER_CELL * dt);
            units = Math.min(units, bodyWater(high));
            if (units <= 0.05D) {
                continue;
            }
            removeBodyWater(high, units);
            addBodyWater(low, units);
            levels.put(high, bodyLevel(high));
            levels.put(low, bodyLevel(low));
        }
    }

    /**
     * 外海 → 舱室：门开在船壳外表面时，按门流量往舱里灌，直到灌满。
     * 外海是无限水源，不需要从任何水体里扣水。
     */
    private void floodFromSea(int body, double dt) {
        double capacity = bodyCapacity(body);
        double current = bodyWater(body);
        if (capacity <= 0.0D || current >= capacity - 0.05D) {
            return;
        }
        double units = Math.min(capacity - current, DOOR_FLOW_PER_CELL * dt);
        if (units > 0.05D) {
            addBodyWater(body, units);
        }
    }

    private void removeBodyWater(int body, double units) {
        if (body >= 0 && body < water.length) {
            water[body] = Math.max(0.0D, water[body] - units);
            return;
        }
        int pocket = body - water.length;
        if (pocket >= 0 && pocket < pocketWater.length) {
            pocketWater[pocket] = Math.max(0.0D, pocketWater[pocket] - units);
        }
    }

    private void addBodyWater(int body, double units) {
        if (body >= 0 && body < water.length) {
            double capacity = volumes[body] * UNITS_PER_BLOCK;
            water[body] = Math.min(capacity, water[body] + units);
            return;
        }
        int pocket = body - water.length;
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        if (pocket >= 0 && pocket < pocketWater.length && pocket < pockets.size()) {
            double capacity = pockets.get(pocket).volume() * UNITS_PER_BLOCK;
            pocketWater[pocket] = Math.min(capacity, pocketWater[pocket] + units);
        }
    }

    /** 第 body 个水体的容量（单位）；泵入时用来判断还能灌多少。 */
    private double bodyCapacity(int body) {
        if (body >= 0 && body < water.length) {
            return volumes[body] * UNITS_PER_BLOCK;
        }
        int pocket = body - water.length;
        List<ShipGeometry.Compartment> pockets = geometry.openPockets();
        if (pocket >= 0 && pocket < pocketWater.length && pocket < pockets.size()) {
            return pockets.get(pocket).volume() * UNITS_PER_BLOCK;
        }
        return 0.0D;
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
        if (byCell == null || byCell.isEmpty()) {
            return;
        }
        List<ShipGeometry.Compartment> sealed = next.compartments();
        for (int i = 0; i < water.length && i < sealed.size(); i++) {
            double sum = 0.0D;
            for (net.minecraft.core.BlockPos cell : sealed.get(i).cells()) {
                sum += byCell.getOrDefault(cell, 0.0D);
            }
            water[i] = Math.min(volumes[i] * UNITS_PER_BLOCK, sum);
        }
        List<ShipGeometry.Compartment> pockets = next.openPockets();
        for (int i = 0; i < pocketWater.length && i < pockets.size(); i++) {
            double sum = 0.0D;
            for (net.minecraft.core.BlockPos cell : pockets.get(i).cells()) {
                sum += byCell.getOrDefault(cell, 0.0D);
            }
            pocketWater[i] = Math.min(pockets.get(i).volume() * UNITS_PER_BLOCK, sum);
        }
    }

    public void setPumpOut(boolean pumpOut) {
        this.pumpOut = pumpOut;
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
        if (cell == null) {
            return;
        }
        pumps.put(cell.immutable(), new PumpReport(
                // 单台普通泵按原版换算后是 ~14062 单位/秒，旧上限 3000 会把它削到 1/5
                Mth.clamp(rate, -30000.0D, 30000.0D),
                Mth.clamp(requestedRate, -30000.0D, 30000.0D),
                pumpTick, ballast, keepBelowDoor));
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

    /** 这一格在第几个水体里（0..n-1 密闭舱室，n.. 连海空腔）；不在任何舱里返回 -1。 */
    public int bodyOf(net.minecraft.core.BlockPos cell) {
        if (cell == null) {
            return -1;
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

    /** 反应堆方块实体每 tick 上报一次。 */
    public void reportReactor(net.minecraft.core.BlockPos cell, double outputKw, double capacityKw,
                              double temperature, boolean overheated, boolean meltingDown) {
        if (cell == null) {
            return;
        }
        reactors.put(cell.immutable(), new ReactorReport(
                Math.max(0.0D, outputKw), Math.max(0.0D, capacityKw),
                Math.max(0.0D, temperature), overheated, meltingDown, reactorTick));
    }

    /** 引擎方块实体每 tick 上报一次：目标力（0..100%）、耗电、最大推力。 */
    public void reportEngine(net.minecraft.core.BlockPos cell, double force,
                             double demandKw, double maxForce) {
        if (cell == null) {
            return;
        }
        engines.put(cell.immutable(), new EngineReport(
                Mth.clamp(force, 0.0D, 100.0D),
                Math.max(0.0D, demandKw),
                Math.max(0.0D, maxForce),
                engineTick));
    }

    /** 上一 tick 登记的引擎数量（诊断/推进倍率用）。 */
    public int engineCount() {
        return lastEngineCount;
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
        if (compartmentIndex < 0 || compartmentIndex >= water.length || units <= 0.0D) {
            return;
        }
        double capacity = volumes[compartmentIndex] * UNITS_PER_BLOCK;
        water[compartmentIndex] = Math.min(capacity, water[compartmentIndex] + units);
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

    /** 物理船第一次启动时调用一次：下一 tick 把压载舱注到中性水位。 */
    public void requestBallastPrime() {
        ballastPrimePending = true;
    }

    /** 中性水位是否已经标定；没标定前压载泵不自动动作。 */
    public boolean ballastPrimed() {
        return ballastPrimed;
    }

    private void primeBallast() {
        double total = 0.0D;
        for (double volume : volumes) {
            total += volume * UNITS_PER_BLOCK;
        }
        double need = total * NEUTRAL_WATER_RATIO - waterUnits();
        if (need > 0.0D) {
            for (int body : ballastBodies) {
                double room = bodyCapacity(body) - bodyWater(body);
                // 和压载泵注水一样：有舱门时最深只能注到舱门下方
                double limit = doorBottomLevel(body);
                if (limit < Double.MAX_VALUE) {
                    room = Math.min(room, Math.max(0.0D,
                            unitsBelowLevel(body, limit) - bodyWater(body)));
                }
                double add = Math.min(room, need);
                if (add > 0.0D) {
                    addBodyWater(body, add);
                    need -= add;
                }
                if (need <= 0.0D) {
                    break;
                }
            }
        }
        double filled = 0.0D;
        double capacity = 0.0D;
        for (int body : ballastBodies) {
            filled += bodyWater(body);
            capacity += bodyCapacity(body);
        }
        this.ballast = filled;
        this.ballastCapacity = capacity;
        this.ballastNeutral = capacity > 0.0D
                ? Math.min(1.0D, Math.max(0.0D, filled / capacity)) : 0.5D;
        this.ballastPrimed = true;
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
