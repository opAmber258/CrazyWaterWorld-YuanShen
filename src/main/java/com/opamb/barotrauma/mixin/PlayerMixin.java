package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.equipment.DivingSuitState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把潜水服穿戴标志挂进原版实体数据表，让服务端写、客户端读都有地方存。
 *
 * <p>{@code SynchedEntityData.defineId} 按类维护自增下标，和原版
 * {@code DATA_PLAYER_MODE_CUSTOMISATION} 等互不冲突。</p>
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements DivingSuitState {

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void barotrauma$defineDivingSuit(CallbackInfo ci) {
        ((Player) (Object) this).getEntityData().define(DivingSuitState.DATA_WEARING, false);
    }

    @Override
    public boolean barotrauma$wearingDivingSuit() {
        return ((Player) (Object) this).getEntityData().get(DivingSuitState.DATA_WEARING);
    }

    @Override
    public void barotrauma$setWearingDivingSuit(boolean wearing) {
        ((Player) (Object) this).getEntityData().set(DivingSuitState.DATA_WEARING, wearing);
    }
}
