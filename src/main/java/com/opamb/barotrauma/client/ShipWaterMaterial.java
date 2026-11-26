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
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.io.IOException;

/** 在世界画面上合成水体，前景遮挡和水体最近的可见界面都由深度测试决定。 */
final class ShipWaterMaterial {
    private static final boolean REFRACTION = !"false".equalsIgnoreCase(
            System.getProperty("barotrauma.waterFix", "true"));
    private static final BufferBuilder BUFFER = new BufferBuilder(8192);
    private static final Vector3d POSITION = new Vector3d();
    private static final Matrix4d VIEW_TO_SHIP = new Matrix4d();
    private static final Matrix4f RELATIVE_TO_SHIP = new Matrix4f();
    private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();

    private static ShaderInstance shader;
    private static TextureTarget scene;
    private static boolean captureFailed;

    private ShipWaterMaterial() {
    }

    record Result(int poolTriangles, int flowTriangles, boolean refracted) {
    }

    static void register(RegisterShadersEvent event) {
        if (!REFRACTION) {
            return;
        }
        shader = null;
        captureFailed = false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(BarotraumaMod.MOD_ID, "ship_water"), DefaultVertexFormat.POSITION_TEX),
                    loaded -> shader = loaded);
        } catch (IOException failure) {
            BarotraumaMod.LOGGER.warn("[water] 水面着色器加载失败，改用透明网格", failure);
        }
    }

    static void release() {
        if (scene != null) {
            scene.destroyBuffers();
            scene = null;
        }
    }

    static boolean refractionAvailable() {
        return shader != null && !captureFailed;
    }

    /** 调用方已经安装本帧投影和单位模型视图矩阵，顶点在提交时乘实际相机姿态。 */
    static Result draw(ShipWaterMesh pool, ShipWaterFlowMesh flows, Matrix4f view, Matrix4f projection, Matrix4d toCamera,
                       float originX, float originY, float originZ, float time, Vec3 eye,
                       boolean eyeWet, boolean eyeInSea) {
        boolean hasGeometry = pool != null || flows != null && flows.hasPaths();
        boolean seaFog = eyeInSea && !DeepBrightnessDebug.seaFogDisabled();
        if (!hasGeometry && !seaFog) {
            return new Result(0, 0, false);
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        boolean refract = refractionAvailable() && canCopy(target) && capture(target);
        if (!refract && !hasGeometry) {
            return new Result(0, 0, false);
        }
        var light = UnderwaterEffects.waterLight(eye.y);
        var seaView = UnderwaterEffects.seaView(eye.y);
        ShaderInstance previousShader = RenderSystem.getShader();
        if (refract) {
            // 扣掉装配原点再变成 float，让波纹一直跟船走，远离世界原点时也有足够精度。
            VIEW_TO_SHIP.set(view);
            if (toCamera != null) {
                VIEW_TO_SHIP.mul(toCamera).translate(originX, originY, originZ);
            }
            VIEW_TO_SHIP.invert();
            RELATIVE_TO_SHIP.set(VIEW_TO_SHIP);
            INVERSE_PROJECTION.set(projection).invert();
            shader.safeGetUniform("ViewToShip").set(RELATIVE_TO_SHIP);
            shader.safeGetUniform("InverseProjection").set(INVERSE_PROJECTION);
            shader.safeGetUniform("WaveTime").set(time);
            shader.safeGetUniform("EyeWet").set(0.0F);
            shader.safeGetUniform("VolumeFill").set(0.0F);
            shader.safeGetUniform("WaterLight").set(light.red(), light.green(), light.blue(), light.seaDensity());
            shader.safeGetUniform("SeaEye").set(seaFog ? 1.0F : 0.0F);
            shader.safeGetUniform("SeaBounds").set((float) (ModDimensions.WATER_TOP_Y - eye.y),
                    (float) (eye.y - ModDimensions.WATER_BOTTOM_Y));
            shader.safeGetUniform("SeaRange").set(seaView.near(), seaView.far());
            shader.safeGetUniform("ViewUp").set(view.m10(), view.m11(), view.m12());
            shader.setSampler("SceneColor", scene.getColorTextureId());
            shader.setSampler("SceneDepth", scene.getDepthTextureId());
            RenderSystem.setShader(() -> shader);
            RenderSystem.disableBlend();
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.depthMask(refract);
        boolean clampDepth = refract && eyeWet && !GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        try {
            if (refract && seaFog) {
                fillSubmergedScene(2.0F);
            }
            if (!hasGeometry) {
                return new Result(0, 0, refract);
            }
            ShipWaterMesh.VertexSink sink = vertices(view, toCamera, refract);
            int flowTriangles = 0;
            if (refract && eyeWet) {
                if (flows != null && flows.hasPaths()) {
                    // 门洞水流在水面之外。先写入场景颜色，眼下的水面还能折射到其图像。
                    BUFFER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
                    flowTriangles = flows.emit(sink);
                    BufferUploader.drawWithShader(BUFFER.end());
                    if (flowTriangles > 0) {
                        target.copyDepthFrom(scene);
                        copyScene(target, GL11.GL_COLOR_BUFFER_BIT);
                    }
                }
                fillSubmergedScene(1.0F);
                // 离眼睛不到一个近裁剪面的水线仍可覆盖上方的干燥画面。
                if (clampDepth) {
                    GL11.glEnable(GL32.GL_DEPTH_CLAMP);
                }
                shader.safeGetUniform("EyeWet").set(1.0F);
            }
            BUFFER.begin(VertexFormat.Mode.TRIANGLES,
                    refract ? DefaultVertexFormat.POSITION_TEX : DefaultVertexFormat.POSITION_COLOR);
            int poolTriangles = pool == null ? 0 : pool.emit(sink);
            if (flows != null && (!refract || !eyeWet)) {
                flowTriangles = flows.emit(sink);
            }
            BufferUploader.drawWithShader(BUFFER.end());
            return new Result(poolTriangles, flowTriangles, refract);
        } finally {
            if (clampDepth) {
                GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            }
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
            if (refract && hasGeometry) {
                // 期间写深度只为了让离镜头最近的水面胜出。交还世界原来的深度。
                try {
                    target.copyDepthFrom(scene);
                } finally {
                    target.bindWrite(false);
                }
            }
            if (BUFFER.building()) {
                BUFFER.end().release();
            }
        }
    }

    private static ShipWaterMesh.VertexSink vertices(Matrix4f view, Matrix4d toCamera, boolean refract) {
        return (x, y, z, belowSurface, strength) -> {
            toCamera.transformPosition(POSITION.set(x, y, z));
            var vertex = BUFFER.vertex(view, (float) POSITION.x, (float) POSITION.y, (float) POSITION.z);
            if (refract) {
                vertex.uv(belowSurface, strength).endVertex();
            } else {
                float lip = strength * (1.0F - Math.min(1.0F, Math.abs(belowSurface) / 0.24F));
                float opacity = belowSurface < 0.0F
                        ? 0.07F + Math.min(0.18F, -belowSurface) + lip * 0.08F : 0.22F + lip * 0.08F;
                // 无法采样场景色时只衰减背后的光，透明备用水网格不能自行发光。
                vertex.color(0.0F, 0.0F, 0.0F, opacity).endVertex();
            }
        };
    }

    /** 直接按场景深度着色。模式 1 是有限舱内水体，模式 2 是外海水柱。 */
    private static void fillSubmergedScene(float mode) {
        shader.safeGetUniform("VolumeFill").set(mode);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            BUFFER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
            BUFFER.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
            BUFFER.vertex(3.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
            BUFFER.vertex(-1.0D, 3.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
            BufferUploader.drawWithShader(BUFFER.end());
        } finally {
            shader.safeGetUniform("VolumeFill").set(0.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static boolean canCopy(RenderTarget target) {
        return target.width > 0 && target.height > 0 && target.getDepthTextureId() > 0
                && target.viewWidth == target.width && target.viewHeight == target.height
                && GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == target.frameBufferId;
    }

    private static boolean capture(RenderTarget target) {
        try {
            if (scene == null || scene.isStencilEnabled() != target.isStencilEnabled()) {
                release();
                scene = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
                if (target.isStencilEnabled()) {
                    scene.enableStencil();
                }
            } else if (scene.width != target.width || scene.height != target.height) {
                scene.resize(target.width, target.height, Minecraft.ON_OSX);
            }
            if (scene.filterMode != GL11.GL_LINEAR) {
                scene.setFilterMode(GL11.GL_LINEAR);
            }
            copyScene(target, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            int error = GlStateManager._getError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("OpenGL framebuffer blit: " + error);
            }
            return true;
        } catch (RuntimeException failure) {
            captureFailed = true;
            release();
            BarotraumaMod.LOGGER.warn("[water] 场景颜色与深度复制失败，改用透明网格", failure);
            return false;
        } finally {
            target.bindWrite(false);
        }
    }

    private static void copyScene(RenderTarget target, int mask) {
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, target.width, target.height, 0, 0, scene.width, scene.height,
                    mask, GL11.GL_NEAREST);
        } finally {
            target.bindWrite(false);
        }
    }
}
