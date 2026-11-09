package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 排水泵方块实体：保存流量设置，并每 tick 把设置推给所在船的 {@link ShipState}。
 *
 * <p>状态模型对照原版 {@code pump + water detector} 的组合：原版水泵读水传感器的
 * {@code FlowPercentage}（-100% 全速泵出、0% 停机、100% 全速泵入）。这里把水传感器
 * 直接并进泵里，所以只有两个状态：</p>
 * <ul>
 *   <li>{@link #auto()}：自动控制。本舱有水就 -100% 泵出，没水就停机。</li>
 *   <li>手动：{@link #flow()} 是 -100..100 的百分比，负值泵出、正值泵入。</li>
 * </ul>
 *
 * <p>船状态解析：物理船激活时用 {@link VsState#activeState()}（方块在船坞里，
 * 找不到正常的核心）；方块船只找 32 格内的船核心。找不到船就空转。</p>
 */
public class DrainPumpBlockEntity extends BlockEntity {

    /**
     * 普通泵满流量（单位/秒）：1 格 = 100 单位。
     *
     * <p>对齐原版本机数据 {@code Content/Items/Pump/pump.xml}：MaxFlow=600、功率 300；
     * 小型泵 {@code smallpump}：MaxFlow=100、功率 60。</p>
     *
     * <p><b>为什么不是 600：</b>反编译 {@code Pump.Update} 里是
     * {@code WaterVolume += flowPercent/100 * MaxFlow * voltage * deltaTime * 60}，
     * 游戏固定 1/60 秒一帧，等于每帧 600、每秒 600×60 = 36000 水体单位。
     * 原版一格是 16×16 = 256 水体单位，我们一格 = 100 单位，
     * 换算后：600 × 60 × (100/256) ≈ 14062（每秒约 140 格³）。
     * 之前直接按 600 用（每秒 6 格³）比原版慢约 23 倍，实测就是
     * "泵入泵出太慢、沉浮延迟高"。</p>
     */
    public static final int MAX_FLOW = 14_062;

    /** 小型泵满流量（原版 MaxFlow=100 → 100×60×100/256 ≈ 2344，每秒约 23 格³）。 */
    public static final int SMALL_MAX_FLOW = 2_344;

    /** 排水口固定流量（模组简化件，原版没有独立方块；按小泵的 1/4 取整）。 */
    public static final int OUTLET_RATE = 586;

    private boolean auto = true;
    /** 手动流量百分比：-100..100。 */
    private int flow = -100;
    /** 自动控制的当前判定结果（服务端每 tick 更新，供菜单显示）。 */
    private boolean autoWet;

    public DrainPumpBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.DRAIN_PUMP.get(), pos, state);
    }

    protected DrainPumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 不同泵的满流量。 */
    public static int maxFlow(BlockState state) {
        if (state.is(com.opamb.barotrauma.registry.ModBlocks.SMALL_PUMP.get())) {
            return SMALL_MAX_FLOW;
        }
        return MAX_FLOW;
    }

    public boolean auto() {
        return auto;
    }

    /** 手动设置的百分比（自动控制关闭时生效）。 */
    public int flow() {
        return flow;
    }

    public boolean autoWet() {
        return autoWet;
    }

    /** 当前实际生效的百分比：自动控制时由水传感器逻辑给出。 */
    public int effectiveFlow() {
        if (auto) {
            return autoWet ? -100 : 0;
        }
        return flow;
    }

    /**
     * 当前流量（单位/秒），**正值泵出、负值泵入**（{@link ShipSimulation} 的约定）。
     *
     * <p>注意 flow 的约定相反（负值泵出、正值泵入），这里必须取负。之前漏了这个
     * 负号，自动/手动泵出实际会往舱里注水。</p>
     */
    public double signedRate(BlockState state) {
        return -maxFlow(state) * effectiveFlow() / 100.0D;
    }

    /** 是否是小型泵（小型泵用 GeckoLib 模型，其它泵仍是普通方块模型）。 */
    public boolean small() {
        return getBlockState().is(com.opamb.barotrauma.registry.ModBlocks.SMALL_PUMP.get());
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
        setChanged();
    }

    /** 手动流量百分比，夹到 -100..100。 */
    public void setFlow(int flow) {
        this.flow = Mth.clamp(flow, -100, 100);
        setChanged();
    }

    /** 服务端每 tick：把泵设置上报给所在船的模拟器（含归属校验）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  DrainPumpBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ShipState ship = resolveShip(server, pos);
        if (ship == null) {
            return;
        }
        // 每台泵每 tick 上报一次；同舱多台泵由 ShipSimulation 汇总。
        // 物理船的方块实体活在船坞里，要换算成装配坐标。
        BlockPos cell = assemblyPos(server, pos);
        // 归属校验：哨站对接区的小型泵不属于任何船，不能上报流量，
        // 否则会被 toAssembly 转换坐标后登记进活动船（issue #22）。
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        // 子类钩子：压载泵在这里读舵台垂直指令（下潜泵入 / 上浮泵出）。
        be.beforeReport(ship);
        if (be.auto) {
            // 泵格子本身是实心方块，要用邻格解析出它服务的舱室（水传感器语义）。
            int body = ship.simulation().pumpBody(cell);
            // 注意：bodyLevel 是绝对 Y（深海是负数），不能拿来和 0.05 比；
            // 用 bodyIsWet（水量 > 0.05 格）判断，自动泵才会真的在水进舱时启动。
            be.autoWet = ship.simulation().bodyIsWet(body);
        }
        // 硬核供电：实际流量按全船供电比例缩放；请求值单独上报，负载按请求值算。
        double requested = be.signedRate(state);
        double ratio = ship.simulation().powerRatio();
        ship.simulation().reportPump(cell, requested * ratio, be.ballast(), requested,
                be.keepBelowDoor(), be.ballastTarget());
    }

    /** 上报前钩子：默认什么也不做；压载泵用它读取舵台垂直指令。 */
    protected void beforeReport(ShipState ship) {
    }

    /** 自动压载泵的目标水位（0..1）；普通泵/手动模式返回 -1（不做单步目标钳制）。 */
    protected double ballastTarget() {
        return -1.0D;
    }

    /**
     * 注水时是否只注到"最近的舱门/舱口下方那一格"。
     *
     * <p>压载泵打开：压载舱注水不能漫过通往上层的舱门，否则一开门水就灌进
     * 驾驶室。普通排水泵没有这个限制。</p>
     */
    protected boolean keepBelowDoor() {
        return false;
    }

    /** 这台泵是否属于压载系统（true 时船体模拟会把它所在舱算作压载舱）。 */
    public boolean ballast() {
        return false;
    }

    /** 物理船的方块实体在船坞坐标里；转成船状态用的装配坐标。 */
    private static BlockPos assemblyPos(ServerLevel level, BlockPos pos) {
        if (!VsState.active()) {
            return pos;
        }
        return com.opamb.barotrauma.ship.vs.VsPilot.toAssembly(pos);
    }

    @Nullable
    public static ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active()) {
            return VsState.activeState();
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(level, pos, 32.0D);
        return core == null ? null : core.state();
    }

    @Override
    /** 保存自动/手动流量设置。 */
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("PumpAuto", auto);
        tag.putInt("PumpFlow", flow);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] migrated = migrateLegacy(
                tag.contains("PumpEnabled"), tag.getBoolean("PumpEnabled"),
                tag.contains("PumpAuto"), tag.getBoolean("PumpAuto"),
                tag.contains("PumpFlow"), tag.getInt("PumpFlow"),
                tag.contains("PumpRate"), tag.getInt("PumpRate"),
                maxFlow(getBlockState()));
        auto = migrated[0] != 0;
        flow = migrated[1];
    }

    /**
     * 旧档/新档统一迁移策略（纯函数，可单测）：返回 {@code [auto, flow]}。
     *
     * <p>旧档明确关掉的泵保持停机——{@code PumpEnabled=false} + 正档位不能
     * 迁移成继续抽水（issue #22）；有 {@code PumpFlow} 走新格式；
     * 只有 {@code PumpRate} 的旧档位换算成负百分比并切手动。</p>
     */
    static int[] migrateLegacy(boolean hasEnabled, boolean enabled,
                               boolean hasAuto, boolean auto,
                               boolean hasFlow, int flow,
                               boolean hasRate, int rate, int maxFlow) {
        if (hasEnabled && !enabled) {
            return new int[] {0, 0};
        }
        if (hasFlow) {
            return new int[] {hasAuto && auto ? 1 : 0, Mth.clamp(flow, -100, 100)};
        }
        if (hasRate) {
            return new int[] {0,
                    -Mth.clamp(Math.round(rate * 100.0F / Math.max(1, maxFlow)), 0, 100)};
        }
        return new int[] {1, -100};
    }
}
