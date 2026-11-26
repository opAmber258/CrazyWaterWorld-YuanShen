package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.SupercapacitorBlock;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.PowerStorage;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 超级电容器方块实体。
 *
 * <p>数值按本机原作 {@code Content/Items/Electricity/poweritems.xml} 的
 * {@code PowerContainer capacity="20" maxoutput="20000000" maxrechargespeed="1000"
 * efficiency="0.1"}：容量很小（20 kW·min），但可以瞬间放出极大电流。本模组的
 * 船网只为电容充电。电磁枪绕过连续耗电结算，发射时从直连电容扣除一次脉冲。
 *
 * <p>充电效率 0.1：充进去 100 kW 只有 10 kW 存下来（原作 PowerContainer.efficiency）。
 * 耐久对应原作 Repairable：通电自然衰减 0.125/s（120–240 秒宽限），泡水 -0.5/s。</p>
 *
 * <p>和电池不同，超级电容器必须先在电力面板里绑定到接线盒才参与充放电；
 * 没绑定时只登记为 0 输出的储能设备，方便面板里找到它并接线。</p>
 */
public class SupercapacitorBlockEntity extends BlockEntity
        implements PowerStorage, RepairTarget, GeoBlockEntity {

    /** 原作 capacity：kW·min。 */
    public static final double CAPACITY_KWM = 20.0D;
    private static final double WEAPON_MAX_OUTPUT_KW = 20_000_000;
    /** 原作 maxoutput：kW。 */
    public static final double MAX_OUTPUT_KW = 20_000_000.0D;
    /** 原作 maxrechargespeed：kW。 */
    public static final double MAX_RECHARGE_KW = 1000.0D;
    /** 原作 efficiency：1 kW 充进去只存 0.1 kW·min。 */
    public static final double EFFICIENCY = 0.1D;

    private static final double DECAY_POWERED = 0.125D;
    private static final double DECAY_SUBMERGED = 0.5D;
    private static final int SAVE_INTERVAL = 20;

    private double chargeKwm;
    private double condition = 100.0D;
    /** 当前设置的充电速率（kW），0..MAX_RECHARGE_KW；原版滑条。 */
    private double rechargeKw = MAX_RECHARGE_KW;
    private BlockPos boundBox;
    /** 通电自然衰减的宽限 tick；-1 = 尚未初始化。 */
    private long graceTicks = -1L;
    private boolean submerged;
    private int saveTicks;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SupercapacitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPERCAPACITOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SupercapacitorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = be.resolveShip(server, pos);
        if (ship == null) {
            return;
        }
        BlockPos cell = assemblyPos(pos);
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        ShipSimulation simulation = ship.simulation();
        // 模拟是电量的权威来源：先读回上一 tick 结算后的值，再上报，避免旧值覆盖。
        Double charge = simulation.storageCharge(cell);
        if (charge != null) {
            be.chargeKwm = charge;
        }

        boolean powered = simulation.voltage() > 0.1D;
        boolean wetBody = simulation.bodyIsWet(simulation.bodyOf(cell));
        if (wetBody) {
            java.util.List<BlockPos> breachSources = new java.util.ArrayList<>();
            for (ShipState.Breach breach : ship.breaches()) {
                breachSources.add(breach.pos());
            }
            be.submerged = cell.getY() < simulation.waterLevelAt(cell, breachSources);
        } else {
            be.submerged = false;
        }
        if (be.graceTicks < 0L) {
            be.graceTicks = 2400L + level.random.nextInt(2400);
        }
        if (be.submerged) {
            be.condition -= DECAY_SUBMERGED / 20.0D;
        } else if (powered) {
            if (be.graceTicks > 0L) {
                be.graceTicks--;
            } else {
                be.condition -= DECAY_POWERED / 20.0D;
            }
        }
        be.condition = Mth.clamp(be.condition, 0.0D, 100.0D);
        be.chargeKwm = Mth.clamp(be.chargeKwm, 0.0D, CAPACITY_KWM);

        // Only the input goes through the junction box. Turret pulses use the dedicated output.
        boolean connected = be.boundBox != null
                && ship.tracksHullBlock(be.boundBox)
                && simulation.boxCondition(be.boundBox) > 0.0D;
        simulation.reportStorage(cell, be.chargeKwm, CAPACITY_KWM,
                0.0D,
                connected ? be.rechargeKw : 0.0D,
                be.boundBox, be.condition, EFFICIENCY);
        be.syncChargeLevel(server, pos, state);

        if (++be.saveTicks >= SAVE_INTERVAL) {
            be.saveTicks = 0;
            be.setChanged();
        }
    }

    /** Read the live grid value so two turrets sharing this capacitor cannot spend the same shot. */
    @Override
    public double weaponCharge(ServerLevel level) {
        ShipState ship = resolveShip(level, worldPosition);
        if (ship != null && ship.tracksHullBlock(assemblyPos(worldPosition))) {
            Double reported = ship.simulation().storageCharge(assemblyPos(worldPosition));
            if (reported != null) chargeKwm = reported;
        }
        return condition > 0 ? chargeKwm : 0;
    }

    @Override
    public boolean weaponOutputEnabled() {
        return condition > 0;
    }

    @Override
    public double weaponMaxOutputKw() {
        return WEAPON_MAX_OUTPUT_KW;
    }

    @Override
    public void consumeWeaponCharge(ServerLevel level, double amount) {
        chargeKwm = Math.max(0, chargeKwm - amount);
        ShipState ship = resolveShip(level, worldPosition);
        BlockPos cell = assemblyPos(worldPosition);
        if (ship != null && ship.tracksHullBlock(cell)) {
            ShipSimulation simulation = ship.simulation();
            boolean connected = boundBox != null && ship.tracksHullBlock(boundBox)
                    && simulation.boxCondition(boundBox) > 0;
            simulation.reportStorage(cell, chargeKwm, CAPACITY_KWM, 0,
                    connected ? rechargeKw : 0, boundBox, condition, EFFICIENCY);
        }
        setChanged();
        syncChargeLevel(level, worldPosition, getBlockState());
    }

    /** 5 段电量屏：0 空、1..5 格；损坏时全暗。 */
    private void syncChargeLevel(ServerLevel level, BlockPos pos, BlockState state) {
        int target = chargeLevel();
        if (state.getValue(SupercapacitorBlock.CHARGE) != target) {
            level.setBlock(pos, state.setValue(SupercapacitorBlock.CHARGE, target),
                    Block.UPDATE_ALL);
        }
    }

    public int chargeLevel() {
        if (condition <= 0.0D) {
            return 0;
        }
        double fraction = chargeFraction();
        if (fraction <= 1.0E-6D) {
            return 0;
        }
        return Mth.clamp((int) Math.ceil(fraction * 5.0D), 1, 5);
    }

    @Override
    public double condition() {
        return condition;
    }

    @Override
    public double maxCondition() {
        return 100.0D;
    }

    @Override
    public void applyRepair(double points) {
        if (points <= 0.0D) {
            return;
        }
        condition = Mth.clamp(condition + points, 0.0D, 100.0D);
        if (condition >= 100.0D) {
            graceTicks = 2400L + (level == null ? 0 : level.random.nextInt(2400));
        }
        setChanged();
    }

    @Override
    public void damage(double points) {
        if (points > 0.0D) {
            condition = Mth.clamp(condition - points, 0.0D, 100.0D);
            setChanged();
        }
    }

    /** 当前充电速率（kW）。 */
    public double rechargeKw() {
        return rechargeKw;
    }

    /** 设置充电速率，0..1000 kW；0 = 不充电。 */
    public void setRechargeKw(double value) {
        rechargeKw = Mth.clamp(value, 0.0D, MAX_RECHARGE_KW);
        setChanged();
    }

    public boolean submerged() {
        return submerged;
    }

    /** 这台电容当前是否已经在船体几何里（决定要不要先重扫）。 */
    public boolean inHull() {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        ShipState ship = resolveShip(server, worldPosition);
        return ship != null && ship.tracksHullBlock(assemblyPos(worldPosition));
    }

    public Component displayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public double storedKwm() {
        return chargeKwm;
    }

    @Override
    public double capacityKwm() {
        return CAPACITY_KWM;
    }

    @Override
    public BlockPos storageBox() {
        return boundBox;
    }

    @Override
    public void setStorageBox(BlockPos box) {
        boundBox = box == null ? null : box.immutable();
        setChanged();
    }

    private static BlockPos assemblyPos(BlockPos pos) {
        return VsState.active() && VsPilot.isYardBlock(pos) ? VsPilot.toAssembly(pos) : pos;
    }

    private BlockPos cachedCorePos;
    private int coreRetryTicks;

    private ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        if (cachedCorePos != null && level.hasChunkAt(cachedCorePos)
                && level.getBlockEntity(cachedCorePos) instanceof ShipCoreBlockEntity core) {
            ShipState state = core.state() != null ? core.state() : core.rescan(level);
            if (state != null && state.tracksHullBlock(pos)) {
                return state;
            }
        }
        if (coreRetryTicks > 0) {
            coreRetryTicks--;
            return null;
        }
        coreRetryTicks = 20;
        ShipCoreBlockEntity core = ShipCores.coreOwning(level, pos, 48.0D);
        cachedCorePos = core == null ? null : core.getBlockPos().immutable();
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
        // 和电池一样：存档落在“模拟结算”和“实体读回”之间时，以模拟的权威值为准。
        double charge = chargeKwm;
        if (level instanceof ServerLevel server) {
            ShipState ship = resolveShip(server, getBlockPos());
            if (ship != null) {
                Double reported = ship.simulation().storageCharge(assemblyPos(getBlockPos()));
                if (reported != null) {
                    charge = reported;
                }
            }
        }
        tag.putDouble("Charge", charge);
        tag.putDouble("Condition", condition);
        tag.putDouble("Recharge", rechargeKw);
        tag.putLong("Grace", graceTicks);
        if (boundBox != null) {
            tag.putLong("PowerBox", boundBox.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        chargeKwm = tag.contains("Charge") ? tag.getDouble("Charge") : 0.0D;
        condition = tag.contains("Condition") ? tag.getDouble("Condition") : 100.0D;
        rechargeKw = tag.contains("Recharge") ? tag.getDouble("Recharge") : MAX_RECHARGE_KW;
        graceTicks = tag.contains("Grace") ? tag.getLong("Grace") : -1L;
        boundBox = tag.contains("PowerBox") ? BlockPos.of(tag.getLong("PowerBox")) : null;
    }
}
