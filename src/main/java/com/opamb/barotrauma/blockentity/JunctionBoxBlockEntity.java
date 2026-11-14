package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 接线盒方块实体：耐久、过压/泡水损坏、向船电网登记。
 *
 * <p>对应原版 {@code PowerTransfer}：电网耗电 0，只作为分线节点和故障点。
 * 通电自然衰减 0.1/s（120–720 秒宽限后），泡水通电 0.25/s，电压超过 1.7
 * 时 10/s。耐久归零后盒子失效，绑在它上面的设备拿不到电。</p>
 */
public class JunctionBoxBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** 通电自然衰减速度（/秒）。 */
    private static final double DECAY_POWERED = 0.1D;
    /** 泡水通电衰减速度（/秒）。 */
    private static final double DECAY_SUBMERGED = 0.25D;
    /** 过压衰减速度（/秒）。 */
    private static final double DECAY_OVERVOLTAGE = 10.0D;
    /** 过压阈值（原版 vanilla 潜艇 1.7）。 */
    public static final double OVERVOLTAGE = 1.7D;

    private double condition = 100.0D;
    /** 通电自然衰减的宽限 tick 数；-1 = 还没初始化。 */
    private long graceTicks = -1L;
    private boolean submerged;
    private double lastVoltage;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public JunctionBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JUNCTION_BOX.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  JunctionBoxBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = resolveShip(server, pos);
        if (ship == null) {
            return;
        }
        BlockPos cell = assemblyPos(pos);
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        double voltage = ship.simulation().voltage();
        be.lastVoltage = voltage;
        boolean powered = voltage > 0.1D;
        be.submerged = ship.simulation().bodyIsWet(ship.simulation().bodyOf(cell));

        if (be.graceTicks < 0L) {
            be.graceTicks = 2400L + level.random.nextInt(12000);
        }

        double before = be.condition;
        if (powered) {
            if (be.graceTicks > 0L) {
                be.graceTicks--;
            } else {
                be.condition -= DECAY_POWERED / 20.0D;
            }
            if (be.submerged) {
                be.condition -= DECAY_SUBMERGED / 20.0D;
            }
            if (voltage > OVERVOLTAGE) {
                be.condition -= DECAY_OVERVOLTAGE / 20.0D;
            }
        }
        be.condition = Mth.clamp(be.condition, 0.0D, 100.0D);
        if (Math.abs(before - be.condition) > 0.001D) {
            be.setChanged();
        }
        ship.simulation().reportJunctionBox(cell, be.condition);
    }

    /** 螺丝刀维修：恢复耐久并重新随机宽限期。 */
    public void repair(double amount) {
        condition = Mth.clamp(condition + Math.max(0.0D, amount), 0.0D, 100.0D);
        graceTicks = 2400L + (level == null ? 0 : level.random.nextInt(12000));
        setChanged();
    }

    public double condition() {
        return condition;
    }

    public boolean submerged() {
        return submerged;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    public boolean broken() {
        return condition <= 0.0D;
    }

    public Component displayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    private static BlockPos assemblyPos(BlockPos pos) {
        return VsState.active() ? VsPilot.toAssembly(pos) : pos;
    }

    private static ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active()) {
            return VsState.activeState();
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(level, pos, 32.0D);
        return core == null ? null : core.state();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：无动画控制器。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Condition", condition);
        tag.putLong("Grace", graceTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        condition = tag.contains("Condition") ? tag.getDouble("Condition") : 100.0D;
        graceTicks = tag.contains("Grace") ? tag.getLong("Grace") : -1L;
    }
}
