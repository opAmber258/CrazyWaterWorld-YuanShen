package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.equipment.EquipmentScreenOverlay;
import com.opamb.barotrauma.client.equipment.ItemSubSlotOverlay;
import com.opamb.barotrauma.network.ItemSubSlotPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让创造模式的"物品栏"标签页也能用装备栏/装备背包。
 *
 * <p>原版在这个标签页会把 {@code player.inventoryMenu} 的槽位逐个包成
 * {@code SlotWrapper}，但只给 0..45 和特殊槽位分配坐标：我们追加的潜水服槽（46）
 * 会掉到热键栏下面，背包格也按原版 3×9 排列。这里在拿到包装槽之后用
 * {@link SlotPositionAccessor} 改坐标：背包格按装备容量排成 4×3/3×2，
 * 21..35 禁用格挪出屏幕，潜水服槽放到右上角空位。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {

    private static final int OFFSCREEN = -10000;
    /** 潜水服槽在创造物品栏里的位置（右上角空白处）。 */
    private static final int SUIT_X = 162;
    private static final int SUIT_Y = 6;

    /** 当前是否停在创造界面的"物品栏"标签页。 */
    private static boolean barotrauma$inventoryTab;

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void barotrauma$afterSelectTab(CreativeModeTab tab, CallbackInfo ci) {
        barotrauma$inventoryTab = tab != null && tab.getType() == CreativeModeTab.Type.INVENTORY;
        if (barotrauma$inventoryTab) {
            barotrauma$layout((CreativeModeInventoryScreen) (Object) this);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void barotrauma$layoutBeforeRender(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        // 装备背包可能在界面打开时换掉，每帧重排一次（只是改 int 坐标）。
        if (barotrauma$inventoryTab) {
            barotrauma$layout((CreativeModeInventoryScreen) (Object) this);
        }
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void barotrauma$renderOverlays(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
                                           CallbackInfo ci) {
        if (!barotrauma$inventoryTab) {
            return;
        }
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        Player player = Minecraft.getInstance().player;
        // 主背包 9..20 已经按容量重排，原版 3×9 底纹会露出"看起来能点"的空格；
        // 用和生存界面相同的背包面板盖住（角色预览在 y<54，不会被盖）。
        if (player != null) {
            BagOverlay.renderIn(graphics, screen.getGuiLeft(), screen.getGuiTop(), player,
                    9, 54, 162, 54);
        }
        EquipmentScreenOverlay.renderSlots(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void barotrauma$renderTooltips(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                           CallbackInfo ci) {
        if (!barotrauma$inventoryTab) {
            return;
        }
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        EquipmentScreenOverlay.renderTooltip(graphics, screen.getMenu(),
                screen.getSlotUnderMouse(), mouseX, mouseY);
        ItemSubSlotOverlay.render(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void barotrauma$subSlotClick(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!barotrauma$inventoryTab || button != 0) {
            return;
        }
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        int index = ItemSubSlotOverlay.hitTest(screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
        if (index >= 0) {
            ModNetwork.sendToServer(new ItemSubSlotPacket(index));
            cir.setReturnValue(true);
        }
    }

    private static void barotrauma$layout(CreativeModeInventoryScreen screen) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        // 装备背包 9..20：按容量排成 3×2 / 4×3，和生存界面一致；
        // 超出容量的格子挪出屏幕（创造页签的包装槽不转发 isActive，不能只靠禁用）。
        int capacity = com.opamb.barotrauma.equipment.BagManager.capacity(player);
        com.opamb.barotrauma.equipment.BagLayout.Grid grid =
                com.opamb.barotrauma.equipment.BagLayout.forCapacityIn(capacity, 9, 54, 162, 54);
        for (int i = 0; i < com.opamb.barotrauma.equipment.BagLayout.MAX_SLOTS; i++) {
            int index = com.opamb.barotrauma.equipment.BagSlots.START + i;
            if (index >= menu.slots.size()) {
                break;
            }
            if (i < capacity) {
                barotrauma$move(menu.slots.get(index), grid.slotX(i), grid.slotY(i));
            } else {
                barotrauma$move(menu.slots.get(index), OFFSCREEN - i, OFFSCREEN);
            }
        }
        // 原版主背包 21..35 是禁用格：挪出屏幕，别让它们在创造物品栏里露出来。
        for (int k = 21; k < 36 && k < menu.slots.size(); k++) {
            barotrauma$move(menu.slots.get(k), OFFSCREEN - k, OFFSCREEN);
        }
        // 潜水服槽：原版会把它排到热键栏下面，挪到右上角。
        if (46 < menu.slots.size()) {
            barotrauma$move(menu.slots.get(46), SUIT_X, SUIT_Y);
        }
    }

    private static void barotrauma$move(Slot slot, int x, int y) {
        SlotPositionAccessor accessor = (SlotPositionAccessor) slot;
        accessor.barotrauma$setX(x);
        accessor.barotrauma$setY(y);
    }
}
