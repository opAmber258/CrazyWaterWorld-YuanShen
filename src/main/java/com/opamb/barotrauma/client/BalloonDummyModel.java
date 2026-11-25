package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.BalloonDummyEntity;
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

/**
 * 训练气球模型：四层方盒拼出的像素气球 + 结 + 绳子。
 *
 * <p>模型坐标以气球球心为原点，+Y 指向绳子（模型向下），渲染器会把 +Y 旋到
 * “气球 → 台座”的方向。贴图 UV 分区见 {@code textures/entity/balloon_dummy.png}。</p>
 */
public class BalloonDummyModel<T extends BalloonDummyEntity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(BarotraumaMod.MOD_ID, "balloon_dummy"), "main");

    private final ModelPart root;

    public BalloonDummyModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 球体：10 px 直径，分四层做出像素圆；+Y 为绳子方向。
        // 层与层在 Y 上重叠 1 px（上层 -5..-2、下层 2..5），避免共面 z-fighting
        // 造成的闪烁——之前层与层正好贴在一起，接缝面会一直抖。
        root.addOrReplaceChild("top",
                CubeListBuilder.create().texOffs(40, 0)
                        .addBox(-3.0F, -5.0F, -3.0F, 6.0F, 3.0F, 6.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("middle",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0F, -3.0F, -5.0F, 10.0F, 6.0F, 10.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("bottom",
                CubeListBuilder.create().texOffs(40, 9)
                        .addBox(-3.0F, 2.0F, -3.0F, 6.0F, 3.0F, 6.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("knot",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-1.0F, 4.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("rope",
                CubeListBuilder.create().texOffs(8, 16)
                        // 1 px 粗并居中：比结细一圈，避免和台座喷嘴的侧面共面 z-fighting。
                        .addBox(-0.5F, 5.0F, -0.5F, 1.0F, 9.0F, 1.0F),
                PartPose.ZERO);

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
        // 气球是静态的；朝向由渲染器按台座 FACING 旋转。
    }
}
