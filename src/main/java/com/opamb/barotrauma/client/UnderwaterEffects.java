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
    static final SeaView HUMAN_SEA_VIEW = new SeaView(7.4F, 18.7F);
    static final SeaView LIT_SEA_VIEW = new SeaView(20.0F, CoilgunLightShadow.REACH);
    private static final float SCENE_FOG_NEAR = 20.0F;
    private static final float SCENE_FOG_FAR = 72.0F;

    record WaterLight(float red, float green, float blue) {
    }

    record SeaView(float near, float far) {
    }

    /** 上一次的浸没判定，只在切换时打日志，方便核对舱内滤镜。 */
    private static boolean lastSubmerged;
    private static ClientLevel particleLevel;
    private static long lastEntryBubbles;

    private UnderwaterEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(ModDimensions.THE_DEEP)) {
            event.setRed(0.0F);
            event.setGreen(0.0F);
            event.setBlue(0.0F);
        }
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
        if (DeepBrightnessDebug.seaFogDisabled() || !OceanEnvironment.isInOcean(mc.level, eye)) {
            return;
        }
        SeaView view = sceneView(eye.y);
        if (isOpenOcean(mc.level, eye)) {
            // Only the screen pass extends visibility for illuminated surfaces.
            view = ShipWaterMaterial.refractionAvailable()
                    ? new SeaView(LIT_SEA_VIEW.far(), LIT_SEA_VIEW.far() + 16.0F) : HUMAN_SEA_VIEW;
        }
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
        long tick = level.getGameTime();
        if (particleLevel != level) {
            lastSubmerged = false;
            lastEntryBubbles = tick - 20;
            particleLevel = level;
        }
        boolean submerged = OceanEnvironment.isSubmerged(level, pos);
        boolean enteredWater = submerged && !lastSubmerged;
        if (submerged != lastSubmerged) {
            lastSubmerged = submerged;
            BarotraumaMod.LOGGER.info("[water] 客户端浸没切换：submerged={} pos=({},{},{})",
                    submerged, pos.x, pos.y, pos.z);
        }
        ParticleStatus particles = mc.options.particles().get();
        if (!OceanEnvironment.isInOcean(level, pos) || particles == ParticleStatus.MINIMAL) {
            return;
        }
        var random = level.random;
        if (particles == ParticleStatus.ALL || tick % 2 == 0) {
            Vec3 ahead = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
            Vec3 right = Vec3.directionFromRotation(0.0F, camera.getYRot() + 90.0F);
            Vec3 up = right.cross(ahead);
            spawnMote(level, pos, ahead, right, up, 7.0 + random.nextDouble() * 9.0);
            if (submerged && particles == ParticleStatus.ALL) {
                spawnMote(level, pos, ahead, right, up, 2.2 + random.nextDouble() * 4.8);
            }
        }
        if (enteredWater && !TurretClient.isActive() && tick - lastEntryBubbles >= 16) {
            Vec3 mouth = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(0.38)).add(0, -0.14, 0);
            if (isOpenOcean(level, mouth)) {
                lastEntryBubbles = tick;
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ModParticles.TURRET_BUBBLE.get(), mouth.x, mouth.y, mouth.z,
                            (random.nextDouble() - 0.5) * 0.08, 0.03 + random.nextDouble() * 0.03,
                            (random.nextDouble() - 0.5) * 0.08);
                }
            }
        }
    }

    private static void spawnMote(ClientLevel level, Vec3 eye, Vec3 ahead, Vec3 right, Vec3 up, double distance) {
        var random = level.random;
        Vec3 point = eye.add(ahead.scale(distance))
                .add(right.scale((random.nextDouble() - 0.5) * distance * 1.8))
                .add(up.scale((random.nextDouble() - 0.5) * distance * 1.15));
        if (isOpenOcean(level, point)) {
            level.addParticle(ModParticles.MARINE_SNOW.get(), point.x, point.y, point.z,
                    (random.nextDouble() - 0.5) * 0.006, 0.004 + random.nextDouble() * 0.002,
                    (random.nextDouble() - 0.5) * 0.006);
        }
    }

    /** 原版天空之后、地形之前，清掉天空在无光海域里留下的颜色。 */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(ModDimensions.THE_DEEP)) {
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
        return new WaterLight(0.0F, 25.0F * light / 255.0F, (5.0F + 35.0F * light) / 255.0F);
    }

    private static SeaView sceneView(double y) {
        return new SeaView(SCENE_FOG_NEAR, SCENE_FOG_FAR - 24.0F * depthAt(y));
    }

    private static float depthAt(double y) {
        return (float) Math.min(1.0D, Math.max(0.0D, (ModDimensions.WATER_TOP_Y - y)
                / (ModDimensions.WATER_TOP_Y - ModDimensions.WATER_BOTTOM_Y)));
    }
}
