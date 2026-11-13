package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 双手持物品的独立栏位（菜单下标 47）。
 *
 * <p>绑定在玩家副手容器上（{@code Inventory} 下标 40）：原作这种箱子
 * 只能"抱着"（占住左右手），但可以收进背包里。HUD 和背包界面都在快捷栏
 * 左侧单独画这一格（见 {@code TwoHandedHud}）。</p>
 */
public class HandsSlot extends Slot {

    public static final int MENU_INDEX = 47;
    /**
     * 面板左侧、和热键栏隔开一段距离（原版面板外）。
     *
     * <p>放外面时点击会被 {@code AbstractContainerScreen} 当成"面板外点击"
     * （= 丢东西）；{@code AbstractContainerScreenMixin} 和
     * {@code InventoryScreenMixin}（背包界面自己重写了这个方法）把这个格子的
     * 命中改回正常槽位点击，所以位置可以留在外面。</p>
     */
    public static final int X = -64;
    public static final int Y = 142;

    /** 鼠标是否落在这格上（用原版 {@code isHovering} 的 ±1 像素边距）。 */
    public static boolean contains(net.minecraft.world.inventory.Slot slot, int guiLeft, int guiTop,
                                   double mouseX, double mouseY) {
        return slot.isActive()
                && mouseX >= guiLeft + slot.x - 1 && mouseX < guiLeft + slot.x + 17
                && mouseY >= guiTop + slot.y - 1 && mouseY < guiTop + slot.y + 17;
    }

    public HandsSlot(Inventory inventory) {
        super(inventory, 40, X, Y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return TwoHanded.is(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
