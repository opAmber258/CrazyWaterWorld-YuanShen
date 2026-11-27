package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.world.StarterOutpost;
import com.opamb.barotrauma.world.StarterSub;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 制氧机（临时单格版本）方块实体。
 *
 * <p>数值来自本机原作 {@code Content/Items/OxygenGenerator/oxygengenerator.xml}：</p>
 * <ul>
 *   <li>{@code OxygenGenerator powerconsumption="1000"}、{@code minvoltage="0.5"}；</li>
 *   <li>{@code GeneratedAmount=400}，输出 {@code min(Voltage,2) × 400 × 100}，再乘
 *       {@code (耐久/满耐久)²}（本项目已折算成 {@link ShipSimulation#O2_GENERATOR_OUTPUT}）；</li>
 *   <li>五个槽位给氧气瓶/焊接油箱充气：{@code Condition="2.0"}/秒；</li>
 *   <li>{@code Repairable}：通电自然劣化 0.125/s、120–750 秒宽限、扳手维修、80% 阈值。</li>
 * </ul>
 *
 * <p>模型/多块/正式 GUI 还没做：现在用普通立方体，右键塞瓶子、潜行空手倒出来。</p>
 */
public class OxygenGeneratorBlockEntity extends BlockEntity
        implements Container, PowerConsumer, RepairTarget {

    /** 原作 {@code ItemContainer capacity="5"}。 */
    public static final int TANK_SLOTS = 5;
    /** 原作 powerconsumption。 */
    public static final double POWER_KW = 1000.0D;
    /** 原作 minvoltage。 */
    public static final double MIN_VOLTAGE = 0.5D;
    /** 原作槽位里 {@code OnContaining Condition="2.0"}：每秒给瓶子回 2 点耐久。 */
    private static final double REFILL_PER_SECOND = 2.0D;
    /** 原作 CurrFlow > 0.1，以原作满功率流量 400 × 100 按本模组流量单位换算。 */
    private static final double MIN_FLOW_FOR_WEAR = ShipSimulation.O2_GENERATOR_OUTPUT * (0.1D / 40000.0D);
    private static final int SAVE_INTERVAL = 20;

    private final NonNullList<ItemStack> tanks =
            NonNullList.withSize(TANK_SLOTS, ItemStack.EMPTY);
    /** 每槽的充气余量（耐久是整数，攒满 1 点再写回）。 */
    private final double[] refillCarry = new double[TANK_SLOTS];
    /** 原作 Repairable：deteriorationspeed=0.125，120–750 秒宽限，80% 阈值。 */
    private final ConditionComponent condition =
            new ConditionComponent(100.0D, 0.125D, 120, 750, 0.0D, 0.8D);

    private BlockPos boundBox;
    private ShipState cachedShip;
    private int shipCacheTicks;
    private boolean submerged;
    private int saveTicks;
    private long maintenanceRound = -1L;
    /** 面板读数：上一 tick 的产氧量（单位/秒）和电压。 */
    private double lastFlow;
    private double lastVoltage;

    public OxygenGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OXYGEN_GENERATOR.get(), pos, state);
    }

    /* ------------------------------------------------------------ 服务端 tick */

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  OxygenGeneratorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = be.resolveShip(server, pos);
        BlockPos cell = assemblyPos(pos);
        if (ship == null || !ship.tracksHullBlock(cell)) {
            double voltage = StarterOutpost.voltage(server, pos, be.boundBox);
            be.clearReadouts();
            if (voltage > 0.0D) be.updateOutput(voltage);
            return;
        }
        ShipSimulation simulation = ship.simulation();
        long round = StarterSub.activeMaintenanceRound(server);
        if (round >= 0 && round != be.maintenanceRound) {
            be.maintenanceRound = round;
            be.condition.renewWearGrace();
            be.setChanged();
        }
        boolean broken = be.condition.broken();
        // 供电：坏掉的机器不吃电；电压按全船电网算（和加工台同一套）。
        simulation.reportConsumer(cell, broken ? 0.0D : POWER_KW, MIN_VOLTAGE, be.boundBox);
        double voltage = simulation.consumerVoltage(cell);

        // 泡水判定：机器格本身是实心、不在任何舱室里，按相邻舱室格的水位算
        // （只看 bodyOf(cell) 会永远 -1，泡水伤害永远不触发）。
        java.util.List<BlockPos> breachSources = new java.util.ArrayList<>();
        for (ShipState.Breach breach : ship.breaches()) {
            breachSources.add(breach.pos());
        }
        be.submerged = simulation.submergedAt(cell, breachSources);
        double flow = be.updateOutput(voltage);
        if (be.condition.tick(level, flow > MIN_FLOW_FOR_WEAR && !StarterSub.atFriendlyOutpost(server))) {
            be.setChanged();
        }

        simulation.reportOxygenSource(cell, flow);
    }

    private double updateOutput(double voltage) {
        boolean broken = condition.broken();
        double fraction = condition.fraction();
        // 原作 OxygenGenerator.Update：HasPower（Voltage >= MinVoltage）才产氧；
        // 0.25V 不能用 min(Voltage,2) 直接乘出一个"半供电"的产量。
        double flow = (broken || voltage < MIN_VOLTAGE) ? 0.0D : ShipSimulation.O2_GENERATOR_OUTPUT
                * Math.min(voltage, 2.0D) * fraction * fraction;
        lastVoltage = voltage;
        lastFlow = flow;

        // 原作：容器里 Voltage > 0.1 才给瓶子充气。
        if (!broken && voltage > 0.1D) {
            refillTanks(1.0D / 20.0D);
        }
        if (voltage > 0.0D && ++saveTicks >= SAVE_INTERVAL) {
            saveTicks = 0;
            setChanged();
        }
        return flow;
    }

    /** 失去船体归属时清空面板读数（面板还读同一个 BE）。 */
    private void clearReadouts() {
        lastFlow = 0.0D;
        lastVoltage = 0.0D;
        submerged = false;
    }

    /** 给槽里的氧气瓶/焊接油箱补耐久（耐久即剩余气量）。 */
    private void refillTanks(double dtSeconds) {        boolean changed = false;
        for (int i = 0; i < tanks.size(); i++) {
            ItemStack stack = tanks.get(i);
            if (stack.isEmpty() || !isTankItem(stack)) {
                refillCarry[i] = 0.0D;
                continue;
            }
            int damage = stack.getDamageValue();
            if (damage <= 0) {
                refillCarry[i] = 0.0D;
                continue;
            }
            refillCarry[i] += REFILL_PER_SECOND * dtSeconds;
            int whole = (int) refillCarry[i];
            if (whole <= 0) {
                continue;
            }
            refillCarry[i] -= whole;
            stack.setDamageValue(Math.max(0, damage - whole));
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    /** 这个物品能不能放进发生器充气（原作 {@code refillableoxygensource}）。 */
    public static boolean isTankItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.OXYGEN_TANK.get())
                || stack.is(ModItems.WELDING_FUEL_TANK.get()));
    }

    /** 面板读数：上一 tick 的产氧量（单位/秒）。 */
    public double flow() {
        return lastFlow;
    }

    /** 面板读数：上一 tick 的电压。 */
    public double voltage() {
        return lastVoltage;
    }

    /* ------------------------------------------------------------ 电力接口 */

    @Override
    public double powerDemandKw() {
        return condition.broken() ? 0.0D : POWER_KW;
    }

    @Override
    public double minVoltage() {
        return MIN_VOLTAGE;
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

    /* ------------------------------------------------------------ 可修理 */

    @Override
    public double condition() {
        return condition.condition();
    }

    @Override
    public double maxCondition() {
        return condition.maxCondition();
    }

    @Override
    public double repairThreshold() {
        return condition.repairThreshold();
    }

    @Override
    public void applyRepair(double points) {
        condition.repair(points);
        setChanged();
    }

    @Override
    public RepairTarget.Tool requiredTool() {
        return RepairTarget.Tool.WRENCH;
    }

    /* ------------------------------------------------------------ 容器 */

    @Override
    public int getContainerSize() {
        return TANK_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : tanks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return tanks.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = tanks.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = stack.split(amount);
        if (stack.isEmpty()) {
            tanks.set(slot, ItemStack.EMPTY);
        }
        refillCarry[slot] = 0.0D;
        setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = tanks.get(slot);
        tanks.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        tanks.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        refillCarry[slot] = 0.0D;
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isTankItem(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void clearContent() {
        tanks.clear();
        java.util.Arrays.fill(refillCarry, 0.0D);
    }

    /* ------------------------------------------------------------ 存盘 */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Tanks", writeTanks());
        condition.save(tag, "Condition");
        tag.putLong("MaintenanceRound", maintenanceRound);
        if (boundBox != null) {
            tag.putLong("PowerBox", boundBox.asLong());
        }
    }

    private CompoundTag writeTanks() {
        CompoundTag list = new CompoundTag();
        for (int i = 0; i < tanks.size(); i++) {
            if (!tanks.get(i).isEmpty()) {
                list.put("Slot" + i, tanks.get(i).save(new CompoundTag()));
            }
        }
        return list;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tanks.clear();
        if (tag.contains("Tanks")) {
            CompoundTag list = tag.getCompound("Tanks");
            for (int i = 0; i < tanks.size(); i++) {
                String key = "Slot" + i;
                if (list.contains(key)) {
                    tanks.set(i, ItemStack.of(list.getCompound(key)));
                }
            }
        }
        condition.load(tag, "Condition");
        maintenanceRound = tag.contains("MaintenanceRound") ? tag.getLong("MaintenanceRound") : -1L;
        boundBox = tag.contains("PowerBox") ? BlockPos.of(tag.getLong("PowerBox")) : null;
    }

    /* ------------------------------------------------------------ 船归属 */

    private ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        // 船状态不存档：世界重开后第一次 tick 靠自己补扫；找不到也要等 1 秒再扫，
        // 否则没接入船体的机器会每 tick 翻一遍附近区块。
        if (shipCacheTicks > 0) {
            shipCacheTicks--;
            return cachedShip;
        }
        shipCacheTicks = 20;
        cachedShip = ShipCores.stateOwning(level, pos, 64.0D);
        return cachedShip;
    }

    private static BlockPos assemblyPos(BlockPos pos) {
        if (!VsState.active() || !VsPilot.isYardBlock(pos)) {
            return pos;
        }
        return VsPilot.toAssembly(pos);
    }
}
