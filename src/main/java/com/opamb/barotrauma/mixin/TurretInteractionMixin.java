package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.TurretClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 炮塔模式下右键=瞄准、左键=开火。
 *
 * <p>右键按住由 {@link TurretClient} 每 tick 读 {@code keyUse} 判断，这里只拦掉
 * {@code startUseItem}，避免顺手用一下脚下的炮塔方块（重新进入/摆动手臂）；
 * 左键改为开火，并拦住 {@code startAttack}/{@code continueAttack}，避免把炮塔挖掉。</p>
 */
@Mixin(Minecraft.class)
public abstract class TurretInteractionMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void barotrauma$turretUse(CallbackInfo ci) {
        if (TurretClient.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void barotrauma$turretAttack(CallbackInfoReturnable<Boolean> cir) {
        if (TurretClient.isActive()) {
            TurretClient.requestFire();
            cir.setReturnValue(false);
        }
    }

    /**
     * 只按住左键不会经过 {@code startAttack}：原版走 {@code continueAttack}，目标变化时
     * 还会转入 {@code startDestroyBlock}。这里在按住左键时取消，松开时仍放行原版
     * {@code stopDestroyBlock}，避免残留破坏进度。
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void barotrauma$turretContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (leftClick && TurretClient.isActive()) {
            ci.cancel();
        }
    }
}
