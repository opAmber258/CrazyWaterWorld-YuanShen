package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 小型泵方块实体：状态（流量/自动控制）全部继承自 {@link DrainPumpBlockEntity}，
 * 只额外挂 GeckoLib，让小型泵用 {@code geo/block/small_pump.geo.json} 的体素模型渲染。
 *
 * <p>模型是静态的（原版小型泵也没有可动部件），所以没有动画控制器。</p>
 */
public class SmallPumpBlockEntity extends DrainPumpBlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SmallPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_PUMP.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：无动画控制器。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
