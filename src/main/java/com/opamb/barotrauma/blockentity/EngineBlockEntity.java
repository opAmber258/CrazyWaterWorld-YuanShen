package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
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
public class EngineBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** 单台标准引擎的额定耗电（原作 engine powerconsumption=2000）。 */
    public static final double MAX_POWER_KW = 2000.0D;
    /** 单台标准引擎的最大推力（原作 maxforce=300；用于面板显示）。 */
    public static final double MAX_FORCE = 300.0D;

    private double lastForce;
    private double lastSupply;
    private double lastDemandKw;
    private int lastEngineCount;
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
            ShipCoreBlockEntity core = com.opamb.barotrauma.ship.ShipCores.nearestCore(server, pos, 64.0D);
            ship = core == null ? null : core.state();
        }
        if (ship == null) {
            be.lastForce = 0.0D;
            be.lastSupply = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastEngineCount = 0;
            return;
        }
        BlockPos cell = VsState.active() ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            be.lastForce = 0.0D;
            be.lastSupply = 0.0D;
            be.lastDemandKw = 0.0D;
            be.lastEngineCount = 0;
            return;
        }

        // 油门停车时目标力 0；有油门时用声纳面板的推力百分比（5..100）。
        double force = ship.throttled() ? ship.power() : 0.0D;
        double demand = MAX_POWER_KW * Math.pow(Math.max(0.0D, force) / 100.0D, 1.5D);
        ship.simulation().reportEngine(cell, force, demand, MAX_FORCE);
        be.lastForce = force;
        be.lastDemandKw = demand;
        be.lastSupply = ship.simulation().powerRatio();
        be.lastEngineCount = ship.simulation().engineCount();
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
