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
 *   <li>功率 = 5000 kW × 涡轮% × min(温度/50, 1)；</li>
 *   <li>温度最佳 40–60，允许 30–70；超过 60 每 20 秒起火，超过 70 累计 120 秒熔毁；</li>
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

    private boolean powerOn;
    private boolean autoTemp = true;
    private double targetFission;
    private double targetTurbine;
    private double fission;
    private double turbine;
    private double temperature;
    private double temperatureBoost;
    private double correctTurbine;
    private double meltdownTimer;
    private double fireTimer;
    private boolean meltedDown;
    private double outputKw;

    /**
     * 推进一个时间步。
     *
     * @param dt              秒（方块实体每 tick 调一次，传 0.05）
     * @param availableFuel   可用燃料值（原作 AvailableFuel：铀 80 / 钍 100 / 富尔古瑞姆 150，
     *                        四根全满最多 600）
     * @param loadKw          本船当前电力负载（kW）
     * @return 位掩码，{@link #EVENT_FIRE} / {@link #EVENT_MELTDOWN}
     */
    public int tick(double dt, double availableFuel, double loadKw) {
        if (!Double.isFinite(dt) || dt <= 0.0D) {
            return 0;
        }
        availableFuel = clamp(availableFuel, 0.0D, 600.0D);
        loadKw = Math.max(0.0D, loadKw);

        if (meltedDown) {
            powerOn = false;
            autoTemp = false;
            targetFission = 0.0D;
            targetTurbine = 0.0D;
            fission = approach(fission, 0.0D, 100.0D * dt);
            turbine = approach(turbine, 0.0D, 100.0D * dt);
            temperature = Math.max(0.0D, temperature - 1000.0D * dt);
            outputKw = 0.0D;
            return 0;
        }

        if (!powerOn) {
            targetFission = 0.0D;
            targetTurbine = 0.0D;
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
        double idealTurbine = loadKw / MAX_POWER_KW * 100.0D;
        correctTurbine = clamp(correctTurbine
                + clamp(idealTurbine - correctTurbine, -20.0D, 20.0D) * dt, 0.0D, 100.0D);

        double temperatureFactor = Math.min(temperature / 50.0D, 1.0D);
        outputKw = MAX_POWER_KW * (turbine / 100.0D) * temperatureFactor;

        if (powerOn && autoTemp) {
            updateAuto(dt, availableFuel, loadKw);
        }

        int events = 0;
        if (temperature > ALLOWED_TEMP_MAX) {
            meltdownTimer += dt;
            if (meltdownTimer >= MELTDOWN_DELAY) {
                meltdownTimer = MELTDOWN_DELAY;
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
            fireTimer += dt;
            if (fireTimer >= FIRE_DELAY) {
                fireTimer = 0.0D;
                events |= EVENT_FIRE;
            }
        } else {
            fireTimer = Math.max(0.0D, fireTimer - dt);
        }
        return events;
    }

    /** 原作 UpdateAutoTemp 的简化版：追最佳区中心，再按温度/负载微调裂变。 */
    private void updateAuto(double dt, double availableFuel, double loadKw) {
        targetTurbine = approach(targetTurbine, correctTurbine, 2.0D * dt);
        targetTurbine = clamp(targetTurbine, 0.0D, 100.0D);

        double optMin = optimalFissionMin(availableFuel);
        double optMax = optimalFissionMax(availableFuel);
        double center = (optMin + optMax) * 0.5D;
        targetFission = approach(targetFission, center, 2.0D * dt);

        if (temperature > (OPTIMAL_TEMP_MIN + OPTIMAL_TEMP_MAX) * 0.5D) {
            targetFission = Math.max(0.0D, targetFission - 4.0D * dt);
        } else if (outputKw < loadKw) {
            targetFission = Math.min(100.0D, targetFission + 4.0D * dt);
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

    public double capacityKw() {
        return MAX_POWER_KW;
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

    /** 最佳裂变区下限（按可用燃料，参考原作 optimalFissionRate）。 */
    public static double optimalFissionMin(double availableFuel) {
        return Math.max(0.0D, Math.min(25.0D, availableFuel - 20.0D));
    }

    /** 最佳裂变区上限。 */
    public static double optimalFissionMax(double availableFuel) {
        return Math.max(optimalFissionMin(availableFuel),
                Math.min(100.0D, availableFuel - 15.0D));
    }

    /** 允许裂变区下限。 */
    public static double allowedFissionMin(double availableFuel) {
        return Math.max(0.0D, Math.min(15.0D, availableFuel - 10.0D));
    }

    /** 允许裂变区上限（燃料不够时裂变也上不去）。 */
    public static double allowedFissionMax(double availableFuel) {
        return Math.max(0.0D, Math.min(100.0D, availableFuel));
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
