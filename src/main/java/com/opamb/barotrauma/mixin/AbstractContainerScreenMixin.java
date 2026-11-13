package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.equipment.HandsSlot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 双手格画在原版面板外面（快捷栏左侧），但点击必须当成正常槽位点击。
 *
 * <p>原版 {@code AbstractContainerScreen.hasClickedOutside} 把面板外的点击算成
 * {@code -999}，{@code mouseClicked} 于是把点击类型定成 {@link ClickType#THROW}；
 * 而这个 THROW 仍然带着 {@code Slot} 对象，{@code slotClicked} 会用槽位下标覆盖
 * -999，服务端就真的把双手格里的东西丢到地上。这里对 {@link HandsSlot} 的格子
 * 返回"没有点在外面"，让它走正常的拾取/放置流程。</p>
 *
 * <p>注意 {@code InventoryScreen} 自己重写了 {@code hasClickedOutside}（带配方书
 * 判定），所以背包界面还要靠 {@code InventoryScreenMixin} 里同样的注入。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void barotrauma$handsSlotCountsAsInside(double mouseX, double mouseY,
                                                    int guiLeft, int guiTop, int mouseButton,
                                                    CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerMenu menu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        if (menu == null) {
            return;
        }
        for (Slot slot : menu.slots) {
            if (slot instanceof HandsSlot && HandsSlot.contains(slot, guiLeft, guiTop, mouseX, mouseY)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    /**
     * 兜底：任何还走到 THROW 的双手格点击都按普通拾取处理。
     * 服务端的 THROW 会绕过 {@code mayPickup} 直接把东西拿走丢地，不能让它落到双手格上。
     */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void barotrauma$handsSlotNeverThrows(@Nullable Slot slot, int slotId, int mouseButton,
                                                 ClickType type, CallbackInfo ci) {
        if (type == ClickType.THROW && slot instanceof HandsSlot) {
            ((AbstractContainerScreenAccessor) this).barotrauma$slotClicked(
                    slot, slot.index, mouseButton, ClickType.PICKUP);
            ci.cancel();
        }
    }
}
