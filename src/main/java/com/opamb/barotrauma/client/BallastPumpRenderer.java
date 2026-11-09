package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BallastPumpBlock;
import com.opamb.barotrauma.blockentity.BallastPumpBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 压载泵渲染器。
 *
 * <p>资源：{@code geo/block/ballast_pump.geo.json}、{@code textures/block/ballast_pump.png}。
 * 模型是朝 +Z（南）做的，而 GeckoLib 的 {@code rotateBlock} 约定是"朝北 = 0°"，
 * 所以把 FACING 取反后再交给它：FACING=south（正面朝南）时不再旋转 180°，
 * 模型正面正好朝南；FACING=north 时旋转 180°，正面朝北。</p>
 */
public class BallastPumpRenderer extends GeoBlockRenderer<BallastPumpBlockEntity> {

    public BallastPumpRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "ballast_pump")));
    }

    @Override
    protected Direction getFacing(BallastPumpBlockEntity animatable) {
        return animatable.getBlockState().getValue(BallastPumpBlock.FACING).getOpposite();
    }
}
