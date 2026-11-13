package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 钢柜 / 货架 / 潜水服柜 / 氧气瓶架渲染器。
 *
 * <p>五种容器共用同一个方块实体类型，模型/贴图/空动画按
 * {@link CabinetBlock.Kind#id()} 动态选择。模型正面朝 +Z，渲染器把
 * {@link CabinetBlock#FACING} 取反后交给 GeckoLib（和反应堆一致）。</p>
 *
 * <p>箱子货架额外把存进去的货运箱当成物品模型画在四层隔板上。</p>
 */
public class CabinetRenderer extends GeoBlockRenderer<CabinetBlockEntity> {

    /**
     * 四层隔板的可用区间来自 {@link CabinetBlockEntity#CRATE_TIER_BOTTOM} /
     * {@link CabinetBlockEntity#CRATE_TIER_TOP}（服务端判定同一份数据）。
     */
    /** 箱子缩到隔板高度以内（模型 12px 高，格内净高 6.4px）；允许一点点穿模。 */
    private static final float CRATE_SCALE = 0.6F;
    /** 货物中心在模型深度上的位置（每层隔板 z 约 -7.2..-0.5）。 */
    private static final float CRATE_Z = -3.8F;

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
        addRenderLayer(new CrateShelfLayer(this));
    }

    @Override
    protected Direction getFacing(CabinetBlockEntity animatable) {
        return animatable.getBlockState().getValue(CabinetBlock.FACING).getOpposite();
    }

    /**
     * 货架上的货运箱。作为 render layer 画在柜体之后，pose 仍然停在
     * GeckoLib 的模型空间里（{@code translate(0.5, 0, 0.5)} + 朝向旋转），
     * 所以箱子坐标直接按 geo 的像素写。
     */
    private static final class CrateShelfLayer extends GeoRenderLayer<CabinetBlockEntity> {

        CrateShelfLayer(GeoRenderer<CabinetBlockEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, CabinetBlockEntity animatable, BakedGeoModel model,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            if (animatable.kind() != CabinetBlock.Kind.CRATE_SHELF || animatable.getLevel() == null) {
                return;
            }
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            int seed = (int) animatable.getBlockPos().asLong();
            for (int tier = 0; tier < CabinetBlockEntity.CRATE_TIERS; tier++) {
                ItemStack stack = animatable.getItem(tier);
                if (stack.isEmpty()) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(0.0D,
                        (CabinetBlockEntity.CRATE_TIER_BOTTOM[tier]
                                + CabinetBlockEntity.CRATE_TIER_TOP[tier]) * 0.5D / 16.0D,
                        CRATE_Z / 16.0D);
                poseStack.scale(CRATE_SCALE, CRATE_SCALE, CRATE_SCALE);
                // NONE = 不套 display 变换，物品模型保持 16px = 1 格的原始比例。
                itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, packedOverlay,
                        poseStack, bufferSource, animatable.getLevel(), seed + tier);
                poseStack.popPose();
            }
        }
    }
}
