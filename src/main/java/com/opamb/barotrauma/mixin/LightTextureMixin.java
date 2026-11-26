package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.DeepBrightnessDebug;
import com.opamb.barotrauma.client.FlashlightRenderer;
import com.opamb.barotrauma.client.VanillaLightingClient;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * 深海黑暗由光照决定。仅为手电合成保留一个不可见的材质底色。
 *
 * <p>F7（{@link VanillaLightingClient}）切到原版光照时，三项改动全部放行原版值，
 * 并把深海环境底光补回 0.1（引入灯光系统前的维度值）。</p>
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

    @ModifyConstant(method = "updateLightTexture",
            constant = @Constant(floatValue = 0.04F, ordinal = 0))
    private float barotrauma$removeAmbientFloor(float original) {
        // 在夜视计算前保留一个小正数，避免 Minecraft 对绝对零光做倒数。
        return barotrauma$customLighting() ? 0.0001F : original;
    }

    @ModifyConstant(method = "updateLightTexture",
            constant = @Constant(floatValue = 0.04F, ordinal = 1))
    private float barotrauma$keepMaterialSample(float original) {
        if (!barotrauma$customLighting()) {
            return original;
        }
        // 0.75 × 0.006 × 255 向下取整为 1。合成时会把这个底色从暗处去掉。
        float materialFloor = FlashlightRenderer.retainsDarkMaterial() ? 0.006F : 0.0F;
        return Math.max(materialFloor, DeepBrightnessDebug.ambientMix());
    }

    @Redirect(method = "updateLightTexture",
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
                    ordinal = 0))
    private Object barotrauma$fixedDeepGamma(OptionInstance<?> option) {
        return barotrauma$customLighting() ? 0.0D : option.get();
    }

    /**
     * 原版模式把深海的环境底光还原成引入灯光系统前的 0.1。
     *
     * <p>维度 JSON 现在是 {@code ambient_light=0.0}，就算放行原版光图，没有方块光的
     * 舱室仍然是纯黑；0.1 才是这套光照改造之前的深海亮度（类似下界）。</p>
     */
    @Redirect(method = "getBrightness",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"))
    private static float barotrauma$vanillaAmbient(DimensionType type) {
        return VanillaLightingClient.isVanilla() && barotrauma$inDeepDimension()
                ? DeepBrightnessDebug.vanillaAmbient() : type.ambientLight();
    }

    @Unique
    private static boolean barotrauma$customLighting() {
        return !VanillaLightingClient.isVanilla() && barotrauma$inDeepDimension();
    }

    @Unique
    private static boolean barotrauma$inDeepDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && ModDimensions.THE_DEEP.equals(minecraft.level.dimension());
    }
}
