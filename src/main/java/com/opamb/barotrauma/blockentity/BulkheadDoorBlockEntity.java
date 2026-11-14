package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.BulkheadDoorBlock;
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
 * 舱壁门的 GeckoLib 方块实体（只挂在最下面一段）。
 *
 * <p>和舱口一样，状态只读 blockstate 的 {@code open}；打开/关闭各一个动画，
 * 由控制器做过渡。模型本身是 3 格高，渲染器挂在最下面一段，向上覆盖两格。</p>
 */
public class BulkheadDoorBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");
    private static final RawAnimation CLOSED = RawAnimation.begin().thenPlayAndHold("closed");

    private AutomationMode automation = AutomationMode.MANUAL;
    private int motionHold;

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
        BlockPos cell = VsState.active() ? VsPilot.toAssembly(pos) : pos;
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        boolean open = state.getValue(BulkheadDoorBlock.OPEN);
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

    private PlayState predicate(AnimationState<BulkheadDoorBlockEntity> state) {
        boolean open = getBlockState().getValue(BulkheadDoorBlock.OPEN);
        return state.setAndContinue(open ? OPEN : CLOSED);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
