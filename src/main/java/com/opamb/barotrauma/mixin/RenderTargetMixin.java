package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.opamb.barotrauma.client.FlashlightRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(RenderTarget.class)
public class RenderTargetMixin {

    // 窗口改尺寸时主画面走这个入口，手电的场景副本也要保留相同精度。
    @ModifyConstant(method = "createBuffers", constant = @Constant(intValue = GL11.GL_RGBA8))
    private int barotrauma$sceneColorPrecision(int format) {
        Object target = this;
        return target instanceof MainTarget || target instanceof FlashlightRenderer.SceneTarget
                ? GL11.GL_RGBA16 : format;
    }
}
