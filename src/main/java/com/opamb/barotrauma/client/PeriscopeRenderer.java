package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.PeriscopeBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** 潜望镜渲染器：{@code geo/block/periscope.geo.json}，单张 128×64 贴图。 */
public class PeriscopeRenderer extends GeoBlockRenderer<PeriscopeBlockEntity> {

    public PeriscopeRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "periscope")));
    }

    @Override
    public ResourceLocation getTextureLocation(PeriscopeBlockEntity animatable) {
        return new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/periscope.png");
    }
}
