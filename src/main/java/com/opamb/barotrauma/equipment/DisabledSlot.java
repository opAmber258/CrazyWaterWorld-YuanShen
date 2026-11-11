package com.opamb.barotrauma.equipment;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 被禁用的原版主背包格（菜单下标 21..35）。
 *
 * <p>不删槽位（否则 36..46 的菜单下标整体前移），只让它不可见、不可交互；
 * 坐标放到屏幕外，避免被点击或画出来。</p>
 */
public class DisabledSlot extends Slot {

    public DisabledSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }
}
