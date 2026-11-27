package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.AutomationMode;
import com.opamb.barotrauma.ship.AutomationUtil;
import com.opamb.barotrauma.ship.ConditionComponent;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 舱口的 GeckoLib 方块实体（只挂在中间段）。
 *
 * <p>动画完全由 blockstate 的 {@code open} 驱动：打开播 {@code open} 并停在最后一帧，
 * 关闭切到 {@code closed}；控制器自带 4 tick 过渡，所以关门不会瞬移。</p>
 */
public class HatchBlockEntity extends BlockEntity implements GeoBlockEntity, RepairTarget {

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSED = RawAnimation.begin().thenPlayAndHold("closed");

    private AutomationMode automation = AutomationMode.MANUAL;
    private int motionHold;
    /** 最近一次手动右键的 tick：按住右键时原版每 4 tick 触发一次，必须节流。 */
    private boolean manuallyClosed;
    private long lastManualUseTick = -100L;
    /** 舱口不自然劣化（原作 Repairable 默认 0/s），只被外力打坏后修理。 */
    private final ConditionComponent condition =
            new ConditionComponent(100.0D, 0.0D, 0, 0, 0.0D, 0.8D);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HATCH.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  HatchBlockEntity be) {
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
        boolean open = state.getValue(HatchBlock.OPEN);
        boolean near = AutomationUtil.playerNear(server, ship, pos, state.getValue(HatchBlock.FACING), true);
        if (be.manuallyClosed) {
            if (near) return;
            be.manuallyClosed = false;
        }
        // The manual command takes precedence over the automatic mode for the hold interval.
        if (open && level.getGameTime() - be.lastManualUseTick < AutomationUtil.MOTION_HOLD_TICKS) return;
        boolean want = open;
        if (be.automation == AutomationMode.MOTION) {
            // 原作自动关舱口：运动探测器 + OrComponent TimeFrame=4
            // （Content/Items/Assemblies/AutoClosingHatch.xml，"nobody around for 4 seconds"）。
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
            if (near) {
                be.motionHold = AutomationUtil.MOTION_HOLD_TICKS;
            } else if (be.motionHold > 0) {
                be.motionHold--;
            }
            boolean motionWant = near || be.motionHold > 0;
            Direction longAxis = state.getValue(HatchBlock.FACING).getClockWise();
            boolean toSea = false;
            for (int offset = -1; offset <= 1; offset++) {
                toSea |= AutomationUtil.opensToSea(ship, cell.relative(longAxis, offset));
            }
            if (toSea) {
                // 任意一段开在海里：水密模式不能自动打开。
                be.motionHold = 0;
                want = false;
            } else {
                // 三格宽的舱口下面可能是两个舱：取左/中/右三段的最大水量，
                // 只查中间格会漏掉左右段下方的湿舱。
                double water = 0.0D;
                for (int offset = -1; offset <= 1; offset++) {
                    water = Math.max(water, AutomationUtil.maxWaterFraction(
                            ship, cell.relative(longAxis, offset)));
                }
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
            HatchBlock.setOpen(level, pos, state, want);
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
        manuallyClosed = false;
        if (automation == AutomationMode.MOTION || automation == AutomationMode.WATERTIGHT) {
            motionHold = AutomationUtil.MOTION_HOLD_TICKS;
        }
    }

    /** 手动关闭：清掉运动保持计时，否则自动模式会在同一 tick 把舱口重新打开。 */
    public void releaseManualHold() {
        manuallyClosed = true;
        motionHold = 0;
    }

    public void cycleAutomation() {
        automation = automation.next();
        manuallyClosed = false;
        lastManualUseTick = -100L;
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
    public int requiredSkill() {
        return 40;
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

    private PlayState predicate(AnimationState<HatchBlockEntity> state) {
        boolean open = getBlockState().getValue(HatchBlock.OPEN);
        return state.setAndContinue(open ? OPEN : CLOSED);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
