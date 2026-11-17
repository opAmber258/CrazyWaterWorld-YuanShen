package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.JunctionBoxBlock;
import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 接线盒渲染器。资源：{@code geo/block/junction_box.geo.json}、
 * {@code textures/block/junction_box.png}。模型正面 +Z，按方块 FACING 旋转。
 */
public class JunctionBoxRenderer extends GeoBlockRenderer<JunctionBoxBlockEntity> {

    public JunctionBoxRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "junction_box")));
    }

    @Override
    protected Direction getFacing(JunctionBoxBlockEntity animatable) {
        // geo 的正面是 +Z（south），GeckoLib 默认把模型正面当 north；
        // 取反抵消 180°，方块 FACING=south 时模型才真正朝南。
        return animatable.getBlockState().getValue(JunctionBoxBlock.FACING).getOpposite();
    }
}
