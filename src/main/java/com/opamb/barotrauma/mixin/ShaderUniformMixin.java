package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.CthulhuGazeClient;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 给覆盖过的核心顶点着色器喂 `Kaleido` uniform（万花筒折叠强度）。
 *
 * <p>原版 shader JSON 的 uniforms 列表里没有这个 uniform，所以不走
 * {@code ShaderInstance.safeGetUniform}，而是在每次 {@code apply()} 结束后
 * 按 program id 缓存 uniform location，直接用 GL 上传。值为 0 时也上传，
 * 避免效果结束后残留上一次的折叠量。</p>
 *
 * <p>未覆盖的 shader（GUI/文字/天空等）没有这个 uniform，
 * {@code glGetUniformLocation} 返回 -1，自动跳过。</p>
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderUniformMixin {

    @Unique
    private static final Map<Integer, Integer> barotrauma$kaleidoLocations = new ConcurrentHashMap<>();

    @Inject(method = "apply", at = @At("TAIL"))
    private void barotrauma$setKaleido(CallbackInfo ci) {
        ShaderInstance self = (ShaderInstance) (Object) this;
        int program = self.getId();
        if (program <= 0) {
            return;
        }
        int location = barotrauma$kaleidoLocations.computeIfAbsent(program,
                id -> GL20.glGetUniformLocation(id, "Kaleido"));
        if (location != -1) {
            GL20.glUniform1f(location, CthulhuGazeClient.kaleidoAmount());
        }
    }
}
