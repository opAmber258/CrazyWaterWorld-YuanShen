package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.TrainingDummyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 临时训练人偶模型：稻草人身体 + 头 + 两条可摆动的木臂 + 固定立柱/底座。
 *
 * <p>本体 + 立柱共 24 px（1.5 格），放在 1 格高的台座上总高 2.5 格。
 * 贴图 64×64，UV 分区见 {@code textures/entity/training_dummy.png}；
 * 动画是程序化的轻微摇摆，不依赖 GeckoLib。</p>
 */
public class TrainingDummyModel<T extends TrainingDummyEntity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(BarotraumaMod.MOD_ID, "training_dummy"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart armLeft;
    private final ModelPart armRight;

    public TrainingDummyModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.armLeft = body.getChild("arm_left");
        this.armRight = body.getChild("arm_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-4.0F, -6.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                // 本体下移 8 px、立柱缩短到 4 px：总高 32 → 24 px（1.5 格），
                // 加上 1 格台座正好 2.5 格。
                PartPose.offset(0.0F, 14.0F, 0.0F));
        body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, -6.0F, 0.0F));
        body.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(24, 16)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-6.0F, -5.0F, 0.0F));
        body.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(6.0F, -5.0F, 0.0F));

        root.addOrReplaceChild("pole",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        root.addOrReplaceChild("base",
                CubeListBuilder.create().texOffs(8, 32)
                        .addBox(-4.0F, 0.0F, -4.0F, 8.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 22.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float sway = Mth.sin(ageInTicks * 0.08F) * 0.06F;
        this.body.zRot = sway;
        this.head.yRot = Mth.sin(ageInTicks * 0.05F) * 0.12F;
        this.armLeft.xRot = 0.15F + Mth.sin(ageInTicks * 0.09F) * 0.12F;
        this.armRight.xRot = 0.15F - Mth.sin(ageInTicks * 0.09F) * 0.12F;
    }
}
