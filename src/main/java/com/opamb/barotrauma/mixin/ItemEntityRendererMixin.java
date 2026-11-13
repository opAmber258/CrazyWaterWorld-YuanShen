package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.opamb.barotrauma.item.CrateItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 掉落物渲染成"躺在地上"：不浮、不转，平放在地面上（原作物品掉地就是躺着的）。
 *
 * <p>原版 {@code ItemEntityRenderer} 会上下浮动 + 绕 Y 轴慢慢转；这里整套接管：
 * 绕 X 轴 -90° 把物品放平，按实体 id 给一个固定朝向，3D 模型（箱子/方块）抬高
 * 半个厚度避免陷进地面。</p>
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void barotrauma$renderLyingFlat(ItemEntity entity, float entityYaw, float partialTick,
                                            PoseStack poseStack, MultiBufferSource bufferSource,
                                            int packedLight, CallbackInfo ci) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, entity.level(), null, entity.getId());
        float scale = stack.getItem() instanceof CrateItem ? 0.55F : 0.5F;
        // 平放后"高度"来自模型原来的 z 深度：按实际最低点贴地，别留缝/陷进去。
        float bottom = (modelMinZ(model) - 0.5F) * scale;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.02D - bottom, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees((entity.getId() & 3) * 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());
        poseStack.popPose();
        ci.cancel();
    }

    /** 模型在 z 轴上的最低点（物品模型坐标 0..1）；拿不到时按薄片处理。 */
    private static float modelMinZ(BakedModel model) {
        float min = Float.MAX_VALUE;
        var random = net.minecraft.util.RandomSource.create(0L);
        for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(null, null, random)) {
            int[] vertices = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                float z = Float.intBitsToFloat(vertices[i * 8 + 2]);
                if (z < min) {
                    min = z;
                }
            }
        }
        return min == Float.MAX_VALUE ? 0.5F : min;
    }
}
