package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.DivingSuitItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 潜水服护甲几何/贴图（美术源：{@code art/models/diving_suit_20261112}，人工微调版）。
 *
 * <p>模型是 Blockbench 的 Bedrock Entity 1.12.0 导出，8 根骨骼分别对应
 * 头/身/双臂/双腿/双靴；GeckoLib 的 {@code GeoArmorRenderer} 按骨骼名
 * 跟随玩家模型。没有动画轨道，走动由护甲渲染器同步玩家骨骼姿态。</p>
 */
public final class DivingSuitModel extends GeoModel<DivingSuitItem> {

    private static final ResourceLocation GEO =
            new ResourceLocation(BarotraumaMod.MOD_ID, "geo/armor/diving_suit.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/models/armor/diving_suit_geckolib.png");
    /** 空动画表：模型没有动画轨道，但给 GeckoLib 一个可加载的资源，避免 null 资源键。 */
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(BarotraumaMod.MOD_ID, "animations/armor/diving_suit.animation.json");

    @Override
    public ResourceLocation getModelResource(DivingSuitItem animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(DivingSuitItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DivingSuitItem animatable) {
        return ANIMATION;
    }
}
