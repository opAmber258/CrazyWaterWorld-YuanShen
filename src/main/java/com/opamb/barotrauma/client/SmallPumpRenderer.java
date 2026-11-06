package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.SmallPumpBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 小型泵渲染器。
 *
 * <p>资源：{@code geo/block/small_pump.geo.json}、{@code textures/block/small_pump.png}。
 * 模型坐标以方块中心为原点、底面 y=0（和舱口同一套约定），
 * {@link GeoBlockRenderer} 会把模型移到方块中心。</p>
 */
public class SmallPumpRenderer extends GeoBlockRenderer<SmallPumpBlockEntity> {

    public SmallPumpRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "small_pump")));
    }
}
