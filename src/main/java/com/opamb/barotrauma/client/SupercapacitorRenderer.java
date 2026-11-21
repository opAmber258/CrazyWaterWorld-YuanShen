package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.SupercapacitorBlock;
import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 超级电容器渲染器。资源：{@code geo/block/supercapacitor.geo.json}，
 * 贴图按 5 段电量屏切换（0 空 + 1..5 段）。
 */
public class SupercapacitorRenderer extends GeoBlockRenderer<SupercapacitorBlockEntity> {

    private static final ResourceLocation[] STATES = {
            tex("supercapacitor"),
            tex("supercapacitor_1"),
            tex("supercapacitor_2"),
            tex("supercapacitor_3"),
            tex("supercapacitor_4"),
            tex("supercapacitor_5"),
    };

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/" + name + ".png");
    }

    public SupercapacitorRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "supercapacitor")));
    }

    @Override
    public ResourceLocation getTextureLocation(SupercapacitorBlockEntity animatable) {
        int level = animatable.getBlockState().getValue(SupercapacitorBlock.CHARGE);
        return STATES[Math.max(0, Math.min(STATES.length - 1, level))];
    }
}
