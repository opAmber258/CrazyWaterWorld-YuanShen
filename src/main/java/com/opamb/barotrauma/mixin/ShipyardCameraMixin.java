package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.ShipyardClient;
import com.opamb.barotrauma.client.TurretClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 船厂编辑器轨道相机；炮塔相机优先。 */
@Mixin(Camera.class)
public abstract class ShipyardCameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void barotrauma$shipyardCamera(BlockGetter level, Entity entity, boolean detached,
                                           boolean thirdPerson, float partialTick, CallbackInfo ci) {
        if (!ShipyardClient.editorActive() || TurretClient.isActive()) {
            return;
        }
        setPosition(ShipyardClient.cameraPosition());
        setRotation(ShipyardClient.cameraYaw(), ShipyardClient.cameraPitch());
    }
}
