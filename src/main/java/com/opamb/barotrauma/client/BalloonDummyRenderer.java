package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.opamb.barotrauma.entity.BalloonDummyEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 气球渲染：把模型旋转到“绳子指向台座”的姿态，再按台座 FACING 摆到相邻格。
 *
 * <p>模型顶点在 {@code ModelPart.Cube} 编译时已经除以 16，这里**不能**再
 * {@code scale(0.0625)}——那会把气球缩成 1/16 的一个点。</p>
 *
 * <p>动画：空闲时绕绳子固定端小幅摆动；死亡时先胀大再缩没（20 tick），
 * 配合实体 {@code die()} 里的 POOF 粒子和破裂声。</p>
 */
public class BalloonDummyRenderer<T extends BalloonDummyEntity> extends EntityRenderer<T> {

    /** 模型绳子末端在局部 +Y 方向的距离（14 px = 0.875 格），摆动支点。 */
    private static final double ROPE_PIVOT = 0.875D;

    private final BalloonDummyModel<T> model;
    private final ResourceLocation texture;

    public BalloonDummyRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context);
        this.model = new BalloonDummyModel<>(context.bakeLayer(BalloonDummyModel.LAYER));
        this.texture = texture;
        // 气球悬空，不投影；默认阴影会落在实体原点（碰撞箱底面）上，看起来是错的。
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Direction facing = entity.stationFacing();
        // 模型 +Y 是绳子方向；绳子要指向台座，即 FACING 的反方向。
        Vector3f toStation = new Vector3f(
                -facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
        Quaternionf rotation = new Quaternionf()
                .rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), toStation);

        float age = entity.tickCount + partialTick;

        poseStack.pushPose();
        // 实体原点在碰撞箱底面，球心在它上方 HITBOX_RADIUS 格。
        poseStack.translate(0.0D, BalloonDummyEntity.HITBOX_RADIUS, 0.0D);
        poseStack.mulPose(rotation);

        // 空闲：绕绳子末端轻摆（约 10.5 s / 15.7 s 一个来回）。
        poseStack.translate(0.0D, ROPE_PIVOT, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.03F) * 3.5F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.cos(age * 0.02F) * 2.5F));
        poseStack.translate(0.0D, -ROPE_PIVOT, 0.0D);

        // 死亡：前 20% 胀到 1.3 倍，再缩到 0；同时转半圈。
        // 注意必须用 deathTime > 0 判断，不能直接看 death：活着的实体 deathTime=0，
        // 但 partialTick 每帧 0..1，会把每帧都当成死亡动画（20 Hz 抖动）。
        if (entity.deathTime > 0) {
            float death = entity.deathTime + partialTick;
            float progress = Mth.clamp(death / 20.0F, 0.0F, 1.0F);
            float scale = progress < 0.2F
                    ? 1.0F + progress * 1.5F
                    : Math.max(0.0F, 1.3F * (1.0F - (progress - 0.2F) / 0.8F));
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(progress * 180.0F));
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(this.texture));
        int light = entity.isSelfIlluminated() ? LightTexture.FULL_BRIGHT : packedLight;
        this.model.renderToBuffer(poseStack, consumer, light,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
