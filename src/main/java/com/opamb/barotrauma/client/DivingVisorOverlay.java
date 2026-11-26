package com.opamb.barotrauma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.equipment.DivingSuitState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.io.IOException;

/** Draw the inside of the worn helmet over the local first-person scene, underneath the HUD. */
final class DivingVisorOverlay {
    private static final BufferBuilder BUFFER = new BufferBuilder(128);
    private static ShaderInstance shader;

    private DivingVisorOverlay() {
    }

    static void registerShader(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(BarotraumaMod.MOD_ID, "diving_visor"), DefaultVertexFormat.POSITION),
                    loaded -> shader = loaded);
        } catch (IOException failure) {
            BarotraumaMod.LOGGER.warn("[visor] 潜水头盔着色器加载失败", failure);
        }
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || mc.player == null || mc.level == null || mc.player.isSpectator()
                || !mc.player.isAlive() || !mc.options.getCameraType().isFirstPerson()
                || mc.getCameraEntity() != mc.player || TurretClient.isActive()) {
            return;
        }
        DivingSuitState suit = DivingSuitState.of(mc.player);
        if (!suit.barotrauma$wearingDivingSuit()) {
            return;
        }

        graphics.flush();
        ShaderInstance previousShader = RenderSystem.getShader();
        shader.safeGetUniform("AspectRatio").set(mc.getWindow().getWidth() / (float) mc.getWindow().getHeight());
        shader.safeGetUniform("GlassVisor").set(suit.barotrauma$wearingGlassDivingSuit() ? 1.0F : 0.0F);
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            BUFFER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            BUFFER.vertex(-1.0, -1.0, 0.0).endVertex();
            BUFFER.vertex(3.0, -1.0, 0.0).endVertex();
            BUFFER.vertex(-1.0, 3.0, 0.0).endVertex();
            BufferUploader.drawWithShader(BUFFER.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previousShader);
        }
    }
}
