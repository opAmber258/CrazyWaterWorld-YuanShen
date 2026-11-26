package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.CoilgunPicking;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CoilgunPickMixin {
    @Inject(method = "pick", at = @At("TAIL"))
    private void barotrauma$pickCoilgun(float partial, CallbackInfo ci) {
        CoilgunPicking.pick(partial);
    }
}
