package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.TurretClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 炮塔模式下右键=开火、左键=无效。
 *
 * <p>直接拦 {@code startUseItem}/{@code startAttack}，否则右键还会顺手用一下
 * 脚下的炮塔方块（重新进入/摆动手臂），左键会把炮塔挖掉。</p>
 */
@Mixin(Minecraft.class)
public abstract class TurretInteractionMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void barotrauma$turretUse(CallbackInfo ci) {
        if (TurretClient.isActive()) {
            TurretClient.requestFire();
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void barotrauma$turretAttack(CallbackInfoReturnable<Boolean> cir) {
        if (TurretClient.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
