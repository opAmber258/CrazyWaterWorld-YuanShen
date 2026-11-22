package com.opamb.barotrauma.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** 舱内水和外海共用的散射颜色，远景背景和悬浮颗粒也沿用该色带。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UnderwaterEffects {

    // Barotrauma's 1920px reference view, at centered and farthest forward human camera positions.
    private static final SeaView HUMAN_SEA_VIEW = new SeaView(7.4F, 18.7F);
    private static final float SCENE_FOG_NEAR = 20.0F;
    private static final float SCENE_FOG_FAR = 72.0F;

    record WaterLight(float red, float green, float blue, float seaDensity) {
    }

    record SeaView(float near, float far) {
    }

    /** 上一次的浸没判定，只在切换时打日志，方便核对舱内滤镜。 */
    private static boolean lastSubmerged;

    private UnderwaterEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        // 只要在深海的水柱范围内就用水色——包括站在未进水的船舱/哨站里。
        // 雾色同时是世界背景色：窗外/舱外空旷处因此永远是海水蓝，和不看它无关。
        if (!OceanEnvironment.isInOcean(mc.level, eye)) {
            return;
        }
        WaterLight water = waterLight(eye.y);
        event.setRed(water.red());
        event.setGreen(water.green());
        event.setBlue(water.blue());
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogMode.FOG_TERRAIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        if (!OceanEnvironment.isInOcean(mc.level, eye)) {
            return;
        }
        SeaView view = !ShipWaterMaterial.refractionAvailable() && isOpenOcean(mc.level, eye)
                ? seaView(eye.y) : sceneView(eye.y);
        event.setNearPlaneDistance(view.near());
        event.setFarPlaneDistance(view.far());
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (level == null || mc.player == null || !camera.isInitialized() || mc.isPaused()) {
            return;
        }
        Vec3 pos = camera.getPosition();
        boolean submerged = OceanEnvironment.isSubmerged(level, pos);
        if (submerged != lastSubmerged) {
            lastSubmerged = submerged;
            BarotraumaMod.LOGGER.info("[water] 客户端浸没切换：submerged={} pos=({},{},{})",
                    submerged, pos.x, pos.y, pos.z);
        }
        ParticleStatus particles = mc.options.particles().get();
        if (!OceanEnvironment.isInOcean(level, pos) || particles == ParticleStatus.MINIMAL
                || (particles == ParticleStatus.DECREASED && level.getGameTime() % 2 != 0)) {
            return;
        }
        var random = level.random;
        Vec3 offset = new Vec3((random.nextDouble() - 0.5D) * 27.0D,
                (random.nextDouble() - 0.5D) * 15.0D, (random.nextDouble() - 0.5D) * 27.0D);
        Vec3 mote = pos.add(offset);
        // 采样出生点，船窗外也能看到悬浮物。每刻最多生成一粒。
        if (offset.lengthSqr() >= 5.0D && isOpenOcean(level, mote)) {
            level.addParticle(ModParticles.MARINE_SNOW.get(), mote.x, mote.y, mote.z,
                    0.006D + random.nextDouble() * 0.006D,
                    -0.002D - random.nextDouble() * 0.004D,
                    (random.nextDouble() - 0.4D) * 0.009D);
        }
    }

    /**
     * 海水背景：天空之后、地形之前，用相机相对的大方块铺一层水色。
     *
     * <p>深海维度没有真正的海水方块，空旷处的“海”其实是天空/清屏色。原版天空是黑的，
     * 只靠“视线中心看到水才加雾”的话，用余光看海就什么都没有。这里把天空直接换成
     * 海水色：它在所有地形后面，所以船内墙壁、哨站内部不会被染色，而窗外任何角度
     * 都是蓝的。半径 16 保证相机在方块内部、任意视线都能打到面，且远小于投影远平面。</p>
     */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        if (!OceanEnvironment.isInOcean(mc.level, eye)) {
            return;
        }
        WaterLight water = waterLight(eye.y);
        drawOceanBackdrop(event, water.red(), water.green(), water.blue());
    }

    private static final float BACKDROP_RADIUS = 16.0F;

    /** 复用的背景 builder（Tesselator 模式：每帧 begin/end；direct 内存不再等 GC）。 */
    private static final BufferBuilder BACKDROP_BUILDER = new BufferBuilder(1024);

    private static void drawOceanBackdrop(RenderLevelStageEvent event, float r, float g, float b) {
        float v = BACKDROP_RADIUS;
        if (BACKDROP_BUILDER.building()) {
            BACKDROP_BUILDER.discard();
        }
        BufferBuilder builder = BACKDROP_BUILDER;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 六面立方体，相机在中心；不剔除、不测深度，并集覆盖整个屏幕。
        // 顶点颜色按世界 Y 做一个上下渐变：上方亮一点、下方暗一点，避免整片死蓝色。
        backdropQuad(builder, r, g, b,
                v, -v, -v, v, -v, v, v, v, v, v, v, -v);      // +X
        backdropQuad(builder, r, g, b,
                -v, -v, -v, -v, v, -v, -v, v, v, -v, -v, v);  // -X
        backdropQuad(builder, r, g, b,
                -v, v, -v, v, v, -v, v, v, v, -v, v, v);      // +Y
        backdropQuad(builder, r, g, b,
                -v, -v, -v, -v, -v, v, v, -v, v, v, -v, -v);  // -Y
        backdropQuad(builder, r, g, b,
                -v, -v, v, v, -v, v, v, v, v, -v, v, v);      // +Z
        backdropQuad(builder, r, g, b,
                -v, -v, -v, -v, v, -v, v, v, -v, v, -v, -v);  // -Z
        BufferBuilder.RenderedBuffer rendered = builder.end();

        Matrix4f pose = event.getPoseStack().last().pose();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting previousSorting = RenderSystem.getVertexSorting();
        float[] previousColor = RenderSystem.getShaderColor();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(pose);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(),
                com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        try {
            BufferUploader.drawWithShader(rendered);
        } finally {
            RenderSystem.enableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1],
                    previousColor[2], previousColor[3]);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
        }
    }

    private static void backdropQuad(BufferBuilder builder, float r, float g, float b,
                                     float x1, float y1, float z1, float x2, float y2, float z2,
                                     float x3, float y3, float z3, float x4, float y4, float z4) {
        backdropVertex(builder, r, g, b, x1, y1, z1);
        backdropVertex(builder, r, g, b, x2, y2, z2);
        backdropVertex(builder, r, g, b, x3, y3, z3);
        backdropVertex(builder, r, g, b, x4, y4, z4);
    }

    private static void backdropVertex(BufferBuilder builder, float r, float g, float b,
                                       float x, float y, float z) {
        // 上下渐变：horizon (y=0) 保持雾色，越往上越亮、越往下越暗。
        float shade = 1.0F + 0.22F * (y / BACKDROP_RADIUS);
        builder.vertex(x, y, z)
                .color(Math.min(1.0F, r * shade), Math.min(1.0F, g * shade),
                        Math.min(1.0F, b * shade), 1.0F)
                .endVertex();
    }

    static boolean isOpenOcean(ClientLevel level, Vec3 point) {
        if (!OceanEnvironment.isInOcean(level, point) || OceanEnvironment.isInOutpostDryZone(level, point)) {
            return false;
        }
        BlockPos block = BlockPos.containing(point);
        return level.hasChunkAt(block) && !OceanEnvironment.insideCollision(level, block, point)
                && ShipWaterRenderer.dryAt(level, point) == null;
    }

    static WaterLight waterLight(double y) {
        float depth = depthAt(y);
        float light = 1.0F - depth;
        // ColdCaverns.xml uses (0,25,40) over a (0,0,5) background.
        return new WaterLight(0.0F, 25.0F * light / 255.0F, (5.0F + 35.0F * light) / 255.0F,
                0.15F + depth * 0.20F);
    }

    static SeaView seaView(double y) {
        return TurretClient.isActive() ? sceneView(y) : HUMAN_SEA_VIEW;
    }

    private static SeaView sceneView(double y) {
        return new SeaView(SCENE_FOG_NEAR, SCENE_FOG_FAR - 24.0F * depthAt(y));
    }

    private static float depthAt(double y) {
        return (float) Math.min(1.0D, Math.max(0.0D, (ModDimensions.WATER_TOP_Y - y)
                / (ModDimensions.WATER_TOP_Y - ModDimensions.WATER_BOTTOM_Y)));
    }
}
