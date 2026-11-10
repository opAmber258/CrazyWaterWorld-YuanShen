package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.SmallPumpBlock;
import com.opamb.barotrauma.blockentity.SmallPumpBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 小型泵渲染器。
 *
 * <p>资源：{@code geo/block/small_pump.geo.json}、{@code textures/block/small_pump.png}。
 * 模型坐标以方块中心为原点、底面 y=0（和舱口同一套约定），
 * {@link GeoBlockRenderer} 会把模型移到方块中心。</p>
 *
 * <p>模型在 geo 里正面朝北；把方块的 {@code FACING} 交给 GeckoLib 的
 * {@link GeoBlockRenderer#getFacing}，由基类绕方块中心转 Y，
 * 和 {@link SmallPumpBlock} 的碰撞形状保持一致。</p>
 */
public class SmallPumpRenderer extends GeoBlockRenderer<SmallPumpBlockEntity> {

    public SmallPumpRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "small_pump")));
    }

    @Override
    protected Direction getFacing(SmallPumpBlockEntity animatable) {
        return animatable.getBlockState().getValue(SmallPumpBlock.FACING);
    }
}
