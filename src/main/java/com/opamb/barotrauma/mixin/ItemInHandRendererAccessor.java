package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 {@code ItemInHandRenderer.renderPlayerArm / renderItem}，双手持自定义渲染用。 */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {

    @Invoker("renderPlayerArm")
    void barotrauma$renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int light,
                                    float equipProgress, float swingProgress, HumanoidArm arm);

    @Invoker("renderItem")
    void barotrauma$renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                               boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int light);
}
