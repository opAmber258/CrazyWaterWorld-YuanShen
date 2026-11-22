package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.ship.vs.VsPlayerCollision;
import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.world.AirOceanSwimmer;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntitySwimmingMixin implements AirOceanSwimmer {

    @Shadow public abstract void setSwimming(boolean swimming);
    @Shadow public abstract void setPose(Pose pose);
    @Shadow public abstract void updateSwimming();

    @Unique
    private boolean barotrauma$airOceanSwimming;
    @Unique
    private boolean barotrauma$immersed;

    @Override
    public void barotrauma$setAirOceanSwimState(boolean immersed, boolean fast) {
        boolean wasSwimming = barotrauma$airOceanSwimming;
        barotrauma$immersed = immersed && (Object) this instanceof Player player
                && AirOceanSwimmer.canSwim(player);
        barotrauma$airOceanSwimming = barotrauma$immersed && fast;
        if (barotrauma$airOceanSwimming) {
            setSwimming(true);
            if (!wasSwimming) {
                setPose(Pose.SWIMMING);
            }
        } else if (wasSwimming) {
            updateSwimming();
        }
    }

    @Override
    public boolean barotrauma$isInAirOceanWater() {
        return barotrauma$immersed;
    }

    /** 原版的净空检查只能看到世界方块。展开碰撞箱时也查 VS2 的舱壁和门框。 */
    @Inject(method = "canEnterPose", at = @At("RETURN"), cancellable = true)
    private void barotrauma$checkShipClearance(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (pose == Pose.STANDING || pose == Pose.CROUCHING)
                && (Object) this instanceof Player player
                && (player.getPose() == Pose.SWIMMING || player.getPose() == Pose.CROUCHING)
                && OceanEnvironment.isInOcean(player.level(), player.position()) && VsSupport.available()
                && !VsPlayerCollision.canExpandPose(player, pose)) {
            cir.setReturnValue(false);
        }
    }

    /** 保持假水泳姿，避免原版在刻中的姿态更新前清除游泳标志。 */
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void barotrauma$keepAirOceanSwimming(CallbackInfo ci) {
        if (barotrauma$isAirOceanSwimming()) {
            setSwimming(true);
            ci.cancel();
        }
    }

    /** VS2 能在密封区域直接调用 setSwimming(false)，跳过原版 updateSwimming。 */
    @Inject(method = "setSwimming", at = @At("HEAD"), cancellable = true)
    private void barotrauma$keepSwimmingFlag(boolean swimming, CallbackInfo ci) {
        if (!swimming && barotrauma$isAirOceanSwimming()) {
            ci.cancel();
        }
    }

    @Override
    public boolean barotrauma$isAirOceanSwimming() {
        return (Object) this instanceof Player player && AirOceanSwimmer.canSwim(player)
                && (barotrauma$airOceanSwimming || barotrauma$remoteSwimmer(player));
    }

    @Unique
    private static boolean barotrauma$remoteSwimmer(Player player) {
        // 其他玩家的游泳标志由实体元数据同步，客户端不按本地的空气重新计算。
        return player.level().isClientSide() && !player.isLocalPlayer() && player.isSwimming()
                && OceanEnvironment.isInOcean(player.level(), player.position());
    }
}
