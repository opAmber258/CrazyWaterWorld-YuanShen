package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.equipment.EquipmentScreenOverlay;
import com.opamb.barotrauma.client.equipment.ItemSubSlotOverlay;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.network.ItemSubSlotPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在原版背包界面上补画：
 * <ul>
 *   <li>装备背包区域（工具带/背包提供的格子 + 物品贴图底纹）；</li>
 *   <li>左侧/右侧的装备槽背景与空槽 tooltip；</li>
 *   <li>潜水服/焊接工具的悬停子格（氧气瓶/油箱）。</li>
 * </ul>
 *
 * <p>注入 {@code renderBg} 的 TAIL 而不是重写整个界面，所以继承
 * {@code InventoryScreen} 的医疗界面也会自动带上。</p>
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void barotrauma$renderEquipmentSlots(GuiGraphics graphics, float partialTick,
                                                 int mouseX, int mouseY, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            // 装备的背包换了（容量变化）→ 重建 9..20 的格子位置，再画。
            BagSlots.rebuildClient(screen.getMenu(), minecraft.player);
            BagOverlay.render(graphics, screen.getGuiLeft(), screen.getGuiTop(), minecraft.player);
        }
        EquipmentScreenOverlay.renderSlots(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void barotrauma$renderEquipmentTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        EquipmentScreenOverlay.renderTooltip(graphics, screen.getMenu(),
                screen.getSlotUnderMouse(), mouseX, mouseY);
        ItemSubSlotOverlay.render(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void barotrauma$subSlotClick(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            return;
        }
        InventoryScreen screen = (InventoryScreen) (Object) this;
        int index = ItemSubSlotOverlay.hitTest(screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
        if (index >= 0) {
            ModNetwork.sendToServer(new ItemSubSlotPacket(index));
            cir.setReturnValue(true);
        }
    }
}
