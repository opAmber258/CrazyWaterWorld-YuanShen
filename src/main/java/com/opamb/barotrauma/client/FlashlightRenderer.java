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
import com.opamb.barotrauma.item.FlashlightItem;
import com.opamb.barotrauma.item.FlashlightState;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/** 在水体合成前照亮实际世界画面，捕获的深度同时包括普通方块和 VS2 船。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class FlashlightRenderer {
    private static final double OTHER_PLAYER_RANGE_SQUARED = 16.0D * 16.0D;
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

    private FlashlightRenderer() {
    }

    private record Beam(Vec3 origin, Vec3 direction, float brightness, boolean scatter, boolean externalView) {
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
            terrainReady = false;
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
        FlashlightItem.Emitter localEmitter = FlashlightItem.findEmitter(mc.player);
        Beam own = localEmitter == null ? null : beam(mc.player, localEmitter.state(), camera, partialTick);
        boolean deep = mc.level.dimension().equals(ModDimensions.THE_DEEP);
        Vec3 halo = null;
        if (deep && own == null && camera.getEntity() == mc.player
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
        if (!deep && own == null && nearest == null) {
            return;
        }
        RenderTarget target = mc.getMainRenderTarget();
        if (!canCapture(target)) {
            return;
        }
        try {
            scene = prepareCapture(target, scene, true);
            copyBuffers(target, scene, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            draw(own, halo, remoteBeam(nearest, camera, partialTick), remoteBeam(second, camera, partialTick),
                    hasTerrainDepth, deep, dimCabin(mc, camera));
        } catch (RuntimeException error) {
            failed = true;
            release();
            target.bindWrite(false);
            BarotraumaMod.LOGGER.warn("[light] 无法合成手电筒光照", error);
        }
    }

    public static boolean dimCabin(Minecraft mc, Camera camera) {
        return mc.level != null && OceanEnvironment.isInsideShipCompartment(mc.level, camera.getPosition());
    }

    private static boolean isRemoteEmitter(Player player, Player local) {
        return player != local && player.isAlive() && !player.isSpectator()
                && ((FlashlightState) player).barotrauma$flashlight() != FlashlightState.OFF;
    }

    private static boolean needsTerrainDepth(Minecraft mc, Camera camera) {
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
        boolean scatter = firstPerson && (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                || OceanEnvironment.isSubmerged(player.level(), eye));
        return new Beam(origin.subtract(camera.getPosition()), direction, brightness, scatter, !firstPerson);
    }

    private static void draw(Beam first, Vec3 halo, Beam second, Beam third, boolean hasTerrainDepth, boolean deep, boolean cabin) {
        ShaderInstance previousShader = RenderSystem.getShader();
        INVERSE_PROJECTION.set(PROJECTION).invert();
        shader.safeGetUniform("InverseProjection").set(INVERSE_PROJECTION);
        shader.safeGetUniform("SceneProjection").set(PROJECTION);
        shader.safeGetUniform("KeepDimScene").set(cabin ? 1.0F : 0.0F);
        shader.safeGetUniform("DeepScene").set(deep ? 1.0F : 0.0F);
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
            drawLighting(first, halo, hasTerrainDepth, deep);
            drawLighting(second, null, hasTerrainDepth, false);
            drawLighting(third, null, hasTerrainDepth, false);
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

    private static void drawLighting(Beam beam, Vec3 halo, boolean hasTerrainDepth, boolean removeDarkFloor) {
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
        if (beam == null) {
            shader.safeGetUniform("BeamOrigin").set(0.0F, 0.0F, 0.0F, 0.0F);
            shader.safeGetUniform("BeamDirection").set(0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            Vec3 p = beam.origin();
            Vec3 d = beam.direction();
            Vector3f origin = VIEW.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
            Vector3f direction = VIEW.transformDirection((float) d.x, (float) d.y, (float) d.z, new Vector3f()).normalize();
            shader.safeGetUniform("BeamOrigin").set(origin.x, origin.y, origin.z, beam.brightness());
            shader.safeGetUniform("BeamDirection").set(direction.x, direction.y, direction.z, beam.scatter() ? 1.0F : 0.0F);
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
