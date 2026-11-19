package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.FreeAimTurretBlock;
import com.opamb.barotrauma.block.TrackingTurretBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 炮塔三层渲染：
 * <ul>
 *   <li>固定底座走方块模型（{@code models/block/turret*.json}）；</li>
 *   <li>水平旋转基座在这里按 yaw 旋转；</li>
 *   <li>炮管在基座基础上再按 pitch 俯仰，并沿炮轴做后坐位移。</li>
 * </ul>
 *
 * <p>三部分共用 512×256 的 8×4 瓦片图集：每个 cube 独占一块 64×64 瓦片，
 * 瓦片内再分顶面/Z 面/X 面/端面四个象限；方块模型画固定底座，BER 画旋转基座和炮管。</p>
 *
 * <p>旋转顺序：先绕世界 Y 轴偏航，再绕局部 X 轴俯仰；局部 +Z 是炮口方向
 * （yaw=0 指南，和 MC 的 yaw 约定一致）。开火后沿 -Z 后退一小段再回位。</p>
 */
public final class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity> {

    private static final ResourceLocation ATLAS =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas.png");
    private static final ResourceLocation ATLAS_FREE_AIM =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas_freeaim.png");
    private static final ResourceLocation ATLAS_TRACKING =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas_tracking.png");

    /**
     * 图集是 512×256 的 8×4 瓦片表，每个 cube 一块 64×64 瓦片；瓦片内再分四个
     * 32×32 象限：顶面 / Z 面 / X 面 / 端面。这里按瓦片号算每个面的 UV。
     */
    private static final float ATLAS_W = 512.0F;
    private static final float ATLAS_H = 256.0F;
    private static final float TILE = 64.0F;
    private static final int TILES_PER_ROW = 8;

    /** 模型单位 → 格，并以 (8,12,8) 为旋转轴心。 */
    private static final float PX = 1.0F / 16.0F;

    /** {x1,y1,z1,x2,y2,z2,tile}；同一组共用一个淡出透明度。 */
    private static final float[][] TURNTABLE_BOXES = {
            {2.8F, 5.0F, 2.8F, 13.2F, 7.0F, 13.2F, 7},
            {5.5F, 7.0F, 5.5F, 10.5F, 9.5F, 10.5F, 8},
            {2.5F, 8.0F, 5.5F, 4.5F, 14.5F, 10.0F, 9},
            {11.5F, 8.0F, 5.5F, 13.5F, 14.5F, 10.0F, 9},
            {1.5F, 10.5F, 6.5F, 3.5F, 13.5F, 9.5F, 10},
            {12.5F, 10.5F, 6.5F, 14.5F, 13.5F, 9.5F, 10},
            {5.5F, 7.5F, 2.5F, 10.5F, 10.5F, 5.5F, 11},
            {4.2F, 8.5F, 6.5F, 5.2F, 12.5F, 7.5F, 12},
            {10.8F, 8.5F, 6.5F, 11.8F, 12.5F, 7.5F, 12},
            {7.0F, 13.5F, 6.0F, 9.0F, 15.0F, 8.0F, 13},
    };

    private static final float[][] BARREL_BOXES = {
            {4.5F, 9.0F, 1.5F, 11.5F, 15.0F, 8.5F, 14},
            {4.8F, 9.3F, 0.8F, 11.2F, 14.7F, 1.8F, 15},
            {5.5F, 9.5F, 8.0F, 10.5F, 14.5F, 12.0F, 16},
            {6.0F, 10.0F, 11.5F, 10.0F, 14.0F, 23.0F, 17},
            {5.2F, 9.4F, 13.5F, 10.8F, 14.6F, 15.0F, 18},
            {5.2F, 9.4F, 19.5F, 10.8F, 14.6F, 21.0F, 18},
            {5.0F, 9.5F, 22.5F, 11.0F, 14.5F, 26.24F, 19},
    };

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

