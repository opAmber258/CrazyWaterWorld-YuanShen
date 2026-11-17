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
            boolean near = AutomationUtil.playerNear(server, ship, cell, 3.0D);
            if (near) {
                be.motionHold = 40;
            } else if (be.motionHold > 0) {
                be.motionHold--;
            }
            want = near || be.motionHold > 0;
        } else if (be.automation == AutomationMode.WATERTIGHT) {
            // 门占上下两格：任一半通海就不能自动开，水量也取两格邻域最大值。
            boolean toSea = AutomationUtil.opensToSea(ship, cell)
                    || AutomationUtil.opensToSea(ship, cell.above());
            if (toSea) {
                // 外海侧的门：舱内再干也不能自动开，开门就是进水。
                want = false;
            } else {
                double water = Math.max(AutomationUtil.maxWaterFraction(ship, cell),
                        AutomationUtil.maxWaterFraction(ship, cell.above()));
                if (water > 0.3D) {
                    want = false;
                } else if (water < 0.1D) {
                    want = true;
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
