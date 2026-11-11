package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.equipment.BagManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版 27 格主背包（Inventory.items 下标 9..35）已经不可见，物品改投装备背包。
 *
 * <p>覆盖拾取（{@code add(-1, stack)}）和显式写主背包下标的调用
 * （{@code placeItemBackInInventory} 等）：先合并/放进装备背包，再进热键栏；
 * 都放不下就返回 false，物品留在原地。</p>
 *
 * <p>热键栏 0..8 保持原版行为。</p>
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void barotrauma$routeAdd(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Inventory inventory = (Inventory) (Object) this;
        Player player = inventory.player;
        if (player == null || player.level().isClientSide || stack.isEmpty()) {
            return;
        }
        if (slot >= 0 && slot < 9) {
            return;
        }
        if (slot >= 9 && slot < 36) {
            cir.setReturnValue(BagManager.insertAt(player, slot - 9, stack));
            return;
        }
        if (slot == -1) {
            cir.setReturnValue(BagManager.insert(player, stack));
        }
    }
}
