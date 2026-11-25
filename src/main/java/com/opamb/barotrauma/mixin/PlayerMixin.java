package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.equipment.DivingSuitState;
import com.opamb.barotrauma.item.FlashlightState;
import com.opamb.barotrauma.world.AirOceanSwimmer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把潜水服穿戴标志挂进原版实体数据表，让服务端写、客户端读都有地方存。
 *
 * <p>{@code SynchedEntityData.defineId} 按类维护自增下标，和原版
 * {@code DATA_PLAYER_MODE_CUSTOMISATION} 等互不冲突。</p>
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements DivingSuitState, FlashlightState {

    @Unique
    private static final EntityDataAccessor<Byte> BAROTRAUMA_FLASHLIGHT =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);

    @Redirect(method = "updatePlayerPose", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z"))
    private boolean barotrauma$crouchOnLand(Player player) {
        return !((AirOceanSwimmer) player).barotrauma$isInAirOceanWater() && player.isShiftKeyDown();
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void barotrauma$defineEquipmentData(CallbackInfo ci) {
        ((Player) (Object) this).getEntityData().define(DivingSuitState.DATA_WEARING, false);
        ((Player) (Object) this).getEntityData().define(DivingSuitState.DATA_WEARING_GLASS, false);
        ((Player) (Object) this).getEntityData().define(BAROTRAUMA_FLASHLIGHT, FlashlightState.OFF);
    }

    @Override
    public byte barotrauma$flashlight() {
        return ((Player) (Object) this).getEntityData().get(BAROTRAUMA_FLASHLIGHT);
    }

    @Override
    public void barotrauma$setFlashlight(byte state) {
        ((Player) (Object) this).getEntityData().set(BAROTRAUMA_FLASHLIGHT, state);
    }

    @Override
    public boolean barotrauma$wearingDivingSuit() {
        return ((Player) (Object) this).getEntityData().get(DivingSuitState.DATA_WEARING);
    }

    @Override
    public void barotrauma$setWearingDivingSuit(boolean wearing) {
        ((Player) (Object) this).getEntityData().set(DivingSuitState.DATA_WEARING, wearing);
    }

    @Override
    public boolean barotrauma$wearingGlassDivingSuit() {
        return ((Player) (Object) this).getEntityData().get(DivingSuitState.DATA_WEARING_GLASS);
    }

    @Override
    public void barotrauma$setWearingGlassDivingSuit(boolean wearing) {
        ((Player) (Object) this).getEntityData().set(DivingSuitState.DATA_WEARING_GLASS, wearing);
    }
}
