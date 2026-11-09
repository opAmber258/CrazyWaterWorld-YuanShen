package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.blockentity.SonarBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 老声纳台的 3D 点云全息（方块模型本身还是那个贴图立方体）。
 * 绘制细节在 {@link SonarHologram}，和导航终端共用。
 */
public class SonarRenderer implements BlockEntityRenderer<SonarBlockEntity> {

    public SonarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SonarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        SonarHologram.render(be.points(), be.getBlockPos(), 1.25F, pose, buffers,
                be.getLevel() == null ? 0L : be.getLevel().getGameTime());
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
