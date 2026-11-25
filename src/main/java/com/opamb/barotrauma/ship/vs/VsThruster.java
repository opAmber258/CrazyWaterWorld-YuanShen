package com.opamb.barotrauma.ship.vs;

import com.opamb.barotrauma.ship.ShipSimulation;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;

import java.util.Locale;

/**
 * 挂在 VS2 船上的推进器 + 姿态稳定器。
 *
 * <p>VS2 每个物理步都会回调 {@link #physTick}，这里把"前进舵令 / 逐引擎推力 / 压载"换算成力施加给刚体。
 * 之所以在物理回调里算而不是每 server tick 算一次：物理步比 tick 密，力是连续给的，船才不会一顿一顿。</p>
 *
 * <p>推力按原作的逐引擎力给：{@code maxforce × 推力% × min(电压,2)^(2/3) × 耐久插值}，
 * 只沿船头前后，没有横移。原作这套力是按儒艮级 47 t 刚体调的；VS2 的方块质量是另一套单位，
 * 所以先换算成"原作加速度"再乘 VS2 质量，见 {@link #REFERENCE_SHIP_MASS}。</p>
 *
 * <p>水阻同样用原作的二次式，但按<b>船体轴</b>分解：纵向 0.01、横向/垂直 0.05。
 * 原作是 2D 且 {@code FixedRotation = true}，世界轴就等于船体轴；3D 有偏航后
 * 必须跟着船头转，否则转弯时阻力还按旧的世界方向算。</p>
 *
 * <h2>姿态：俯仰/横滚锁死，偏航可转</h2>
 *
 * <p>潜艇不能像木筏一样翻。推力作用在质心上，不产生力矩；倾斜只可能来自碰撞。这里做三件事：</p>
 * <ol>
 *   <li><b>俯仰/横滚阻尼</b>：只压世界 X/Z 角速度，把翻滚掐死在源头；</li>
 *   <li><b>回正力矩</b>：船被撞歪了，按倾角把"船的上方向"拉回世界 +Y；</li>
 *   <li><b>偏航 PD</b>：A/D 给目标角速度（原作 2D 没有偏航，这是 3D 适配）；
 *       导航台/电网有电且有引擎时才有舵效，松舵/断电时把偏航角速度拉回 0。</li>
 * </ol>
 * <p>力矩都乘了质量——转动惯量随船规模增长，不按质量缩放的话大船纹丝不动、小船被甩飞。</p>
 */
public final class VsThruster implements ShipPhysicsListener {

    /** The mod's standard engine at +100%, rated voltage and full condition. */
    private static final double REFERENCE_RAW_THRUST = com.opamb.barotrauma.blockentity.EngineBlockEntity.DUGONG_MAX_FORCE * 100.0D * 2.0D;
    /** Original Dugong's measured 21.6 km/h (6 m/s) at full ahead. */
    private static final double REFERENCE_TOP_SPEED = 6.0D;
    /**
     * 原版 {@code SubmarineBody} 的二次水阻：纵向 0.01、垂直 0.05，封顶 0.1；
     * 原作还会加“挂在船上的角色质量 / 5000”（本模组暂不计）。
     *
     * <p>原作是 2D 且 {@code FixedRotation = true}，所以世界 X = 船体纵向、
     * 世界 Y = 垂直；3D 有偏航后必须按船体轴分解，否则船头转了、阻力还按
     * 旧的世界方向算。横向是原作 2D 没有的轴，取 0.05（和垂直同档，把
     * “非纵向”都当高阻力），转弯时速度会更贴船头。</p>
     */
    private static final double DRAG_LONGITUDINAL = 0.01D;
    private static final double DRAG_LATERAL = 0.05D;
    private static final double DRAG_VERTICAL = 0.05D;
    private static final double DRAG_MAX = 0.1D;
    /** 维度重力，和 data/valkyrienskies/vs_dimension_parameters/the_deep.json 里的 -10 对应。 */
    private static final double GRAVITY = 10.0D;

