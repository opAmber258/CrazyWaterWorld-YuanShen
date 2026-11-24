package com.opamb.barotrauma.mixin;

import com.mojang.blaze3d.pipeline.MainTarget;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MainTarget.class)
public class MainTargetMixin {

    // 世界画面第一次分配时保留暗部通道差异，供手电与近身微光使用。
    @ModifyConstant(method = "allocateColorAttachment", constant = @Constant(intValue = GL11.GL_RGBA8))
    private int barotrauma$sceneColorPrecision(int format) {
        return GL11.GL_RGBA16;
    }
}
