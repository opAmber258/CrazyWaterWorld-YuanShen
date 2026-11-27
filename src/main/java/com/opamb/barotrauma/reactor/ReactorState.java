package com.opamb.barotrauma.reactor;

/**
 * 核反应堆的纯数值状态机（不依赖任何 Minecraft 类，方便 L1 单测）。
 *
 * <p>数值全部对照本机原作 {@code Content/Items/Reactor/reactor.xml} 和反编译的
 * {@code Barotrauma.Items.Components.Reactor}：</p>
 *
 * <ul>
 *   <li>裂变速率 / 涡轮输出各 0–100%，滑条给目标值，实际值按 1 秒时间常数跟随；</li>
 *   <li>产热 = 裂变% × (可用燃料/100) × 2 + 温度增益；温度以最多 10/s 追向
 *       {@code 产热 - 涡轮}，范围 0–100；</li>
 *   <li>出力 = clamp(当前负载, 出力带下限, 出力带上限)：带中心是涡轮实际值，
 *       宽度 ±1%（涡轮和温度都在最佳区时 ±3%），上下限再乘温度系数
 *       min(温度/50, 1)。对应原作 {@code Reactor.MinMaxPowerOut} /
 *       {@code GetConnectionPowerOut}，不是把涡轮额定功率直接灌进电网；</li>
 *   <li>温度最佳 40–60，允许 30–70；超过 60 每 20 秒起火，超过 70 累计 120 秒熔毁；
 *       起火/熔毁计时按耐久比例 {@code Lerp(2×dt, dt, condition/max)} 加速；</li>
 *   <li>自动控制：涡轮追当前负载对应的理想值，裂变追最佳区中心；温度过高降裂变，
 *       输出不足升裂变；</li>
 *   <li>±25 温度增益按钮，使用后按 1/s 衰减回 0。</li>
 * </ul>
 *
 * <p>燃料消耗在方块实体里做（要动物品栏），这里只接收"可用燃料值"。原作
 * AvailableFuel 是每根燃料棒的额定值（铀 80 / 钍 100 / 富尔古瑞姆 150），多根相加；
 * 四根富尔古瑞姆全满就是 600。剩余耐久只影响燃料棒还能烧多久，不影响额定值。</p>
 */
public final class ReactorState {

    public static final double MAX_POWER_KW = 5000.0D;

    /** 100% 裂变时每根燃料棒每秒消耗的耐久点数（原作 fuelconsumptionrate=0.2）。 */
    public static final double FUEL_BURN_PER_SECOND = 0.2D;

    public static final double OPTIMAL_TEMP_MIN = 40.0D;
    public static final double OPTIMAL_TEMP_MAX = 60.0D;
    public static final double ALLOWED_TEMP_MIN = 30.0D;
    public static final double ALLOWED_TEMP_MAX = 70.0D;

    /** 超过允许温度多少秒后熔毁（原作 meltdowndelay=120）。 */
    public static final double MELTDOWN_DELAY = 120.0D;
    /** 超过最佳温度后每隔多少秒起火（原作 firedelay=20）。 */
    public static final double FIRE_DELAY = 20.0D;

    /** 温度增益按钮一次加减的温度（原作 TemperatureBoostAmount=25）。 */
    public static final double TEMPERATURE_BOOST = 25.0D;

    public static final int EVENT_FIRE = 1;
    public static final int EVENT_MELTDOWN = 2;

    /** 涡轮最佳带半径：原作 optimalTurbineOutput = CorrectTurbineOutput ± Lerp(2.5, 10, 技能)。 */
    private static final double OPTIMAL_TURBINE_HALF_WIDTH = 2.5D;

    private boolean powerOn;
    private boolean autoTemp = true;
    private double targetFission;
    private double targetTurbine;
    private boolean dugongStock;
    private boolean outpost;

    public void setDugongStock(boolean dugongStock) {
        this.dugongStock = dugongStock;
    }

    public void setOutpost(boolean outpost) {
        this.outpost = outpost;
    }

    private double fission;
    private double turbine;
    private double temperature;
    private double temperatureBoost;
    private double correctTurbine;
    private double meltdownTimer;
    private double fireTimer;
    private boolean meltedDown;
    private double outputKw;
    private double minOutputKw;
    private double maxOutputKw;

