package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.equipment.CratePreviewOverlay;
import com.opamb.barotrauma.client.equipment.EquipmentScreenOverlay;
import com.opamb.barotrauma.client.equipment.ItemSubSlotOverlay;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.network.ItemSubSlotPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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
        // 创造物品栏会把 47 号双手栏位挪出屏幕，回到背包界面时复位。
        if (screen.getMenu().slots.size() > com.opamb.barotrauma.equipment.HandsSlot.MENU_INDEX) {
            var hands = screen.getMenu().slots.get(
                    com.opamb.barotrauma.equipment.HandsSlot.MENU_INDEX);
            if (hands instanceof com.opamb.barotrauma.equipment.HandsSlot) {
                SlotPositionAccessor accessor = (SlotPositionAccessor) hands;
                accessor.barotrauma$setX(com.opamb.barotrauma.equipment.HandsSlot.X);
                accessor.barotrauma$setY(com.opamb.barotrauma.equipment.HandsSlot.Y);
            }
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
        CratePreviewOverlay.render(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
        // 赃物（任务货物）格子上的红手。
        com.opamb.barotrauma.client.StolenMarker.renderSlots(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop());
    }

    /**
     * 双手格画在面板外（快捷栏左侧）。原版 {@code hasClickedOutside} 会把面板外的
     * 点击算成 -999，{@code mouseClicked} 于是把点击类型定成 {@code THROW}；它仍然带着
     * 槽位对象，{@code slotClicked} 会用槽位下标覆盖 -999，服务端于是真的把双手格里的
     * 箱子丢到地上（这就是"取出会丢出"）。
     *
     * <p>父类 {@code AbstractContainerScreen} 上的同名 mixin 对背包界面不生效：
     * {@code InventoryScreen} 自己重写了 {@code hasClickedOutside}（带配方书判定），
     * 所以这里必须再拦一次。</p>
     */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void barotrauma$handsSlotCountsAsInside(double mouseX, double mouseY,
                                                    int guiLeft, int guiTop, int mouseButton,
                                                    CallbackInfoReturnable<Boolean> cir) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null) {
            return;
        }
        // 手上的箱子弹窗也画在面板外：点完格子松手时不能被当成"面板外点击"，
        // 否则光标上那件从箱子里拿出来的东西会被丢掉。
        if (CratePreviewOverlay.isOverPopup(mouseX, mouseY)) {
            cir.setReturnValue(false);
            return;
        }
        for (Slot slot : menu.slots) {
            if (slot instanceof com.opamb.barotrauma.equipment.HandsSlot
                    && com.opamb.barotrauma.equipment.HandsSlot.contains(
                    slot, guiLeft, guiTop, mouseX, mouseY)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void barotrauma$subSlotClick(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        AbstractContainerMenu menu = screen.getMenu();
        // 拿在手上的箱子：悬停弹窗里的格子（左键整堆、右键 1 个）。
        int[] cell = CratePreviewOverlay.hitTest(menu,
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
        if (cell != null && (button == 0 || button == 1)) {
            ModNetwork.sendToServer(new com.opamb.barotrauma.network.CrateSlotPacket(
                    cell[0], cell[1], button));
            cir.setReturnValue(true);
            return;
        }
        // 背包界面里右键箱子物品 → 打开箱子容器界面。
        if (button == 1) {
            Slot hovered = screen.getSlotUnderMouse();
            if (hovered != null && hovered.hasItem()
                    && hovered.getItem().getItem() instanceof com.opamb.barotrauma.item.CrateItem crate
                    && crate.capacity() > 0) {
                int index = menu.slots.indexOf(hovered);
                if (index >= 0) {
                    ModNetwork.sendToServer(
                            new com.opamb.barotrauma.network.OpenCratePacket(index));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        if (button != 0) {
            return;
        }
        int index = ItemSubSlotOverlay.hitTest(menu,
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
        if (index >= 0) {
            ModNetwork.sendToServer(new ItemSubSlotPacket(index));
            cir.setReturnValue(true);
        }
    }
}
