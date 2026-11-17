package com.opamb.barotrauma.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让掉落物能被准星选中（右键拾取的前提）。
 *
 * <p>原版 {@code Entity#isPickable()} 默认 false，{@code ItemEntity} 没重写，
 * 所以玩家根本瞄不到地上的物品；这里对 ItemEntity 放开。</p>
 *
 * <p>拾取判定半径保持原版的 0：之前放大到 0.5 会让地上物品抢走旁边机器/舱门的
 * 右键（物品比方块面更近，准星先命中它），实际体验是"点机器却捡起东西"。</p>
 */
@Mixin(Entity.class)
public abstract class EntityPickMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void barotrauma$itemsPickable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ItemEntity) {
            cir.setReturnValue(true);
        }
    }
}
