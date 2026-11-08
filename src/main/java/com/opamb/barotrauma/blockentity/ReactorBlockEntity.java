package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.ReactorBlock;
import com.opamb.barotrauma.medical.MedicalCapabilities;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.reactor.ReactorFuel;
import com.opamb.barotrauma.reactor.ReactorState;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
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
 * 反应堆方块实体（只有前层下排中间的主方块有）。
 *
 * <p>本类负责三件事：</p>
 * <ol>
 *   <li>燃料棒物品栏（4 格）和按裂变速率烧燃料；</li>
 *   <li>每 tick 推进 {@link ReactorState}，并把输出/温度上报给所在船的
 *       {@link com.opamb.barotrauma.ship.ShipSimulation}；</li>
 *   <li>处理起火 / 熔毁（爆炸、辐射、烧船）和 GeckoLib 渲染。</li>
 * </ol>
 *
 * <p>供电是"硬核"的：船上没有反应堆或反应堆没开机，ShipSimulation 的
 * powerRatio 就是 0，推进器和泵都不会动。</p>
 */
public class ReactorBlockEntity extends BlockEntity implements Container, GeoBlockEntity {

    public static final int FUEL_SLOTS = 4;
    private static final double SECONDS_PER_TICK = 1.0D / 20.0D;

    private final ReactorState state = new ReactorState();
    private final NonNullList<ItemStack> fuel = NonNullList.withSize(FUEL_SLOTS, ItemStack.EMPTY);
    /** 每格燃料棒的小数耐久损耗（0.2/s 太细，先攒到 1 再扣）。 */
    private final double[] burnCarry = new double[FUEL_SLOTS];
    private double lastLoadKw;
    private double lastRatio;
    /** 过热冒烟的节流计时（秒）。 */
    private double smokeTimer;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.REACTOR.get(), pos, state);
    }

    protected ReactorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState blockState,
                                  ReactorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = DrainPumpBlockEntity.resolveShip(server, pos);
        BlockPos cell = null;
        double load = 0.0D;
        if (ship != null) {
            BlockPos assembly = VsState.active() ? VsPilot.toAssembly(pos) : pos;
            if (ship.tracksHullBlock(assembly)) {
                cell = assembly;
                load = ship.simulation().report().powerDemand();
            }
        }
        be.lastLoadKw = load;

        double available = be.availableFuel();
        int events = be.state.tick(SECONDS_PER_TICK, available, load);
        be.burnFuel(SECONDS_PER_TICK);

        if (ship != null && cell != null) {
            ship.simulation().reportReactor(cell, be.state.outputKw(), be.state.capacityKw(),
                    be.state.temperature(), be.state.overheated(), be.state.meltedDown());
            be.lastRatio = ship.simulation().powerRatio();
        } else {
            be.lastRatio = 0.0D;
        }

        if ((events & ReactorState.EVENT_FIRE) != 0) {
            be.spawnFire(server);
        }
        if ((events & ReactorState.EVENT_MELTDOWN) != 0) {
            be.meltdown(server);
        }

        // 起火之前：温度超过最佳区就持续往外喷白烟，越热越密。
        if (!be.state.meltedDown() && be.state.temperature() > ReactorState.OPTIMAL_TEMP_MAX) {
            be.smokeTimer += SECONDS_PER_TICK;
            double interval = be.state.temperature() > ReactorState.ALLOWED_TEMP_MAX ? 0.15D : 0.35D;
            if (be.smokeTimer >= interval) {
                be.smokeTimer = 0.0D;
                be.spawnOverheatSmoke(server);
            }
        } else {
            be.smokeTimer = 0.0D;
        }
    }

    /** 每 tick 按当前裂变速率烧每根燃料棒；烧完清空该格。 */
    private void burnFuel(double dt) {
        if (state.meltedDown()) {
            return;
        }
        double perRod = state.fission() / 100.0D * ReactorState.FUEL_BURN_PER_SECOND * dt;
        if (perRod <= 0.0D) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < FUEL_SLOTS; i++) {
            ItemStack stack = fuel.get(i);
            if (!ReactorFuel.isFuel(stack)) {
                burnCarry[i] = 0.0D;
                continue;
            }
            burnCarry[i] += perRod;
            int whole = (int) Math.floor(burnCarry[i]);
            if (whole <= 0) {
                continue;
            }
            burnCarry[i] -= whole;
            int max = stack.getMaxDamage();
            if (max <= 0) {
                continue;
            }
            int damage = stack.getDamageValue() + whole;
            if (damage >= max) {
                fuel.set(i, ItemStack.EMPTY);
                burnCarry[i] = 0.0D;
            } else {
                stack.setDamageValue(damage);
            }
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    // ------------------------------------------------------------------ 状态访问

    public boolean powerOn() {
        return state.powerOn();
    }

    public void setPowerOn(boolean on) {
        state.setPowerOn(on);
        setChanged();
    }

    public boolean autoTemp() {
        return state.autoTemp();
    }

    public void setAutoTemp(boolean auto) {
        state.setAutoTemp(auto);
        setChanged();
    }

    public double targetFission() {
        return state.targetFission();
    }

    public void setTargetFission(double value) {
        state.setTargetFission(value);
        setChanged();
    }

    public double targetTurbine() {
        return state.targetTurbine();
    }

    public void setTargetTurbine(double value) {
        state.setTargetTurbine(value);
        setChanged();
    }

    public void applyTemperatureBoost(double amount) {
        state.applyTemperatureBoost(amount);
        setChanged();
    }

    public ReactorState state() {
        return state;
    }

    /** 四格燃料棒的额定燃料值之和（原作 AvailableFuel：铀 80 / 钍 100 / 富尔古瑞姆 150）。 */
    public double availableFuel() {
        double sum = 0.0D;
        for (ItemStack stack : fuel) {
            sum += ReactorFuel.fuelValue(stack);
        }
        return sum;
    }

    public double lastLoadKw() {
        return lastLoadKw;
    }

    public double lastRatio() {
        return lastRatio;
    }

    public Component displayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    // ------------------------------------------------------------------ 起火 / 熔毁

    /** 玩家/物理船所在的世界坐标；物理船的方块实体在船坞里，要用船体变换换算。 */
    private Vec3 effectCenter(ServerLevel level) {
        if (VsState.active()) {
            Vec3 mapped = VsPilot.assemblyToWorld(VsPilot.toAssembly(worldPosition));
            if (mapped != null) {
                return mapped;
            }
            Vec3 position = VsPilot.shipPosition();
            if (position != null) {
                return position;
            }
        }
        return Vec3.atCenterOf(worldPosition).add(0.0D, 1.0D, 0.0D);
    }

    /**
     * 过热但还没起火：像破口进水那样从机身正面往外喷白烟，再在顶部补深烟。
     * 温度越高喷得越密（调用方按 0.35s / 0.15s 节流）。
     */
    private void spawnOverheatSmoke(ServerLevel level) {
        if (!VsState.active()) {
            BlockState blockState = getBlockState();
            if (blockState.getBlock() instanceof ReactorBlock) {
                Direction facing = blockState.getValue(ReactorBlock.FACING);
                Vec3 front = Vec3.atCenterOf(worldPosition.relative(facing))
                        .add(0.0D, 1.0D, 0.0D);
                level.sendParticles(ParticleTypes.CLOUD, front.x, front.y, front.z,
                        6, 0.30D, 0.24D, 0.30D, 0.06D);
                Vec3 top = Vec3.atCenterOf(worldPosition).add(0.0D, 2.7D, 0.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z,
                        3, 0.35D, 0.10D, 0.35D, 0.02D);
                return;
            }
        }
        Vec3 center = effectCenter(level);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 1.2D, center.z,
                8, 0.8D, 0.4D, 0.8D, 0.06D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1.6D, center.z,
                3, 0.6D, 0.3D, 0.6D, 0.02D);
    }

    private void spawnFire(ServerLevel level) {        Vec3 center = effectCenter(level);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                24, 1.6D, 1.0D, 1.6D, 0.02D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.8D, center.z,
                12, 1.2D, 0.8D, 1.2D, 0.01D);

        if (!VsState.active()) {
            BlockState blockState = getBlockState();
            if (!(blockState.getBlock() instanceof ReactorBlock)) {
                return;
            }
            Direction facing = blockState.getValue(ReactorBlock.FACING);
            Direction axis = facing.getClockWise();
            BlockPos front = worldPosition.relative(facing);
            BlockPos[] spots = {
                    front,
                    front.relative(axis, -1),
                    front.relative(axis, 1),
                    front.above()
            };
            for (BlockPos spot : spots) {
                BlockPos support = spot.below();
                if (level.getBlockState(spot).isAir()
                        && level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                    level.setBlockAndUpdate(spot, BaseFireBlock.getState(level, spot));
                }
            }
        }

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || player.position().distanceToSqr(center) > 64.0D) {
                continue;
            }
            AfflictionContainer container = MedicalCapabilities.container(player);
            if (container != null) {
                container.add(AfflictionType.BURN, Limb.TORSO, 12.0D);
            }
            player.setSecondsOnFire(3);
        }
    }

    private void meltdown(ServerLevel level) {
        // 燃料棒全部烧毁
        for (int i = 0; i < FUEL_SLOTS; i++) {
            fuel.set(i, ItemStack.EMPTY);
            burnCarry[i] = 0.0D;
        }
        setChanged();

        Vec3 center = effectCenter(level);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1.0D, center.z,
                60, 3.0D, 2.0D, 3.0D, 0.05D);

        if (VsState.active()) {
            // 物理船：按世界坐标对装配快照扣船体耐久，别把爆炸砸在船坞里。
            VsState.damageExplosion(level, center, 7.0D, 300.0F);
        } else {
            level.explode(null, center.x, center.y, center.z, 5.0F,
                    Level.ExplosionInteraction.BLOCK);
        }

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || player.position().distanceToSqr(center) > 1024.0D) {
                continue;
            }
            AfflictionContainer container = MedicalCapabilities.container(player);
            if (container != null) {
                container.add(AfflictionType.RADIATION, null, 75.0D);
                container.add(AfflictionType.BURN, Limb.TORSO, 150.0D);
                container.add(AfflictionType.ORGAN_DAMAGE, null, 80.0D);
            }
            player.setSecondsOnFire(8);
        }
    }

    // ------------------------------------------------------------------ 容器

    @Override
    public int getContainerSize() {
        return FUEL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : fuel) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return fuel.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(fuel, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(fuel, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        fuel.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return ReactorFuel.isFuel(stack);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < FUEL_SLOTS; i++) {
            fuel.set(i, ItemStack.EMPTY);
            burnCarry[i] = 0.0D;
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // ------------------------------------------------------------------ 存档

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("ReactorState", doubleList(state.snapshot()));
        tag.put("ReactorBurn", doubleList(burnCarry));
        ContainerHelper.saveAllItems(tag, fuel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        state.restore(doubleArray(tag.getList("ReactorState", Tag.TAG_DOUBLE)));
        double[] burn = doubleArray(tag.getList("ReactorBurn", Tag.TAG_DOUBLE));
        for (int i = 0; i < Math.min(FUEL_SLOTS, burn.length); i++) {
            burnCarry[i] = Math.max(0.0D, burn[i]);
        }
        ContainerHelper.loadAllItems(tag, fuel);
    }

    /** CompoundTag 没有 putDoubleArray，这里用 ListTag&lt;DoubleTag&gt; 存。 */
    private static net.minecraft.nbt.ListTag doubleList(double[] values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (double value : values) {
            list.add(net.minecraft.nbt.DoubleTag.valueOf(value));
        }
        return list;
    }

    private static double[] doubleArray(net.minecraft.nbt.ListTag list) {
        double[] values = new double[list.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = ((net.minecraft.nbt.DoubleTag) list.get(i)).getAsDouble();
        }
        return values;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型，无动画控制器。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 2.0D, p.getY() - 1.0D, p.getZ() - 2.0D,
                p.getX() + 3.0D, p.getY() + 4.0D, p.getZ() + 3.0D);
    }
}