    /**
     * 推进一个时间步（耐久按满值算）。
     *
     * @param dt              秒（方块实体每 tick 调一次，传 0.05）
     * @param availableFuel   可用燃料值（原作 AvailableFuel：铀 80 / 钍 100 / 富尔古瑞姆 150，
     *                        四根全满最多 600）
     * @param loadKw          本船当前电力负载（kW，含电池充电请求）
     * @return 位掩码，{@link #EVENT_FIRE} / {@link #EVENT_MELTDOWN}
     */
    public int tick(double dt, double availableFuel, double loadKw) {
        return tick(dt, availableFuel, loadKw, 1.0D);
    }

    /**
     * 推进一个时间步。
     *
     * @param conditionFraction 当前耐久比例（0..1）。原作起火/熔毁计时按
     *                          {@code Lerp(dt×2, dt, condition/max)} 加速，耐久越差越快。
     */
    public int tick(double dt, double availableFuel, double loadKw,
                    double conditionFraction) {
        if (!Double.isFinite(dt) || dt <= 0.0D) {
            return 0;
        }
        availableFuel = clamp(availableFuel, 0.0D, 600.0D);
        loadKw = Math.max(0.0D, loadKw);
        double condition = clamp(conditionFraction, 0.0D, 1.0D);

        if (meltedDown) {
            powerOn = false;
            autoTemp = false;
            targetFission = 0.0D;
            targetTurbine = 0.0D;
            fission = approach(fission, 0.0D, 100.0D * dt);
            turbine = approach(turbine, 0.0D, 100.0D * dt);
            temperature = Math.max(0.0D, temperature - 1000.0D * dt);
            outputKw = 0.0D;
            minOutputKw = 0.0D;
            maxOutputKw = 0.0D;
            return 0;
        }

        // 温度：先算产热目标，再以最多 10/s 追过去（原作 Update）。
        double fuelFactor = availableFuel / 100.0D;
        double heat = fission * fuelFactor * 2.0D + temperatureBoost;
        double equilibrium = heat - turbine;
        temperature = approach(temperature, equilibrium, 10.0D * dt);
        temperature = clamp(temperature, 0.0D, 100.0D);
        temperatureBoost = approach(temperatureBoost, 0.0D, dt);

        // 实际裂变/涡轮按约 1 秒时间常数跟随目标；裂变受可用燃料上限约束。
        double fissionTarget = Math.min(targetFission, availableFuel);
        fission = lerp(fission, fissionTarget, Math.min(1.0D, dt));
        turbine = lerp(turbine, targetTurbine, Math.min(1.0D, dt));

        // 理想涡轮：把当前负载吃满所需的百分比（原作 CorrectTurbineOutput）。
        double idealTurbine = loadKw / capacityKw() * 100.0D;
        correctTurbine = clamp(correctTurbine
                + clamp(idealTurbine - correctTurbine, -20.0D, 20.0D) * dt, 0.0D, 100.0D);

        // 出力带（原作 MinMaxPowerOut）：涡轮若落在 CorrectTurbine ±2.5 且温度在
        // 最佳区，带宽 ±3%，否则 ±1%；上下限再被温度系数 min(温度/50, 1) 截断。
        double temperatureFactor = Math.min(temperature / 50.0D, 1.0D);
        boolean optimalBand = turbine > correctTurbine - OPTIMAL_TURBINE_HALF_WIDTH
                && turbine < correctTurbine + OPTIMAL_TURBINE_HALF_WIDTH
                && temperature > OPTIMAL_TEMP_MIN && temperature < OPTIMAL_TEMP_MAX;
        double band = optimalBand ? 3.0D : 1.0D;
        double minFraction = clamp(
                Math.min((turbine - band) / 100.0D, temperatureFactor), 0.0D, 1.0D);
        minOutputKw = capacityKw() * minFraction;
        maxOutputKw = capacityKw() * Math.max(minFraction,
                Math.min((turbine + band) / 100.0D, temperatureFactor));

        // 原作 GetConnectionPowerOut：反应堆只按需求出力，夹在本机出力带里。
        // 负载在带内 → 输出正好等于负载（电压 1.0）；只有负载低于带底时才过压。
        outputKw = clamp(loadKw, minOutputKw, maxOutputKw);

        if (!powerOn) {
            targetFission = 0.0D;
            targetTurbine = 0.0D;
        } else if (autoTemp) {
            updateAuto(dt, availableFuel, loadKw);
        }

        // 原作 UpdateFailures：超过允许温度累计熔毁、超过最佳温度累计起火；
        // 计时速度按耐久 Lerp(2×dt, dt, condition/max)。
        int events = 0;
        double damageScale = 2.0D - condition;
        if (temperature > ALLOWED_TEMP_MAX) {
            meltdownTimer += dt * damageScale;
            if (meltdownTimer >= (dugongStock ? 30.0D : MELTDOWN_DELAY)) {
                meltdownTimer = dugongStock ? 30.0D : MELTDOWN_DELAY;
                meltedDown = true;
                powerOn = false;
                autoTemp = false;
                targetFission = 0.0D;
                targetTurbine = 0.0D;
                events |= EVENT_MELTDOWN;
            }
        } else {
            meltdownTimer = Math.max(0.0D, meltdownTimer - dt);
        }

        if (temperature > OPTIMAL_TEMP_MAX) {
            fireTimer += dt * damageScale;
            if (fireTimer >= (dugongStock ? 10.0D : FIRE_DELAY)) {
                fireTimer = 0.0D;
                events |= EVENT_FIRE;
            }
        } else {
            fireTimer = Math.max(0.0D, fireTimer - dt);
        }
        return events;
    }

