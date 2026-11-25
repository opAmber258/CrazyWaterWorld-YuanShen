package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.DeepBrightnessDebug;
import com.opamb.barotrauma.client.FlashlightRenderer;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** 深海黑暗由光照决定。仅为手电合成保留一个不可见的材质底色。 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

    @ModifyConstant(method = "updateLightTexture",
            constant = @Constant(floatValue = 0.04F, ordinal = 0))
    private float barotrauma$removeAmbientFloor(float original) {
        // 在夜视计算前保留一个小正数，避免 Minecraft 对绝对零光做倒数。
        return barotrauma$inDeep() ? 0.0001F : original;
    }

    @ModifyConstant(method = "updateLightTexture",
            constant = @Constant(floatValue = 0.04F, ordinal = 1))
    private float barotrauma$keepMaterialSample(float original) {
        if (!barotrauma$inDeep()) {
            return original;
        }
        // 0.75 × 0.006 × 255 向下取整为 1。合成时会把这个底色从暗处去掉。
        Minecraft mc = Minecraft.getInstance();
        float materialFloor = FlashlightRenderer.dimCabin(mc, mc.gameRenderer.getMainCamera())
                ? 0.016F : FlashlightRenderer.retainsDarkMaterial() ? 0.006F : 0.0F;
        return Math.max(materialFloor, DeepBrightnessDebug.ambientMix());
    }

    @Redirect(method = "updateLightTexture",
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 0))
    private Object barotrauma$fixedDeepGamma(OptionInstance<?> option) {
        return barotrauma$inDeep() ? 0.0D : option.get();
    }

    @Unique
    private static boolean barotrauma$inDeep() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && ModDimensions.THE_DEEP.equals(minecraft.level.dimension());
    }
}
