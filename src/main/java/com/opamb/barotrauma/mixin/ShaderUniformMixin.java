package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.CthulhuGazeClient;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给覆盖过的核心顶点着色器喂 `Kaleido` uniform（万花筒折叠强度）。
 *
 * <p>原版 shader JSON 的 uniforms 列表里没有这个 uniform，所以不走
 * {@code ShaderInstance.safeGetUniform}，而是在每次 {@code apply()} 结束后
 * 直接用 GL 上传。location 缓存在 <b>ShaderInstance 实例字段</b>上，不能用
 * 全局的 program-id 表：{@code F3+T} 或换资源包会关闭旧 shader、驱动会复用
 * program 名，按 id 缓存会拿到别的 shader 的 location（CodeRabbit 审查意见）。</p>
 *
 * <p>值为 0 时也上传，避免效果结束后残留上一次的折叠量。未覆盖的 shader
 * （GUI/文字/天空等）没有这个 uniform，location = -1，自动跳过。</p>
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderUniformMixin {

    /** -2 = 还没查过；-1 = 该 shader 没有 Kaleido uniform；其它 = location。 */
    @Unique
    private int barotrauma$kaleidoLocation = -2;

    @Inject(method = "apply", at = @At("TAIL"))
    private void barotrauma$setKaleido(CallbackInfo ci) {
        ShaderInstance self = (ShaderInstance) (Object) this;
        int program = self.getId();
        if (program <= 0) {
            return;
        }
        if (barotrauma$kaleidoLocation == -2) {
            barotrauma$kaleidoLocation = GL20.glGetUniformLocation(program, "Kaleido");
        }
        if (barotrauma$kaleidoLocation != -1) {
            GL20.glUniform1f(barotrauma$kaleidoLocation, CthulhuGazeClient.kaleidoAmount());
        }
    }
}
