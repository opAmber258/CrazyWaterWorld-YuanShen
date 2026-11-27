package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 接线盒渲染器。资源：{@code geo/block/junction_box.geo.json}、
 * {@code textures/block/junction_box.png}。候选 C 正面朝北，交由 GeckoLib 按 FACING 旋转。
 */
public class JunctionBoxRenderer extends GeoBlockRenderer<JunctionBoxBlockEntity> {

    public JunctionBoxRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "junction_box")));
    }
}
