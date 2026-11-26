package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.PowerGrid;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.world.StarterSub;
import com.opamb.barotrauma.menu.SonarMenu;
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
 * 引擎方块实体：跟踪带方向的目标和实际出力，向船模拟上报推力与负载。
 *
 * <p>声纳油门决定目标力（−100…100%）。耗电为目标幅度的 1.5 次方乘额定功率，
 * 实际力随目标平滑变化。船模拟按每台引擎的电压与耐久汇总可用推力。</p>
 */
public class EngineBlockEntity extends BlockEntity
        implements GeoBlockEntity, PowerConsumer, RepairTarget {

    /** 单台标准引擎的额定耗电（原作 engine powerconsumption=2000）。 */
    public static final double MAX_POWER_KW = 2000.0D;
    /** 单台标准引擎的最大推力（原作 maxforce=300；用于面板显示）。 */
    public static final double MAX_FORCE = 300.0D;

    public static final double DUGONG_POWER_KW = 600.0D;
    public static final double DUGONG_MAX_FORCE = 270.0D;
    private boolean dugongStock;

    private double lastTargetForce;
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

    private ShipState ship(ServerLevel level) {
        return VsState.isPhysical(level, worldPosition) ? VsState.activeState()
                : com.opamb.barotrauma.ship.ShipCores.stateOwning(level, worldPosition, 64.0D);
    }

    private static boolean helmHasControl(ServerLevel level, ShipState ship) {
        if (ship.helmHasControl(level.getGameTime())) return true;
        for (var player : level.players()) {
            if (player.containerMenu instanceof SonarMenu menu && menu.controls(ship, player)) return true;
        }
        return false;
    }

    public enum Control { UNAVAILABLE, LOCAL, HELM }

    public Control controlMode() {
        if (!(level instanceof ServerLevel server) || isRemoved() || condition.broken()
                || !VsState.isPhysical(server, worldPosition)) return Control.UNAVAILABLE;
        ShipState ship = VsState.activeState();
        if (ship == null || !ship.tracksHullBlock(VsPilot.toAssembly(worldPosition))) return Control.UNAVAILABLE;
        return helmHasControl(server, ship) ? Control.HELM : Control.LOCAL;
    }

    public boolean setLocalForce(int force) {
        if (controlMode() != Control.LOCAL) return false;
        VsState.activeState().setLocalEngineForce(VsPilot.toAssembly(worldPosition), force);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  EngineBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        boolean physical = VsState.isPhysical(server, pos);
        ShipState ship = be.ship(server);
        if (ship == null) {
            be.currentForce = 0.0D;
            be.lastTargetForce = 0.0D;
            be.lastSupply = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastEngineCount = 0;
            return;
        }
        BlockPos cell = physical ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            be.currentForce = 0.0D;
            be.lastTargetForce = 0.0D;
            be.lastSupply = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastEngineCount = 0;
            return;
        }

        // 原作 Engine.Update：目标力来自舵台（物理船 = 前进舵令；方块船 = 八向油门）；
        // 电压低于 MinVoltage 时实际 Force 以 10/s 向 0 插值，但**耗电请求仍按目标力算**
        // （原作 GetCurrentPowerConsumption 用 targetForce）。不能把目标力也归零：
        // 那样负载消失→电压回到 1→目标力恢复，会每 tick 抖动。
        double targetForce = ship.engineThrottle(physical) * ship.power();
        if (physical) {
            targetForce *= ship.helmForward();
        }
        if (physical && ship.hasLocalEngineControl()) {
            if (helmHasControl(server, ship)) {
                ship.clearLocalEngineControl();
            } else {
                targetForce = ship.localEngineForce(cell);
            }
        }
        double driveTarget = PowerGrid.canRun(be.lastSupply, be.minVoltage()) ? targetForce : 0.0D;
        double dt = 1.0D / 20.0D;
        be.currentForce += (driveTarget - be.currentForce) * (1.0D - Math.exp(-10.0D * dt));
        if (Math.abs(be.currentForce) < 0.01D) {
            be.currentForce = 0.0D;
        }
        // 耗电按目标力算（原作 GetCurrentPowerConsumption 用 targetForce）。
        if (!be.dugongStock && StarterSub.stockEquipment(server, cell, physical, -13, -4, 4)) {
            be.dugongStock = true;
            be.setChanged();
        }
        double ratedDemand = (be.dugongStock ? DUGONG_POWER_KW : MAX_POWER_KW) * be.condition.powerPenalty();
        double maxForce = be.lastMaxForce();
        double demand = ratedDemand * Math.pow(Math.abs(targetForce) / 100.0D,
                ShipSimulation.ENGINE_POWER_EXPONENT);
        // 耐久：只有在“有油门且上一 tick 确实拿到电”时才劣化；
        // 光看目标推力的话，全船停电时没转的引擎也会掉耐久。
        boolean running = Math.abs(be.currentForce) > 1.0D
                && !StarterSub.atFriendlyOutpost(server);
        if (be.condition.tick(server, running, dt, Math.abs(be.currentForce) / 100.0D)) {
            be.setChanged();
        }
        if (be.condition.broken()) {
            ship.setLocalEngineForce(cell, 0);
            be.currentForce = 0.0D;
            be.lastTargetForce = targetForce;
            be.lastDemandKw = 0.0D;
            be.lastSupply = 0.0D;
            ship.simulation().reportEngine(cell, 0.0D, 0.0D, maxForce, 0.0D);
            ship.simulation().reportConsumer(cell, 0.0D, be.minVoltage(), be.boundBox);
            return;
        }
        be.lastTargetForce = targetForce;
        be.lastDemandKw = demand;
        ship.simulation().reportEngine(cell, be.currentForce, demand, maxForce,
                be.condition.fraction(), ratedDemand);
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
        return currentForce;
    }

    public double lastTargetForce() {
        return lastTargetForce;
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
        return dugongStock ? DUGONG_MAX_FORCE : MAX_FORCE;
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
        if (dugongStock) tag.putBoolean("DugongStock", true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundBox = tag.contains("PowerBox") ? BlockPos.of(tag.getLong("PowerBox")) : null;
        condition.load(tag, "Condition");
        dugongStock = tag.getBoolean("DugongStock");
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
