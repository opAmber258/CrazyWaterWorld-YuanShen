package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.PowerGrid;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 引擎方块实体：只做"船上有一台引擎"的上报和面板数据，不直接给刚体施加力。
 *
 * <p>声纳油门决定目标力（0–100%）：油门停车时引擎空转不耗电；有油门时按原作公式
 * {@code 目标力/100 的 1.5 次方 × 2000 kW} 上报耗电。多台引擎相加，
 * {@code VsThruster} 按引擎数量放大推力，并受全船供电比例限制。</p>
 */
public class EngineBlockEntity extends BlockEntity
        implements GeoBlockEntity, PowerConsumer, RepairTarget {

    /** 单台标准引擎的额定耗电（原作 engine powerconsumption=2000）。 */
    public static final double MAX_POWER_KW = 2000.0D;
    /** 单台标准引擎的最大推力（原作 maxforce=300；用于面板显示）。 */
    public static final double MAX_FORCE = 300.0D;

    private double lastForce;
    /** 原作 {@code Engine.Force}：实际推力向目标插值，速度 10/s。 */
    private double currentForce;
    private double lastSupply;
    private double lastDemandKw;
    private int lastEngineCount;
    /** 绑定的接线盒；null = 默认总线。 */
    private BlockPos boundBox;
    /** 耐久：原作 engine 0.125/s，延迟 120–300s，阈值 80%。 */
    private final ConditionComponent condition =
            new ConditionComponent(100.0D, 0.125D, 120, 300, 0.0D, 0.8D);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public EngineBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ENGINE.get(), pos, state);
    }

    protected EngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  EngineBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship;
        if (VsState.active()) {
            ship = VsState.activeState();
        } else {
            // 引擎常在船尾，离船核心可能超过水泵用的 32 格；引擎放宽到 64。
            // 船状态不存档：世界重开后第一次 tick 靠自己补扫，不能等玩家去开电力面板。
            ship = com.opamb.barotrauma.ship.ShipCores.stateNear(server, pos, 64.0D);
        }
        if (ship == null) {
            be.currentForce = 0.0D;
            be.lastForce = 0.0D;
            be.lastSupply = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastEngineCount = 0;
            return;
        }
        BlockPos cell = VsState.active() ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            be.currentForce = 0.0D;
            be.lastForce = 0.0D;
            be.lastSupply = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastEngineCount = 0;
            return;
        }

        // 原作 Engine.Update：目标力来自舵台（物理船 = 前进舵令；方块船 = 八向油门）；
        // 电压低于 MinVoltage 时实际 Force 以 10/s 向 0 插值，但**耗电请求仍按目标力算**
        // （原作 GetCurrentPowerConsumption 用 targetForce）。不能把目标力也归零：
        // 那样负载消失→电压回到 1→目标力恢复，会每 tick 抖动。
        boolean physical = VsState.active() && VsState.activeState() == ship;
        double targetForce = ship.engineThrustWanted(physical) ? ship.power() : 0.0D;
        double driveTarget = PowerGrid.canRun(be.lastSupply, be.minVoltage()) ? targetForce : 0.0D;
        double dt = 1.0D / 20.0D;
        be.currentForce += (driveTarget - be.currentForce) * (1.0D - Math.exp(-10.0D * dt));
        if (be.currentForce < 0.01D) {
            be.currentForce = 0.0D;
        }
        // 耗电按目标力算（原作 GetCurrentPowerConsumption 用 targetForce）。
        double demand = MAX_POWER_KW * Math.pow(Math.max(0.0D, targetForce) / 100.0D, 1.5D)
                * be.condition.powerPenalty();
        // 耐久：只有在“有油门且上一 tick 确实拿到电”时才劣化；
        // 光看目标推力的话，全船停电时没转的引擎也会掉耐久。
        boolean running = targetForce > 0.1D
                && PowerGrid.canRun(be.lastSupply, be.minVoltage());
        if (be.condition.tick(server, running)) {
            be.setChanged();
        }
        if (be.condition.broken()) {
            be.currentForce = 0.0D;
            be.lastForce = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastSupply = 0.0D;
            ship.simulation().reportEngine(cell, 0.0D, 0.0D, MAX_FORCE, 0.0D);
            ship.simulation().reportConsumer(cell, 0.0D, be.minVoltage(), be.boundBox);
            return;
        }
        be.lastForce = be.currentForce;
        be.lastDemandKw = demand;
        ship.simulation().reportEngine(cell, be.currentForce, demand, MAX_FORCE,
                be.condition.condition() / be.condition.maxCondition());
        ship.simulation().reportConsumer(cell, demand, be.minVoltage(), be.boundBox);
        be.lastSupply = ship.simulation().consumerVoltage(cell);
        be.lastEngineCount = ship.simulation().engineCount();
    }

    @Override
    public double powerDemandKw() {
        return lastDemandKw;
    }

    @Override
    public double minVoltage() {
        return 0.5D;
    }

    @Override
    public BlockPos powerBox() {
        return boundBox;
    }

    @Override
    public void setPowerBox(BlockPos box) {
        this.boundBox = box == null ? null : box.immutable();
        setChanged();
    }

    @Override
    public double condition() {
        return condition.condition();
    }

    @Override
    public double maxCondition() {
        return condition.maxCondition();
    }

    @Override
    public void applyRepair(double points) {
        condition.repair(points);
        setChanged();
    }

    @Override
    public void damage(double points) {
        condition.damage(points);
        setChanged();
    }

    @Override
    public RepairTarget.Tool requiredTool() {
        return RepairTarget.Tool.WRENCH;
    }

    public double lastForce() {
        return lastForce;
    }

    public double lastSupply() {
        return lastSupply;
    }

    public double lastDemandKw() {
        return lastDemandKw;
    }

    public int lastEngineCount() {
        return lastEngineCount;
    }

    public double lastMaxForce() {
        return MAX_FORCE;
    }

    public Component displayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (boundBox != null) {
            tag.putLong("PowerBox", boundBox.asLong());
        }
        condition.save(tag, "Condition");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundBox = tag.contains("PowerBox") ? BlockPos.of(tag.getLong("PowerBox")) : null;
        condition.load(tag, "Condition");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型；螺旋桨目前只是占位，不做动画。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 2.0D, p.getY() - 1.0D, p.getZ() - 3.0D,
                p.getX() + 3.0D, p.getY() + 3.0D, p.getZ() + 3.0D);
    }
}
