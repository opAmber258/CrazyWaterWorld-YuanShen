package com.opamb.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.network.ShipWaterStatePacket;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipWaterBoxes;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.opamb.barotrauma.network.ShipWaterStatePacket.BOX_STRIDE;
import static com.opamb.barotrauma.network.ShipWaterStatePacket.BREACH_STRIDE;

/**
 * 使用水面包在船的装配坐标中生成水体，按本帧船体位置和相机姿态合成到世界画面。
 * 可用 `-Dbarotrauma.waterDebug=true` 或 F9 打开矩阵、网格数量和遮挡诊断。
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShipWaterRenderer {

    /** F9 的诊断标记独立于水体材质。 */
    private static final class DiagnosticRenderType extends RenderStateShard {

        private DiagnosticRenderType() {
            super("barotrauma_water_diagnostic", () -> {
            }, () -> {
            });
        }

        private static final RenderType TYPE = RenderType.create(
                "barotrauma_water_diagnostic",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(new WriteMaskStateShard(true, false))
                        .createCompositeState(false));
    }

    /** 初始值：系统属性 / 环境变量（默认关闭）。游戏内 F9 可随时切换。 */
    private static final boolean DEBUG_DEFAULT = readDebugDefault();
    /**
     * 舱内水体绘制开关。空气海域仍然不生成真实水方块；这里画的是客户端自绘水面，
     * 数据来自 {@link ShipWaterStatePacket}。默认开启，可用 -Dbarotrauma.renderWater=false 关闭
     * （排查“数据没到”还是“渲染没画”时用）。
     */
    private static final boolean RENDER_WATER = !"false".equalsIgnoreCase(
            System.getProperty("barotrauma.renderWater", "true"));

    private static final Matrix4f LEVEL_VIEW = new Matrix4f();
    private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
    private static ClientLevel capturedLevel;
    private static Camera capturedCamera;
    private static ClientLevel renderedLevel;
    private static ShipWaterMesh waterMesh = new ShipWaterMesh();
    private static float[] breachBoxes = new float[0];

    private static volatile boolean debug = DEBUG_DEFAULT;
    private static volatile ShipWaterStatePacket state = ShipWaterStatePacket.empty();
    private static long packetCount;
    // 不要用 Long.MIN_VALUE 做初值：gameTime - Long.MIN_VALUE 会 long 溢出成负数，
    // 限频条件永远不成立。用 -100 让第一帧就输出一次。
    private static long lastDebugTick = -100L;
    private static long lastMatrixTick = -100L;
    private static long frameSequence;
    private static boolean missingShipLogged;
    /** 客户端平滑后的水面高度，服务端以 10 Hz 同步。 */
    private static float[] smoothedBoxes = new float[0];
    private static long smoothedShipId = Long.MIN_VALUE;
    private static boolean smoothedPhysical;

    private static long lastFrameNanos;
    private static double waveSeconds;
    private static ClientLevel particleLevel;
    private static long openingParticleTick = Long.MIN_VALUE;
    /** 每个落点占四个 float：x、y、z 和波幅。 */
    private static final int IMPACTS_PER_FLOW_TYPE = 12;
    private static final float[] streamImpacts = new float[2 * IMPACTS_PER_FLOW_TYPE * 4];
    private static int streamImpactCount;
    /** 满水舱气泡减半发送用。 */
    /** 破口很多时轮转起点：每次从下一个洞开始发粒子，避免总是前几个洞喷。 */
    private static int breachRoundRobin;
    /** 已经打过水盒快照日志的船 id（一次性诊断）。 */
    private static long dumpedShipId = Long.MIN_VALUE;

    private ShipWaterRenderer() {
    }

    private static boolean readDebugDefault() {
        String property = System.getProperty("barotrauma.waterDebug");
        String env = System.getenv("BAROTRAUMA_WATER_DEBUG");
        return truthy(property) || truthy(env);
    }

    private static boolean truthy(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1")
                || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on"));
    }

    public static void accept(ShipWaterStatePacket packet) {
        // 方块船水包是广播的：带源维度时，别的维度同坐标不能画水/做干湿判定。
        Minecraft minecraft = Minecraft.getInstance();
        String dimension = packet.dimension();
        if (dimension != null && !dimension.isEmpty() && minecraft.level != null
                && !dimension.equals(minecraft.level.dimension().location().toString())) {
            return;
        }
        state = packet;
        if (++packetCount % 40L == 0L) {
            BarotraumaMod.LOGGER.info("[water] 收到水面包：physical={} ship={} boxes={} breaches={} streams={} sideFlows={} anchor=({},{},{})",
                    packet.physical(), packet.shipId(), packet.boxes().length / 7,
                    packet.breaches().length / 9, packet.streams().length / ShipWaterStatePacket.STREAM_STRIDE,
                    packet.sideFlows().length / ShipWaterStatePacket.SIDE_FLOW_STRIDE,
                    packet.anchorX(), packet.anchorY(), packet.anchorZ());
        }
        // 一次性诊断：新船的第一包水盒结构。渲染再出问题时直接对照真实数据。
        if (packet.shipId() != dumpedShipId && packet.boxes().length >= 7) {
            dumpedShipId = packet.shipId();
            float[] boxes = packet.boxes();
            int count = boxes.length / 7;
            java.util.TreeSet<Float> surfaces = new java.util.TreeSet<>();
            for (int i = 0; i + 6 < boxes.length; i += 7) {
                surfaces.add(boxes[i + 6]);
            }
            StringBuilder sb = new StringBuilder(1024);
            sb.append("[water] 水盒快照 ship=").append(packet.shipId())
                    .append(" boxes=").append(count)
                    .append(" 不同水面值=").append(surfaces.size());
            int shown = Math.min(12, count);
            for (int b = 0; b < shown; b++) {
                int o = b * 7;
                sb.append("\n[water]  box[").append(b).append("] (")
                        .append(boxes[o]).append(',').append(boxes[o + 1]).append(',')
                        .append(boxes[o + 2]).append(")-(")
                        .append(boxes[o + 3]).append(',').append(boxes[o + 4]).append(',')
                        .append(boxes[o + 5]).append(") surface=").append(boxes[o + 6]);
            }
            BarotraumaMod.LOGGER.info(sb.toString());
        }
    }

    /** 查水面包中的精确位置。船体包围盒内仍可能存在船外海水。 */
    @org.jetbrains.annotations.Nullable
    public static Boolean dryAt(Level level, Vec3 pos) {
        ShipWaterStatePacket data = state;
        if (data.physical()) {
            return com.opamb.barotrauma.ship.vs.VsClientCompartments.dryAt(level, pos, data);
        }
        return com.opamb.barotrauma.ship.ShipWaterBoxes.dryAt(data.boxes(), pos.x, pos.y, pos.z);
    }

    /** 物理船舱室判断供潜行防掉边使用。 */
    public static boolean isInsidePhysicalCompartment(Level level, Vec3 pos) {
        ShipWaterStatePacket data = state;
        return data.physical() && com.opamb.barotrauma.ship.vs.VsClientCompartments
                .isInsideCompartment(level, pos, data);
    }

    /** F9 切换诊断；默认关闭，不需要重启客户端。 */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_F9) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        debug = !debug;
        BarotraumaMod.LOGGER.info("[water] 诊断模式：{}", debug ? "开" : "关");
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[water] 诊断模式：" + (debug ? "开" : "关")), true);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (!mc.level.dimension().equals(ModDimensions.THE_DEEP)
                && (!RENDER_WATER || state.boxes().length < BOX_STRIDE))) {
            return;
        }
        // F7 原版模式：深海不画模组水体（水面与水下压暗都是 ship_water 后处理）。
        if (DeepBrightnessDebug.hidesWaterAndFog()
                && mc.level.dimension().equals(ModDimensions.THE_DEEP)) {
            return;
        }
        // 切维度后旧包还在 state 里：源维度对不上就不画（否则另一维度同坐标会出水）。
        String dimension = state.dimension();
        if (dimension != null && !dimension.isEmpty()
                && !dimension.equals(mc.level.dimension().location().toString())) {
            return;
        }
        var stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            // AFTER_LEVEL 的 pose 是 GameRenderer 的投影栈。取地形阶段实际用的相机姿态。
            LEVEL_VIEW.set(event.getPoseStack().last().pose());
            LEVEL_PROJECTION.set(event.getProjectionMatrix());
            capturedLevel = mc.level;
            capturedCamera = event.getCamera();
            return;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        ClientLevel level = mc.level;
        boolean captured = capturedLevel == level && capturedCamera == event.getCamera();
        capturedLevel = null;
        capturedCamera = null;
        if (!captured || level == null || mc.player == null) {
            return;
        }
        if (renderedLevel != level) {
            renderedLevel = level;
            smoothedBoxes = new float[0];
            waterMesh = new ShipWaterMesh();
            lastFrameNanos = 0L;
            lastDebugTick = lastMatrixTick = level.getGameTime() - 100L;
        }
        ShipWaterStatePacket data = state;
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();

        ClientShip ship = null;
        if (RENDER_WATER && data.physical() && data.shipId() >= 0) {
            ship = findClientShip(level, data.shipId());
            if (ship == null && !missingShipLogged) {
                BarotraumaMod.LOGGER.warn("[water] 客户端找不到船 id={}", data.shipId());
            }
        }
        boolean shipReady = RENDER_WATER && (!data.physical() || ship != null);
        missingShipLogged = RENDER_WATER && data.physical() && ship == null;

        float dx = 0.0F;
        float dy = 0.0F;
        float dz = 0.0F;
        if (ship != null) {
            Vector3dc now = ship.getRenderTransform().getPositionInWorld();
            dx = (float) (now.x() - data.anchorX());
            dy = (float) (now.y() - data.anchorY());
            dz = (float) (now.z() - data.anchorZ());
        }
        // 服务端 10 Hz 同步一次水面，这里插值到每一帧；波浪相位也按真实时间走，
        // 不再跟 20 TPS 的 gameTime 绑定，避免水面一跳一跳。
        float dt = frameDeltaSeconds();
        float[] boxes = shipReady ? smoothWater(data, dt) : EMPTY_BOXES;
        float seconds = (float) waveSeconds;

        // 运行日志降到 5 秒一条（原 1Hz）：长会话刷屏，日志体积和字符串分配都不划算。
        if (level.getGameTime() - lastDebugTick >= 100L) {
            lastDebugTick = level.getGameTime();
            BarotraumaMod.LOGGER.info("[water] 画水面：ship={} physical={} boxes={} breaches={} streams={} sideFlows={} delta=({},{},{}) firstSurface={}",
                    data.shipId(), data.physical(), data.boxes().length / 7, data.breaches().length / 9,
                    data.streams().length / ShipWaterStatePacket.STREAM_STRIDE,
                    data.sideFlows().length / ShipWaterStatePacket.SIDE_FLOW_STRIDE,
                    dx, dy, dz,
                    data.boxes().length >= 7 ? data.boxes()[6] + dy : Float.NaN);
        }

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(LEVEL_PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            WaterStats water = drawWater(event, level, data, boxes, dt, seconds, ship, cameraPos);
            if (debug && shipReady) {
                try {
                    MarkerStats markers = drawDiagnostics(camera, cameraPos);
                    if (level.getGameTime() - lastMatrixTick >= 20L) {
                        lastMatrixTick = level.getGameTime();
                        logDiagnostics(event, mc, camera, cameraPos, data, ship, dx, dy, dz, water, markers);
                    }
                } catch (RuntimeException failure) {
                    logDiagnosticFailure("诊断渲染失败", failure);
                }
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = ShipWaterStatePacket.empty();
        capturedLevel = renderedLevel = null;
        particleLevel = null;
        openingParticleTick = Long.MIN_VALUE;
        ShipBubbleParticle.setShipCarry(0, 0, 0);
        capturedCamera = null;
        if (RenderSystem.isOnRenderThread()) {
            ShipWaterMaterial.release();
        } else {
            RenderSystem.recordRenderCall(ShipWaterMaterial::release);
        }
    }

    private static boolean diagnosticErrorLogged;

    private static void logDiagnosticFailure(String what, Throwable failure) {
        if (!diagnosticErrorLogged) {
            diagnosticErrorLogged = true;
            BarotraumaMod.LOGGER.warn("[water] {}（只记录一次；主水面路径不受影响）", what, failure);
        }
    }

    static ClientShip findClientShip(ClientLevel level, long shipId) {
        for (Ship candidate : org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(level).getAllShips()) {
            if (candidate.getId() == shipId && candidate instanceof ClientShip client) {
                return client;
            }
        }
        return null;
    }

    private record WaterStats(int triangles, int flowTriangles, int wetColumns,
                              boolean refracted, boolean eyeInWater, boolean eyeInSea) {
    }

    private record WaterPlacement(Matrix4d toCamera, BlockPos offset) {
    }

    private static WaterPlacement waterPlacement(ShipWaterStatePacket data, ShipTransform transform, Vec3 camera) {
        if (transform == null) {
            return new WaterPlacement(new Matrix4d().translation(-camera.x, -camera.y, -camera.z), BlockPos.ZERO);
        }
        Vector3dc anchor = transform.getPositionInShip();
        BlockPos offset = new BlockPos((int) Math.round(anchor.x() - data.anchorX()),
                (int) Math.round(anchor.y() - data.anchorY()), (int) Math.round(anchor.z() - data.anchorZ()));
        return new WaterPlacement(waterToCamera(transform, camera, data), offset);
    }

    private static final MultiBufferSource.BufferSource DIAG_BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(256));
    private static final BufferBuilder RAW_QUAD_BUILDER = new BufferBuilder(256);
    private static final float[] EMPTY_BOXES = new float[0];

    private static WaterStats drawWater(RenderLevelStageEvent event, ClientLevel level,
                                        ShipWaterStatePacket data, float[] boxes, float dt,
                                        float seconds, ClientShip ship, Vec3 cameraPos) {
        if (boxes.length < BOX_STRIDE) {
            boolean sea = UnderwaterEffects.isOpenOcean(level, cameraPos);
            var ocean = sea ? ShipWaterMaterial.draw(null, null, LEVEL_VIEW, LEVEL_PROJECTION, null,
                    0, 0, 0, seconds, cameraPos, false, true) : new ShipWaterMaterial.Result(0, 0, false);
            return new WaterStats(0, 0, 0, ocean.refracted(), false, sea);
        }
        WaterPlacement placement = waterPlacement(data, ship == null ? null : ship.getRenderTransform(), cameraPos);
        Matrix4d toCamera = placement.toCamera();
        BlockPos packetToShipyard = placement.offset();

        float[] renderBoxes = withOpenBreaches(level, packetToShipyard, boxes, data.breaches());
        float[] sideFlows = sideFlowsAtOpenings(level, packetToShipyard, data.sideFlows());
        boolean poolInView = waterInView(event, renderBoxes, toCamera, cameraPos);
        // 船完全在视锥外时，非空流数组也不该触发整船网格重建：先对流光端点做一次
        // 保守视锥测试，只有池面或任一流路可能可见时才更新。
        boolean flowsVisible = poolInView
                || flowsInView(event, data.streams(), sideFlows, toCamera, cameraPos);
        int wetColumns = 0;
        if (flowsVisible) {
            collectStreamImpacts(data.streams(), sideFlows);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            wetColumns = waterMesh.update(renderBoxes, dt, seconds, streamImpacts, streamImpactCount,
                    (x, z, nextX, nextZ, low, high) -> connectsWater(level, packetToShipyard, cursor,
                            x, z, nextX, nextZ, low, high));
        }
        boolean eyeWet = wetColumns > 0 && cameraInWater(level, toCamera, packetToShipyard, cameraPos);
        boolean eyeInSea = !eyeWet && UnderwaterEffects.isOpenOcean(level, cameraPos);
        boolean drawPool = wetColumns > 0 && (poolInView || eyeWet);
        ShipWaterFlowMesh flows = new ShipWaterFlowMesh(waterMesh, data.streams(), sideFlows, seconds,
                mouth -> hatchLongAxis(level, packetToShipyard, mouth));
        ShipWaterMaterial.Result surface = drawPool || flows.hasPaths() || eyeInSea
                ? ShipWaterMaterial.draw(drawPool ? waterMesh : null, flows, LEVEL_VIEW, LEVEL_PROJECTION,
                        toCamera, boxes[0], boxes[1], boxes[2], seconds, cameraPos, eyeWet, eyeInSea)
                : new ShipWaterMaterial.Result(0, 0, false);

        return new WaterStats(surface.poolTriangles(), surface.flowTriangles(), wetColumns,
                surface.refracted(), eyeWet, eyeInSea);
    }

    private static boolean cameraInWater(ClientLevel level, Matrix4d toCamera, BlockPos offset, Vec3 camera) {
        Vector3d local = new Matrix4d(toCamera).invert().transformPosition(new Vector3d());
        if (!waterMesh.contains(local.x, local.y, local.z) || cameraInsideHull(level, camera)) {
            return false;
        }
        Vec3 yard = new Vec3(local.x + offset.getX(), local.y + offset.getY(), local.z + offset.getZ());
        return !cameraInsideHull(level, yard);
    }

    private static boolean cameraInsideHull(ClientLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState block = level.getBlockState(pos);
        // Rendered equipment uses scene depth. Its coarse collision box can cover visible gaps.
        return (!ModShipBlocks.isEquipment(block) || ModShipBlocks.sealsHull(block)
                || block.isCollisionShapeFullBlock(level, pos)) && OceanEnvironment.insideCollision(level, pos, point);
    }

    private static boolean connectsWater(ClientLevel level, BlockPos offset, BlockPos.MutableBlockPos cursor,
                                         int x, int z, int nextX, int nextZ, float low, float high) {
        return waterEdgeOpenAt(level, offset, cursor, x, z, nextX, nextZ, low)
                && waterEdgeOpenAt(level, offset, cursor, nextX, nextZ, x, z, low)
                && (low == high || (waterEdgeOpenAt(level, offset, cursor, x, z, nextX, nextZ, high)
                && waterEdgeOpenAt(level, offset, cursor, nextX, nextZ, x, z, high)));
    }

    private static boolean waterEdgeOpenAt(ClientLevel level, BlockPos offset, BlockPos.MutableBlockPos cursor,
                                           int x, int z, int nextX, int nextZ, float surface) {
        double y = surface + offset.getY();
        // 水面正好落在整格边界（y 是整数）时，floor(y) 会读到水面之上的顶板格，
        // 把这条共享边误判成墙、在连续水面上留下台阶。往水侧退 1e-4 再取格子。
        cursor.set(x + offset.getX(), (int) Math.floor(y - 1.0E-4D), z + offset.getZ());
        BlockState block = level.getBlockState(cursor);
        if (!(block.getBlock() instanceof BulkheadDoorBlock)) {
            return block.isAir() || !block.isCollisionShapeFullBlock(level, cursor);
        }
        if (!block.getValue(BulkheadDoorBlock.OPEN)) {
            return false;
        }
        Vec3 from = new Vec3(cursor.getX() + 0.5D, y, cursor.getZ() + 0.5D);
        return block.getShape(level, cursor).clip(from, from.add(nextX - x, 0.0D, nextZ - z), cursor) == null;
    }

    /** 空的破口格接进同一张网格。完整或仍显示模型的船壳由场景深度遮挡，来水仍绘制路径。 */
    private static float[] withOpenBreaches(ClientLevel level, BlockPos offset, float[] boxes, float[] breaches) {
        int added = breaches.length / BREACH_STRIDE * BOX_STRIDE;
        if (added == 0) {
            return boxes;
        }
        if (breachBoxes.length != boxes.length + added) {
            breachBoxes = new float[boxes.length + added];
        }
        System.arraycopy(boxes, 0, breachBoxes, 0, boxes.length);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int row = 0; row < breaches.length; row += BREACH_STRIDE) {
            int next = boxes.length + row / BREACH_STRIDE * BOX_STRIDE;
            int x = (int) Math.floor(breaches[row]);
            int y = (int) Math.floor(breaches[row + 1]);
            int z = (int) Math.floor(breaches[row + 2]);
            int source = Math.round(breaches[row + 3]);
            pos.set(x + offset.getX(), y + offset.getY(), z + offset.getZ());
            float surface = y;
            if (source >= 0 && source < boxes.length && source % BOX_STRIDE == 0
                    && level.getBlockState(pos).isAir()) {
                surface = Math.max(y, Math.min(y + 1.0F, boxes[source + 6]));
            }
            breachBoxes[next] = breachBoxes[next + 3] = x;
            breachBoxes[next + 1] = breachBoxes[next + 4] = y;
            breachBoxes[next + 2] = breachBoxes[next + 5] = z;
            breachBoxes[next + 6] = surface;
        }
        return breachBoxes;
    }

    private static boolean waterInView(RenderLevelStageEvent event, float[] boxes, Matrix4d toCamera, Vec3 camera) {
        if (event.getFrustum() == null) {
            return true;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < boxes.length; i += BOX_STRIDE) {
            if (boxes[i + 6] <= boxes[i + 1] + ShipWaterMesh.MIN_DEPTH) {
                continue;
            }
            minX = Math.min(minX, boxes[i]);
            minY = Math.min(minY, boxes[i + 1]);
            minZ = Math.min(minZ, boxes[i + 2]);
            maxX = Math.max(maxX, boxes[i + 3] + 1.0D);
            maxY = Math.max(maxY, Math.min(boxes[i + 6] + 0.08D, boxes[i + 4] + 1.0D));
            maxZ = Math.max(maxZ, boxes[i + 5] + 1.0D);
        }
        if (!Double.isFinite(minX)) {
            return false;
        }
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();
        toCamera.transformAab(minX, minY, minZ, maxX, maxY, maxZ, min, max);
        if (min.x <= 0 && max.x >= 0 && min.y <= 0 && max.y >= 0 && min.z <= 0 && max.z >= 0) {
            return true;
        }
        return event.getFrustum().isVisible(new AABB(min.x + camera.x, min.y + camera.y, min.z + camera.z,
                max.x + camera.x, max.y + camera.y, max.z + camera.z));
    }

    /**
     * 流路是否可能出现在画面里。所有流端点合成一个保守包围盒做视锥测试：
     * 只有“池面不可见、流路也全在视野外”时才跳过整船网格重建。
     */
    private static boolean flowsInView(RenderLevelStageEvent event, float[] streams, float[] sideFlows,
                                       Matrix4d toCamera, Vec3 camera) {
        if (streams.length == 0 && sideFlows.length == 0) {
            return false;
        }
        if (event.getFrustum() == null) {
            return true;
        }
        int boxes = (streams.length / ShipWaterStatePacket.STREAM_STRIDE) * 2
                + sideFlows.length / ShipWaterStatePacket.SIDE_FLOW_STRIDE;
        float[] flowBoxes = new float[Math.max(0, boxes) * BOX_STRIDE];
        int at = 0;
        for (int i = 0; i + ShipWaterStatePacket.STREAM_STRIDE <= streams.length;
             i += ShipWaterStatePacket.STREAM_STRIDE) {
            at = flowPointBox(flowBoxes, at, streams[i], streams[i + 1], streams[i + 2]);
            at = flowPointBox(flowBoxes, at, streams[i + 3], streams[i + 4], streams[i + 5]);
        }
        for (int i = 0; i + ShipWaterStatePacket.SIDE_FLOW_STRIDE <= sideFlows.length;
             i += ShipWaterStatePacket.SIDE_FLOW_STRIDE) {
            at = flowPointBox(flowBoxes, at, sideFlows[i], sideFlows[i + 1], sideFlows[i + 2]);
        }
        return at > 0 && waterInView(event, flowBoxes, toCamera, camera);
    }

    /** 把流路上的一个点包成 1×1×1 的盒子（水面设到顶，免得被 dry 过滤掉）。 */
    private static int flowPointBox(float[] boxes, int at, float x, float y, float z) {
        if (at + BOX_STRIDE > boxes.length) {
            return at;
        }
        boxes[at] = x;
        boxes[at + 1] = y;
        boxes[at + 2] = z;
        boxes[at + 3] = x;
        boxes[at + 4] = y;
        boxes[at + 5] = z;
        boxes[at + 6] = y + 1.0F;
        return at + BOX_STRIDE;
    }

    /** 侧流行落在两格之间。实船的门板在门格中线，水和水花也从那里开始。 */
    private static float[] sideFlowsAtOpenings(ClientLevel level, BlockPos offset, float[] rows) {
        if (rows.length == 0) {
            return rows;
        }
        float[] visible = new float[rows.length];
        int out = 0;
        int stride = ShipWaterStatePacket.SIDE_FLOW_STRIDE;
        for (int i = 0; i < rows.length; i += stride) {
            float x = rows[i];
            float y = rows[i + 1];
            float z = rows[i + 2];
            float dx = rows[i + 3];
            float dz = rows[i + 4];
            Direction.Axis axis = dx != 0.0F ? Direction.Axis.X : Direction.Axis.Z;
            BlockState from = level.getBlockState(BlockPos.containing(x - dx * 0.5F, y, z - dz * 0.5F).offset(offset));
            BlockState to = level.getBlockState(BlockPos.containing(x + dx * 0.5F, y, z + dz * 0.5F).offset(offset));
            if (!allowsSideFlow(from, axis) || !allowsSideFlow(to, axis)) {
                continue;
            }
            float shift = 0.0F;
            if (from.getBlock() instanceof BulkheadDoorBlock) {
                shift = -0.5F;
            } else if (to.getBlock() instanceof BulkheadDoorBlock) {
                shift = 0.5F;
            }
            System.arraycopy(rows, i, visible, out, stride);
            visible[out] = x + dx * shift;
            visible[out + 2] = z + dz * shift;
            out += stride;
        }
        out = joinBulkheadFlowRows(level, offset, visible, out);
        return out == visible.length ? visible : Arrays.copyOf(visible, out);
    }

    /** The two wet block faces of a single tall doorway can feed one continuous fan. */
    private static int joinBulkheadFlowRows(ClientLevel level, BlockPos offset, float[] rows, int end) {
        int stride = ShipWaterStatePacket.SIDE_FLOW_STRIDE;
        if (end <= stride) {
            return end;
        }
        Map<BlockPos, Integer> upperParts = new HashMap<>();
        for (int i = 0; i < end; i += stride) {
            BlockPos pos = BlockPos.containing(rows[i], rows[i + 1], rows[i + 2]).offset(offset);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BulkheadDoorBlock
                    && state.getValue(BulkheadDoorBlock.PART) == BulkheadDoorBlock.Part.UPPER) {
                upperParts.put(pos, i);
            }
        }
        if (upperParts.isEmpty()) {
            return end;
        }
        for (int i = 0; i < end; i += stride) {
            if (rows[i + 5] < rows[i + 1] + 0.93F || rows[i + 6] >= rows[i + 5] - 0.025F) {
                continue;
            }
            BlockPos lower = BlockPos.containing(rows[i], rows[i + 1], rows[i + 2]).offset(offset);
            Integer upper = upperParts.get(lower.above());
            if (upper == null || rows[upper + 7] <= 0.0F || rows[upper + 3] != rows[i + 3]
                    || rows[upper + 4] != rows[i + 4] || Math.abs(rows[upper + 6] - rows[i + 6]) > 0.0625F) {
                continue;
            }
            BlockState state = level.getBlockState(lower);
            if (state.getBlock() instanceof BulkheadDoorBlock
                    && state.getValue(BulkheadDoorBlock.PART) == BulkheadDoorBlock.Part.LOWER) {
                rows[i + 5] = rows[upper + 5];
                rows[i + 7] = Math.max(rows[i + 7], rows[upper + 7]);
                rows[upper + 7] = 0.0F;
            }
        }
        int out = 0;
        for (int i = 0; i < end; i += stride) {
            if (rows[i + 7] > 0.0F) {
                System.arraycopy(rows, i, rows, out, stride);
                out += stride;
            }
        }
        return out;
    }

    private static boolean allowsSideFlow(BlockState state, Direction.Axis axis) {
        if (state.getBlock() instanceof BulkheadDoorBlock) {
            return state.getValue(BulkheadDoorBlock.OPEN)
                    && state.getValue(BulkheadDoorBlock.FACING).getAxis() == axis;
        }
        return !(state.getBlock() instanceof HatchBlock);
    }

    private static Direction.Axis hatchLongAxis(ClientLevel level, BlockPos offset, Vec3 mouth) {
        BlockPos cell = BlockPos.containing(mouth.x, mouth.y + 0.001D, mouth.z).offset(offset);
        BlockState state = level.getBlockState(cell);
        return state.getBlock() instanceof HatchBlock
                ? state.getValue(HatchBlock.FACING).getClockWise().getAxis() : null;
    }

    private static final Vector3d WATER_SCRATCH = new Vector3d();

    private static void waterVertex(VertexConsumer vc, Matrix4f pose, Matrix4d toCamera,
                                    float x, float y, float z,
                                    float r, float g, float b, float a) {
        WATER_SCRATCH.set(x, y, z);
        toCamera.transformPosition(WATER_SCRATCH);
        vc.vertex(pose, (float) WATER_SCRATCH.x, (float) WATER_SCRATCH.y, (float) WATER_SCRATCH.z)
                .color(r, g, b, a)
                .endVertex();
    }

    // ------------------------------------------------------------------ 来水、飞溅和气泡

    @SubscribeEvent
    public static void tickOpeningParticles(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !RENDER_WATER) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        ClientLevel level = game.level;
        Camera camera = game.gameRenderer.getMainCamera();
        if (game.isPaused() || level == null || game.player == null || !camera.isInitialized()) {
            return;
        }
        long tick = level.getGameTime();
        if (level == particleLevel && openingParticleTick == tick) {
            return;
        }
        particleLevel = level;
        openingParticleTick = tick;
        ShipWaterStatePacket data = state;
        ClientShip ship = data.physical() ? findClientShip(level, data.shipId()) : null;
        Vector3dc velocity = ship == null ? null : ship.getVelocity();
        if (velocity == null) {
            ShipBubbleParticle.setShipCarry(0, 0, 0);
        } else {
            ShipBubbleParticle.setShipCarry(velocity.x(), velocity.y(), velocity.z());
        }
        if ((tick & 1L) != 0L || data.boxes().length < BOX_STRIDE || (data.physical() && ship == null)) {
            return;
        }
        int budget = switch (game.options.particles().get()) {
            case ALL -> 24;
            case DECREASED -> 12;
            case MINIMAL -> 0;
        };
        if (budget == 0) {
            return;
        }
        Vec3 eye = camera.getPosition();
        WaterPlacement placement = waterPlacement(data, ship == null ? null : ship.getTransform(), eye);
        budget = spawnStreamSplashes(level, placement, eye, data.streams(), budget);
        float[] sideFlows = sideFlowsAtOpenings(level, placement.offset(), data.sideFlows());
        budget = spawnSideFlowParticles(level, placement.toCamera(), eye, sideFlows, budget);
        spawnBreachBubbles(level, placement.toCamera(), eye, data.breaches(), data.boxes(), budget);
    }

    /** 返回剩余预算：三类开口粒子共用同一份配额，附近同时开口也不会超发。 */
    private static int spawnStreamSplashes(ClientLevel level, WaterPlacement placement, Vec3 camera,
                                           float[] flows, int budget) {
        var random = level.random;
        for (int i = 0; i < flows.length && budget > 0; i += ShipWaterStatePacket.STREAM_STRIDE) {
            double drop = flows[i + 1] - flows[i + 4];
            if (drop < 0.16D) {
                continue;
            }
            Vec3 from = new Vec3(flows[i], flows[i + 1], flows[i + 2]);
            Vec3 end = new Vec3(flows[i + 3], flows[i + 4], flows[i + 5]);
            Vec3 across = ShipWaterFlowMesh.fallAcross(from, end, hatchLongAxis(level, placement.offset(), from));
            Vec3 normal = new Vec3(across.z, 0, -across.x);
            float strength = flows[i + 6];
            double width = ShipWaterFlowMesh.fallHalfWidth(strength);
            int drops = Math.min(budget, 1 + (int) (strength * 4.0F));
            for (int j = 0; j < drops; j++) {
                double edge = random.nextDouble() * 2.0D - 1.0D;
                double start = random.nextDouble() * Math.min(0.27D, 0.68D / drop);
                Vec3 at = from.lerp(end, start).add(across.scale(edge * width))
                        .add(normal.scale((random.nextDouble() - 0.5D) * (0.09D + strength * 0.10D)));
                Vec3 velocity = across.scale(edge * 0.065D)
                        .add(normal.scale((random.nextDouble() - 0.48D) * 0.13D))
                        .add(0, -(0.075D + strength * 0.14D + random.nextDouble() * 0.095D), 0);
                budget -= spawnOpeningParticle(level, placement.toCamera(), camera,
                        ModParticles.SHIP_SPRAY.get(), at, velocity);
            }
            if (budget > 0 && drop >= 0.38D && (strength >= 0.1F || random.nextFloat() < strength * 4.0F)) {
                double edge = random.nextDouble() * 2.0D - 1.0D;
                Vec3 at = end.add(across.scale(edge * width)).add(0, 0.081D, 0);
                Vec3 velocity = across.scale(edge * (0.075D + strength * 0.10D))
                        .add(normal.scale((random.nextDouble() - 0.5D) * 0.25D))
                        .add(0, 0.115D + random.nextDouble() * (0.06D + strength * 0.16D), 0);
                budget -= spawnOpeningParticle(level, placement.toCamera(), camera,
                        ModParticles.SHIP_SPRAY.get(), at, velocity);
            }
        }
        return budget;
    }

    private static int spawnSideFlowParticles(ClientLevel level, Matrix4d toCamera, Vec3 camera,
                                               float[] flows, int budget) {
        var random = level.random;
        for (int i = 0; i < flows.length && budget > 0; i += ShipWaterStatePacket.SIDE_FLOW_STRIDE) {
            float sill = flows[i + 1];
            float dirX = flows[i + 3];
            float dirZ = flows[i + 4];
            float x = flows[i] + dirX * 0.34F;
            float z = flows[i + 2] + dirZ * 0.34F;
            float top = flows[i + 5];
            float surface = flows[i + 6];
            float strength = flows[i + 7];
            float exposedDepth = top - Math.max(sill, surface);
            boolean exposed = exposedDepth > 0.025F;
            int count = Math.min(budget, 1 + (int) (strength * (exposed ? 4.0F : 2.0F)));
            for (int c = 0; c < count; c++) {
                double speed = 0.075D + strength * 0.19D + random.nextDouble() * 0.08D;
                double edge = random.nextDouble() * 2.0D - 1.0D;
                double spread = edge * (0.14D + strength * 0.21D);
                double y = exposed ? top - exposedDepth * (0.15D + random.nextDouble() * 0.77D)
                        : Math.min(sill + Math.max(0.025D, (top - sill) * random.nextDouble()), surface - 0.065D);
                Vec3 at = new Vec3(x - dirZ * spread, y, z + dirX * spread);
                double slip = edge * (0.065D + strength * 0.10D);
                Vec3 velocity = new Vec3(dirX * speed - dirZ * slip,
                        exposed ? 0.015D + random.nextDouble() * 0.075D : 0.025D,
                        dirZ * speed + dirX * slip);
                budget -= spawnOpeningParticle(level, toCamera, camera,
                        exposed ? ModParticles.SHIP_SPRAY.get() : ModParticles.SHIP_BUBBLE.get(), at, velocity);
            }
        }
        return budget;
    }

    /** The moving spray follows the flow paths. Bubbles start inside the wet receiving cell of a breach. */
    private static int spawnBreachBubbles(ClientLevel level, Matrix4d toCamera, Vec3 camera, float[] breaches,
                                           float[] boxes, int budget) {
        int holes = breaches.length / BREACH_STRIDE;
        if (holes == 0) {
            return budget;
        }
        var random = level.random;
        int first = Math.floorMod(breachRoundRobin++, holes);
        for (int n = 0; n < holes && budget > 0; n++) {
            int row = (first + n) % holes * BREACH_STRIDE;
            int box = Math.round(breaches[row + 3]);
            if (box < 0 || box + 6 >= boxes.length) {
                continue;
            }
            Vec3 inward = switch (Math.round(breaches[row + 8])) {
                case 1 -> new Vec3(0, -1, 0);
                case 2 -> new Vec3(0, 1, 0);
                default -> new Vec3(breaches[row + 4], 0, breaches[row + 5]).normalize();
            };
            Vec3 mouth = new Vec3(breaches[row], breaches[row + 1], breaches[row + 2]).add(inward.scale(0.66D));
            if (mouth.y >= boxes[box + 6] - 0.03D || inward.lengthSqr() < 0.01D) {
                continue;
            }
            Vec3 at = mouth.add((random.nextDouble() - 0.5D) * 0.21D,
                    (random.nextDouble() - 0.5D) * 0.14D, (random.nextDouble() - 0.5D) * 0.21D);
            if (!Boolean.FALSE.equals(ShipWaterBoxes.dryAt(boxes, at.x, at.y, at.z))) {
                continue;
            }
            Vec3 motion = inward.scale(0.055D + random.nextDouble() * 0.14D).add(0, 0.015D, 0);
            budget -= spawnOpeningParticle(level, toCamera, camera, ModParticles.SHIP_BUBBLE.get(), at, motion);
        }
        return budget;
    }

    private static int spawnOpeningParticle(ClientLevel level, Matrix4d toCamera, Vec3 camera, ParticleOptions type,
                                             Vec3 local, Vec3 motion) {
        toCamera.transformPosition(WATER_SCRATCH.set(local.x, local.y, local.z));
        if (WATER_SCRATCH.lengthSquared() > 32.0D * 32.0D) {
            return 0;
        }
        Vec3 at = camera.add(WATER_SCRATCH.x, WATER_SCRATCH.y, WATER_SCRATCH.z);
        if (type == ModParticles.SHIP_BUBBLE.get() && !OceanEnvironment.isOceanWaterAt(level, at)) {
            return 0;
        }
        toCamera.transformDirection(WATER_SCRATCH.set(motion.x, motion.y, motion.z));
        level.addParticle(type, at.x, at.y, at.z, WATER_SCRATCH.x, WATER_SCRATCH.y, WATER_SCRATCH.z);
        return 1;
    }

    // ------------------------------------------------------------------ 帧率平滑

    /**
     * 帧间隔（秒），同时推进波浪相位。
     *
     * <p>上限 0.1s：切窗口/卡顿后不要一帧跨过很长一段波浪；水面平滑也用它，
     * 保证 10 Hz 的网络数据在 60/144/240 Hz 下都是连续上升。</p>
     */
    private static float frameDeltaSeconds() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L
                ? 1.0F / 60.0F
                : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        dt = Math.max(0.0F, Math.min(0.1F, dt));
        waveSeconds += dt;
        return dt;
    }

    /**
     * 把服务端 10 Hz 的水面高度插值到每一帧。
     *
     * <p>只改客户端显示。{@link #dryAt} 仍然使用原始包，干湿/氧气判定
     * 不受视觉平滑影响。bbox 变了说明重扫过，直接对齐，不做跨舱动画。</p>
     */
    private static float[] smoothWater(ShipWaterStatePacket data, float dt) {
        float[] source = data.boxes();
        boolean otherShip = smoothedShipId != data.shipId() || smoothedPhysical != data.physical();
        if (otherShip || (source.length < BOX_STRIDE && smoothedBoxes.length >= BOX_STRIDE)) {
            waterMesh = new ShipWaterMesh();
        }
        if (source.length < BOX_STRIDE) {
            smoothedBoxes = source;
            smoothedShipId = data.shipId();
            smoothedPhysical = data.physical();
            return source;
        }
        if (smoothedBoxes.length != source.length || otherShip) {
            smoothedBoxes = source.clone();
            smoothedShipId = data.shipId();
            smoothedPhysical = data.physical();
            return smoothedBoxes;
        }
        // 指数趋近：时间常数约 100ms，和 10 Hz 的同步步长对齐，水面连续而不是台阶。
        float alpha = 1.0F - (float) Math.exp(-dt * 10.0F);
        for (int i = 0; i + 6 < smoothedBoxes.length; i += 7) {
            boolean sameBox = smoothedBoxes[i] == source[i]
                    && smoothedBoxes[i + 1] == source[i + 1]
                    && smoothedBoxes[i + 2] == source[i + 2]
                    && smoothedBoxes[i + 3] == source[i + 3]
                    && smoothedBoxes[i + 4] == source[i + 4]
                    && smoothedBoxes[i + 5] == source[i + 5];
            smoothedBoxes[i] = source[i];
            smoothedBoxes[i + 1] = source[i + 1];
            smoothedBoxes[i + 2] = source[i + 2];
            smoothedBoxes[i + 3] = source[i + 3];
            smoothedBoxes[i + 4] = source[i + 4];
            smoothedBoxes[i + 5] = source[i + 5];
            float target = source[i + 6];
            float current = smoothedBoxes[i + 6];
            if (!sameBox || target <= source[i + 1] || Math.abs(target - current) > 4.0F) {
                // 重扫/瞬移：直接对齐，避免水面慢慢爬过一大段。
                current = target;
            } else {
                current += (target - current) * alpha;
            }
            smoothedBoxes[i + 6] = current;
        }
        return smoothedBoxes;
    }

    // ------------------------------------------------------------------ 诊断显示

    private record MarkerStats(int productionQuads, int rawDepthOnQuads, int rawDepthOffQuads,
                               int absoluteQuads, int vertices) {
    }

    /**
     * 视线前方 2m 的三个 0.2m 测试面 + 深度对照。
     *
     * <p>所有标记都以"相机相对坐标"提交（world − camera），因为 RenderLevelStageEvent 的
     * PoseStack 只有相机旋转、没有相机平移（见 Forge 47.4.0 GameRenderer.renderLevel 与
     * LevelRenderer.renderChunkLayer）。另外画一个"绝对世界坐标"对照面，正常情况下应该
     * 出现在远处/画面外——如果它反而出现在眼前，说明我们对坐标空间的理解错了。</p>
     */
    private static MarkerStats drawDiagnostics(Camera camera, Vec3 cameraPos) {
        Vec3 forward = new Vec3(camera.getLookVector()).normalize();
        Vec3 up = new Vec3(camera.getUpVector()).normalize();
        Vec3 right = new Vec3(camera.getLeftVector()).normalize().scale(-1.0D);
        Vec3 base = cameraPos.add(forward.scale(2.0D));

        Vec3[] centers = {
                base,
                base.add(right.scale(0.30D)),
                base.add(right.scale(0.60D))
        };
        float[][] colors = {
                {1.00F, 0.25F, 0.25F, 0.90F},
                {0.25F, 1.00F, 0.35F, 0.90F},
                {0.30F, 0.55F, 1.00F, 0.90F}
        };

        // 三个位置的"现有管线"标记用一个独立 buffer，避免碰主水路径的 buffer。
        VertexConsumer productionVc = DIAG_BUFFERS.getBuffer(DiagnosticRenderType.TYPE);
        int productionQuads = 0;
        for (int i = 0; i < centers.length; i++) {
            Vec3[] quad = markerQuad(centers[i], right, up, 0.10D);
            for (Vec3 v : quad) {
                Vec3 rel = v.subtract(cameraPos);
                productionVc.vertex(LEVEL_VIEW, (float) rel.x, (float) rel.y, (float) rel.z)
                        .color(colors[i][0], colors[i][1], colors[i][2], colors[i][3])
                        .endVertex();
            }
            productionQuads++;
        }
        DIAG_BUFFERS.endBatch(DiagnosticRenderType.TYPE);

        // 同位置配对：诊断显式 QUADS 管线（NO_CULL + 深度测试开 + 不写深度）。
        int rawDepthOn = 0;
        for (int i = 0; i < centers.length; i++) {
            Vec3[] quad = markerQuad(centers[i], right, up, 0.10D);
            drawRawQuad(cameraPos, quad, colors[i][0] * 0.9F, colors[i][1] * 0.9F,
                    colors[i][2] * 0.9F, 0.55F, true, false);
            rawDepthOn++;
        }

        // 深度对照：同一位置，一个开深度测试、一个关深度测试（品红/黄）。
        Vec3 depthCenter = base.add(right.scale(0.90D));
        Vec3[] depthQuad = markerQuad(depthCenter, right, up, 0.10D);
        drawRawQuad(cameraPos, depthQuad, 1.00F, 0.20F, 0.20F, 0.90F, true, false);
        drawRawQuad(cameraPos, depthQuad, 1.00F, 0.90F, 0.10F, 0.90F, false, false);
        int rawDepthOff = 1;
        rawDepthOn++;

        // 坐标空间对照：同一个"相机前 2m"位置，但顶点直接提交绝对世界坐标。
        Vec3 absoluteCenter = base.add(right.scale(1.20D));
        Vec3[] absoluteQuad = markerQuad(absoluteCenter, right, up, 0.10D);
        drawRawQuad(Vec3.ZERO, absoluteQuad, 0.85F, 0.20F, 0.95F, 0.90F, false, false);

        int vertices = productionQuads * 4 + (rawDepthOn + rawDepthOff) * 4 + 4;
        return new MarkerStats(productionQuads, rawDepthOn, rawDepthOff, 1, vertices);
    }

    /** 相机右/上平面上的一个 0.2m × 0.2m 四边形（世界坐标）。 */
    private static Vec3[] markerQuad(Vec3 center, Vec3 right, Vec3 up, double half) {
        return new Vec3[]{
                center.add(right.scale(-half)).add(up.scale(-half)),
                center.add(right.scale(half)).add(up.scale(-half)),
                center.add(right.scale(half)).add(up.scale(half)),
                center.add(right.scale(-half)).add(up.scale(half))
        };
    }

    /** 独立于 RenderType 的深度测试对照。 */
    private static void drawRawQuad(Vec3 cameraPos, Vec3[] worldQuad,
                                    float r, float g, float b, float a,
                                    boolean depthTest, boolean depthWrite) {
        if (RAW_QUAD_BUILDER.building()) {
            RAW_QUAD_BUILDER.discard();
        }
        BufferBuilder builder = RAW_QUAD_BUILDER;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Vec3 v : worldQuad) {
            Vec3 rel = v.subtract(cameraPos);
            builder.vertex(LEVEL_VIEW, (float) rel.x, (float) rel.y, (float) rel.z)
                    .color(r, g, b, a)
                    .endVertex();
        }
        BufferBuilder.RenderedBuffer rendered = builder.end();

        var previousShader = RenderSystem.getShader();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(depthWrite);
        try {
            BufferUploader.drawWithShader(rendered);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
        }
    }

    // ------------------------------------------------------------------ 诊断日志

    private static void logDiagnostics(RenderLevelStageEvent event, Minecraft mc, Camera camera, Vec3 cameraPos,
                                       ShipWaterStatePacket data, ClientShip ship,
                                       float dx, float dy, float dz,
                                       WaterStats water, MarkerStats markers) {
        StringBuilder sb = new StringBuilder(2048);
        long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        sb.append("\n[water-diag] frame=").append(++frameSequence)
                .append(" gameTime=").append(gameTime)
                .append(" renderTick=").append(event.getRenderTick())
                .append(" partialTick=").append(fmt(partialTick))
                .append(" stage=").append(event.getStage());
        sb.append("\n[water-diag] camera=").append(fmt(cameraPos))
                .append(" cameraEntity=").append(describeEntity(mc.getCameraEntity()))
                .append(" camType=").append(mc.options.getCameraType())
                .append(" eye=").append(fmt(mc.player.getEyePosition(partialTick)))
                .append(" playerPos=").append(fmt(mc.player.position()))
                .append(" playerBlockY=").append(mc.player.blockPosition().getY())
                .append(" playerY=").append(fmt(mc.player.getY()));
        sb.append("\n[water-diag] packet: physical=").append(data.physical())
                .append(" shipId=").append(data.shipId())
                .append(" boxes=").append(data.boxes().length / 7)
                .append(" anchor=").append(fmt(data.anchorX(), data.anchorY(), data.anchorZ()))
                .append(" delta=").append(fmt(dx, dy, dz));
        if (ship != null) {
            ShipTransform render = ship.getRenderTransform();
            ShipTransform current = ship.getTransform();
            ShipTransform previous = ship.getPrevTickTransform();
            sb.append("\n[water-diag] ship=").append(ship.getId())
                    .append(" renderPos=").append(fmt(render.getPositionInWorld()))
                    .append(" renderPosInShip=").append(fmt(render.getPositionInShip()))
                    .append(" renderRot=").append(fmt(render.getShipToWorldRotation()))
                    .append(" renderScale=").append(fmt(render.getShipToWorldScaling()))
                    .append(" currentPos=").append(fmt(current.getPositionInWorld()))
                    .append(" currentPosInShip=").append(fmt(current.getPositionInShip()))
                    .append(" currentRot=").append(fmt(current.getShipToWorldRotation()))
                    .append(" currentScale=").append(fmt(current.getShipToWorldScaling()))
                    .append(" prevPos=").append(fmt(previous.getPositionInWorld()))
                    .append(" prevPosInShip=").append(fmt(previous.getPositionInShip()))
                    .append(" prevRot=").append(fmt(previous.getShipToWorldRotation()))
                    .append(" prevScale=").append(fmt(previous.getShipToWorldScaling()));
            appendReferenceMapping(sb, render, data);
        }
        if (data.boxes().length >= 7) {
            int count = Math.min(3, data.boxes().length / 7);
            for (int i = 0; i < count; i++) {
                int o = i * 7;
                sb.append("\n[water-diag] box[").append(i).append("] min=(")
                        .append(fmt(data.boxes()[o])).append(',')
                        .append(fmt(data.boxes()[o + 1])).append(',')
                        .append(fmt(data.boxes()[o + 2])).append(") max=(")
                        .append(fmt(data.boxes()[o + 3])).append(',')
                        .append(fmt(data.boxes()[o + 4])).append(',')
                        .append(fmt(data.boxes()[o + 5])).append(") surface=")
                        .append(fmt(data.boxes()[o + 6]))
                        .append(" surface+dy=").append(fmt(data.boxes()[o + 6] + dy));
            }
        }
        sb.append("\n[water-diag] main: refracted=").append(water.refracted())
                .append(" wetColumns=").append(water.wetColumns())
                .append(" triangles=").append(water.triangles())
                .append(" flowTriangles=").append(water.flowTriangles())
                .append(" eyeInWater=").append(water.eyeInWater())
                .append(" eyeInSea=").append(water.eyeInSea())
                .append(" stage=").append(event.getStage());
        if (markers != null) {
            sb.append("\n[water-diag] markers: type=").append(DiagnosticRenderType.TYPE)
                    .append(" productionQuads=").append(markers.productionQuads())
                    .append(" rawQuadsDepthOn=").append(markers.rawDepthOnQuads())
                    .append(" rawQuadsDepthOff=").append(markers.rawDepthOffQuads())
                    .append(" absoluteWorldQuads=").append(markers.absoluteQuads())
                    .append(" vertices=").append(markers.vertices())
                    .append(" flush=").append("production.endBatch+raw BufferUploader.drawWithShader")
                    .append(" stage=").append(event.getStage());
        }
        // 矩阵每 100 tick（5 秒）一次，避免刷屏。
        boolean matrixFrame = (gameTime % 100L) < 20L;
        if (matrixFrame) {
            appendMatrix(sb, "cpuView", LEVEL_VIEW);
            appendMatrix(sb, "cpuProjection", LEVEL_PROJECTION);
            appendMatrix(sb, "gpuModelView", RenderSystem.getModelViewMatrix());
            appendMatrix(sb, "gpuProjection", RenderSystem.getProjectionMatrix());
        }
        BarotraumaMod.LOGGER.info(sb.toString());
    }

    /**
     * 用一个具体坐标验证"装配原坐标 → 船坞坐标 → render shipToWorld"的对齐。
     *
     * <p>参考点取第一个水面盒的 min 角（装配原坐标）。船坞偏移由
     * {@code renderTransform.positionInShip − anchor} 反推（装配时若旋转为单位阵，
     * 这个关系成立）。同时给出"仅平移 delta"的结果，两者之差就是旋转带来的偏差。</p>
     */
    private static void appendReferenceMapping(StringBuilder sb, ShipTransform render, ShipWaterStatePacket data) {
        if (data.boxes().length < 7) {
            return;
        }
        Vector3dc positionInShip = render.getPositionInShip();
        Vector3d reference = new Vector3d(data.boxes()[0], data.boxes()[1], data.boxes()[2]);
        Vector3d yardOffset = new Vector3d(positionInShip)
                .sub(data.anchorX(), data.anchorY(), data.anchorZ());
        Vector3d yard = new Vector3d(reference).add(yardOffset);
        Vector3d viaShipToWorld = render.getShipToWorld().transformPosition(new Vector3d(yard), new Vector3d());
        Vector3d viaDelta = new Vector3d(reference)
                .add(render.getPositionInWorld())
                .sub(data.anchorX(), data.anchorY(), data.anchorZ());
        Vector3d diff = new Vector3d(viaShipToWorld).sub(viaDelta);
        Vector3d shipOrigin = render.getShipToWorld()
                .transformPosition(new Vector3d(positionInShip), new Vector3d());
        Vector3d originDiff = shipOrigin.sub(render.getPositionInWorld());
        sb.append("\n[water-diag] ref: orig=").append(fmt(reference))
                .append(" yardOffset=").append(fmt(yardOffset))
                .append(" yard=").append(fmt(yard))
                .append(" viaShipToWorld=").append(fmt(viaShipToWorld))
                .append(" viaDelta=").append(fmt(viaDelta))
                .append(" diff=").append(fmt(diff))
                .append(" shipToWorld(posInShip)-posInWorld=").append(fmt(originDiff));
    }

    private static Matrix4d waterToCamera(ShipTransform render, Vec3 cameraPos, ShipWaterStatePacket data) {
        Vector3dc positionInShip = render.getPositionInShip();
        Matrix4d matrix = new Matrix4d();
        matrix.translation(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        matrix.mul(render.getShipToWorld());
        matrix.translate(positionInShip.x() - data.anchorX(),
                positionInShip.y() - data.anchorY(),
                positionInShip.z() - data.anchorZ());
        return matrix;
    }

    private static void appendMatrix(StringBuilder sb, String name, Matrix4fc m) {
        sb.append("\n[water-diag] ").append(name).append("=[");
        for (int row = 0; row < 4; row++) {
            if (row > 0) {
                sb.append(';');
            }
            // JOML 是列主序：get(column, row)。
            sb.append(String.format(Locale.ROOT, "% .4f % .4f % .4f % .4f",
                    m.get(0, row), m.get(1, row), m.get(2, row), m.get(3, row)));
        }
        sb.append(']');
    }

    private static String describeEntity(net.minecraft.world.entity.Entity entity) {
        if (entity == null) {
            return "null";
        }
        return entity.getClass().getSimpleName() + "#" + entity.getId();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String fmt(Vec3 vec) {
        return fmt(vec.x, vec.y, vec.z);
    }

    private static String fmt(float x, float y, float z) {
        return String.format(Locale.ROOT, "(%.4f,%.4f,%.4f)", x, y, z);
    }

    private static String fmt(double x, double y, double z) {
        return String.format(Locale.ROOT, "(%.4f,%.4f,%.4f)", x, y, z);
    }

    private static String fmt(Vector3dc vec) {
        return fmt(vec.x(), vec.y(), vec.z());
    }

    private static String fmt(Vector3f vec) {
        return fmt(vec.x(), vec.y(), vec.z());
    }

    private static String fmt(Quaterniondc quat) {
        return String.format(Locale.ROOT, "(%.4f,%.4f,%.4f,%.4f)",
                quat.x(), quat.y(), quat.z(), quat.w());
    }

    // ------------------------------------------------------------------ 几何辅助

    private static void collectStreamImpacts(float[] flows, float[] sideFlows) {
        streamImpactCount = 0;
        for (int i = 0; i < flows.length && streamImpactCount < IMPACTS_PER_FLOW_TYPE * 4;
             i += ShipWaterStatePacket.STREAM_STRIDE) {
            float drop = flows[i + 1] - flows[i + 4];
            if (drop < 0.18F) {
                continue;
            }
            streamImpacts[streamImpactCount++] = flows[i + 3];
            streamImpacts[streamImpactCount++] = flows[i + 4];
            streamImpacts[streamImpactCount++] = flows[i + 5];
            streamImpacts[streamImpactCount++] = Math.min(1.0F, drop) * (0.024F + flows[i + 6] * 0.066F);
        }
        int sideLimit = streamImpactCount + IMPACTS_PER_FLOW_TYPE * 4;
        for (int i = 0; i < sideFlows.length && streamImpactCount < sideLimit;
             i += ShipWaterStatePacket.SIDE_FLOW_STRIDE) {
            float sill = sideFlows[i + 1];
            float surface = sideFlows[i + 6];
            if (surface > sideFlows[i + 5] - 0.025F) {
                continue;
            }
            streamImpacts[streamImpactCount++] = sideFlows[i] + sideFlows[i + 3] * 0.75F;
            streamImpacts[streamImpactCount++] = surface;
            streamImpacts[streamImpactCount++] = sideFlows[i + 2] + sideFlows[i + 4] * 0.75F;
            float dropStrength = Math.min(1.0F, Math.max(0.25F, sideFlows[i + 5] - Math.min(sill, surface)));
            streamImpacts[streamImpactCount++] = dropStrength * (0.015F + sideFlows[i + 7] * 0.07F);
        }
    }

}
