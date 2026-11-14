package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.AutomationMode;
import com.opamb.barotrauma.ship.AutomationUtil;
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
 * 舱口的 GeckoLib 方块实体（只挂在中间段）。
 *
 * <p>动画完全由 blockstate 的 {@code open} 驱动：打开播 {@code open} 并停在最后一帧，
 * 关闭切到 {@code closed}；控制器自带 4 tick 过渡，所以关门不会瞬移。</p>
 */
public class HatchBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSED = RawAnimation.begin().thenPlayAndHold("closed");

    private AutomationMode automation = AutomationMode.MANUAL;
    private int motionHold;

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
        BlockPos cell = VsState.active() ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        boolean open = state.getValue(HatchBlock.OPEN);
        boolean want = open;
        if (be.automation == AutomationMode.MOTION) {
            boolean near = AutomationUtil.playerNear(server, cell, 3.0D);
            if (near) {
                be.motionHold = 40;
            } else if (be.motionHold > 0) {
                be.motionHold--;
            }
            want = near || be.motionHold > 0;
        } else if (be.automation == AutomationMode.WATERTIGHT) {
            double water = AutomationUtil.maxWaterFraction(ship, cell);
            if (water > 0.3D) {
                want = false;
            } else if (water < 0.1D) {
                want = true;
            }
        }
        if (want != open) {
            HatchBlock.setOpen(level, pos, state, want);
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Automation", automation.name());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            automation = AutomationMode.valueOf(tag.getString("Automation"));
        } catch (IllegalArgumentException ignored) {
            automation = AutomationMode.MANUAL;
        }
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
