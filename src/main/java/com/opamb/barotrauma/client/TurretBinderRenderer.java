package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.block.TurretStationBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.blockentity.TurretStationBlockEntity;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 手持炮台绑定器时，把所有“瞄准站 → 炮塔”的绑定画成带下垂的线缆。
 *
 * <p>数据来源是已加载区块里的 {@link TurretStationBlockEntity}；站台实体把绑定同步到
 * 客户端，所以不需要额外的同步包。每 10 tick 重扫一次（只在手持绑定器时扫描），
 * 渲染时只读缓存。线缆用 {@link RenderType#lines()}，深度测试正常，墙后会遮挡。</p>
 *
 * <p>颜色：绿色 = 目标炮塔存在；琥珀 = 目标区块未加载；红色 = 目标方块已不是炮塔。
 * 准星指向已绑定的炮塔/瞄准站时，还会给它的另一端画一圈穿透墙壁的发光描边。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TurretBinderRenderer {

    /** 以玩家所在区块为中心扫描的半径（区块）。8 区块 = 129 格，覆盖一般船体。 */
    private static final int SCAN_CHUNK_RADIUS = 8;

    /** 缓存刷新间隔（tick）。 */
    private static final int REFRESH_TICKS = 10;

    /** 最多绘制的连线数，防止极端存档每帧画几千条。 */
    private static final int MAX_LINKS = 96;

    private static final int SAG_SEGMENTS_MIN = 8;
    private static final int SAG_SEGMENTS_MAX = 48;

    /** 准星检查距离：比交互距离远，方便隔着船体找另一端。 */
    private static final double INSPECT_DISTANCE = 64.0D;

    private static final BufferBuilder LINE_BUILDER = new BufferBuilder(16384);
    private static final MultiBufferSource.BufferSource LINE_BUFFERS =
            MultiBufferSource.immediate(LINE_BUILDER);
    private static final BufferBuilder GLOW_BUILDER = new BufferBuilder(8192);
    private static final MultiBufferSource.BufferSource GLOW_BUFFERS =
            MultiBufferSource.immediate(GLOW_BUILDER);

    private static List<Link> links = List.of();
    private static boolean holding;
    private static int ticksSinceScan;

    private record Link(BlockPos stationPos, BlockPos turretPos) {
    }

    private TurretBinderRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            links = List.of();
            holding = false;
            return;
        }
        holding = isBinder(minecraft.player.getMainHandItem())
                || isBinder(minecraft.player.getOffhandItem());
        if (!holding) {
            links = List.of();
            ticksSinceScan = 0;
            return;
        }
        // 每 REFRESH_TICKS 重扫一次；links 为空也要节流，否则“手持绑定器但附近没有绑定”
        // 这个最常见场景会每 tick 扫 17×17 个区块。
        if (ticksSinceScan++ % REFRESH_TICKS != 0) {
            return;
        }
        links = scan(level, minecraft.player.blockPosition());
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !holding) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();

        if (!links.isEmpty()) {
            VertexConsumer vertices = LINE_BUFFERS.getBuffer(RenderType.lines());
            for (Link link : links) {
                drawLink(vertices, matrix, camera, level, link);
            }
            // 即使一条都没画也要 endBatch：复用的 builder 留在 begun 状态会跨帧累积顶点。
            LINE_BUFFERS.endBatch(RenderType.lines());
        }

        List<BlockPos> highlights = resolveHighlights(level, minecraft.player, event.getPartialTick());
        if (!highlights.isEmpty()) {
            drawHighlights(level, matrix, camera, event.getPartialTick(), highlights);
        }
    }

    /**
     * 准星指向一个已绑定的方块时，返回它另一端的坐标：
     * 瞄准站 → 它绑定的炮塔；炮塔 → 所有绑定到它的瞄准站。
     */
    private static List<BlockPos> resolveHighlights(ClientLevel level, Player player,
                                                    float partialTick) {
        // 炮塔视角里准星由炮塔相机决定，别用玩家身体视角去选目标。
        if (TurretClient.isActive()) {
            return List.of();
        }
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 end = eye.add(player.getViewVector(partialTick).scale(INSPECT_DISTANCE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return List.of();
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof TurretStationBlock) {
            if (level.getBlockEntity(pos) instanceof TurretStationBlockEntity station
                    && station.turretPos() != null
                    && level.hasChunkAt(station.turretPos())) {
                return List.of(station.turretPos());
            }
            return List.of();
        }

        if (state.getBlock() instanceof TurretBlock) {
            // 未来多块炮塔：点到任意一块都换算成主方块再匹配绑定。
            BlockPos master = TurretBlockEntity.masterPos(state, pos);
            List<BlockPos> result = new ArrayList<>();
            for (Link link : links) {
                if (link.turretPos().equals(master) && level.hasChunkAt(link.stationPos())) {
                    result.add(link.stationPos());
                }
            }
            return result;
        }
        return List.of();
    }

    /** 发光描边：一圈贴方块的实线 + 一圈更淡的外晕，脉冲呼吸。 */
    private static void drawHighlights(ClientLevel level, Matrix4f matrix, Vec3 camera,
                                       float partialTick, List<BlockPos> highlights) {
        VertexConsumer vertices = GLOW_BUFFERS.getBuffer(GlowLineType.TYPE);
        float pulse = 0.72F + 0.28F * Mth.sin((level.getGameTime() + partialTick) * 0.4F);
        for (BlockPos pos : highlights) {
            box(vertices, matrix, camera, new AABB(pos).inflate(0.004D),
                    0.45F, 1.0F, 0.95F, 0.95F * pulse);
            box(vertices, matrix, camera, new AABB(pos).inflate(0.035D),
                    0.18F, 0.85F, 0.80F, 0.30F * pulse);
        }
        GLOW_BUFFERS.endBatch(GlowLineType.TYPE);
    }

    private static void box(VertexConsumer vertices, Matrix4f matrix, Vec3 camera, AABB box,
                            float red, float green, float blue, float alpha) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        line(vertices, matrix, camera, x0, y0, z0, x1, y0, z0, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y0, z1, x1, y0, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y1, z0, x1, y1, z0, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y1, z1, x1, y1, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y0, z0, x0, y1, z0, red, green, blue, alpha);
        line(vertices, matrix, camera, x1, y0, z0, x1, y1, z0, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y0, z1, x0, y1, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x1, y0, z1, x1, y1, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y0, z0, x0, y0, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x1, y0, z0, x1, y0, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x0, y1, z0, x0, y1, z1, red, green, blue, alpha);
        line(vertices, matrix, camera, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
    }

    /**
     * 绑定高亮专用：关闭深度测试，隔着船体/墙也能看到另一端的描边。
     * 线框格式仍是 {@code POSITION_COLOR_NORMAL}，复用原版 lines shader。
     */
    private static final class GlowLineType extends RenderStateShard {

        private GlowLineType() {
            super("barotrauma_turret_binder_glow", () -> {
            }, () -> {
            });
        }

        private static final RenderType TYPE = RenderType.create(
                "barotrauma_turret_binder_glow",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static boolean isBinder(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.TURRET_BINDER.get();
    }

    private static List<Link> scan(ClientLevel level, BlockPos center) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        List<Link> found = new ArrayList<>();
        for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
            for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
                ChunkAccess chunk = level.getChunkSource()
                        .getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, false);
                if (!(chunk instanceof LevelChunk levelChunk)) {
                    continue;
                }
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    if (blockEntity instanceof TurretStationBlockEntity station
                            && station.turretPos() != null) {
                        found.add(new Link(station.getBlockPos(), station.turretPos()));
                        if (found.size() >= MAX_LINKS) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }

    private static void drawLink(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                                 ClientLevel level, Link link) {
        Vec3 start = Vec3.atCenterOf(link.stationPos()).add(0.0D, 0.35D, 0.0D);
        Vec3 end = new Vec3(link.turretPos().getX() + 0.5D,
                link.turretPos().getY() + 0.75D,
                link.turretPos().getZ() + 0.5D);

        float red;
        float green;
        float blue;
        if (!level.hasChunkAt(link.turretPos())) {
            // 未加载：琥珀色。客户端不能为了画线去同步加载区块。
            red = 1.0F;
            green = 0.68F;
            blue = 0.18F;
        } else if (!(level.getBlockState(link.turretPos()).getBlock() instanceof TurretBlock)) {
            red = 1.0F;
            green = 0.26F;
            blue = 0.20F;
        } else {
            red = 0.32F;
            green = 1.0F;
            blue = 0.42F;
        }
        float alpha = 0.85F;

        double distance = start.distanceTo(end);
        double sag = Math.min(0.8D, distance * 0.04D);
        int segments = Mth.clamp((int) Math.ceil(distance * 3.0D),
                SAG_SEGMENTS_MIN, SAG_SEGMENTS_MAX);
        double previousX = start.x;
        double previousY = start.y;
        double previousZ = start.z;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double x = Mth.lerp(t, start.x, end.x);
            double y = Mth.lerp(t, start.y, end.y) - Math.sin(Math.PI * t) * sag;
            double z = Mth.lerp(t, start.z, end.z);
            line(vertices, matrix, camera, previousX, previousY, previousZ,
                    x, y, z, red, green, blue, alpha);
            previousX = x;
            previousY = y;
            previousZ = z;
        }

        // 两端各画一个小十字，明确“这一格是端点”。
        cross(vertices, matrix, camera, start, red, green, blue);
        cross(vertices, matrix, camera, end, red, green, blue);
    }

    private static void cross(VertexConsumer vertices, Matrix4f matrix, Vec3 camera, Vec3 point,
                              float red, float green, float blue) {
        double size = 0.16D;
        line(vertices, matrix, camera, point.x - size, point.y, point.z,
                point.x + size, point.y, point.z, red, green, blue, 0.9F);
        line(vertices, matrix, camera, point.x, point.y - size, point.z,
                point.x, point.y + size, point.z, red, green, blue, 0.9F);
        line(vertices, matrix, camera, point.x, point.y, point.z - size,
                point.x, point.y, point.z + size, red, green, blue, 0.9F);
    }

    private static void line(VertexConsumer vertices, Matrix4f matrix, Vec3 camera,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             float red, float green, float blue, float alpha) {
        // RenderLevelStageEvent 的顶点是相机相对坐标（和 HullDamageRenderer 一致）。
        vertices.vertex(matrix, (float) (x0 - camera.x), (float) (y0 - camera.y),
                        (float) (z0 - camera.z))
                .color(red, green, blue, alpha)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
        vertices.vertex(matrix, (float) (x1 - camera.x), (float) (y1 - camera.y),
                        (float) (z1 - camera.z))
                .color(red, green, blue, alpha)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
