package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opamb.barotrauma.equipment.DivingSuitState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 穿潜水服时不再画原版玩家模型。
 *
 * <p>潜水服是整件外壳（{@code DivingSuitLayer} 单独一层画在身体之后）。
 * 原版皮肤/四肢继续渲染会和外壳互相穿插：皮肤从护腕、靴口、腋下这些缝里
 * 顶出来，看起来像"人和潜水服卡在一起"。原作的潜水服把人完全包住，
 * 所以这里在 {@link LivingEntityRenderer#render} 提交身体模型时直接跳过。</p>
 *
 * <p>透明潜水服例外：面罩是半透明的，要能看见里面的脸，所以只跳过
 * 身体/四肢，保留头和皮肤外层（{@code hat}）。</p>
 *
 * <p>只跳过**身体网格**：名字牌、手持物、披风、其它模组的 RenderLayer 以及
 * 潜水服层都照常渲染；第一人称手臂走 {@code ItemInHandRenderer}，不受影响。</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void barotrauma$skipBodyUnderDivingSuit(EntityModel<?> model, PoseStack poseStack,
                                                    VertexConsumer consumer, int packedLight, int packedOverlay,
                                                    float red, float green, float blue, float alpha,
                                                    LivingEntity entity, float entityYaw, float partialTick,
                                                    PoseStack outerPoseStack, MultiBufferSource buffer,
                                                    int outerPackedLight) {
        if (entity instanceof Player player
                && DivingSuitState.of(player).barotrauma$wearingDivingSuit()) {
            if (DivingSuitState.of(player).barotrauma$wearingGlassDivingSuit()
                    && model instanceof PlayerModel<?> playerModel) {
                barotrauma$renderHeadOnly(playerModel, poseStack, consumer, packedLight, packedOverlay,
                        red, green, blue, alpha);
            }
            return;
        }
        model.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** 只画头和皮肤外层，其余部件临时隐藏（透明面罩要透出脸）。 */
    private static void barotrauma$renderHeadOnly(PlayerModel<?> model, PoseStack poseStack,
                                                  VertexConsumer consumer, int packedLight, int packedOverlay,
                                                  float red, float green, float blue, float alpha) {
        ModelPart[] hidden = {
                model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg,
                model.jacket, model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants
        };
        boolean[] wasVisible = new boolean[hidden.length];
        for (int i = 0; i < hidden.length; i++) {
            wasVisible[i] = hidden[i].visible;
            hidden[i].visible = false;
        }
        try {
            model.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
        } finally {
            for (int i = 0; i < hidden.length; i++) {
                hidden[i].visible = wasVisible[i];
            }
        }
    }
}
