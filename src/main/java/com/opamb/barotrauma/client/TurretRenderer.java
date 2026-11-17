package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 炮塔炮管渲染：底座/炮座走方块模型，炮管在这里按同步的 yaw/pitch 旋转。
 *
 * <p>旋转顺序：先绕局部 X 轴俯仰，再绕世界 Y 轴偏航；局部 +Z 是炮口方向
 * （yaw=0 指南，和 MC 的 yaw 约定一致）。开火后按冷却剩余量做一小段后坐位移。</p>
 */
public final class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_barrel.png");

    public TurretRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TurretBlockEntity turret, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        float yaw;
        float pitch;
        if (TurretClient.isOperating(turret.getBlockPos())) {
            // 操作者用本地瞄准角：服务端状态包有 RTT 和 20Hz 采样，直接用会让炮管
            // 在准星下面一顿一顿地追（用户反馈的“抽搐”）。
            yaw = TurretClient.aimYaw();
            pitch = TurretClient.aimPitch();
        } else {
            yaw = Mth.rotLerp(partialTick, turret.prevYaw(), turret.yaw());
            pitch = Mth.lerp(partialTick, turret.prevPitch(), turret.pitch());
        }
        float recoil = turret.cooldown() > 0
                ? turret.cooldown() / (float) TurretBlockEntity.FIRE_COOLDOWN_TICKS * 0.10F
                : 0.0F;

        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pose.pushPose();
        pose.translate(0.5D, TurretBlockEntity.PIVOT_Y, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0.0D, 0.0D, recoil);

        // 炮尾（局部 +Z 是炮口方向，yaw=0 指南）
        box(pose, vertices, light, overlay, -0.22F, -0.19F, -0.36F, 0.22F, 0.19F, 0.20F);
        // 炮管
        box(pose, vertices, light, overlay, -0.12F, -0.12F, 0.18F, 0.12F, 0.12F, 1.02F);
        // 炮口制退器
        box(pose, vertices, light, overlay, -0.17F, -0.17F, 1.0F, 0.17F, 0.17F,
                (float) TurretBlockEntity.MUZZLE_LENGTH);

        pose.popPose();
    }

    /**
     * 六面长方体。{@code entityCutoutNoCull} 关了背面剔除，所以顶点顺序不重要；
     * 每个面都铺整张 16×16 贴图（金属噪点纹理，看不出重复）。
     */
    private static void box(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                            float x1, float y1, float z1, float x2, float y2, float z2) {
        // 北 (-Z)
        quad(pose, vertices, light, overlay, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1);
        // 南 (+Z)
        quad(pose, vertices, light, overlay, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
        // 西 (-X)
        quad(pose, vertices, light, overlay, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
        // 东 (+X)
        quad(pose, vertices, light, overlay, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
        // 下 (-Y)
        quad(pose, vertices, light, overlay, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, 0, -1, 0);
        // 上 (+Y)
        quad(pose, vertices, light, overlay, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, 0, 1, 0);
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz) {
        vertex(pose, vertices, light, overlay, ax, ay, az, 0.0F, 0.0F, nx, ny, nz);
        vertex(pose, vertices, light, overlay, bx, by, bz, 1.0F, 0.0F, nx, ny, nz);
        vertex(pose, vertices, light, overlay, cx, cy, cz, 1.0F, 1.0F, nx, ny, nz);
        vertex(pose, vertices, light, overlay, dx, dy, dz, 0.0F, 1.0F, nx, ny, nz);
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        vertices.vertex(pose.last().pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.last().normal(), nx, ny, nz)
                .endVertex();
    }
}
