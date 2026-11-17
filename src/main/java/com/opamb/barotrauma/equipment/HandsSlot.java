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

    /**
     * 容器界面里的双手格：和装备背包区同高、放在面板左侧外面（和背包界面的摆法一致）。
     *
     * <p>{@code -30} 表示面板左边缘往左 30px（18px 格子 + 12px 间距），不压在背包区上；
     * {@code ScaledContainerScreen} 会按负坐标自动给面板左侧留出缩放空间。</p>
     */
    public static final int CONTAINER_X = -30;
    /**
     * 双手格相对装备背包区顶部的偏移：和快捷栏同一行（背包界面 47 号位也在热键栏那一行），
     * 这样双手格和快捷栏平行。
     */
    public static final int CONTAINER_ROW = 58;

    public static int containerY(int areaY) {
        return areaY + CONTAINER_ROW;
    }

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

    /**
     * 容器界面里的双手格：放在装备背包区左侧的空位里（面板内，不会被缩放裁掉）。
     *
     * <p>背包界的 47 号位在面板外（{@link #X}），容器界面的面板更小，放外面在
     * 小窗口下会被裁掉，所以统一放进背包区左边距。</p>
     */
    public HandsSlot(Inventory inventory, int x, int y) {
        super(inventory, 40, x, y);
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
