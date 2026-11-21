package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.PeriscopeStandBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** 潜望镜底座渲染器：{@code geo/block/periscope_stand.geo.json}。 */
public class PeriscopeStandRenderer extends GeoBlockRenderer<PeriscopeStandBlockEntity> {

    public PeriscopeStandRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "periscope_stand")));
    }

    @Override
    public ResourceLocation getTextureLocation(PeriscopeStandBlockEntity animatable) {
        return new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/periscope_stand.png");
    }
}