        BlockState state = turret.getBlockState();
        ResourceLocation atlas = atlasFor(state.getBlock());
        VertexConsumer cutout = buffer.getBuffer(RenderType.entityCutoutNoCull(atlas));
        VertexConsumer translucent = buffer.getBuffer(RenderType.entityTranslucent(atlas));
        Vec3 pivot = TurretBlockEntity.pivotOffset(state);
        Vec3 pivotWorld = new Vec3(turret.getBlockPos().getX() + pivot.x,
                turret.getBlockPos().getY() + pivot.y,
                turret.getBlockPos().getZ() + pivot.z);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // 整组共用一个透明度：相机贴近炮管任何一段，整根炮管一起淡出，
        // 不会出现“末端消失、前端还挡着”的拼块感。
        float turntableAlpha = groupAlpha(state, pivotWorld, camera, yaw, 0.0F,
                TURNTABLE_BOXES, false);
        float barrelAlpha = groupAlpha(state, pivotWorld, camera, yaw, pitch,
                BARREL_BOXES, true);

        // 转盘轴始终是世界 Y，和落地炮塔共用同一套 yaw/pitch。
        pose.pushPose();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        turntable(pose, cutout, translucent, light, overlay, turntableAlpha);
        pose.popPose();

        // 炮管：yaw + pitch，后坐沿炮轴向后（局部 -Z）。
        pose.pushPose();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0.0D, 0.0D, -recoil);
        barrel(pose, cutout, translucent, light, overlay, barrelAlpha);
        pose.popPose();
    }

    private static ResourceLocation atlasFor(Block block) {
        if (block instanceof FreeAimTurretBlock) {
            return ATLAS_FREE_AIM;
        }
        if (block instanceof TrackingTurretBlock) {
            return ATLAS_TRACKING;
        }
        return ATLAS;
    }

    /** 水平旋转基座：转盘、轮毂、两侧叉臂、耳轴、供弹箱、液压杆和瞄具。 */
    private static void turntable(PoseStack pose, VertexConsumer cutout, VertexConsumer translucent,
                                  int light, int overlay, float alpha) {
        for (float[] box : TURNTABLE_BOXES) {
            drawBox(pose, cutout, translucent, light, overlay, alpha,
                    box[0], box[1], box[2], box[3], box[4], box[5], (int) box[6]);
        }
    }

    /** 炮管：炮尾、尾盖、套筒、主管、两道加强环、炮口制退器和顶部导轨。 */
    private static void barrel(PoseStack pose, VertexConsumer cutout, VertexConsumer translucent,
                               int light, int overlay, float alpha) {
        for (float[] box : BARREL_BOXES) {
            drawBox(pose, cutout, translucent, light, overlay, alpha,
                    box[0], box[1], box[2], box[3], box[4], box[5], (int) box[6]);
        }
    }

    /** 一组 cube 的最小 AABB 距离 → 整组共用的透明度。 */
    private static float groupAlpha(BlockState state, Vec3 pivotWorld, Vec3 camera,
                                    float yaw, float pitch, float[][] boxes, boolean pitched) {
        Vec3 relative = camera.subtract(pivotWorld);
        Vec3 base = rotateY(relative, yaw);
        if (pitched) {
            base = rotateX(base, -pitch);
        }
        double lx = base.x * 16.0D + 8.0D;
        double ly = base.y * 16.0D + 12.0D;
        double lz = base.z * 16.0D + 8.0D;
        double nearest = Double.MAX_VALUE;
        for (float[] box : boxes) {
            double dx = Math.max(Math.max(box[0] - lx, 0.0D), lx - box[3]);
            double dy = Math.max(Math.max(box[1] - ly, 0.0D), ly - box[4]);
            double dz = Math.max(Math.max(box[2] - lz, 0.0D), lz - box[5]);
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz) / 16.0D);
        }
        return (float) Mth.clamp((nearest - 0.01D) / 0.10D, 0.0D, 1.0D);
    }

    /**
     * 近处淡出：按 cube 中心到相机的距离（扣掉半个对角线）平滑透明。
     * 距离 <0.01 格完全消失，>0.11 格恢复不透明；只影响 BER 的旋转部件，
     * 固定底座仍由方块模型绘制。
     */
    private static void drawBox(PoseStack pose, VertexConsumer cutout, VertexConsumer translucent,
                                int light, int overlay, float alpha,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                int tile) {
        if (alpha <= 0.01F) {
            return;
        }
        boxPx(pose, alpha >= 0.99F ? cutout : translucent, light, overlay,
                x1, y1, z1, x2, y2, z2, tile, alpha);
    }

    private static Vec3 rotateY(Vec3 v, float degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    private static Vec3 rotateX(Vec3 v, float degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
    }

    /**
     * 模型像素坐标 → 以 (8,12,8) 为轴心的局部格坐标，并按瓦片号取 UV。
     * 每块 64×64 瓦片分四个 32×32 象限：顶面 / Z 面 / X 面 / 端面。
     */
    private static void boxPx(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              int tile, float alpha) {
        float tx = (tile % TILES_PER_ROW) * TILE / ATLAS_W;
        float ty = (tile / TILES_PER_ROW) * TILE / ATLAS_H;
        float du = TILE / ATLAS_W;
        float dv = TILE / ATLAS_H;
        float qu = 32.0F / ATLAS_W;
        float qv = 32.0F / ATLAS_H;
        float[] top = {tx, ty, tx + qu, ty + qv};
        float[] zSide = {tx + qu, ty, tx + du, ty + qv};
        float[] xSide = {tx, ty + qv, tx + qu, ty + dv};

        float lx1 = (x1 - 8.0F) * PX;
        float ly1 = (y1 - 12.0F) * PX;
        float lz1 = (z1 - 8.0F) * PX;
        float lx2 = (x2 - 8.0F) * PX;
        float ly2 = (y2 - 12.0F) * PX;
        float lz2 = (z2 - 8.0F) * PX;

        // 北 (-Z) / 南 (+Z)
        quad(pose, vertices, light, overlay, lx2, ly1, lz1, lx1, ly1, lz1,
                lx1, ly2, lz1, lx2, ly2, lz1, zSide, 0, 0, -1, alpha);
        quad(pose, vertices, light, overlay, lx1, ly1, lz2, lx2, ly1, lz2,
                lx2, ly2, lz2, lx1, ly2, lz2, zSide, 0, 0, 1, alpha);
        // 西 (-X) / 东 (+X)
        quad(pose, vertices, light, overlay, lx1, ly1, lz1, lx1, ly1, lz2,
                lx1, ly2, lz2, lx1, ly2, lz1, xSide, -1, 0, 0, alpha);
        quad(pose, vertices, light, overlay, lx2, ly1, lz2, lx2, ly1, lz1,
                lx2, ly2, lz1, lx2, ly2, lz2, xSide, 1, 0, 0, alpha);
        // 下 (-Y) / 上 (+Y)
        quad(pose, vertices, light, overlay, lx1, ly1, lz2, lx2, ly1, lz2,
                lx2, ly1, lz1, lx1, ly1, lz1, top, 0, -1, 0, alpha);
        quad(pose, vertices, light, overlay, lx1, ly2, lz1, lx2, ly2, lz1,
                lx2, ly2, lz2, lx1, ly2, lz2, top, 0, 1, 0, alpha);
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float[] uv, float nx, float ny, float nz, float alpha) {
        vertex(pose, vertices, light, overlay, ax, ay, az, uv[0], uv[1], nx, ny, nz, alpha);
        vertex(pose, vertices, light, overlay, bx, by, bz, uv[2], uv[1], nx, ny, nz, alpha);
        vertex(pose, vertices, light, overlay, cx, cy, cz, uv[2], uv[3], nx, ny, nz, alpha);
        vertex(pose, vertices, light, overlay, dx, dy, dz, uv[0], uv[3], nx, ny, nz, alpha);
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, float alpha) {
        vertices.vertex(pose.last().pose(), x, y, z)
                .color(255, 255, 255, (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F))
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.last().normal(), nx, ny, nz)
                .endVertex();
    }
}
