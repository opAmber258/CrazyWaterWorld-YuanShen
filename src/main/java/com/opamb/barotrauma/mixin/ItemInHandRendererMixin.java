package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 双手持物品的第一人称：整套手部渲染换成"两条手臂 + 中间一个物品"。
 *
 * <p>原版 {@code renderHandsWithItems} 只画一只手（另一只手要拿副手物品才画），
 * 这里在 HEAD 直接接管：左右手臂各画一条并向内收，箱子模型放在正前方中间，
 * 形成两只手抱着箱子的画面。普通物品完全不进这条路径。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void barotrauma$renderTwoHanded(float partialTicks, PoseStack poseStack,
                                            MultiBufferSource.BufferSource buffer, LocalPlayer player,
                                            int light, CallbackInfo ci) {
        ItemStack held = TwoHanded.heldBy(player);
        if (held.isEmpty()) {
            return;
        }
        // 主手还拿着别的东西：交给原版渲染（那件物品 + 副手箱子），别把它藏起来。
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        ItemInHandRendererAccessor accessor = (ItemInHandRendererAccessor) (Object) this;
        poseStack.pushPose();

        // 右臂 / 左臂：从屏幕两侧向中间、向上抬。
        this.barotrauma$drawArm(accessor, poseStack, buffer, light, HumanoidArm.RIGHT, -0.34F, -16F);
        this.barotrauma$drawArm(accessor, poseStack, buffer, light, HumanoidArm.LEFT, 0.34F, 16F);

        // 物品：屏幕中下、略往前，正对镜头。
        poseStack.pushPose();
        poseStack.translate(0.0F, -0.36F, -0.55F);
        poseStack.mulPose(Axis.XP.rotationDegrees(8.0F));
        accessor.barotrauma$renderItem(player, held, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                false, poseStack, buffer, light);
        poseStack.popPose();

        poseStack.popPose();
        // 原版方法末尾会 endBatch 提交手部顶点；这里提前 cancel，必须自己 flush。
        buffer.endBatch();
        ci.cancel();
    }

    private void barotrauma$drawArm(ItemInHandRendererAccessor accessor, PoseStack poseStack,
                                    MultiBufferSource.BufferSource buffer, int light,
                                    HumanoidArm arm, float shiftX, float yaw) {
        poseStack.pushPose();
        poseStack.translate(shiftX, 0.06F, -0.02F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-8.0F));
        accessor.barotrauma$renderPlayerArm(poseStack, buffer, light, 0.0F, 0.0F, arm);
        poseStack.popPose();
    }
}
