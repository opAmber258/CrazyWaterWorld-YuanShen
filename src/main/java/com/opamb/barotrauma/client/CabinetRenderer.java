package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 钢柜 / 货架 / 潜水服柜 / 氧气瓶架渲染器。
 *
 * <p>五种容器共用同一个方块实体类型，模型/贴图/空动画按
 * {@link CabinetBlock.Kind#id()} 动态选择。模型正面朝 +Z，渲染器把
 * {@link CabinetBlock#FACING} 取反后交给 GeckoLib（和反应堆一致）。</p>
 */
public class CabinetRenderer extends GeoBlockRenderer<CabinetBlockEntity> {

    private static final class KindModel extends DefaultedBlockGeoModel<CabinetBlockEntity> {
        KindModel() {
            super(new ResourceLocation(BarotraumaMod.MOD_ID, "cabinet"));
        }

        @Override
        public ResourceLocation getModelResource(CabinetBlockEntity animatable) {
            return new ResourceLocation(BarotraumaMod.MOD_ID,
                    "geo/block/" + animatable.kind().id() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(CabinetBlockEntity animatable) {
            return new ResourceLocation(BarotraumaMod.MOD_ID,
                    "textures/block/" + animatable.kind().id() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(CabinetBlockEntity animatable) {
            return new ResourceLocation(BarotraumaMod.MOD_ID,
                    "animations/block/" + animatable.kind().id() + ".animation.json");
        }

        @Override
        public RenderType getRenderType(CabinetBlockEntity animatable, ResourceLocation texture) {
            // 静态柜体：剔除背面，镜头贴近时不会看到内壁。
            return RenderType.entityCutout(texture);
        }
    }

    public CabinetRenderer() {
        super(new KindModel());
    }

    @Override
    protected Direction getFacing(CabinetBlockEntity animatable) {
        return animatable.getBlockState().getValue(CabinetBlock.FACING).getOpposite();
    }
}
