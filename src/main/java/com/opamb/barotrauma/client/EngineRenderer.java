package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.EngineBlock;
import com.opamb.barotrauma.blockentity.EngineBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** 引擎渲染：模型做在 +Z（南），FACING 取反后交给 GeckoLib。 */
public class EngineRenderer extends GeoBlockRenderer<EngineBlockEntity> {

    public EngineRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "engine")));
    }

    @Override
    protected Direction getFacing(EngineBlockEntity animatable) {
        return animatable.getBlockState().getValue(EngineBlock.FACING).getOpposite();
    }
}
