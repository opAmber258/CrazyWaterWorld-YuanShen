package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.DeconstructorBlock;
import com.opamb.barotrauma.fabricator.FabricatorCatalog;
import com.opamb.barotrauma.fabricator.FabricatorData;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 解构仪方块实体。
 *
 * <p>对应原作 {@code Deconstructor}：500 kW、MinVoltage 0.5，输入 3 格、输出 5 格；
 * 放进去的东西按原作的 {@code <Deconstruct>} 表拆回材料。工作时驱动方块状态
 * {@code working}，客户端据此播放双刀轴 1 秒循环动画。</p>
 *
 * <p>耐久同加工台：{@code Repairable.ShouldDeteriorate} 排除了这两台机器，
 * 所以不自然劣化，只吃泡水（-0.5/s）和爆炸伤害。</p>
 */
public class DeconstructorBlockEntity extends BlockEntity
        implements Container, PowerConsumer, RepairTarget, GeoBlockEntity {

    public static final double POWER_KW = 500.0D;
    public static final double MIN_VOLTAGE = 0.5D;

    public static final int INPUT_SLOTS = 8;
    public static final int OUTPUT_SLOTS = 5;
    public static final int SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;

    private static final double SUBMERGED_DECAY = 0.5D;
    private static final RawAnimation WORK =
            RawAnimation.begin().thenLoop("animation.deconstructor.work");

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ConditionComponent condition =
            new ConditionComponent(100.0D, 0.0D, 60, 120, 0.0D, 80.0D);

    private double progressSeconds;
    private double durationSeconds;
    private boolean working;
    private int workingSlot = -1;
    private boolean powered;
    /** 电网电压够不够（空转时也算，给面板显示用）。 */
    private boolean powerAvailable;
    private double lastVoltage;
    private BlockPos boundBox;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DeconstructorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.DECONSTRUCTOR.get(), pos, state);
    }

    protected DeconstructorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /* ------------------------------------------------------------ 服务端 tick */

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  DeconstructorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = resolveShip(server, pos);
        if (ship == null) {
            be.powered = false;
            be.powerAvailable = false;
            be.lastVoltage = 0.0D;
            if (be.working) {
                be.stopWorking(server, pos, state);
            }
            return;
        }
        BlockPos cell = assemblyPos(server, pos);
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        boolean submerged = ship.simulation().bodyIsWet(ship.simulation().bodyOf(cell));
        if (submerged) {
            be.condition.damage(SUBMERGED_DECAY / 20.0D);
            be.setChanged();
        }
        if (be.condition.broken() && be.working) {
            be.stopWorking(server, pos, state);
        }
        // 空转也上报（需求 0），面板才能显示"现在有没有电"。
        double demand = be.working ? POWER_KW * be.condition.powerPenalty() : 0.0D;
        ship.simulation().reportConsumer(cell, demand, MIN_VOLTAGE, be.boundBox);
        be.lastVoltage = ship.simulation().consumerVoltage(cell);
        be.powerAvailable = be.lastVoltage >= MIN_VOLTAGE;
        be.powered = be.working && be.powerAvailable;
        if (!be.working || !be.powered) {
            return;
        }
        be.progressSeconds += 1.0D / 20.0D;
        if (be.progressSeconds >= be.durationSeconds) {
            be.finish(server, pos, state);
        }
    }

    /* ------------------------------------------------------------ 拆解流程 */

    public boolean isWorking() {
        return working;
    }

    public double progressFraction() {
        return durationSeconds <= 0.0D ? 0.0D
                : Math.min(1.0D, progressSeconds / durationSeconds);
    }

    public boolean powered() {
        return powered;
    }

    /** 电网有没有电（不要求正在拆解）；面板和"能不能开工"都用它。 */
    public boolean powerAvailable() {
        return powerAvailable;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    /** 第一个输入格里的物品对应的拆解表；没有就返回 null。 */
    public FabricatorCatalog.Deconstruct recipeForInput() {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString();
            return FabricatorData.catalog().deconstruct(id);
        }
        return null;
    }

    public boolean canStart() {
        if (working || condition.broken() || !powerAvailable) {
            return false;
        }
        return recipeForInput() != null;
    }

    public boolean start(ServerLevel level, BlockPos pos, BlockState state) {
        if (!canStart()) {
            return false;
        }
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                workingSlot = i;
                break;
            }
        }
        FabricatorCatalog.Deconstruct recipe = recipeForInput();
        if (recipe == null) {
            return false;
        }
        this.durationSeconds = Math.max(0.05D, recipe.time());
        this.progressSeconds = 0.0D;
        this.working = true;
        setWorkingState(level, pos, state, true);
        setChanged();
        return true;
    }

    public void cancel(ServerLevel level, BlockPos pos, BlockState state) {
        if (working) {
            stopWorking(level, pos, state);
        }
    }

    private void stopWorking(ServerLevel level, BlockPos pos, BlockState state) {
        working = false;
        progressSeconds = 0.0D;
        workingSlot = -1;
        setWorkingState(level, pos, state, false);
        setChanged();
    }

    private void finish(ServerLevel level, BlockPos pos, BlockState state) {
        FabricatorCatalog.Deconstruct recipe = workingSlot >= 0
                ? inputRecipe(items.get(workingSlot)) : null;
        int slot = workingSlot;
        stopWorking(level, pos, state);
        if (recipe == null || slot < 0) {
            return;
        }
        ItemStack input = items.get(slot);
        if (input.isEmpty()) {
            return;
        }
        input.shrink(1);
        if (input.isEmpty()) {
            items.set(slot, ItemStack.EMPTY);
        }
        for (FabricatorCatalog.Ingredient output : recipe.outputs()) {
            ItemStack stack = new ItemStack(FabricatorData.item(output.itemId()), output.count());
            if (stack.isEmpty() || stack.getItem() == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            if (!addToOutputs(stack)) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D,
                        pos.getZ() + 0.5D, stack);
            }
        }
        setChanged();
    }

    private static FabricatorCatalog.Deconstruct inputRecipe(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString();
        return FabricatorData.catalog().deconstruct(id);
    }

    private boolean addToOutputs(ItemStack stack) {
        for (int i = INPUT_SLOTS; i < SLOTS; i++) {
            ItemStack existing = items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                int room = existing.getMaxStackSize() - existing.getCount();
                if (room > 0) {
                    int move = Math.min(room, stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        for (int i = INPUT_SLOTS; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack);
                return true;
            }
        }
        return stack.isEmpty();
    }

    private static void setWorkingState(Level level, BlockPos pos, BlockState state, boolean value) {
        if (level.isClientSide || !(state.getBlock() instanceof DeconstructorBlock)) {
            return;
        }
        if (state.hasProperty(DeconstructorBlock.WORKING)
                && state.getValue(DeconstructorBlock.WORKING) != value) {
            level.setBlock(pos, state.setValue(DeconstructorBlock.WORKING, value),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /* ------------------------------------------------------------ 电力/修理 */

    @Override
    public double powerDemandKw() {
        return working ? POWER_KW : 0.0D;
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
    public void damage(double points) {
        condition.damage(points);
        setChanged();
    }

    @Override
    public RepairTarget.Tool requiredTool() {
        return RepairTarget.Tool.WRENCH;
    }

    private static ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        // 船状态不存档：世界重开后第一次 tick 靠自己补扫，不能等玩家去开电力面板。
        return ShipCores.stateNear(level, pos, 64.0D);
    }

    private static BlockPos assemblyPos(ServerLevel level, BlockPos pos) {
        if (!VsState.active()) {
            return pos;
        }
        return VsPilot.toAssembly(pos);
    }

    /* ------------------------------------------------------------ 容器 */

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
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
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS) {
            return;
        }
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /* ------------------------------------------------------------ 存档/渲染 */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (boundBox != null) {
            tag.putLong("PowerBox", boundBox.asLong());
        }
        condition.save(tag, "Condition");
        tag.putBoolean("Working", working);
        tag.putDouble("Progress", progressSeconds);
        tag.putDouble("Duration", durationSeconds);
        tag.putInt("WorkingSlot", workingSlot);
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                tag.put("Slot" + i, items.get(i).save(new CompoundTag()));
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundBox = tag.contains("PowerBox") ? BlockPos.of(tag.getLong("PowerBox")) : null;
        condition.load(tag, "Condition");
        working = tag.getBoolean("Working");
        progressSeconds = tag.getDouble("Progress");
        durationSeconds = tag.getDouble("Duration");
        workingSlot = tag.getInt("WorkingSlot");
        for (int i = 0; i < SLOTS; i++) {
            items.set(i, tag.contains("Slot" + i)
                    ? ItemStack.of(tag.getCompound("Slot" + i)) : ItemStack.EMPTY);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "work", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<DeconstructorBlockEntity> state) {
        boolean running = getBlockState().hasProperty(DeconstructorBlock.WORKING)
                && getBlockState().getValue(DeconstructorBlock.WORKING);
        return running ? state.setAndContinue(WORK) : PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 2.0D, p.getY() - 1.0D, p.getZ() - 2.0D,
                p.getX() + 4.0D, p.getY() + 4.0D, p.getZ() + 4.0D);
    }

    public Component displayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }
}
