package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.EnumMap;
import java.util.Map;

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

    private static final Map<CabinetBlock.Kind, Boolean> ASSETS_READY = new EnumMap<>(CabinetBlock.Kind.class);
    private static Map<ResourceLocation, BakedGeoModel> bakedModels;

    private static final class KindModel extends DefaultedBlockGeoModel<CabinetBlockEntity> {
        KindModel() {
            super(new ResourceLocation(BarotraumaMod.MOD_ID, "cabinet"));
        }

        /** 氧气瓶架/潜水服站尚无独立 geo，复用中型钢柜避免 GeckoLib 找不到模型崩溃。 */
        private static String assetId(CabinetBlockEntity animatable) {
            return switch (animatable.kind()) {
                case OXYGEN_TANK_SHELF, DIVING_SUIT_STATION -> "medium_steel_cabinet";
                default -> animatable.kind().id();
            };
        }

        @Override
        public ResourceLocation getModelResource(CabinetBlockEntity animatable) {
            return new ResourceLocation(BarotraumaMod.MOD_ID,
                    "geo/block/" + assetId(animatable) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(CabinetBlockEntity animatable) {
            return new ResourceLocation(BarotraumaMod.MOD_ID,
                    "textures/block/" + assetId(animatable) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(CabinetBlockEntity animatable) {
            return new ResourceLocation(BarotraumaMod.MOD_ID,
                    "animations/block/" + assetId(animatable) + ".animation.json");
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
     * GeckoLib 4.8.4 的 {@code GeoBlockRenderer.render} 擦除后固定是 {@code BlockEntity}，
     * 子类没法覆盖（会报名称冲突），所以挡在 {@code defaultRender} 这一层：
     * 它在 {@code GeoBlockRenderer.render} 里是虚调用，缺资源时可以在取模型前画替代外形。
     */
    @Override
    public void defaultRender(PoseStack poseStack, CabinetBlockEntity animatable, MultiBufferSource bufferSource,
                              RenderType renderType, VertexConsumer buffer, float partialTick, float red,
                              int packedLight) {
        if (!assetsReady(animatable)) {
            renderFallback(poseStack, bufferSource, animatable, packedLight);
            return;
        }
        super.defaultRender(poseStack, animatable, bufferSource, renderType, buffer, partialTick, red, packedLight);
    }

    private boolean assetsReady(CabinetBlockEntity animatable) {
        var current = GeckoLibCache.getBakedModels();
        if (current != bakedModels) {
            ASSETS_READY.clear();
            bakedModels = current;
        }
        var kind = animatable.kind();
        Boolean cached = ASSETS_READY.get(kind);
        if (cached != null) {
            return cached;
        }
        var resources = Minecraft.getInstance().getResourceManager();
        boolean ready = current.containsKey(getGeoModel().getModelResource(animatable))
                && resources.getResource(getGeoModel().getTextureResource(animatable)).isPresent();
        if (!ready) {
            BarotraumaMod.LOGGER.warn("[cabinet] {} 缺可用模型或贴图，使用备用外形", kind.id());
        }
        ASSETS_READY.put(kind, ready);
        return ready;
    }

    private static void renderFallback(PoseStack pose, MultiBufferSource buffers, CabinetBlockEntity cabinet, int light) {
        var kind = cabinet.kind();
        double depth = kind.depth() / 16.0;
        // Match CabinetBlock.shape: full width, with the back flush against the wall.
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-cabinet.getBlockState().getValue(CabinetBlock.FACING).toYRot()));
        pose.translate(-0.5, 0, -0.5);
        if (kind == CabinetBlock.Kind.OXYGEN_TANK_SHELF || kind == CabinetBlock.Kind.DIVING_SUIT_STATION) {
            fallbackPiece(pose, buffers, light, Blocks.IRON_BLOCK, 0, 0, 0, 1, 0.125, depth);
            fallbackPiece(pose, buffers, light, Blocks.IRON_BLOCK, 0, 0.125, 0, 1, 0.61, 0.07);
            for (int i = 0; i < 3; i++) {
                double x = 0.125 + i * 0.29;
                fallbackPiece(pose, buffers, light, Blocks.GRAY_CONCRETE, x, 0.125, 0.08, 0.17, 0.07, 0.25);
                int slot = kind == CabinetBlock.Kind.DIVING_SUIT_STATION ? i + 1 : i;
                if (cabinet.getItem(slot).isEmpty()) continue;
                fallbackPiece(pose, buffers, light, Blocks.LIGHT_BLUE_CONCRETE, x, 0.196, 0.082, 0.17, 0.48, 0.235);
                fallbackPiece(pose, buffers, light, Blocks.IRON_BLOCK, x + 0.04, 0.676, 0.13, 0.09, 0.11, 0.12);
            }
        }
        if (kind != CabinetBlock.Kind.OXYGEN_TANK_SHELF) {
            double base = kind == CabinetBlock.Kind.DIVING_SUIT_STATION ? 13.25 / 16.0 : 0.0;
            fallbackPiece(pose, buffers, light, Blocks.IRON_BLOCK, 0, base, 0, 1, kind.height() / 16.0 - base, depth);
            fallbackPiece(pose, buffers, light, Blocks.GRAY_CONCRETE, 0.14, base + 0.14, depth, 0.72,
                    kind.height() / 16.0 - base - 0.28, 0.012);
        }
        pose.popPose();
    }

    private static void fallbackPiece(PoseStack pose, MultiBufferSource buffers, int light, Block block,
                                      double x, double y, double z, double width, double height, double depth) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale((float) width, (float) height, (float) depth);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.popPose();
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
