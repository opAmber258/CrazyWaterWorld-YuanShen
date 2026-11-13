package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Q 键丢弃：抱着双手持物品时丢的是双手栏位里的箱子，而不是热键栏物品。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void barotrauma$dropHandsItem(boolean all, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Inventory inventory = self.getInventory();
        ItemStack hands = inventory.offhand.get(0);
        if (!TwoHanded.is(hands)) {
            return;
        }
        ItemStack dropped = all ? hands.copy() : hands.copyWithCount(1);
        // 和原版 drop(boolean) 一样先过 onDroppedByPlayer；其他模组可以拒绝丢弃。
        if (!dropped.onDroppedByPlayer(self)) {
            cir.setReturnValue(false);
            return;
        }
        if (all) {
            inventory.offhand.set(0, ItemStack.EMPTY);
        } else {
            hands.shrink(1);
        }
        // 走 Forge 的丢掷入口：触发 ItemTossEvent，监听器能看到这次丢弃。
        net.minecraftforge.common.ForgeHooks.onPlayerTossEvent(self, dropped, true);
        cir.setReturnValue(true);
    }
}