    /** 角速度阻尼（乘质量；越大越"锁死"在水平）。 */
    private static final double ATTITUDE_DAMPING = 900.0D;
    /** 姿态回正增益（乘质量；每弧度倾角给多少力矩）。 */
    private static final double ATTITUDE_GAIN = 700.0D;
    /** 力矩上限（乘质量），防止极端情况下把船甩飞。 */
    private static final double ATTITUDE_TORQUE_CAP = 4000.0D;

    /** 3D 适配：A/D 偏航的最大角速度，15°/s（180° 约 12 秒）。 */
    private static final double MAX_YAW_RATE = Math.toRadians(15.0D);
    /** 偏航角速度跟踪增益（乘质量）；约 2 秒达到目标转速。 */
    private static final double YAW_GAIN = 50.0D;
    /** 偏航力矩上限（乘质量）。 */
    private static final double YAW_TORQUE_CAP = 500.0D;

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }

    /** Shared by the steering prediction and the force actually sent to the rigid body. */
    static double forwardAcceleration(double rawForce, double skill, double multiplier) {
        double skillFactor = 0.1D * (0.5D + 1.5D * Math.sqrt(clamp01(skill / 100.0D)));
        double referenceSkillFactor = 0.1D * (0.5D + 1.5D * Math.sqrt(0.5D));
        double referenceAcceleration = DRAG_LONGITUDINAL * REFERENCE_TOP_SPEED * REFERENCE_TOP_SPEED;
        return rawForce / REFERENCE_RAW_THRUST * (skillFactor / referenceSkillFactor)
                * multiplier * referenceAcceleration;
    }

    static double verticalAcceleration(double waterRatio, double speed) {
        double lift = ShipSimulation.NEUTRAL_WATER_RATIO - clamp01(waterRatio);
        lift = lift > 0.0D ? lift * 2.0D : Math.max(lift, -0.5D);
        return GRAVITY * lift - speed * Math.abs(speed) * Math.min(DRAG_MAX, DRAG_VERTICAL);
    }

    private volatile int helmForward;
    private volatile int helmYaw;
    /** 偏航舵令幅度 0..1：手动满舵 = 1，自动驾驶按航向误差成比例（防左右摇）。 */
    private volatile double helmYawScale = 1.0D;
    private volatile int helmVertical;
    /**
     * 舵效供电：导航台/电网有电时才有偏航力矩（原作导航台没电就不发舵令）。
     * 和推进分开：引擎空转时也能转舵，断电时偏航归零并刹住。
     */
    private volatile boolean helmPowered;
    private volatile int power = 100;
    /** 船上已安装的引擎数量；0 时完全不能推进。 */
    private volatile int engineCount;
    /**
     * 逐引擎原始推力之和（原作 {@code Engine.Update} 的 maxforce × 推力% ×
     * 电压^(2/3) × 耐久插值）。还没乘驾驶技能 0.1 系数。
     */
    private volatile double engineForceRaw;
    /** 掌舵者驾驶技能（0..100）；技能系统落地前先用 50（船长起始中值）。 */
    private volatile double helmSkill = 50.0D;
    /** 船头方向（船体局部 XZ 平面的单位向量）；由引擎/核心位置反推。 */
    private volatile double forwardLocalX = 1.0D;
    private volatile double forwardLocalZ = 0.0D;
    /**
     * 全船水量占比（含压载水，0..1）。原版 {@code SubmarineBody.CalculateBuoyancy()}
     * 的唯一输入：7% 中性，低于 7% 正浮力 ×2，低于 0 的部分封顶 -0.5。
     */
    private volatile double waterRatio;

    // 诊断用：上一次物理步实际施加的力（牛顿）
    private volatile double lastForceX;
    private volatile double lastForceY;
    private volatile double lastForceZ;

    /** 物理回调被调用的次数。用来判断"力没生效"到底是没回调还是回调了没用。 */
    private static final java.util.concurrent.atomic.AtomicLong CALLS =
            new java.util.concurrent.atomic.AtomicLong();

    public static long calls() {
        return CALLS.get();
    }

    // 物理回调里看到的最新状态：判断"船不动/穿模"到底是静态船、还是浮力/碰撞没算
    private static volatile boolean probeStatic;
    private static volatile double probeMass;
    private static volatile double probeLiquid;
    private static volatile double probeForceY;
    private static volatile double probeForceX;
    private static volatile double probeForceZ;
    private static volatile double probeX;
    private static volatile double probeY;
    private static volatile double probeZ;
    /** 物理体自己的速度——判断"力到底进没进积分器"的关键。 */
    private static volatile double probeVelX;
    private static volatile double probeVelY;
    private static volatile double probeVelZ;
    private static volatile boolean probeSleeping;
    /** 回调给的 PhysShip 和 level.getShipById() 是不是同一个对象。 */
    private static volatile String probeLive = "";
    /** 姿态：倾角（度）和船头朝向的世界偏航（度）。 */
    private static volatile double probeTiltDegrees;
    private static volatile double probeYawDegrees;

    public static String probe() {
        return String.format(Locale.ROOT,
                "static=%s sleeping=%s live=%s mass=%.0f liquid=%.3f 力=(%.0f,%.0f,%.0f) 物理体pos=(%.1f,%.1f,%.1f) vel=(%.3f,%.3f,%.3f)",
                probeStatic, probeSleeping, probeLive, probeMass, probeLiquid,
                probeForceX, probeForceY, probeForceZ,
                probeX, probeY, probeZ, probeVelX, probeVelY, probeVelZ);
    }

    /** 姿态摘要：倾角越小越好，理想是 0.0°。 */
    public static String attitude() {
        return String.format(Locale.ROOT, "倾角 %.1f° 偏航 %.1f°", probeTiltDegrees, probeYawDegrees);
    }

    /** 当前倾角（度）。0 = 完全平行于海平面；GameTest 用它断言"不会翻"。 */
    public static double tiltDegrees() {
        return probeTiltDegrees;
    }

    /**
     * 物理船舵令：前进/后退 + 偏航 + 垂直。
     *
     * @param forward +1 前进 / −1 后退 / 0 停车
     * @param yaw     +1 左转 / −1 右转 / 0 保持（绕世界 +Y 正方向）
     * @param vertical +1 上浮 / −1 下潜 / 0 保持（压载泵读 ShipState.throttleY）
     */
    public void setHelm(int forward, int yaw, int vertical) {
        helmForward = clampAxis(forward);
        helmYaw = clampAxis(yaw);
        helmVertical = clampAxis(vertical);
    }

    private static int clampAxis(int value) {
        return value < 0 ? -1 : Math.min(value, 1);
    }

    public int helmForward() {
        return helmForward;
    }

    public int helmYaw() {
        return helmYaw;
    }

    public void setHelmYawScale(double scale) {
        helmYawScale = Double.isFinite(scale) ? Math.max(0.0D, Math.min(1.0D, scale)) : 0.0D;
    }

    public int helmVertical() {
        return helmVertical;
    }

    /** 导航台/电网是否有电；由 {@code VsPilot.applyControl} 每 tick 同步。 */
    public void setHelmPowered(boolean powered) {
        helmPowered = powered;
    }

    public boolean helmPowered() {
        return helmPowered;
    }

    /** 船头方向（船体局部 XZ，未归一化也可以，物理步里会归一化）。 */
    public void setForward(double x, double z) {
        if (Double.isFinite(x) && Double.isFinite(z) && x * x + z * z > 1.0E-9D) {
            forwardLocalX = x;
            forwardLocalZ = z;
        }
    }

    public double forwardLocalX() {
        return forwardLocalX;
    }

    public double forwardLocalZ() {
        return forwardLocalZ;
    }

    /** 引擎升级推力倍率（ShipState 推过来；1.0 = 未升级）。 */
    private volatile double engineMultiplier = 1.0D;

    public void setEngineMultiplier(double multiplier) {
        engineMultiplier = Math.max(1.0D, Math.min(multiplier, 2.0D));
    }

    public double engineMultiplier() {
        return engineMultiplier;
    }

    public void setPower(int percent) {
        power = percent < 5 ? 5 : Math.min(percent, 100);
    }

    public int power() {
        return power;
    }

    /** 船上引擎数量；多台叠加推力，0 台完全不推进（最多 8 台，见 ShipSimulation.MAX_ENGINES）。 */
    public void setEngineCount(int count) {
        engineCount = Math.max(0, Math.min(count, com.opamb.barotrauma.ship.ShipSimulation.MAX_ENGINES));
    }

    public int engineCount() {
        return engineCount;
    }

    /** 逐引擎原始推力之和（原作力单位）；由 ShipSimulation 每 tick 汇总。 */
    public void setEngineForceRaw(double force) {
        engineForceRaw = force;
    }

    public double engineForceRaw() {
        return engineForceRaw;
    }

    /** 掌舵者驾驶技能 0..100（原作 num2 = 0.1 × lerp(0.5,2,√(skill/100))）。 */
    public void setHelmSkill(double skill) {
        helmSkill = Math.max(0.0D, Math.min(skill, 100.0D));
    }

    private boolean powered() {
        return engineForceRaw != 0.0D && engineCount > 0;
    }

    /** 全船水量占比（含压载水），原版浮力公式的唯一输入。 */
    public void setWaterRatio(double ratio) {
        waterRatio = ratio;
    }

    public double lastForceX() {
        return lastForceX;
    }

    public double lastForceY() {
        return lastForceY;
    }

    public double lastForceZ() {
        return lastForceZ;
    }

    /** 唤醒次数：诊断"力没进积分器"到底是休眠还是别的。 */
    private static final java.util.concurrent.atomic.AtomicLong WAKES =
            new java.util.concurrent.atomic.AtomicLong();

    public static long wakes() {
        return WAKES.get();
    }

    @Override
    public void physTick(PhysShip ship, PhysLevel level) {
        CALLS.incrementAndGet();
        // 回调拿到的 ship 可能只是一份"快照"，物理世界里的真身要用 level.getShipById 取。
        // 如果两者不是同一个对象，力施加在快照上就会全部丢失——这正是"力一直在算、
        // 速度恒为 0"的另一种可能原因，直接探出来。
        org.valkyrienskies.core.api.ships.PhysShip live = null;
        try {
            live = level.getShipById(ship.getId());
            probeLive = live == null ? "无" : (live == ship ? "同一个" : "不同对象");
        } catch (Throwable error) {
            probeLive = "查询异常";
        }
        // 后面一律用 target：读 transform / 质心 / 施力必须是同一个对象。
        // 不能从回调的 ship 读旋转和质心、却把力压在 target 上——两个对象不同步时
        // 力的方向和位置都会差一个物理步。
        org.valkyrienskies.core.api.ships.PhysShip target = live != null ? live : ship;
        double mass = target.getMass();
        // VS2 会把静止的船标成 static / 让它进休眠；之后力不再进积分器
        // （实测：力每个物理步都在给 325 kN，速度却恒定 0、位置一个格都不动）。
        // 只要舵令不是零就保证这艘船是动态的——垂直舵令也算：压载改变水量后
        // 浮力要在物理步里生效，不能因为船在睡觉就不动。
        if ((target.isStatic() || target.isSleeping())
                && (helmForward != 0 || helmYaw != 0 || helmVertical != 0)) {
            target.setStatic(false);
            WAKES.incrementAndGet();
        }
        probeStatic = target.isStatic();
        probeMass = mass;
        probeLiquid = target.getLiquidOverlap();
        probeSleeping = target.isSleeping();
        org.joml.Vector3dc where = target.getTransform().getPositionInWorld();
        probeX = where.x();
        probeY = where.y();
        probeZ = where.z();
        org.joml.Vector3dc vel = target.getVelocity();
        probeVelX = vel.x();
        probeVelY = vel.y();
        probeVelZ = vel.z();

        stabilize(target, mass);

        if (mass <= 0.0D) {
            return;
        }
        // 船头在水平面上的世界方向：推力和阻力共用这一套轴。
        Vector3d forwardLocal = new Vector3d(forwardLocalX, 0.0D, forwardLocalZ);
        if (forwardLocal.lengthSquared() < 1.0E-9D) {
            forwardLocal.set(1.0D, 0.0D, 0.0D);
        }
        forwardLocal.normalize();
        Vector3d forwardWorld = target.getTransform().getShipToWorldRotation()
                .transform(forwardLocal, new Vector3d());
        forwardWorld.y = 0.0D;
        if (forwardWorld.lengthSquared() < 1.0E-9D) {
            forwardWorld.set(1.0D, 0.0D, 0.0D);
        }
        forwardWorld.normalize();
        double forwardX = forwardWorld.x;
        double forwardZ = forwardWorld.z;
        // 右舷（水平面内垂直船头）。二次阻力不看正负号，只用来分解速度。
        double rightX = -forwardZ;
        double rightZ = forwardX;

        double ax = 0.0D;
        double az = 0.0D;
        // 舵令回中后，引擎报告仍会短暂有推力。按实际轴力衰减，和反转共用同一路径。
        if (helmPowered && powered()) {
            double accel = forwardAcceleration(engineForceRaw, helmSkill, engineMultiplier);
            ax = forwardX * accel;
            az = forwardZ * accel;
        }
        // 原版二次水阻，按船体轴分解：纵向 0.01、横向/垂直 0.05，封顶 0.1。
        // 原作 2D 的 FixedRotation 让世界轴等于船体轴；3D 有偏航后必须用船体轴，
        // 否则船头转了、阻力还按旧的世界方向算，转弯会像在冰上漂。
        double vForward = vel.x() * forwardX + vel.z() * forwardZ;
        double vLateral = vel.x() * rightX + vel.z() * rightZ;
        double dragLongitudinal = Math.min(DRAG_MAX, DRAG_LONGITUDINAL);
        double dragLateral = Math.min(DRAG_MAX, DRAG_LATERAL);
        double accelForward = -Math.signum(vForward) * vForward * vForward * dragLongitudinal;
        double accelLateral = -Math.signum(vLateral) * vLateral * vLateral * dragLateral;
        ax += forwardX * accelForward + rightX * accelLateral;
        az += forwardZ * accelForward + rightZ * accelLateral;
        // 原版 SubmarineBody.CalculateBuoyancy()：刚体忽略重力（IgnoreGravity = true），
        // 垂直力只有浮力：lift = 0.07 - 全船水量占比，正浮力 ×2，负浮力封顶 -0.5。
        // +1 这一项补偿 VS2 的世界重力 -10，等价于原作的 IgnoreGravity。
        double ay = GRAVITY + verticalAcceleration(waterRatio, vel.y());

        double fx = ax * mass;
        double fy = ay * mass;
        double fz = az * mass;
        lastForceX = fx;
        lastForceY = fy;
        lastForceZ = fz;
        probeForceY = fy;
        probeForceX = fx;
        probeForceZ = fz;
        if (fx != 0.0D || fy != 0.0D || fz != 0.0D) {
            // 用 applyWorldForce(力, 世界坐标点)，**不要用 applyInvariantForce**。
            // 反编译 core 的 PhysShipImpl 看得很清楚：
            //   applyInvariantForce(f) → applyWorldForceToBodyPos(f, null)
            //     → invPosPositions.add(null); invPosForces.add(f)
            // 然后 applyQueuedForces() 里执行 rigidBodyReference.a(null, force)——
            // 位置是 null，力到底有没有落到刚体上完全不可控。这就是"力一直在算、
            // 速度恒为 0"最像的成因。
            // applyWorldForce 会先把世界坐标变换到模型坐标、再减质心，得到一个真实的
            // 体坐标点，走的是同一条队列但没有 null。
            org.joml.Vector3dc comModel = target.getCenterOfMass();
            Vector3d comWorld = target.getTransform().getShipToWorld()
                    .transformPosition(new Vector3d(comModel), new Vector3d());
            target.applyWorldForce(new Vector3d(fx, fy, fz), comWorld);
            DIRECT_CALLS.incrementAndGet();
        }
    }

    /**
     * 姿态稳定：把船的"上方向"锁在世界 +Y，并阻尼一切转动。
     *
     * <p>推力本身不产生力矩（都作用在质心），所以只要把转动掐死，
     * 船就永远平行于海平面，不会翻。</p>
     */
    private void stabilize(PhysShip ship, double mass) {
        try {
            if (ship.isStatic() || mass <= 0.0D) {
                return;
            }
            Quaterniondc rotation = ship.getTransform().getShipToWorldRotation();
            Vector3dc angular = ship.getAngularVelocity();
            // 船自己的上方向，转成世界方向
            Vector3d up = rotation.transform(new Vector3d(0.0D, 1.0D, 0.0D), new Vector3d());
            double cosTilt = Math.max(-1.0D, Math.min(1.0D, up.y));
            double tilt = Math.acos(cosTilt);
            probeTiltDegrees = Math.toDegrees(tilt);
            // 船头方向 → 偏航角（纯诊断用；船头是局部 +X）
            Vector3d forward = rotation.transform(new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d());
            probeYawDegrees = Math.toDegrees(Math.atan2(forward.x, forward.z));

            // 1) 俯仰/横滚角速度阻尼：只压世界 X/Z，**不压偏航 Y**。
            //    applyInvariantTorque 在 VS2 里就是 applyWorldTorque（反编译确认），
            //    所以这里给的力矩是**世界系**，不要再乘船体逆旋转。
            Vector3d torque = new Vector3d(-angular.x(), 0.0D, -angular.z())
                    .mul(ATTITUDE_DAMPING * mass);
            // 2) 回正：转轴 = 上方向 × 世界 +Y，绕它正转就能把 up 转回 +Y
            if (tilt > 1.0E-4D) {
                Vector3d axis = up.cross(new Vector3d(0.0D, 1.0D, 0.0D), new Vector3d());
                double axisLength = axis.length();
                if (axisLength > 1.0E-6D) {
                    axis.div(axisLength);
                    torque.fma(tilt * ATTITUDE_GAIN * mass, axis);
                } else if (cosTilt < 0.0D) {
                    // 正好完全翻过来：转轴退化了，随便挑一个水平轴把它推回去
                    torque.fma(tilt * ATTITUDE_GAIN * mass, new Vector3d(1.0D, 0.0D, 0.0D));
                }
            }
            // 3) 偏航：目标角速度 ±15°/s，按世界 Y 角速度误差给力矩。
            //    原作没有偏航；3D 适配要求“导航台/电网有电 + 至少一台引擎”才有舵效：
            //    断电后目标角速度归零，PD 会把残余转速刹住；引擎空转也能转舵，
            //    不需要一直按着 W/S（否则玩家实测“转向转不动”）。
            double targetYawRate = (helmPowered && engineCount > 0)
                    ? helmYaw * helmYawScale * MAX_YAW_RATE : 0.0D;
            double yawTorque = (targetYawRate - angular.y()) * YAW_GAIN * mass;
            double yawCap = YAW_TORQUE_CAP * mass;
            torque.y += Math.max(-yawCap, Math.min(yawTorque, yawCap));

            // 力矩封顶，别把船甩飞
            double cap = ATTITUDE_TORQUE_CAP * mass;
            double magnitude = torque.length();
            if (magnitude > cap) {
                torque.mul(cap / magnitude);
            }
            if (torque.lengthSquared() > 1.0E-12D) {
                ship.applyInvariantTorque(torque);
            }
        } catch (Throwable ignored) {
            // 姿态锁失败不影响推力
        }
    }

    /** 直接压在 PhysShip 上的次数（诊断用）。 */
    private static final java.util.concurrent.atomic.AtomicLong DIRECT_CALLS =
            new java.util.concurrent.atomic.AtomicLong();

    public static long directCalls() {
        return DIRECT_CALLS.get();
    }
}
