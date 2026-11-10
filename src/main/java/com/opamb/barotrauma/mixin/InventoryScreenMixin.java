package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.equipment.EquipmentScreenOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版背包界面上补画装备槽背景与空槽 tooltip。
 *
 * <p>注入 {@code renderBg} 的 TAIL 而不是重写整个界面，所以继承
 * {@code InventoryScreen} 的医疗界面也会自动带上装备槽。</p>
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void barotrauma$renderEquipmentSlots(GuiGraphics graphics, float partialTick,
                                                 int mouseX, int mouseY, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        EquipmentScreenOverlay.renderSlots(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void barotrauma$renderEquipmentTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        EquipmentScreenOverlay.renderTooltip(graphics, screen.getMenu(),
                screen.getSlotUnderMouse(), mouseX, mouseY);
    }
}
