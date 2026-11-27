package com.opamb.barotrauma.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CoilgunBlock;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.item.FlashlightItem;
import com.opamb.barotrauma.item.FlashlightState;
import com.opamb.barotrauma.ship.vs.VsClientTurret;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import com.opamb.barotrauma.world.level.LevelParams;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** 在水体合成前照亮实际世界画面，捕获的深度同时包括普通方块和 VS2 船。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class FlashlightRenderer {
    private static final double OTHER_PLAYER_RANGE_SQUARED = 16.0D * 16.0D;
    private static final float FLASHLIGHT_RANGE = 12.0F;
    // The original draws a 2 × Range texture with the origin on its left edge.
    static final float COILGUN_LIGHT_RANGE = (float) (2.0 * 2500.0 / LevelParams.UNITS_PER_BLOCK);
    private static final double COILGUN_VIEW_DISTANCE_SQUARED = 64.0 * 64.0;
    private static final Set<TurretBlockEntity> COILGUN_LIGHTS = new HashSet<>();
    private static ClientLevel coilgunLevel;
    private static Beam nearCoilgun;
    private static Beam farCoilgun;
    private static final BufferBuilder BUFFER = new BufferBuilder(128);
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();

    private static ShaderInstance shader;
    private static TextureTarget scene;
    private static TextureTarget terrain;
    private static ClientLevel capturedLevel;
    private static Camera capturedCamera;
    private static boolean terrainReady;
    private static boolean terrainFailed;
    private static boolean failed;
    private static Beam[] oceanBeams;

    private FlashlightRenderer() {
    }

    private record Beam(Vec3 origin, Vec3 direction, float brightness, boolean scatter, boolean externalView,
                        float range) {
    }

    public static void addCoilgunLight(ClientLevel level, TurretBlockEntity turret) {
        if (coilgunLevel != level) {
            COILGUN_LIGHTS.clear();
            coilgunLevel = level;
        }
        COILGUN_LIGHTS.add(turret);
    }

    public static void removeCoilgunLight(TurretBlockEntity turret) {
        COILGUN_LIGHTS.remove(turret);
    }

    private static void findCoilgunBeams(ClientLevel level, Camera camera, float partialTick) {
        nearCoilgun = null;
        farCoilgun = null;
        if (level != coilgunLevel) return;
        Vec3 eye = camera.getPosition();
        boolean eyeWet = OceanEnvironment.isOceanWaterAt(level, eye);
        double nearDistance = COILGUN_VIEW_DISTANCE_SQUARED;
        double farDistance = COILGUN_VIEW_DISTANCE_SQUARED;
        for (TurretBlockEntity turret : COILGUN_LIGHTS) {
            if (turret.isRemoved()) continue;
            var pos = turret.getBlockPos();
            var state = turret.getBlockState();
            float yaw = Mth.rotLerp(partialTick, turret.prevYaw(), turret.yaw());
            float pitch = Mth.lerp(partialTick, turret.prevPitch(), turret.pitch());
            Vec3 sight = CoilgunBlock.sight(state, pos, yaw, pitch);
            Vec3 sightWorld = VsClientTurret.toWorld(level, pos, sight);
            Vec3 offset = sightWorld.subtract(eye);
            boolean operated = TurretClient.isOperating(pos);
            double distance = operated ? -1.0 : offset.lengthSqr();
            if (distance >= farDistance) continue;
            Vec3 direction = VsClientTurret.directionToWorld(level, pos, TurretBlockEntity.localLookVector(yaw, pitch));
            // Like the handheld beam, start just ahead of the viewpoint when operating the turret.
            Vec3 lightOrigin = sightWorld.add(direction.scale(0.10));
            boolean scatter = eyeWet && OceanEnvironment.isOceanWaterAt(level, lightOrigin);
            Beam beam = new Beam(lightOrigin.subtract(eye), direction, 1.0F, scatter, true, COILGUN_LIGHT_RANGE);
            if (distance < nearDistance) {
                farCoilgun = nearCoilgun;
                farDistance = nearDistance;
                nearCoilgun = beam;
                nearDistance = distance;
            } else {
                farCoilgun = beam;
                farDistance = distance;
            }
        }
    }

    /** 颜色副本与主画面采用同一精度。只用于手电与近身微光。 */
    public static final class SceneTarget extends TextureTarget {
        private SceneTarget(int width, int height) {
            super(width, height, true, Minecraft.ON_OSX);
        }
    }

    static void register(RegisterShadersEvent event) {
        shader = null;
        failed = false;
        terrainFailed = false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(BarotraumaMod.MOD_ID, "flashlight"), DefaultVertexFormat.POSITION),
                    loaded -> shader = loaded);
        } catch (IOException error) {
            BarotraumaMod.LOGGER.warn("[light] 手电筒着色器加载失败", error);
        }
    }

    public static boolean retainsDarkMaterial() {
        return shader != null && !failed;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || shader == null || failed) {
            return;
        }
        // F7 原版光照：深海不再合成手电/近身微光（该合成会去掉原版 0.04 环境底光）。
        if (VanillaLightingClient.isVanilla()
                && ModDimensions.THE_DEEP.equals(mc.level.dimension())) {
            return;
        }
        var stage = event.getStage();
        Camera camera = event.getCamera();
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            oceanBeams = null;
            terrainReady = false;
            findCoilgunBeams(mc.level, camera, event.getPartialTick());
            if (!terrainFailed && needsTerrainDepth(mc, camera)) {
                captureTerrain(mc.getMainRenderTarget());
            }
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS
                || stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            VIEW.set(event.getPoseStack().last().pose());
            PROJECTION.set(event.getProjectionMatrix());
            capturedLevel = mc.level;
            capturedCamera = camera;
            return;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        boolean captured = capturedLevel == mc.level && capturedCamera == camera;
        boolean hasTerrainDepth = captured && terrainReady;
        capturedLevel = null;
        capturedCamera = null;
        terrainReady = false;
        if (!captured) {
            return;
        }
        float partialTick = event.getPartialTick();
        FlashlightItem.Emitter localEmitter = TurretClient.isActive() ? null : FlashlightItem.findEmitter(mc.player);
        Beam own = localEmitter == null ? null : beam(mc.player, localEmitter.state(), camera, partialTick);
        boolean deep = mc.level.dimension().equals(ModDimensions.THE_DEEP);
        Vec3 halo = null;
        if (deep && own == null && !TurretClient.isActive() && camera.getEntity() == mc.player
                && mc.player.isAlive() && !mc.player.isSpectator()) {
            halo = mc.player.getEyePosition(partialTick).add(0.0, -mc.player.getEyeHeight() * 0.25, 0.0)
                    .subtract(camera.getPosition());
        }
        // 除本机玩家外最多绘制最近的两束，避免多人密集时后处理成本随人数增长。
        Player nearest = null;
        Player second = null;
        double nearDistance = OTHER_PLAYER_RANGE_SQUARED;
        double secondDistance = OTHER_PLAYER_RANGE_SQUARED;
        for (Player player : mc.level.players()) {
            if (!isRemoteEmitter(player, mc.player)) {
                continue;
            }
            double distance = player.distanceToSqr(camera.getPosition());
            if (distance < nearDistance) {
                second = nearest;
                secondDistance = nearDistance;
                nearest = player;
                nearDistance = distance;
            } else if (distance < secondDistance) {
                second = player;
                secondDistance = distance;
            }
        }
        if (!deep && own == null && nearest == null && nearCoilgun == null) {
            return;
        }
        RenderTarget target = mc.getMainRenderTarget();
        if (!canCapture(target)) {
            return;
        }
        try {
            scene = prepareCapture(target, scene, true);
            copyBuffers(target, scene, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            Beam[] beams = {own, remoteBeam(nearest, camera, partialTick), remoteBeam(second, camera, partialTick),
                    nearCoilgun, farCoilgun};
            boolean deferScatter = !DeepBrightnessDebug.seaFogDisabled() && ShipWaterMaterial.refractionAvailable()
                    && UnderwaterEffects.isOpenOcean(mc.level, camera.getPosition());
            draw(beams, halo, hasTerrainDepth, deep, dimCabin(mc, camera), deferScatter, false);
            if (deferScatter) {
                for (Beam beam : beams) {
                    if (beam != null && beam.scatter()) {
                        oceanBeams = beams;
                        break;
                    }
                }
            }
        } catch (RuntimeException error) {
            failed = true;
            release();
            target.bindWrite(false);
            BarotraumaMod.LOGGER.warn("[light] 无法合成手电筒光照", error);
        }
    }

    @SubscribeEvent
    public static void onRenderSeaMonster(RenderLivingEvent.Pre<?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (!TurretClient.isActive() || mc.level == null || mc.player == null
                || DeepBrightnessDebug.seaFogDisabled() || !(event.getEntity() instanceof Enemy)) return;
        var entity = event.getEntity();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (!UnderwaterEffects.isOpenOcean(mc.level, eye)) return;
        Vec3 target = entity.getPosition(event.getPartialTick()).add(0, entity.getBbHeight() * 0.5, 0);
        if (!UnderwaterEffects.isOpenOcean(mc.level, target)
                || mc.level.getBrightness(LightLayer.BLOCK, entity.blockPosition()) >= 2) return;
        if (!illuminates(nearCoilgun, mc, eye, target) && !illuminates(farCoilgun, mc, eye, target)) {
            event.setCanceled(true);
        }
    }

    private static boolean illuminates(Beam lamp, Minecraft mc, Vec3 camera, Vec3 target) {
        if (lamp == null) return false;
        Vec3 origin = lamp.origin().add(camera);
        Vec3 offset = target.subtract(origin);
        float range = lamp.range();
        if (offset.lengthSqr() >= range * range) return false;
        // Match the outer edge of coneProfile in flashlight.fsh.
        if (offset.normalize().dot(lamp.direction()) <= 0.895) return false;
        return mc.level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    public static boolean dimCabin(Minecraft mc, Camera camera) {
        return mc.level != null && OceanEnvironment.isInsideShipCompartment(mc.level, camera.getPosition());
    }

    private static boolean isRemoteEmitter(Player player, Player local) {
        return player != local && player.isAlive() && !player.isSpectator()
                && ((FlashlightState) player).barotrauma$flashlight() != FlashlightState.OFF;
    }

    private static boolean needsTerrainDepth(Minecraft mc, Camera camera) {
        if (nearCoilgun != null) return true;
        if ((camera.isDetached() || camera.getEntity() != mc.player)
                && FlashlightItem.findEmitter(mc.player) != null) {
            return true;
        }
        for (Player player : mc.level.players()) {
            if (isRemoteEmitter(player, mc.player)
                    && player.distanceToSqr(camera.getPosition()) < OTHER_PLAYER_RANGE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static Beam remoteBeam(Player player, Camera camera, float partialTick) {
        return player == null ? null : beam(player, ((FlashlightState) player).barotrauma$flashlight(),
                camera, partialTick);
    }

    private static Beam beam(Player player, byte state, Camera camera, float partialTick) {
        int mount = state & 3;
        boolean firstPerson = camera.getEntity() == player && !camera.isDetached();
        Vec3 eye = firstPerson ? camera.getPosition() : player.getEyePosition(partialTick);
        Vec3 direction = player.getViewVector(partialTick);
        Vec3 right = direction.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 origin;
        if (mount == FlashlightState.HEAD) {
            origin = eye.add(0.0, firstPerson ? 0.08 : 0.12, 0.0)
                    .add(direction.scale(firstPerson ? 0.09 : 0.24));
        } else {
            HumanoidArm arm = mount == FlashlightState.MAIN_HAND
                    ? player.getMainArm() : player.getMainArm().getOpposite();
            origin = eye.add(right.scale((arm == HumanoidArm.RIGHT ? 1.0 : -1.0)
                    * (firstPerson ? 0.16 : 0.28)))
                    .add(0.0, firstPerson ? -0.12 : -0.29, 0.0)
                    .add(direction.scale(firstPerson ? 0.10 : 0.36));
        }
        // 正常电量恒亮。最后一格电量只以十秒为周期缓慢脉动。
        boolean low = (state & FlashlightState.LOW_CHARGE) != 0;
        double phase = (player.level().getGameTime() % 200L + partialTick) / 200.0;
        float brightness = low ? (float) (0.85 + 0.15 * Math.cos(phase * Math.PI * 2.0)) : 1.0F;
        boolean scatter = camera.getEntity() == player && (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                || OceanEnvironment.isSubmerged(player.level(), eye));
        return new Beam(origin.subtract(camera.getPosition()), direction, brightness, scatter, !firstPerson,
                FLASHLIGHT_RANGE);
    }

    /** Called immediately after the sea fog pass, with the original world depth still available. */
    static void drawOceanScatter() {
        Beam[] beams = oceanBeams;
        oceanBeams = null;
        if (beams == null) return;
        try {
            draw(beams, null, false, false, false, false, true);
        } finally {
            // ShipWaterMaterial draws both sides of the remaining water geometry.
            RenderSystem.disableCull();
        }
    }

    private static void draw(Beam[] beams, Vec3 halo, boolean hasTerrainDepth, boolean deep, boolean cabin,
                             boolean deferScatter, boolean scatterOnly) {
        ShaderInstance previousShader = RenderSystem.getShader();
        INVERSE_PROJECTION.set(PROJECTION).invert();
        shader.safeGetUniform("InverseProjection").set(INVERSE_PROJECTION);
        shader.safeGetUniform("SceneProjection").set(PROJECTION);
        shader.safeGetUniform("KeepDimScene").set(cabin ? 1.0F : 0.0F);
        shader.safeGetUniform("DeepScene").set(deep ? 1.0F : 0.0F);
        shader.safeGetUniform("ScatterPass").set(scatterOnly ? 1.0F : deferScatter ? -1.0F : 0.0F);
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        try {
            for (int i = 0; i < beams.length; i++) {
                Beam beam = beams[i];
                if (scatterOnly && (beam == null || !beam.scatter())) continue;
                drawLighting(beam, i == 0 ? halo : null, hasTerrainDepth, i == 0 && deep, i == 0);
            }
        } finally {
            if (BUFFER.building()) {
                BUFFER.end().release();
            }
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static void drawLighting(Beam beam, Vec3 halo, boolean hasTerrainDepth, boolean removeDarkFloor,
                                     boolean localHandheld) {
        if (beam == null && halo == null && !removeDarkFloor) {
            return;
        }
        shader.safeGetUniform("RemoveDarkFloor").set(removeDarkFloor ? 1.0F : 0.0F);
        if (halo == null) {
            shader.safeGetUniform("PlayerHalo").set(0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            Vector3f center = VIEW.transformPosition((float) halo.x, (float) halo.y, (float) halo.z, new Vector3f());
            shader.safeGetUniform("PlayerHalo").set(center.x, center.y, center.z, 1.0F);
        }
        // 第三人称仍照亮最终画面中的人物和场景，但人物深度不参与该光束的投影。
        shader.setSampler("ShadowDepth", (hasTerrainDepth && beam != null && beam.externalView()
                ? terrain : scene).getDepthTextureId());
        shader.safeGetUniform("BeamRange").set(beam == null ? FLASHLIGHT_RANGE : beam.range());
        shader.safeGetUniform("FilledBeam").set(localHandheld && beam != null ? 1.0F : 0.0F);
        if (beam == null) {
            shader.safeGetUniform("BeamOrigin").set(0.0F, 0.0F, 0.0F, 0.0F);
            shader.safeGetUniform("BeamDirection").set(0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            Vec3 p = beam.origin();
            Vec3 d = beam.direction();
            Vector3f origin = VIEW.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
            Vector3f direction = VIEW.transformDirection((float) d.x, (float) d.y, (float) d.z, new Vector3f()).normalize();
            shader.safeGetUniform("BeamOrigin").set(origin.x, origin.y, origin.z, beam.brightness());
            shader.safeGetUniform("BeamDirection").set(direction.x, direction.y, direction.z,
                    beam.scatter() ? 1.0F : 0.0F);
        }
        BUFFER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        BUFFER.vertex(-1.0, -1.0, 0.0).endVertex();
        BUFFER.vertex(3.0, -1.0, 0.0).endVertex();
        BUFFER.vertex(-1.0, 3.0, 0.0).endVertex();
        BufferUploader.drawWithShader(BUFFER.end());
    }

    private static boolean canCapture(RenderTarget target) {
        return target.width > 0 && target.height > 0
                && target.width == target.viewWidth && target.height == target.viewHeight
                && target.getDepthTextureId() > 0
                && GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == target.frameBufferId;
    }

    private static void captureTerrain(RenderTarget target) {
        if (!canCapture(target)) {
            return;
        }
        try {
            terrain = prepareCapture(target, terrain, false);
            copyBuffers(target, terrain, GL11.GL_DEPTH_BUFFER_BIT);
            terrainReady = true;
        } catch (RuntimeException error) {
            terrainFailed = true;
            BarotraumaMod.LOGGER.warn("[light] 无法捕获第三人称手电筒遮挡深度", error);
        } finally {
            target.bindWrite(false);
        }
    }

    private static TextureTarget prepareCapture(RenderTarget target, TextureTarget copy, boolean withColor) {
        if (copy == null || copy.isStencilEnabled() != target.isStencilEnabled()) {
            // 创建成功再替换，异常时调用方仍可以安全释放原缓冲。
            TextureTarget replacement = withColor ? new SceneTarget(target.width, target.height)
                    : new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
            try {
                if (target.isStencilEnabled()) {
                    replacement.enableStencil();
                }
            } catch (RuntimeException error) {
                replacement.destroyBuffers();
                throw error;
            }
            if (copy != null) {
                copy.destroyBuffers();
            }
            return replacement;
        }
        if (copy.width != target.width || copy.height != target.height) {
            copy.resize(target.width, target.height, Minecraft.ON_OSX);
        }
        return copy;
    }

    private static void copyBuffers(RenderTarget target, TextureTarget copy, int mask) {
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, target.width, target.height, 0, 0, copy.width, copy.height,
                    mask, GL11.GL_NEAREST);
            int error = GlStateManager._getError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("OpenGL framebuffer blit: " + error);
            }
        } finally {
            target.bindWrite(false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        capturedLevel = null;
        capturedCamera = null;
        terrainReady = false;
        terrainFailed = false;
        oceanBeams = null;
        COILGUN_LIGHTS.clear();
        coilgunLevel = null;
        nearCoilgun = farCoilgun = null;
        failed = false;
        if (RenderSystem.isOnRenderThread()) {
            release();
        } else {
            RenderSystem.recordRenderCall(FlashlightRenderer::release);
        }
    }

    private static void release() {
        if (scene != null) {
            scene.destroyBuffers();
            scene = null;
        }
        if (terrain != null) {
            terrain.destroyBuffers();
            terrain = null;
        }
    }
}
