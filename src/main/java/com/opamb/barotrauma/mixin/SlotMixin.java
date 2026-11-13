package com.opamb.barotrauma.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家主背包 9..35 已经不可见（由装备背包取代），这里从槽位层挡住所有菜单
 * 往这些隐藏格放东西：箱子/熔炉等菜单的 Shift 点击只会落到热键栏或装备背包。
 *
 * <p>只对 {@code container instanceof Inventory} 的主背包格生效；
 * 本模组的 {@code BagSlot} 用的是 {@code BagContainer}，不受影响。</p>
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void barotrauma$blockHiddenMainSlots(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        // 双手持物品不能放进热键栏/装备槽/背包（原作箱子是 container，但只能抱着或放货架）。
        // 例外：创造模式物品栏没有 47 号的显示位，允许直接放热键栏——那边由
        // TwoHanded.heldBy 的"主手也算抱着"分支接管。
        if (com.opamb.barotrauma.item.TwoHanded.is(stack)
                && !(slot instanceof com.opamb.barotrauma.equipment.HandsSlot)) {
            boolean creativeHotbar = slot.container instanceof Inventory inv
                    && inv.player != null && inv.player.isCreative()
                    && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9;
            if (!creativeHotbar) {
                cir.setReturnValue(false);
                return;
            }
        }
        if (slot.container instanceof Inventory
                && slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36) {
            cir.setReturnValue(false);
        }
    }
}
