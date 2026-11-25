package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.AdvancedTrainingDummyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 高级训练人偶渲染：复用普通版骨骼与动画，换深色装甲贴图，并横向加宽 12%。
 *
 * <p>只缩放 X/Z，不缩放 Y：人偶脚底在实体原点，拉高会把底座压进台面。</p>
 */
public class AdvancedTrainingDummyRenderer extends MobRenderer<AdvancedTrainingDummyEntity,
        TrainingDummyModel<AdvancedTrainingDummyEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            BarotraumaMod.MOD_ID, "textures/entity/advanced_training_dummy.png");

    /** 比普通版更敦实的横向比例。 */
    private static final float WIDTH_SCALE = 1.12F;

    public AdvancedTrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new TrainingDummyModel<>(context.bakeLayer(TrainingDummyModel.LAYER)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(AdvancedTrainingDummyEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(AdvancedTrainingDummyEntity entity, PoseStack poseStack, float partialTick) {
        float inflate = entity.spawnScale(partialTick);
        poseStack.scale(inflate * WIDTH_SCALE, inflate, inflate * WIDTH_SCALE);
        super.scale(entity, poseStack, partialTick);
    }

    /** 面板勾选“自发光”时，方块光/天空光都按 15 算，模型不再吃环境明暗。 */
    @Override
    protected int getBlockLightLevel(AdvancedTrainingDummyEntity entity, BlockPos pos) {
        return entity.isSelfIlluminated() ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    protected int getSkyLightLevel(AdvancedTrainingDummyEntity entity, BlockPos pos) {
        return entity.isSelfIlluminated() ? 15 : super.getSkyLightLevel(entity, pos);
    }
}
