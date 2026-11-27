package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.fabricator.FabricatorCatalog;
import com.opamb.barotrauma.fabricator.FabricatorData;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.world.StarterOutpost;
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
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 加工台方块实体。
 *
 * <p>对应原作 {@code Fabricator}：500 kW 耗电、MinVoltage 0.5，制作期间持续耗电；
 * 材料从台面上的 5 个输入格和玩家背包里扣，成品进 1 格输出槽。
 * 耐久照 {@code Repairable}：加工台/解构仪在 {@code ShouldDeteriorate} 里被显式排除，
 * 所以不会自然劣化，只有泡水（{@code InWater} 每秒 -0.5）和爆炸伤害会掉耐久。</p>
 */
public class FabricatorBlockEntity extends BlockEntity
        implements Container, PowerConsumer, RepairTarget, GeoBlockEntity {

    /** 原作 fabricator powerconsumption。 */
    public static final double POWER_KW = 500.0D;
    public static final double MIN_VOLTAGE = 0.5D;

    public static final int INPUT_SLOTS = 5;
    public static final int OUTPUT_SLOT = 5;
    public static final int SLOTS = INPUT_SLOTS + 1;

    /** 泡水时每秒掉的耐久（原作 fabricator/deconstructor 的 InWater 状态效果）。 */
    private static final double SUBMERGED_DECAY = 0.5D;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ConditionComponent condition =
            new ConditionComponent(100.0D, 0.0D, 60, 120, 0.0D, 0.8D);

    private String recipeId;
    /** 剩余要做的件数（原作 Fabricator 的 AmountToFabricate 队列）。 */
    private int pendingCrafts;
    /** 本件实际扣掉的原材料副本（保留损耗 NBT）：取消时原样退回。 */
    private final java.util.List<ItemStack> consumedForCraft = new java.util.ArrayList<>();
    /** 发起制作的玩家，续做时用来扣料。 */
    private java.util.UUID ownerId;
    private double progressSeconds;
    private double durationSeconds;
    private boolean working;
    private boolean powered;
    /** 电网电压够不够（空转时也算，给面板显示用）。 */
    private boolean powerAvailable;
    private double lastVoltage;
    private BlockPos boundBox;
    /** 缓存的船状态 + 重扫冷却：stateNear 每次要翻 9×9 区块，不能每 tick 扫。 */
    private ShipState cachedShip;
    private int shipCacheTicks;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FabricatorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.FABRICATOR.get(), pos, state);
    }

    protected FabricatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /* ------------------------------------------------------------ 服务端 tick */

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  FabricatorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = be.resolveShip(server, pos);
        BlockPos cell = assemblyPos(server, pos);
        if (ship != null && ship.tracksHullBlock(cell)) {
            // 任一相邻舱室进水就算泡水，机器自身是实心格。
            java.util.List<BlockPos> breachSources = new java.util.ArrayList<>();
            for (ShipState.Breach breach : ship.breaches()) {
                breachSources.add(breach.pos());
            }
            if (ship.simulation().submergedAt(cell, breachSources)) {
                be.condition.damage(SUBMERGED_DECAY / 20.0D);
                be.setChanged();
            }
            ship.simulation().reportConsumer(cell, be.powerDemandKw(), MIN_VOLTAGE, be.boundBox);
            be.lastVoltage = ship.simulation().consumerVoltage(cell);
        } else {
            be.lastVoltage = StarterOutpost.voltage(server, pos, be.boundBox);
        }
        be.powerAvailable = be.lastVoltage >= MIN_VOLTAGE;
        // 耐久归零的机器不能继续制作；working 保留，修理后接着做。
        be.powered = be.working && be.powerAvailable && !be.condition.broken();
        if (!be.working || !be.powered) {
            return;
        }
        be.progressSeconds += 1.0D / 20.0D;
        be.setChanged();
        if (be.progressSeconds >= be.durationSeconds) {
            be.finishCraft(server);
        }
    }

    /* ------------------------------------------------------------ 制作流程 */

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

    /** 电网有没有电（不要求正在加工）；面板和"能不能开工"都用它。 */
    public boolean powerAvailable() {
        return powerAvailable;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    public String recipeId() {
        return recipeId;
    }

    /** 能不能开始做这条配方（材料、输出格、耐久、供电）。 */
    public boolean canStart(Player player, FabricatorCatalog.Recipe recipe) {
        if (working || recipe == null || condition.broken() || !powerAvailable) {
            return false;
        }
        ItemStack result = FabricatorData.result(recipe);
        if (result.isEmpty()) {
            return false;
        }
        if (!canOutputAccept(result)) {
            return false;
        }
        return countAvailable(player, recipe) == 0;
    }

    /** 开始制作：扣材料、置进度。{@code amount} 是排队件数（原作最多 99）。 */
    public boolean start(Player player, FabricatorCatalog.Recipe recipe, int amount) {
        if (!canStart(player, recipe)) {
            return false;
        }
        consume(player, recipe);
        this.recipeId = recipe.id();
        this.pendingCrafts = Math.max(1, Math.min(99, amount));
        this.ownerId = player == null ? null : player.getUUID();
        this.durationSeconds = Math.max(0.05D, recipe.time());
        this.progressSeconds = 0.0D;
        this.working = true;
        this.setChanged();
        return true;
    }

    /**
     * 取消制作：当前这一件已经扣过料，先退给发起者（离线/背包满就掉在机器旁）。
     * 队列里没开工的件数没有扣料，不用退。
     */
    public void cancel(ServerLevel level) {
        if (!working) {
            return;
        }
        FabricatorCatalog.Recipe recipe = recipeId == null ? null
                : FabricatorData.catalog().recipe(recipeId);
        Player owner = ownerId == null ? null
                : level.getServer().getPlayerList().getPlayer(ownerId);
        // 退的是"实际扣掉的物品"，不是按配方新建：扣料只按物品种类匹配，
        // 损耗的燃料棒也能当输入，按配方重建会把它洗成全新的（物品复制）。
        java.util.List<ItemStack> refunds = new java.util.ArrayList<>(consumedForCraft);
        if (refunds.isEmpty() && recipe != null) {
            // 旧存档没有扣料明细：退回本件应消耗的原料数量。
            for (FabricatorCatalog.Ingredient ingredient : recipe.ingredients()) {
                ItemStack refund = new ItemStack(FabricatorData.item(ingredient.itemId()),
                        ingredient.count());
                if (!refund.isEmpty()) {
                    refunds.add(refund);
                }
            }
        }
        for (ItemStack refund : refunds) {
            if (owner != null && owner.getInventory().add(refund)) {
                continue;
            }
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, refund);
        }
        consumedForCraft.clear();
        working = false;
        progressSeconds = 0.0D;
        recipeId = null;
        pendingCrafts = 0;
        ownerId = null;
        setChanged();
    }

    private void finishCraft(ServerLevel level) {
        FabricatorCatalog.Recipe recipe = recipeId == null ? null
                : FabricatorData.catalog().recipe(recipeId);
        working = false;
        progressSeconds = 0.0D;
        recipeId = null;
        // 本件已产出：这一份扣料不再是"待退款"。续做会在 consume 里换成新的明细。
        consumedForCraft.clear();
        if (recipe == null) {
            setChanged();
            return;
        }
        ItemStack result = FabricatorData.result(recipe);
        if (result.isEmpty()) {
            setChanged();
            return;
        }
        ItemStack output = items.get(OUTPUT_SLOT);
        if (output.isEmpty()) {
            items.set(OUTPUT_SLOT, result);
        } else if (ItemStack.isSameItemSameTags(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize()) {
            output.grow(result.getCount());
        } else {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, result);
        }
        // 排队：还有件数就继续扣料接着做（玩家不在/材料不够就停）。
        if (pendingCrafts > 1) {
            Player owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
            FabricatorCatalog.Recipe next = FabricatorData.catalog().recipe(recipe.id());
            if (owner != null && next != null && canStart(owner, next)) {
                consume(owner, next);
                pendingCrafts--;
                this.recipeId = next.id();
                this.progressSeconds = 0.0D;
                this.working = true;
                setChanged();
                return;
            }
        }
        pendingCrafts = 0;
        ownerId = null;
        setChanged();
    }

    /** 返回还缺多少件材料（0 = 齐了）。材料来自 5 个输入格 + 玩家背包。 */
    public int countAvailable(Player player, FabricatorCatalog.Recipe recipe) {
        int missing = 0;
        for (FabricatorCatalog.Ingredient ingredient : recipe.ingredients()) {
            int have = countInSlots(ingredient) + (player == null ? 0 : countInInventory(player, ingredient));
            missing += Math.max(0, ingredient.count() - have);
        }
        return missing;
    }

    private int countInSlots(FabricatorCatalog.Ingredient ingredient) {
        int have = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && stack.is(FabricatorData.item(ingredient.itemId()))) {
                have += stack.getCount();
            }
        }
        return have;
    }

    private static int countInInventory(Player player, FabricatorCatalog.Ingredient ingredient) {
        int have = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(FabricatorData.item(ingredient.itemId()))) {
                have += stack.getCount();
            }
        }
        // 工具带/背包里的材料也算（背包格是独立容器）。
        var bag = com.opamb.barotrauma.equipment.BagManager.container(player);
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack stack = bag.getItem(i);
            if (!stack.isEmpty() && stack.is(FabricatorData.item(ingredient.itemId()))) {
                have += stack.getCount();
            }
        }
        return have;
    }

    private void consume(Player player, FabricatorCatalog.Recipe recipe) {
        java.util.List<ItemStack> taken = new java.util.ArrayList<>();
        for (FabricatorCatalog.Ingredient ingredient : recipe.ingredients()) {
            int left = ingredient.count();
            left = takeFromSlots(ingredient, left, taken);
            if (left > 0 && player != null) {
                takeFromInventory(player, ingredient, left, taken);
            }
        }
        // 记下每次实际移出的副本（含损耗 NBT）：取消时按这份退回。
        consumedForCraft.clear();
        consumedForCraft.addAll(taken);
        setChanged();
    }

    private int takeFromSlots(FabricatorCatalog.Ingredient ingredient, int amount,
                              java.util.List<ItemStack> taken) {
        for (int i = 0; i < INPUT_SLOTS && amount > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || !stack.is(FabricatorData.item(ingredient.itemId()))) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            taken.add(stack.copyWithCount(take));
            stack.shrink(take);
            amount -= take;
            if (stack.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
        }
        return amount;
    }

    private static void takeFromInventory(Player player, FabricatorCatalog.Ingredient ingredient,
                                          int amount, java.util.List<ItemStack> taken) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(FabricatorData.item(ingredient.itemId()))) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            taken.add(stack.copyWithCount(take));
            stack.shrink(take);
            amount -= take;
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        var bag = com.opamb.barotrauma.equipment.BagManager.container(player);
        boolean bagChanged = false;
        for (int i = 0; i < bag.getContainerSize() && amount > 0; i++) {
            ItemStack stack = bag.getItem(i);
            if (stack.isEmpty() || !stack.is(FabricatorData.item(ingredient.itemId()))) {
                continue;
            }
            int take = Math.min(amount, stack.getCount());
            taken.add(stack.copyWithCount(take));
            stack.shrink(take);
            amount -= take;
            bagChanged = true;
            if (stack.isEmpty()) {
                bag.setItem(i, ItemStack.EMPTY);
            }
        }
        if (bagChanged) {
            // 只 shrink 不落盘的话，换下再穿回背包会按旧 NBT 恢复整堆材料；
            // BagContainer.setChanged() 会把当前内容写回背包 NBT。
            bag.setChanged();
        }
    }

    private boolean canOutputAccept(ItemStack result) {
        ItemStack output = items.get(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameTags(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /* ------------------------------------------------------------ 电力/修理 */

    @Override
    public double powerDemandKw() {
        return working && !condition.broken() ? POWER_KW * condition.powerPenalty() : 0.0D;
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

    private ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        // 船状态不存档：世界重开后第一次 tick 靠自己补扫，不能等玩家去开电力面板。
        // stateNear 会翻 9×9 区块，缓存 1 秒；核心被拆/换船时最多 1 秒后自动失效。
        // 冷却只看计时：找不到船时 cachedShip 是 null，也要等 1 秒再扫，
        // 否则没接入船体的机器会每 tick 翻一遍附近 9×9 区块。
        if (shipCacheTicks > 0) {
            shipCacheTicks--;
            return cachedShip;
        }
        shipCacheTicks = 20;
        cachedShip = ShipCores.stateOwning(level, pos, 64.0D);
        return cachedShip;
    }

    private static BlockPos assemblyPos(ServerLevel level, BlockPos pos) {
        // 只有当前船坞里的方块才做装配坐标换算；别的船/普通世界的机器原样返回，
        // 否则会减掉活动船的船坞偏移，tracksHullBlock 检查错位置。
        if (!VsState.active() || !VsPilot.isYardBlock(pos)) {
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
        tag.putInt("PendingCrafts", pendingCrafts);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putDouble("Progress", progressSeconds);
        tag.putDouble("Duration", durationSeconds);
        if (!consumedForCraft.isEmpty()) {
            net.minecraft.nbt.ListTag consumed = new net.minecraft.nbt.ListTag();
            for (ItemStack stack : consumedForCraft) {
                consumed.add(stack.save(new CompoundTag()));
            }
            tag.put("Consumed", consumed);
        }
        if (recipeId != null) {
            tag.putString("Recipe", recipeId);
        }
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
        pendingCrafts = tag.getInt("PendingCrafts");
        ownerId = tag.contains("Owner") ? tag.getUUID("Owner") : null;
        progressSeconds = tag.getDouble("Progress");
        durationSeconds = tag.getDouble("Duration");
        recipeId = tag.contains("Recipe") ? tag.getString("Recipe") : null;
        consumedForCraft.clear();
        net.minecraft.nbt.ListTag consumed =
                tag.getList("Consumed", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < consumed.size(); i++) {
            consumedForCraft.add(ItemStack.of(consumed.getCompound(i)));
        }
        for (int i = 0; i < SLOTS; i++) {
            items.set(i, tag.contains("Slot" + i)
                    ? ItemStack.of(tag.getCompound("Slot" + i)) : ItemStack.EMPTY);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：原作只有工作时的装饰轮组，这里没有绑定动画。
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
