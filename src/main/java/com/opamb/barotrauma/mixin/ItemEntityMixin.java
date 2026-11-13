package com.opamb.barotrauma.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 掉落物不再走过去自动捡：只能右键拿（见 {@code ItemPickupEvents}）。
 *
 * <p>和潜渊症一致——东西掉在地上就是躺着的物件，要对着它交互才捡起来。</p>
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void barotrauma$noWalkOverPickup(Player player, CallbackInfo ci) {
        ci.cancel();
    }
}
