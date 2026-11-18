package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.TurretClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 炮塔视角接管鼠标：不转玩家，转炮塔。
 *
 * <p>原版 {@code turnPlayer()} 把累积的鼠标增量加到玩家 yaw/pitch 上；炮塔模式下
 * 改为交给 {@link TurretClient#onMouseTurn}，并按每秒最大转角截断（拖拽速度上限）。</p>
 */
@Mixin(MouseHandler.class)
public abstract class TurretMouseMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void barotrauma$turretTurn(CallbackInfo ci) {
        if (!TurretClient.isActive()) {
            return;
        }
        if (TurretClient.isTracking()) {
            // 跟随型炮塔的视角就是普通鼠标视角，交给原版转玩家；但瞄准时要乘同一个
            // 角度增益，否则缩放后它会比另外两种炮塔灵敏约 3 倍。
            double scale = TurretClient.mouseAimScale();
            if (scale < 1.0D) {
                this.accumulatedDX *= scale;
                this.accumulatedDY *= scale;
            }
            return;
        }
        TurretClient.onMouseTurn(this.accumulatedDX, this.accumulatedDY);
        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;
        ci.cancel();
    }
}
