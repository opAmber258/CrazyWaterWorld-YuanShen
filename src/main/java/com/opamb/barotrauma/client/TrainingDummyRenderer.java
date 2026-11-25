package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.TrainingDummyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** 训练人偶渲染：临时手写方块模型 + 生成时的充气放大。 */
public class TrainingDummyRenderer extends MobRenderer<TrainingDummyEntity,
        TrainingDummyModel<TrainingDummyEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            BarotraumaMod.MOD_ID, "textures/entity/training_dummy.png");

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new TrainingDummyModel<>(context.bakeLayer(TrainingDummyModel.LAYER)), 0.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummyEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(TrainingDummyEntity entity, PoseStack poseStack, float partialTick) {
        float scale = entity.spawnScale(partialTick);
        poseStack.scale(scale, scale, scale);
        super.scale(entity, poseStack, partialTick);
    }
}
