package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.AutomationMode;
import com.opamb.barotrauma.ship.AutomationUtil;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 舱壁门的 GeckoLib 方块实体（只挂在最下面一段）。
 *
 * <p>和舱口一样，状态只读 blockstate 的 {@code open}；打开/关闭各一个动画，
 * 由控制器做过渡。模型本身是 3 格高，渲染器挂在最下面一段，向上覆盖两格。</p>
 */
public class BulkheadDoorBlockEntity extends BlockEntity implements GeoBlockEntity, RepairTarget {

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSED = RawAnimation.begin().thenPlayAndHold("closed");

    private AutomationMode automation = AutomationMode.MANUAL;
    private int motionHold;
    /**
     * 最近一次手动右键的 tick。按住右键时客户端每 4 tick 触发一次 use，
     * 门会每 0.2 秒开/关一次，看起来像“打不开”。10 tick 内的重复触发直接忽略。
     */
    private long lastManualUseTick = -100L;
    /** 门不自然劣化（原作 Repairable 默认 0/s），只被外力打坏后修理。 */
    private final ConditionComponent condition =
            new ConditionComponent(100.0D, 0.0D, 0, 0, 0.0D, 0.8D);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BulkheadDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BULKHEAD_DOOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  BulkheadDoorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        if (be.automation == AutomationMode.MANUAL) {
            be.motionHold = 0;
            return;
        }
        ShipState ship = DrainPumpBlockEntity.resolveShip(server, pos);
        if (ship == null) {
            return;
        }
        boolean physical = AutomationUtil.isPhysicalShip(ship);
        BlockPos cell = physical ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        boolean open = state.getValue(BulkheadDoorBlock.OPEN);
        boolean want = open;
        if (be.automation == AutomationMode.MOTION) {
            // 原作自动关门：运动探测器 + OrComponent TimeFrame=4
            // （Content/Items/Assemblies/AutoClosingDoor.xml，说明是
            // "closes automatically if nobody is around for 4 seconds"）。
            // 没人后保持 4 秒再关，对应 80 tick。
            boolean near = AutomationUtil.playerNear(server, ship, cell, 3.0D);
            if (near) {
                be.motionHold = AutomationUtil.MOTION_HOLD_TICKS;
            } else if (be.motionHold > 0) {
                be.motionHold--;
            }
            want = near || be.motionHold > 0;
        } else if (be.automation == AutomationMode.WATERTIGHT) {
            // 原作没有单独的“水密门”：自动关门装配用运动探测器（开门 + 延时关门），
            // 气闸装配用水探测器 + NOT/AND（只管有水时的时序）。合并到一扇门里就是
            // 运动负责开、水只负责关——水永远不触发“开”（用户要求：有水不自动开门）。
            boolean near = AutomationUtil.playerNear(server, ship, cell, 3.0D);
            if (near) {
                be.motionHold = AutomationUtil.MOTION_HOLD_TICKS;
            } else if (be.motionHold > 0) {
                be.motionHold--;
            }
            boolean motionWant = near || be.motionHold > 0;
            // 门占上下两格：任一半通海就不能自动开，水量也取两格邻域最大值。
            boolean toSea = AutomationUtil.opensToSea(ship, cell)
                    || AutomationUtil.opensToSea(ship, cell.above());
            if (toSea) {
                // 外海侧的门：舱内再干也不能自动开，开门就是进水。
                be.motionHold = 0;
                want = false;
            } else {
                double water = Math.max(AutomationUtil.maxWaterFraction(ship, cell),
                        AutomationUtil.maxWaterFraction(ship, cell.above()));
                if (water > 0.3D) {
                    // 有水：强制关，并清掉运动保持，水退之前不会被顶开。
                    be.motionHold = 0;
                    want = false;
                } else if (water < 0.1D) {
                    // 干的：回到运动感应（有人靠近才开）。
                    want = motionWant;
                } else {
                    // 0.1~0.3 滞回带：保持当前状态，水本身不改变开合。
                    want = open;
                }
            }
        }
        if (want != open) {
            BulkheadDoorBlock.setOpen(level, pos, state, want);
        }
    }

    public AutomationMode automation() {
        return automation;
    }

    /** 手动开关的节流：同一次按住右键只认第一次。 */
    public boolean tryManualUse(long gameTime) {
        if (gameTime - lastManualUseTick < 10L) {
            return false;
        }
        lastManualUseTick = gameTime;
        return true;
    }

    public void holdAfterManualOpen() {
        if (automation == AutomationMode.MOTION || automation == AutomationMode.WATERTIGHT) {
            motionHold = AutomationUtil.MOTION_HOLD_TICKS;
        }
    }

    /** 手动关门：清掉运动保持计时，否则自动模式会在同一 tick 把门重新打开。 */
    public void releaseManualHold() {
        motionHold = 0;
    }

    public void cycleAutomation() {
        automation = automation.next();
        motionHold = 0;
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Automation", automation.name());
        condition.save(tag, "Condition");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            automation = AutomationMode.valueOf(tag.getString("Automation"));
        } catch (IllegalArgumentException ignored) {
            automation = AutomationMode.MANUAL;
        }
        condition.load(tag, "Condition");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::predicate)
                .transitionLength(4));
    }

    private PlayState predicate(AnimationState<BulkheadDoorBlockEntity> state) {
        boolean open = getBlockState().getValue(BulkheadDoorBlock.OPEN);
        return state.setAndContinue(open ? OPEN : CLOSED);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
