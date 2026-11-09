package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.ReactorBlock;
import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 反应堆渲染：模型做在 +Z（南），和压载泵一样把 FACING 取反再交给 GeckoLib。
 */
public class ReactorRenderer extends GeoBlockRenderer<ReactorBlockEntity> {

    public ReactorRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "reactor")));
    }

    @Override
    protected Direction getFacing(ReactorBlockEntity animatable) {
        return animatable.getBlockState().getValue(ReactorBlock.FACING).getOpposite();
    }
}
