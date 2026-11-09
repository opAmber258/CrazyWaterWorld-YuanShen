package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.CthulhuGazeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「平面凝视」：把世界投影从透视平滑混向正交。
 *
 * <p>后处理只能在成品画面上做像素位移，改不了几何视差；真正的“压平”必须换投影矩阵。
 * 这里在 {@code GameRenderer#getProjectionMatrix(double)} 的返回值上与正交矩阵做插值，
 * 混合量来自 {@link CthulhuGazeClient#projectionBlend()}（0 = 原版透视）。
 * 该矩阵每帧重建，所以淡出时混合量归零就会自然回到透视，不需要额外恢复。</p>
 *
 * <p>只挂在客户端的 mixin 列表里；服务端不加载。</p>
 */
@Mixin(GameRenderer.class)
public abstract class CameraProjectionMixin {

    /** 以玩家前方 12 格处的视野高度为基准，压平过程中画面中心比例大致不变。 */
    private static final float FLAT_FOCUS_DISTANCE = 12.0F;

    @Inject(method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;", at = @At("RETURN"), cancellable = true)
    private void barotrauma$blendToOrtho(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        float blend = CthulhuGazeClient.projectionBlend();
        if (blend <= 0.001F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float halfHeight = (float) (FLAT_FOCUS_DISTANCE * Math.tan(Math.toRadians(fov) * 0.5));
        float halfWidth = halfHeight * aspect;
        Matrix4f ortho = new Matrix4f().setOrtho(
                -halfWidth, halfWidth, -halfHeight, halfHeight,
                0.05F, mc.gameRenderer.getDepthFar());
        Matrix4f perspective = cir.getReturnValue();
        cir.setReturnValue(new Matrix4f(perspective).lerp(ortho, Math.min(1.0F, blend)));
    }
}
