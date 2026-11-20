package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.PowerGrid;
import com.opamb.barotrauma.ship.RepairTarget;
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
 * 通电自然衰减 0.1/s（120–720 秒宽限后），泡水通电 0.25/s；电压超过 1.7
 * 时按原作的随机过压损伤（每 tick 1% 概率，满耐久期望约 2.5/s），不是固定
 * 10/s。耐久归零后盒子失效，绑在它上面的设备拿不到电。</p>
 */
public class JunctionBoxBlockEntity extends BlockEntity implements RepairTarget, GeoBlockEntity {

    /** 通电自然衰减速度（/秒）。 */
    private static final double DECAY_POWERED = 0.1D;
    /** 泡水通电衰减速度（/秒）。 */
    private static final double DECAY_SUBMERGED = 0.25D;
    /**
     * 过压阈值：Dugong/Orca 等原版潜艇的接线盒实例把
     * {@code PowerTransfer.OverloadVoltage} 覆盖成 1.7（prefab 默认 2.0）。
     * 本模组的起始船是儒艮级，按实船用 1.7。
     *
     * <p>全仓只有这一份阈值；判定统一走 {@link PowerGrid#overvoltage}。</p>
     */
    public static final double OVERVOLTAGE = 1.7D;

    private double condition = 100.0D;
    /** 通电自然衰减的宽限 tick 数；-1 = 还没初始化。 */
    private long graceTicks = -1L;
    private boolean submerged;
    private double lastVoltage;
    /** 上次确认过的归属核心；正常 tick 直接用它，核心没了才重新搜。 */
    private BlockPos cachedCorePos;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public JunctionBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JUNCTION_BOX.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  JunctionBoxBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        // 先判断这个盒子是否真的属于活动物理船：物理船 A 活动时，
        // 方块船 B 的盒子不能减 A 的船坞偏移，否则 tracksHullBlock 会拒绝、设备断电。
        boolean physical = VsState.isPhysical(server, pos);
        ShipState ship = physical ? VsState.activeState() : be.resolveBlockShip(server, pos);
        if (ship == null) {
            return;
        }
        BlockPos cell = physical ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        double voltage = ship.simulation().voltage();
        be.lastVoltage = voltage;
        boolean powered = voltage > 0.1D;
        // 按接线盒所在高度判泡水：舱底有积水不代表墙上的盒子被淹。
        // 连海空腔的 bodyLevel 是整块统一水位；两个低洼区只靠高处孔洞连通时，
        // 破口侧进水、另一侧仍干燥，必须按盒子所在列的实际水位判断。
        int body = ship.simulation().bodyOf(cell);
        java.util.List<BlockPos> breachSources = new java.util.ArrayList<>();
        for (ShipState.Breach breach : ship.breaches()) {
            breachSources.add(breach.pos());
        }
        be.submerged = ship.simulation().bodyIsWet(body)
                && cell.getY() < ship.simulation().waterLevelAt(cell, breachSources);

        if (be.graceTicks < 0L) {
            be.graceTicks = 2400L + level.random.nextInt(12000);
            be.setChanged();
        }

        double before = be.condition;
        if (powered) {
            if (be.graceTicks > 0L) {
                be.graceTicks--;
                // 持久字段变了要标脏，否则区块没别的变化时宽限期会回退。
                be.setChanged();
            } else {
                be.condition -= DECAY_POWERED / 20.0D;
            }
            if (be.submerged) {
                be.condition -= DECAY_SUBMERGED / 20.0D;
            }
            // 阈值判定走 PowerGrid，别在这里再写一份比较：两边各一份的话，
            // 以后调阈值只会改到其中一处。
            if (PowerGrid.overvoltage(voltage, OVERVOLTAGE)) {
                // 原作 PowerTransfer.UpdateOvervoltage：每帧 1% 概率扣
                // deltaTime × Rand(10,500) × Lerp(5,1,condition/maxCondition)。
                // 20Hz 下 1% 概率 + dt=1/20，每秒期望和原作 60Hz 一致
                // （满耐久约 2.5/s，不是固定 10/s 十秒全烧）。
                if (level.random.nextFloat() < 0.01F) {
                    double fraction = Mth.clamp(be.condition / 100.0D, 0.0D, 1.0D);
                    double scale = 5.0D - 4.0D * fraction;
                    be.condition -= (1.0D / 20.0D)
                            * (10.0D + level.random.nextDouble() * 490.0D) * scale;
                }
            }
        }
        be.condition = Mth.clamp(be.condition, 0.0D, 100.0D);
        if (Math.abs(before - be.condition) > 0.001D) {
            be.setChanged();
        }
        ship.simulation().reportJunctionBox(cell, be.condition);
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
            graceTicks = 2400L + (level == null ? 0 : level.random.nextInt(12000));
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

    public boolean submerged() {
        return submerged;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    public boolean broken() {
        return condition <= 0.0D;
    }

    /** 这个盒子是否已经在船体几何里（决定要不要先重扫）。 */
    public boolean inHull() {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        // 两艘船靠近时最近核心可能属于另一艘船；按船体归属解析，
        // 否则 inHull() 误报 false，玩家每次开面板都会触发一次重扫。
        ShipState ship = VsState.active() && VsPilot.isYardBlock(worldPosition)
                ? VsState.activeState() : resolveBlockShip(server, worldPosition);
        return ship != null && ship.tracksHullBlock(assemblyPos(worldPosition));
    }

    public Component displayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    /** 不属于活动物理船：按附近核心找船体几何包含该坐标的方块船。 */
    private ShipState resolveBlockShip(ServerLevel level, BlockPos pos) {
        // 正常 tick 只查上次确认的核心；重扫会换 ShipState 对象，所以缓存坐标不缓存状态。
        if (cachedCorePos != null && level.hasChunkAt(cachedCorePos)
                && level.getBlockEntity(cachedCorePos) instanceof ShipCoreBlockEntity core) {
            ShipState state = core.state() != null ? core.state() : core.rescan(level);
            if (state != null && state.tracksHullBlock(pos)) {
                return state;
            }
        }
        // 两艘船靠近时，最近的核心可能属于另一艘船；必须按船体归属选。
        for (ShipCoreBlockEntity core : ShipCores.coresNear(level, pos, 64.0D)) {
            ShipState state = core.state() != null ? core.state() : core.rescan(level);
            if (state != null && state.tracksHullBlock(pos)) {
                cachedCorePos = core.getBlockPos();
                return state;
            }
        }
        cachedCorePos = null;
        return null;
    }

    private static BlockPos assemblyPos(BlockPos pos) {
        return VsState.active() && VsPilot.isYardBlock(pos) ? VsPilot.toAssembly(pos) : pos;
    }

    private static ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        // 船状态不存档：世界重开后第一次 tick 靠自己补扫，不能等玩家去开电力面板。
        return ShipCores.stateNear(level, pos, 32.0D);
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
