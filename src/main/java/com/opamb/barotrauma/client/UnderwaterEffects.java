package com.opamb.barotrauma.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** 深海黑色背景和可视距离，保留有光时水体与颗粒的散射色。 */
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
        if (DeepBrightnessDebug.hidesWaterAndFog()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (OceanEnvironment.isOceanDimension(mc.level)) {
            event.setRed(0.0F);
            event.setGreen(0.0F);
            event.setBlue(0.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (DeepBrightnessDebug.hidesWaterAndFog()) {
            return;
        }
        if (event.getMode() != FogMode.FOG_TERRAIN) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        if (DeepBrightnessDebug.seaFogDisabled() || !OceanEnvironment.isInOcean(mc.level, eye)) {
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
        if (DeepBrightnessDebug.hidesWaterAndFog()) {
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

    /** 原版天空之后、地形之前，清掉天空在无光海域里留下的颜色。 */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (DeepBrightnessDebug.hidesWaterAndFog()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (OceanEnvironment.isOceanDimension(mc.level)) {
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        }
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
