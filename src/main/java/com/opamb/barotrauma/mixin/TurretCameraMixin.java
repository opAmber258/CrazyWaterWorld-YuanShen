package com.opamb.barotrauma.mixin;

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

/**
 * 炮塔视角的相机位置/朝向：在 {@code Camera#setup} 末尾覆盖为炮塔视点。
 *
 * <p>视点和射线起点是同一个点，所以画面中心准星和实际弹道重合。</p>
 */
@Mixin(Camera.class)
public abstract class TurretCameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void barotrauma$turretCamera(BlockGetter level, Entity entity, boolean detached,
                                         boolean thirdPerson, float partialTick, CallbackInfo ci) {
        Vec3 position = TurretClient.cameraPosition();
        if (position == null) {
            return;
        }
        setPosition(position);
        // 跟随型炮塔的视角由玩家自己的鼠标控制，不覆盖旋转。
        if (!TurretClient.isTracking()) {
            setRotation(TurretClient.aimYaw(), TurretClient.aimPitch());
        }
    }
}
