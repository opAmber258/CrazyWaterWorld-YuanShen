package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.BatteryBlock;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 电池（设备）方块实体。
 *
 * <p>数值按本机原作 {@code Content/Items/Electricity/poweritems.xml} 的
 * {@code PowerContainer capacity="2000" maxoutput="1000" maxrechargespeed="500"}：
 * 容量单位是 kW·min，所以满电 2000 kW·min 可以按 1000 kW 输出两分钟。
 * 充电效率 0.95，低于 10% 时最大输出线性降额，都由
 * {@link ShipSimulation} 统一结算。</p>
 *
 * <p>耐久对应原作 {@code Repairable}：通电自然衰减 0.125/s（120–240 秒宽限），
 * 泡水额外 -0.5/s；耐久归零后不充不放。</p>
 */
public class BatteryBlockEntity extends BlockEntity
        implements PowerStorage, RepairTarget, GeoBlockEntity, net.minecraft.world.Container {

    /** 电池顶上的三个充电槽（原作 ItemContainer capacity=3）。 */
    public static final int CELL_SLOTS = 3;

    /** 原作 capacity：kW·min。 */
    public static final double CAPACITY_KWM = 2000.0D;
    /** 原作 maxoutput：kW。 */
    public static final double MAX_OUTPUT_KW = 1000.0D;
    /** 原作 maxrechargespeed：kW。 */
    public static final double MAX_RECHARGE_KW = 500.0D;

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
    /** 三个充电槽里的电池 cell。 */
    private final net.minecraft.core.NonNullList<ItemStack> cells =
            net.minecraft.core.NonNullList.withSize(CELL_SLOTS, ItemStack.EMPTY);
    /** 充电余量（每 tick +0.1，攒够 1 点耐久再写回 cell）。 */
    private double cellChargeCarry;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  BatteryBlockEntity be) {
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
        // 按电池位置的实际水位判泡水：舱底有点积水不代表高处的电池被淹。
        // 连海空腔用所在列的水位（和渲染/接线盒同一套）。
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

        simulation.reportStorage(cell, be.chargeKwm, CAPACITY_KWM,
                MAX_OUTPUT_KW, be.rechargeKw, be.boundBox, be.condition);
        be.syncChargeLevel(server, pos, state);
        // 原作 PowerContainer 的 OnActive：电池自身电量 >1% 时给槽里的电池 cell
        // 每秒 +2 耐久（Condition="2.0"）。
        be.chargeCells();

        if (++be.saveTicks >= SAVE_INTERVAL) {
            be.saveTicks = 0;
            be.setChanged();
        }
    }

    /** 电量跨过 1/3、2/3 时换方块状态，客户端据此换屏幕贴图。 */
    private void syncChargeLevel(ServerLevel level, BlockPos pos, BlockState state) {
        int target = chargeLevel();
        if (state.getValue(BatteryBlock.CHARGE) != target) {
            level.setBlock(pos, state.setValue(BatteryBlock.CHARGE, target), Block.UPDATE_ALL);
        }
    }

    /** 0=空、1=低（1 段）、2=半（2 段）、3=满（3 段）；损坏时屏幕全暗。 */
    public int chargeLevel() {
        if (condition <= 0.0D) {
            return 0;
        }
        double fraction = chargeFraction();
        if (fraction >= 2.0D / 3.0D) {
            return 3;
        }
        if (fraction >= 1.0D / 3.0D) {
            return 2;
        }
        return fraction > 1.0E-6D ? 1 : 0;
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
            // 原作修满后重置劣化延迟。
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

    /** 设置充电速率，0..500 kW；0 = 不充电。 */
    public void setRechargeKw(double value) {
        rechargeKw = Mth.clamp(value, 0.0D, MAX_RECHARGE_KW);
        setChanged();
    }

    public boolean submerged() {
        return submerged;
    }

    /** 这块电池当前是否已经在船体几何里（决定要不要先重扫）。 */
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

    /** 已确认的核心位置；每 tick 扫 49 个区块太贵，和接线盒一样缓存。 */
    private BlockPos cachedCorePos;
    /** 找不到船时的重试冷却（tick）。 */
    private int coreRetryTicks;

    private ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        // 先读缓存核心的当前状态（不缓存 ShipState，避免重扫前的旧对象）。
        if (cachedCorePos != null && level.hasChunkAt(cachedCorePos)
                && level.getBlockEntity(cachedCorePos) instanceof ShipCoreBlockEntity core) {
            ShipState state = core.state() != null ? core.state() : core.rescan(level);
            if (state != null && state.tracksHullBlock(pos)) {
                return state;
            }
        }
        // 找不到船也要冷却，否则没接入船体的电池每 tick 重扫附近区块。
        if (coreRetryTicks > 0) {
            coreRetryTicks--;
            return null;
        }
        coreRetryTicks = 20;
        // 两艘船靠近时最近核心可能属于另一艘船，按船体几何归属选；
        // 核心扫描是各轴 ±24 格，对角设备离核心约 41.6 格，所以用 48。
        ShipCoreBlockEntity core = ShipCores.coreOwning(level, pos, 48.0D);
        cachedCorePos = core == null ? null : core.getBlockPos().immutable();
        return core == null ? null : core.state();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：无动画控制器。
    }

    /* ------------------------------------------------------------ 电池 cell 充电槽 */

    private void chargeCells() {
        if (condition <= 0.0D || chargeFraction() <= 0.01D) {
            return;
        }
        cellChargeCarry += 0.1D;
        if (cellChargeCarry < 1.0D) {
            return;
        }
        int points = (int) cellChargeCarry;
        cellChargeCarry -= points;
        boolean changed = false;
        for (int i = 0; i < CELL_SLOTS; i++) {
            ItemStack stack = cells.get(i);
            if (stack.isEmpty() || !(stack.getItem()
                    instanceof com.opamb.barotrauma.item.BatteryCellItem)) {
                continue;
            }
            int damage = stack.getDamageValue();
            if (damage <= 0) {
                continue;
            }
            stack.setDamageValue(Math.max(0, damage - points));
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    @Override
    public int getContainerSize() {
        return CELL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : cells) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < CELL_SLOTS ? cells.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = stack.split(amount);
        if (!split.isEmpty()) {
            setChanged();
        }
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (slot >= 0 && slot < CELL_SLOTS) {
            cells.set(slot, ItemStack.EMPTY);
        }
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CELL_SLOTS) {
            return;
        }
        cells.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        cells.clear();
        setChanged();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Charge", chargeKwm);
        tag.putDouble("Condition", condition);
        tag.putDouble("Recharge", rechargeKw);
        tag.putLong("Grace", graceTicks);
        if (boundBox != null) {
            tag.putLong("PowerBox", boundBox.asLong());
        }
        tag.putDouble("CellCarry", cellChargeCarry);
        for (int i = 0; i < CELL_SLOTS; i++) {
            if (!cells.get(i).isEmpty()) {
                tag.put("Cell" + i, cells.get(i).save(new CompoundTag()));
            }
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
        cellChargeCarry = tag.getDouble("CellCarry");
        for (int i = 0; i < CELL_SLOTS; i++) {
            cells.set(i, tag.contains("Cell" + i)
                    ? ItemStack.of(tag.getCompound("Cell" + i)) : ItemStack.EMPTY);
        }
    }
}
