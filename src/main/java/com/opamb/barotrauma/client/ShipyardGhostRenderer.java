package com.opamb.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.network.ShipyardStatusPacket;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/** 编辑器悬停高亮、多方块完整虚影、选区/剪贴板预览，以及未连通/破口三维诊断高亮。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class ShipyardGhostRenderer {

    private static final int DETAIL_PREVIEW_LIMIT = 512;

    private ShipyardGhostRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            ShipyardClient.setCameraMatrices(new Matrix4f(event.getPoseStack().last().pose()),
                    new Matrix4f(event.getProjectionMatrix()), event.getCamera().getPosition());
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !ShipyardClient.editorActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (ShipyardClient.matricesReady() && mc.getWindow() != null
                && !ShipyardClient.isPointerOverUi()) {
            ShipyardClient.updateTarget(ShipyardClient.lastMouseX(), ShipyardClient.lastMouseY(),
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float pulse = 0.5F + 0.5F * Mth.sin((mc.level.getGameTime() + event.getPartialTick()) * 0.35F);

        if (ShipyardClient.dragActive()) {
            renderDragPreview(pose, matrix, buffers, camera, pulse);
            return;
        }

        if (ShipyardClient.showDiagnostics()) {
            renderDiagnostics(pose, matrix, buffers, camera, pulse);
        }
        if (ShipyardClient.showCompartments()) {
            renderCompartments(matrix, buffers, camera);
        }
        if (ShipyardClient.showWiring() || ShipyardClient.toolMode() == ShipyardClient.ToolMode.WIRE) {
            renderWiring(pose, buffers, camera);
        }
        if (ShipyardClient.showRuler()) {
            renderRuler(pose, buffers, camera);
        }

        byte mirror = ShipyardClient.mirrorAxis();
        int mirrorCoord = ShipyardClient.mirrorCoord();
        BlockPos target = ShipyardClient.target();
        if (target != null) {
            boolean eraseMode = ShipyardClient.toolMode() == ShipyardClient.ToolMode.BOX_ERASE;
            float r = 1.0F;
            float g = eraseMode ? 0.25F : 0.92F;
            float b = eraseMode ? 0.25F : 0.65F;
            float a = eraseMode ? 0.90F : 0.78F;
            drawWireCube(pose, buffers, camera, target, 0.004D, r, g, b, a);
            if (mirror != ShipyardShapes.MIRROR_NONE) {
                BlockPos mTarget = ShipyardShapes.mirrorPos(target, mirror, mirrorCoord);
                if (!mTarget.equals(target)) {
                    drawWireCube(pose, buffers, camera, mTarget, 0.004D, 0.25F, 0.90F, 1.0F, 0.65F);
                }
            }
        }

        BlockPos pos = ShipyardClient.placePos();
        if (pos == null) {
            return;
        }
        if (ShipyardClient.toolMode() == ShipyardClient.ToolMode.PASTE && ShipyardClient.hasClipboard()) {
            renderPastePreview(mc, pose, matrix, buffers, camera,
                    pos.offset(ShipyardClient.pasteNudgeOffset()), pulse, mirror, mirrorCoord);
            return;
        }

        if (ShipyardClient.ghostState() == null
                || ShipyardClient.toolMode() == ShipyardClient.ToolMode.BOX_ERASE
                || ShipyardClient.toolMode() == ShipyardClient.ToolMode.REPLACE
                || ShipyardClient.toolMode() == ShipyardClient.ToolMode.COPY_SELECT
                || ShipyardClient.toolMode() == ShipyardClient.ToolMode.CUT_SELECT
                || ShipyardClient.toolMode() == ShipyardClient.ToolMode.WIRE) {
            return;
        }
        float alpha = 0.16F + 0.14F * pulse;
        BlockState ghost = ShipyardClient.ghostState();
        renderGhostBlock(mc, pose, matrix, buffers, camera, pos, ghost, 1.0F, 0.55F, 0.18F, alpha);
        drawWireCube(pose, buffers, camera, pos, 0.003D, 1.0F, 0.68F, 0.25F, 0.85F);

        if (mirror != ShipyardShapes.MIRROR_NONE) {
            BlockPos mPos = ShipyardShapes.mirrorPos(pos, mirror, mirrorCoord);
            if (!mPos.equals(pos)) {
                BlockState mGhost = ShipyardShapes.mirrorState(ghost, mirror);
                renderGhostBlock(mc, pose, matrix, buffers, camera, mPos, mGhost,
                        0.25F, 0.85F, 1.0F, alpha * 0.9F);
                drawWireCube(pose, buffers, camera, mPos, 0.003D, 0.25F, 0.90F, 1.0F, 0.80F);
            }
        }
    }

    private static void renderDiagnostics(PoseStack pose, Matrix4f matrix,
                                          MultiBufferSource.BufferSource buffers,
                                          Vec3 camera, float pulse) {
        ShipyardStatusPacket status = ShipyardClient.status();
        if (status == null) {
            return;
        }
        List<BlockPos> disconnected = status.disconnectedPos();
        List<BlockPos> leaks = status.leakPos();
        if (disconnected.isEmpty() && leaks.isEmpty()) {
            return;
        }
        float alpha = 0.16F + 0.14F * pulse;
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        for (BlockPos pos : disconnected) {
            drawBox(vertices, matrix, camera,
                    pos.getX() - 0.01F, pos.getY() - 0.01F, pos.getZ() - 0.01F,
                    pos.getX() + 1.01F, pos.getY() + 1.01F, pos.getZ() + 1.01F,
                    1.0F, 0.18F, 0.18F, alpha);
        }
        for (BlockPos pos : leaks) {
            drawBox(vertices, matrix, camera,
                    pos.getX() + 0.04F, pos.getY() + 0.04F, pos.getZ() + 0.04F,
                    pos.getX() + 0.96F, pos.getY() + 0.96F, pos.getZ() + 0.96F,
                    1.0F, 0.82F, 0.15F, alpha * 0.9F);
        }
        buffers.endBatch(RenderType.debugQuads());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        for (BlockPos pos : disconnected) {
            drawWireCube(pose, buffers, camera, pos, 0.015D, 1.0F, 0.22F, 0.22F, 0.90F);
        }
        for (BlockPos pos : leaks) {
            drawWireCube(pose, buffers, camera, pos, -0.02D, 1.0F, 0.85F, 0.20F, 0.75F);
        }
    }

    private static void renderRuler(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        ShipyardStatusPacket status = ShipyardClient.status();
        if (status == null || status.minPos() == null || status.maxPos() == null) {
            return;
        }
        BlockPos min = status.minPos();
        BlockPos max = status.maxPos();
        drawWireBox(pose, buffers, camera,
                min.getX() - 0.02D, min.getY() - 0.02D, min.getZ() - 0.02D,
                max.getX() + 1.02D, max.getY() + 1.02D, max.getZ() + 1.02D,
                0.35F, 0.85F, 0.55F, 0.55F);
    }

    private static void renderCompartments(Matrix4f matrix, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        ShipyardStatusPacket status = ShipyardClient.status();
        if (status == null) {
            return;
        }
        List<BlockPos> ballast = status.ballastFloorCells();
        List<BlockPos> dry = status.dryFloorCells();
        if (ballast.isEmpty() && dry.isEmpty()) {
            return;
        }
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        for (BlockPos p : ballast) {
            float y = (float) (p.getY() + 0.03D - camera.y);
            quad(vertices, matrix,
                    (float) (p.getX() + 0.04D - camera.x), y, (float) (p.getZ() + 0.04D - camera.z),
                    (float) (p.getX() + 0.96D - camera.x), y, (float) (p.getZ() + 0.04D - camera.z),
                    (float) (p.getX() + 0.96D - camera.x), y, (float) (p.getZ() + 0.96D - camera.z),
                    (float) (p.getX() + 0.04D - camera.x), y, (float) (p.getZ() + 0.96D - camera.z),
                    0.22F, 0.58F, 1.00F, 0.34F);
        }
        for (BlockPos p : dry) {
            float y = (float) (p.getY() + 0.03D - camera.y);
            quad(vertices, matrix,
                    (float) (p.getX() + 0.06D - camera.x), y, (float) (p.getZ() + 0.06D - camera.z),
                    (float) (p.getX() + 0.94D - camera.x), y, (float) (p.getZ() + 0.06D - camera.z),
                    (float) (p.getX() + 0.94D - camera.x), y, (float) (p.getZ() + 0.94D - camera.z),
                    (float) (p.getX() + 0.06D - camera.x), y, (float) (p.getZ() + 0.94D - camera.z),
                    0.22F, 0.85F, 0.55F, 0.22F);
        }
        buffers.endBatch(RenderType.debugQuads());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderWiring(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        ShipyardStatusPacket status = ShipyardClient.status();
        List<com.opamb.barotrauma.world.shipyard.ShipyardValidator.WireLink> links =
                status == null ? List.of() : status.wireLinks();
        BlockPos wireAnchor = ShipyardClient.wireAnchor();
        if (links.isEmpty() && wireAnchor == null) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = pose.last().pose();
        for (var link : links) {
            boolean power = link.kind() == com.opamb.barotrauma.world.shipyard.ShipyardValidator.WIRE_POWER;
            float r = power ? 1.0F : 0.35F;
            float g = power ? 0.78F : 0.90F;
            float b = power ? 0.20F : 1.00F;
            drawLine3D(lines, matrix, camera, link.from(), link.to(), r, g, b, 0.85F);
        }
        if (wireAnchor != null) {
            BlockPos end = ShipyardClient.target() != null ? ShipyardClient.target() : ShipyardClient.placePos();
            if (end != null && !end.equals(wireAnchor)) {
                drawLine3D(lines, matrix, camera, wireAnchor, end, 0.35F, 1.00F, 0.75F, 0.95F);
            }
        }
        buffers.endBatch(RenderType.lines());
        if (wireAnchor != null) {
            drawWireCube(pose, buffers, camera, wireAnchor, 0.015D, 0.35F, 1.00F, 0.75F, 0.95F);
        }
    }

    private static void drawLine3D(VertexConsumer lines, Matrix4f matrix, Vec3 camera,
                                   BlockPos a, BlockPos b, float r, float g, float bCol, float alpha) {
        float x0 = (float) (a.getX() + 0.5D - camera.x);
        float y0 = (float) (a.getY() + 0.5D - camera.y);
        float z0 = (float) (a.getZ() + 0.5D - camera.z);
        float x1 = (float) (b.getX() + 0.5D - camera.x);
        float y1 = (float) (b.getY() + 0.5D - camera.y);
        float z1 = (float) (b.getZ() + 0.5D - camera.z);
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;
        lines.vertex(matrix, x0, y0, z0).color(r, g, bCol, alpha).normal(nx, ny, nz).endVertex();
        lines.vertex(matrix, x1, y1, z1).color(r, g, bCol, alpha).normal(nx, ny, nz).endVertex();
    }

    private static void renderPastePreview(Minecraft mc, PoseStack pose, Matrix4f matrix,
                                           MultiBufferSource.BufferSource buffers,
                                           Vec3 camera, BlockPos anchor, float pulse,
                                           byte mirror, int mirrorCoord) {
        List<ShipyardClient.ClipboardBlock> entries = ShipyardClient.rotatedClipboard();
        if (entries.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ShipyardClient.ClipboardBlock entry : entries) {
            BlockPos p = anchor.offset(entry.rel());
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        drawWireBox(pose, buffers, camera,
                minX - 0.006D, minY - 0.006D, minZ - 0.006D,
                maxX + 1.006D, maxY + 1.006D, maxZ + 1.006D,
                0.55F, 0.48F, 1.0F, 0.95F);

        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        float alpha = 0.18F + 0.12F * pulse;
        if (entries.size() <= DETAIL_PREVIEW_LIMIT) {
            for (ShipyardClient.ClipboardBlock entry : entries) {
                BlockPos p = anchor.offset(entry.rel());
                boolean conflict = !mc.level.getBlockState(p).canBeReplaced();
                float r = conflict ? 1.0F : 0.55F;
                float g = conflict ? 0.18F : 0.48F;
                float b = conflict ? 0.18F : 1.0F;
                drawBox(vertices, matrix, camera,
                        p.getX() + 0.02F, p.getY() + 0.02F, p.getZ() + 0.02F,
                        p.getX() + 0.98F, p.getY() + 0.98F, p.getZ() + 0.98F,
                        r, g, b, alpha);
                if (mirror != ShipyardShapes.MIRROR_NONE) {
                    BlockPos mp = ShipyardShapes.mirrorPos(p, mirror, mirrorCoord);
                    if (!mp.equals(p)) {
                        drawBox(vertices, matrix, camera,
                                mp.getX() + 0.02F, mp.getY() + 0.02F, mp.getZ() + 0.02F,
                                mp.getX() + 0.98F, mp.getY() + 0.98F, mp.getZ() + 0.98F,
                                0.25F, 0.85F, 1.0F, alpha * 0.85F);
                    }
                }
            }
        } else {
            drawBox(vertices, matrix, camera,
                    minX + 0.01F, minY + 0.01F, minZ + 0.01F,
                    maxX + 0.99F, maxY + 0.99F, maxZ + 0.99F,
                    0.55F, 0.48F, 1.0F, alpha);
        }
        buffers.endBatch(RenderType.debugQuads());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderDragPreview(PoseStack pose, Matrix4f matrix,
                                          MultiBufferSource.BufferSource buffers,
                                          Vec3 camera, float pulse) {
        BlockPos anchor = ShipyardClient.dragAnchor();
        BlockPos current = ShipyardClient.dragCurrent();
        byte mode = ShipyardClient.dragMode();
        long count = mode == ShipyardShapes.MODE_ELLIPSOID
                ? ShipyardShapes.collectCells(anchor, current, mode, ShipyardShapes.MAX_VOLUME).size()
                : ShipyardShapes.estimateCount(anchor, current, mode);
        boolean overLimit = count > ShipyardShapes.MAX_VOLUME || count <= 0;

        float r;
        float g;
        float b;
        if (overLimit) {
            r = 1.0F;
            g = 0.15F;
            b = 0.15F;
        } else if (mode == ShipyardShapes.MODE_CLEAR) {
            r = 1.0F;
            g = 0.30F;
            b = 0.25F;
        } else if (mode == ShipyardShapes.MODE_COPY) {
            r = 0.55F;
            g = 0.48F;
            b = 1.00F;
        } else if (mode == ShipyardShapes.MODE_REPLACE) {
            r = 0.25F;
            g = 0.88F;
            b = 0.65F;
        } else if (mode == ShipyardShapes.MODE_ELLIPSOID) {
            r = 0.35F;
            g = 0.90F;
            b = 0.75F;
        } else if (mode == ShipyardShapes.MODE_LINE) {
            r = 0.30F;
            g = 0.90F;
            b = 1.0F;
        } else {
            r = 1.0F;
            g = 0.62F;
            b = 0.20F;
        }

        int minX = Math.min(anchor.getX(), current.getX());
        int minY = Math.min(anchor.getY(), current.getY());
        int minZ = Math.min(anchor.getZ(), current.getZ());
        int maxX = Math.max(anchor.getX(), current.getX());
        int maxY = Math.max(anchor.getY(), current.getY());
        int maxZ = Math.max(anchor.getZ(), current.getZ());

        drawWireBox(pose, buffers, camera,
                minX - 0.005D, minY - 0.005D, minZ - 0.005D,
                maxX + 1.005D, maxY + 1.005D, maxZ + 1.005D,
                r, g, b, 0.95F);

        byte mirror = ShipyardClient.mirrorAxis();
        int mirrorCoord = ShipyardClient.mirrorCoord();
        if (mirror != ShipyardShapes.MIRROR_NONE && mode != ShipyardShapes.MODE_COPY) {
            BlockPos ma = ShipyardShapes.mirrorPos(new BlockPos(minX, minY, minZ), mirror, mirrorCoord);
            BlockPos mb = ShipyardShapes.mirrorPos(new BlockPos(maxX, maxY, maxZ), mirror, mirrorCoord);
            drawWireBox(pose, buffers, camera,
                    Math.min(ma.getX(), mb.getX()) - 0.005D,
                    Math.min(ma.getY(), mb.getY()) - 0.005D,
                    Math.min(ma.getZ(), mb.getZ()) - 0.005D,
                    Math.max(ma.getX(), mb.getX()) + 1.005D,
                    Math.max(ma.getY(), mb.getY()) + 1.005D,
                    Math.max(ma.getZ(), mb.getZ()) + 1.005D,
                    0.25F, 0.90F, 1.0F, 0.85F);
        }

        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        float alpha = 0.16F + 0.12F * pulse;

        if (!overLimit && mode != ShipyardShapes.MODE_COPY && count <= DETAIL_PREVIEW_LIMIT) {
            List<BlockPos> cells = ShipyardShapes.collectCells(anchor, current, mode, DETAIL_PREVIEW_LIMIT);
            for (BlockPos cell : cells) {
                drawBox(vertices, matrix, camera,
                        cell.getX() + 0.02F, cell.getY() + 0.02F, cell.getZ() + 0.02F,
                        cell.getX() + 0.98F, cell.getY() + 0.98F, cell.getZ() + 0.98F,
                        r, g, b, alpha);
                if (mirror != ShipyardShapes.MIRROR_NONE) {
                    BlockPos mCell = ShipyardShapes.mirrorPos(cell, mirror, mirrorCoord);
                    if (!mCell.equals(cell)) {
                        drawBox(vertices, matrix, camera,
                                mCell.getX() + 0.02F, mCell.getY() + 0.02F, mCell.getZ() + 0.02F,
                                mCell.getX() + 0.98F, mCell.getY() + 0.98F, mCell.getZ() + 0.98F,
                                0.25F, 0.85F, 1.0F, alpha * 0.85F);
                    }
                }
            }
        } else {
            drawBox(vertices, matrix, camera,
                    minX + 0.01F, minY + 0.01F, minZ + 0.01F,
                    maxX + 0.99F, maxY + 0.99F, maxZ + 0.99F,
                    r, g, b, alpha);
        }
        buffers.endBatch(RenderType.debugQuads());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderGhostBlock(Minecraft mc, PoseStack pose, Matrix4f matrix,
                                         MultiBufferSource.BufferSource buffers, Vec3 camera,
                                         BlockPos pos, BlockState ghost,
                                         float r, float g, float b, float alpha) {
        if (ghost.getRenderShape() == RenderShape.MODEL) {
            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.55F);
            var ghostType = Sheets.translucentCullBlockSheet();
            mc.getBlockRenderer().renderSingleBlock(ghost, pose, buffers,
                    0xF000F0, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, ghostType);
            buffers.endBatch(ghostType);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
        List<BlockPos> footprint = ShipyardShapes.footprintOffsets(ghost);
        if (footprint.size() > 1) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            for (BlockPos off : footprint) {
                BlockPos part = pos.offset(off);
                minX = Math.min(minX, part.getX());
                minY = Math.min(minY, part.getY());
                minZ = Math.min(minZ, part.getZ());
                maxX = Math.max(maxX, part.getX());
                maxY = Math.max(maxY, part.getY());
                maxZ = Math.max(maxZ, part.getZ());
                boolean conflict = !mc.level.getBlockState(part).canBeReplaced();
                drawBox(vertices, matrix, camera,
                        part.getX() + 0.02F, part.getY() + 0.02F, part.getZ() + 0.02F,
                        part.getX() + 0.98F, part.getY() + 0.98F, part.getZ() + 0.98F,
                        conflict ? 1.0F : r, conflict ? 0.15F : g, conflict ? 0.15F : b,
                        conflict ? alpha * 1.4F : alpha * 0.85F);
            }
            buffers.endBatch(RenderType.debugQuads());
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            drawWireBox(pose, buffers, camera,
                    minX - 0.004D, minY - 0.004D, minZ - 0.004D,
                    maxX + 1.004D, maxY + 1.004D, maxZ + 1.004D,
                    r, g, b, 0.92F);
            if (ghost.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                drawFacingArrow(pose, buffers, camera,
                        (minX + maxX + 1) * 0.5D, maxY + 1.08D, (minZ + maxZ + 1) * 0.5D,
                        ghost.getValue(BlockStateProperties.HORIZONTAL_FACING), r, g, b);
            }
            return;
        }

        VoxelShape shape = ghost.getShape(mc.level, pos);
        AABB box = shape.isEmpty() ? new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D) : shape.bounds();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        drawBox(vertices, matrix, camera,
                (float) (pos.getX() + box.minX + 0.005D),
                (float) (pos.getY() + box.minY + 0.005D),
                (float) (pos.getZ() + box.minZ + 0.005D),
                (float) (pos.getX() + box.maxX - 0.005D),
                (float) (pos.getY() + box.maxY - 0.005D),
                (float) (pos.getZ() + box.maxZ - 0.005D),
                r, g, b, alpha);
        buffers.endBatch(RenderType.debugQuads());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawFacingArrow(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                        Vec3 camera, double cx, double y, double cz,
                                        Direction facing, float r, float g, float b) {
        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double rx = -fz;
        double rz = fx;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Matrix4f m = pose.last().pose();
        float x0 = (float) (cx - fx * 0.35D - camera.x);
        float z0 = (float) (cz - fz * 0.35D - camera.z);
        float tipX = (float) (cx + fx * 0.55D - camera.x);
        float tipZ = (float) (cz + fz * 0.55D - camera.z);
        float wy = (float) (y - camera.y);
        float w1X = (float) (cx + fx * 0.20D + rx * 0.28D - camera.x);
        float w1Z = (float) (cz + fz * 0.20D + rz * 0.28D - camera.z);
        float w2X = (float) (cx + fx * 0.20D - rx * 0.28D - camera.x);
        float w2Z = (float) (cz + fz * 0.20D - rz * 0.28D - camera.z);
        lines.vertex(m, x0, wy, z0).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(m, tipX, wy, tipZ).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(m, tipX, wy, tipZ).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(m, w1X, wy, w1Z).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(m, tipX, wy, tipZ).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        lines.vertex(m, w2X, wy, w2Z).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawWireCube(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                     Vec3 camera, BlockPos pos, double inflate,
                                     float r, float g, float b, float a) {
        drawWireBox(pose, buffers, camera,
                pos.getX() - inflate, pos.getY() - inflate, pos.getZ() - inflate,
                pos.getX() + 1.0D + inflate, pos.getY() + 1.0D + inflate, pos.getZ() + 1.0D + inflate,
                r, g, b, a);
    }

    private static void drawWireBox(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                    Vec3 camera,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ,
                                    float r, float g, float b, float a) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines,
                minX - camera.x, minY - camera.y, minZ - camera.z,
                maxX - camera.x, maxY - camera.y, maxZ - camera.z,
                r, g, b, a);
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                float wx0, float wy0, float wz0, float wx1, float wy1, float wz1,
                                float r, float g, float b, float a) {
        float x0 = (float) (wx0 - camera.x);
        float y0 = (float) (wy0 - camera.y);
        float z0 = (float) (wz0 - camera.z);
        float x1 = (float) (wx1 - camera.x);
        float y1 = (float) (wy1 - camera.y);
        float z1 = (float) (wz1 - camera.z);
        quad(vertices, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(vertices, matrix, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(vertices, matrix, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(vertices, matrix, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, a);
        quad(vertices, matrix, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, a);
        quad(vertices, matrix, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        vertex(vertices, matrix, x0, y0, z0, r, g, b, a);
        vertex(vertices, matrix, x1, y1, z1, r, g, b, a);
        vertex(vertices, matrix, x2, y2, z2, r, g, b, a);
        vertex(vertices, matrix, x3, y3, z3, r, g, b, a);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix,
                               float x, float y, float z, float r, float g, float b, float a) {
        vertices.vertex(matrix, x, y, z).color(r, g, b, a).endVertex();
    }
}