    /** 原作 UpdateBroken：损坏后温度快速降到 0，目标值以 10/s 回落，不再起火/熔毁。 */
    public void brokenTick(double dt) {
        if (!Double.isFinite(dt) || dt <= 0.0D) {
            return;
        }
        targetFission = Math.max(0.0D, targetFission - 10.0D * dt);
        targetTurbine = Math.max(0.0D, targetTurbine - 10.0D * dt);
        temperature = Math.max(0.0D, temperature - 1000.0D * dt);
        outputKw = 0.0D;
        minOutputKw = 0.0D;
        maxOutputKw = 0.0D;
    }

    /** 原作 UpdateAutoTemp：涡轮追 CorrectTurbine，裂变追最佳区中心。 */
    private void updateAuto(double dt, double availableFuel, double loadKw) {
        targetTurbine = approach(targetTurbine, correctTurbine, 2.0D * dt);
        targetTurbine = clamp(targetTurbine, 0.0D, 100.0D);

        double center = (optimalFissionMin(availableFuel)
                + optimalFissionMax(availableFuel)) * 0.5D;
        targetFission = approach(targetFission, center, 2.0D * dt);

        if (temperature > (OPTIMAL_TEMP_MIN + OPTIMAL_TEMP_MAX) * 0.5D) {
            targetFission = Math.min(targetFission - 4.0D * dt,
                    allowedFissionMax(availableFuel));
        } else if (outputKw < loadKw) {
            targetFission = Math.min(targetFission + 4.0D * dt, 100.0D);
        }
        targetFission = clamp(targetFission, 0.0D, 100.0D);
        // 原作防止自动控制一步跳太远：目标值不离开当前裂变 ±5。
        targetFission = clamp(targetFission, Math.max(0.0D, fission - 5.0D),
                Math.min(100.0D, fission + 5.0D));
    }

    // ------------------------------------------------------------------ 查询

    public boolean powerOn() {
        return powerOn;
    }

    public void setPowerOn(boolean powerOn) {
        if (meltedDown) {
            this.powerOn = false;
            return;
        }
        this.powerOn = powerOn;
    }

    public boolean autoTemp() {
        return autoTemp;
    }

    public void setAutoTemp(boolean autoTemp) {
        this.autoTemp = autoTemp;
    }

    public double targetFission() {
        return targetFission;
    }

    public void setTargetFission(double value) {
        this.targetFission = clamp(value, 0.0D, 100.0D);
    }

    public double targetTurbine() {
        return targetTurbine;
    }

    public void setTargetTurbine(double value) {
        this.targetTurbine = clamp(value, 0.0D, 100.0D);
    }

    public double fission() {
        return fission;
    }

    public double turbine() {
        return turbine;
    }

    public double temperature() {
        return temperature;
    }

    /** 调试/测试用：直接设置温度（正常游戏里只由 tick 改变）。 */
    void setTemperatureForTest(double value) {
        this.temperature = clamp(value, 0.0D, 100.0D);
    }

    public double temperatureBoost() {
        return temperatureBoost;
    }

