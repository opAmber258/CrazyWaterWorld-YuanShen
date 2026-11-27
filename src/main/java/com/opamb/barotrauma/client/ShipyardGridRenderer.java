package com.opamb.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.network.ShipyardStatePacket;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** 编辑器世界网格与对称辅助面：单线单 Quad，主次刻度分级、原点中心轴与工作平面高度一致。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class ShipyardGridRenderer {

    private ShipyardGridRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !ShipyardClient.editorActive()) {
            return;
        }
        byte mirror = ShipyardClient.mirrorAxis();
        boolean drawGrid = ShipyardClient.gridMode() != ShipyardClient.GridMode.OFF;
        if (!drawGrid && mirror == ShipyardShapes.MIRROR_NONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        double lw = Mth.clamp(ShipyardClient.distance() * 0.0008D, 0.012D, 0.06D);
        ShipyardStatePacket state = ShipyardClient.state();

        if (drawGrid) {
            boolean global = ShipyardClient.gridMode() == ShipyardClient.GridMode.GLOBAL;
            int radius = global ? 128 : 32;
            int step = global ? 4 : 1;
            ShipyardClient.ViewMode view = ShipyardClient.viewMode();
            if (view == ShipyardClient.ViewMode.FRONT || view == ShipyardClient.ViewMode.BACK) {
                double z = Math.floor(ShipyardClient.focus().z) + 0.5D;
                int cx = Math.floorDiv(Mth.floor(ShipyardClient.focus().x), step) * step;
                int cy = Math.floorDiv(Mth.floor(ShipyardClient.focus().y), step) * step;
                drawFrontGrid(vertices, matrix, camera, cx, cy, z, radius, step, global, lw);
            } else if (view == ShipyardClient.ViewMode.SIDE || view == ShipyardClient.ViewMode.LEFT) {
                double x = Math.floor(ShipyardClient.focus().x) + 0.5D;
                int cy = Math.floorDiv(Mth.floor(ShipyardClient.focus().y), step) * step;
                int cz = Math.floorDiv(Mth.floor(ShipyardClient.focus().z), step) * step;
                drawSideGrid(vertices, matrix, camera, x, cy, cz, radius, step, global, lw);
            } else {
                BlockPos target = ShipyardClient.target();
                BlockPos center = target != null ? target : BlockPos.containing(ShipyardClient.focus());
                int centerX = Math.floorDiv(center.getX(), step) * step;
                int centerZ = Math.floorDiv(center.getZ(), step) * step;
                double y = ShipyardClient.workPlaneY() + 0.004D;
                drawHorizontalGrid(vertices, matrix, camera, centerX, centerZ, y, radius, step, global, lw);
                if (state != null) {
                    drawOriginAxes(vertices, matrix, camera, state, centerX, centerZ,
                            y + 0.003D, radius, lw * 1.7D);
                }
            }
        }

        if (mirror != ShipyardShapes.MIRROR_NONE && state != null) {
            drawMirrorPlane(vertices, matrix, camera, state, mirror, ShipyardClient.mirrorCoord(), lw * 1.8D);
        }

        buffers.endBatch(RenderType.debugQuads());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawHorizontalGrid(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                           int centerX, int centerZ, double y,
                                           int radius, int step, boolean global, double lw) {
        float baseR = global ? 0.52F : 0.62F;
        float baseG = global ? 0.72F : 0.82F;
        float baseB = global ? 0.88F : 1.00F;
        for (int i = -radius; i <= radius; i += step) {
            float f = fade(i, radius);
            if (f <= 0.02F) {
                continue;
            }
            int wx = centerX + i;
            boolean majorX = Math.floorMod(wx, 4) == 0;
            double wX = majorX ? lw * 1.4D : lw;
            float aX = f * (majorX ? 0.55F : 0.26F);
            quadXZ(vertices, matrix, camera, wx - wX, centerZ - radius, wx + wX, centerZ + radius,
                    y, baseR, baseG, baseB, aX);

            int wz = centerZ + i;
            boolean majorZ = Math.floorMod(wz, 4) == 0;
            double wZ = majorZ ? lw * 1.4D : lw;
            float aZ = f * (majorZ ? 0.55F : 0.26F);
            quadXZ(vertices, matrix, camera, centerX - radius, wz - wZ, centerX + radius, wz + wZ,
                    y, baseR, baseG, baseB, aZ);
        }
    }

    private static void drawOriginAxes(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                       ShipyardStatePacket state, int centerX, int centerZ,
                                       double y, int radius, double lw) {
        double axisZ = state.originZ() + 0.5D;
        if (Math.abs(axisZ - centerZ) <= radius) {
            quadXZ(vertices, matrix, camera, centerX - radius, axisZ - lw,
                    centerX + radius, axisZ + lw, y, 1.0F, 0.35F, 0.35F, 0.72F);
        }
        double axisX = state.originX() + 0.5D;
        if (Math.abs(axisX - centerX) <= radius) {
            quadXZ(vertices, matrix, camera, axisX - lw, centerZ - radius,
                    axisX + lw, centerZ + radius, y, 0.38F, 0.65F, 1.0F, 0.72F);
        }
    }

    private static void drawFrontGrid(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                      int cx, int cy, double z, int radius, int step,
                                      boolean global, double lw) {
        float r = global ? 0.52F : 0.62F;
        float g = global ? 0.72F : 0.82F;
        float b = global ? 0.88F : 1.00F;
        for (int i = -radius; i <= radius; i += step) {
            float f = fade(i, radius);
            if (f <= 0.02F) {
                continue;
            }
            int wx = cx + i;
            boolean majorX = Math.floorMod(wx, 4) == 0;
            double wX = majorX ? lw * 1.4D : lw;
            float aX = f * (majorX ? 0.55F : 0.26F);
            quad4(vertices, matrix, camera,
                    wx - wX, cy - radius, z, wx + wX, cy - radius, z,
                    wx + wX, cy + radius, z, wx - wX, cy + radius, z,
                    r, g, b, aX);

            int wy = cy + i;
            boolean majorY = Math.floorMod(wy, 4) == 0;
            double wY = majorY ? lw * 1.4D : lw;
            float aY = f * (majorY ? 0.55F : 0.26F);
            quad4(vertices, matrix, camera,
                    cx - radius, wy - wY, z, cx + radius, wy - wY, z,
                    cx + radius, wy + wY, z, cx - radius, wy + wY, z,
                    r, g, b, aY);
        }
    }

    private static void drawSideGrid(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                     double x, int cy, int cz, int radius, int step,
                                     boolean global, double lw) {
        float r = global ? 0.52F : 0.62F;
        float g = global ? 0.72F : 0.82F;
        float b = global ? 0.88F : 1.00F;
        for (int i = -radius; i <= radius; i += step) {
            float f = fade(i, radius);
            if (f <= 0.02F) {
                continue;
            }
            int wz = cz + i;
            boolean majorZ = Math.floorMod(wz, 4) == 0;
            double wZ = majorZ ? lw * 1.4D : lw;
            float aZ = f * (majorZ ? 0.55F : 0.26F);
            quad4(vertices, matrix, camera,
                    x, cy - radius, wz - wZ, x, cy - radius, wz + wZ,
                    x, cy + radius, wz + wZ, x, cy + radius, wz - wZ,
                    r, g, b, aZ);

            int wy = cy + i;
            boolean majorY = Math.floorMod(wy, 4) == 0;
            double wY = majorY ? lw * 1.4D : lw;
            float aY = f * (majorY ? 0.55F : 0.26F);
            quad4(vertices, matrix, camera,
                    x, wy - wY, cz - radius, x, wy - wY, cz + radius,
                    x, wy + wY, cz + radius, x, wy + wY, cz - radius,
                    r, g, b, aY);
        }
    }

    private static void drawMirrorPlane(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                        ShipyardStatePacket state, byte mirror, int coord, double lw) {
        double y0 = state.originY() - 2.0D;
        double y1 = state.originY() + 22.0D;
        double span = 24.0D;
        if (mirror == ShipyardShapes.MIRROR_X) {
            double x = coord + 0.5D;
            double z0 = ShipyardClient.focus().z - span;
            double z1 = ShipyardClient.focus().z + span;
            quad4(vertices, matrix, camera,
                    x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0,
                    0.25F, 0.90F, 1.00F, 0.13F);
            double wy = ShipyardClient.workPlaneY() + 0.01D;
            quadXZ(vertices, matrix, camera, x - lw, z0, x + lw, z1, wy,
                    0.35F, 0.95F, 1.00F, 0.85F);
        } else if (mirror == ShipyardShapes.MIRROR_Z) {
            double z = coord + 0.5D;
            double x0 = ShipyardClient.focus().x - span;
            double x1 = ShipyardClient.focus().x + span;
            quad4(vertices, matrix, camera,
                    x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z,
                    0.25F, 0.90F, 1.00F, 0.13F);
            double wy = ShipyardClient.workPlaneY() + 0.01D;
            quadXZ(vertices, matrix, camera, x0, z - lw, x1, z + lw, wy,
                    0.35F, 0.95F, 1.00F, 0.85F);
        }
    }

    private static void quadXZ(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                               double x0, double z0, double x1, double z1, double y,
                               float r, float g, float b, float a) {
        quad4(vertices, matrix, camera,
                x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1,
                r, g, b, a);
    }

    private static void quad4(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                              double x0, double y0, double z0, double x1, double y1, double z1,
                              double x2, double y2, double z2, double x3, double y3, double z3,
                              float r, float g, float b, float a) {
        vertex(vertices, matrix, camera, x0, y0, z0, r, g, b, a);
        vertex(vertices, matrix, camera, x1, y1, z1, r, g, b, a);
        vertex(vertices, matrix, camera, x2, y2, z2, r, g, b, a);
        vertex(vertices, matrix, camera, x3, y3, z3, r, g, b, a);
    }

    private static float fade(int offset, int radius) {
        double dist = Math.abs((double) offset) / Math.max(1, radius);
        double value = 1.0D - dist;
        return (float) Math.max(0.0D, Math.min(1.0D, value * value));
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                               double x, double y, double z, float r, float g, float b, float a) {
        vertices.vertex(matrix, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
                .color(r, g, b, a)
                .endVertex();
    }
}
