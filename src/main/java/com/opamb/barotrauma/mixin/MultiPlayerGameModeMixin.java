package com.opamb.barotrauma.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 掉落物现在能被准星选中（右键拾取需要），左键攻击必须挡掉：
 * 服务端收到"攻击 ItemEntity"的包会直接踢人（multiplayer.disconnect.invalid_entity_attacked）。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void barotrauma$noAttackItems(Player player, Entity target, CallbackInfo ci) {
        if (target instanceof ItemEntity) {
            ci.cancel();
        }
    }
}