    /** 按一次 ±25 温度增益；与最近一次增益叠加并夹在 ±25。 */
    public void applyTemperatureBoost(double amount) {
        this.temperatureBoost = clamp(temperatureBoost + amount,
                -TEMPERATURE_BOOST, TEMPERATURE_BOOST);
    }

    public double meltdownTimer() {
        return meltdownTimer;
    }

    /** 当前负载对应的理想涡轮百分比（面板画表盘绿区用）。 */
    public double correctTurbine() {
        return correctTurbine;
    }

    public double fireTimer() {
        return fireTimer;
    }

    public boolean meltedDown() {
        return meltedDown;
    }

    public double outputKw() {
        return outputKw;
    }

    /** 本 tick 反应堆能给出的最小出力（原作 minUpdatePowerOut，kW）。 */
    public double minOutputKw() {
        return minOutputKw;
    }

    /** 本 tick 反应堆能给出的最大出力（原作 maxUpdatePowerOut，kW）。 */
    public double maxOutputKw() {
        return maxOutputKw;
    }

    public double capacityKw() {
        if (outpost) return 20000.0D;
        return dugongStock ? 3000.0D : MAX_POWER_KW;
    }

    public boolean overheated() {
        return temperature > ALLOWED_TEMP_MAX;
    }

    public boolean temperatureCritical() {
        return temperature > ALLOWED_TEMP_MAX;
    }

    public boolean temperatureLow() {
        return temperature < ALLOWED_TEMP_MIN;
    }

    // ------------------------------------------------------------------ 区间

    /**
     * 最佳裂变区下限。原作 {@code optimalFissionRate = Lerp([30, fuel-20],
     * [20, fuel-10], 技能)}，技能按 0 算：{@code [30, fuel-20]}，且下限不高于
     * 上限减 10。
     */
    public static double optimalFissionMin(double availableFuel) {
        double max = optimalFissionMax(availableFuel);
        return Math.max(0.0D, Math.min(30.0D, max - 10.0D));
    }

    /** 最佳裂变区上限（原作 {@code fuel-20}；技能越高变成 {@code fuel-10}）。 */
    public static double optimalFissionMax(double availableFuel) {
        return Math.max(0.0D, availableFuel - 20.0D);
    }

    /**
     * 允许裂变区下限。原作 {@code allowedFissionRate = Lerp([20, fuel],
     * [10, fuel], 技能)}，技能按 0 算：{@code [20, fuel]}，下限不高于上限减 10。
     */
    public static double allowedFissionMin(double availableFuel) {
        double max = allowedFissionMax(availableFuel);
        return Math.max(0.0D, Math.min(20.0D, max - 10.0D));
    }

    /** 允许裂变区上限（原作就是 AvailableFuel，可以超过 100；目标值另行夹到 100）。 */
    public static double allowedFissionMax(double availableFuel) {
        return Math.max(0.0D, availableFuel);
    }

    // ------------------------------------------------------------------ 工具

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double approach(double from, double to, double maxDelta) {
        if (to < from) {
            return Math.max(to, from - maxDelta);
        }
        return Math.min(to, from + maxDelta);
    }

    // ------------------------------------------------------------------ 存档

    /** 把玩家可调状态写进 NBT 的辅助快照（方块实体负责实际 NBT 读写）。 */
    public double[] snapshot() {
        return new double[] {
                powerOn ? 1 : 0, autoTemp ? 1 : 0,
                targetFission, targetTurbine, fission, turbine, temperature,
                temperatureBoost, meltdownTimer, fireTimer, meltedDown ? 1 : 0
        };
    }

    /** 从 {@link #snapshot()} 恢复。 */
    public void restore(double[] values) {
        if (values == null || values.length < 11) {
            return;
        }
        powerOn = values[0] != 0.0D;
        autoTemp = values[1] != 0.0D;
        targetFission = clamp(values[2], 0.0D, 100.0D);
        targetTurbine = clamp(values[3], 0.0D, 100.0D);
        fission = clamp(values[4], 0.0D, 100.0D);
        turbine = clamp(values[5], 0.0D, 100.0D);
        temperature = clamp(values[6], 0.0D, 100.0D);
        temperatureBoost = clamp(values[7], -TEMPERATURE_BOOST, TEMPERATURE_BOOST);
        meltdownTimer = Math.max(0.0D, values[8]);
        fireTimer = Math.max(0.0D, values[9]);
        meltedDown = values[10] != 0.0D;
    }
}
