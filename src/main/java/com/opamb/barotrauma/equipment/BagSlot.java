package com.opamb.barotrauma.equipment;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;

/**
 * 背包界面里由工具带/背包提供的格子（菜单下标 9..20）。
 *
 * <p>容量是动态的：没装备背包时 {@link #isActive()} 为 false（不画、不可点），
 * 换工具带（6 格）/背包（12 格）时立刻生效。位置由 {@link BagLayout} 算，
 * 容量变化时客户端会重建这些 Slot 对象（x/y 是 final）。</p>
 */
public class BagSlot extends Slot {

    private final int bagIndex;
    private final IntSupplier capacity;
    private final int layoutCapacity;

    public BagSlot(Container container, int bagIndex, int x, int y,
                   IntSupplier capacity, int layoutCapacity) {
        super(container, bagIndex, x, y);
        this.bagIndex = bagIndex;
        this.capacity = capacity;
        this.layoutCapacity = layoutCapacity;
    }

    /** 建这个槽时用的布局容量，客户端据此判断要不要重建位置。 */
    public int layoutCapacity() {
        return layoutCapacity;
    }

    /** 服务端以绑定容器的容量为准（绑定失败时退回装备槽计算）；客户端没绑定，按装备槽算。 */
    private int capacityNow() {
        if (container instanceof BagContainer bag && bag.capacity() > 0) {
            return bag.capacity();
        }
        return capacity.getAsInt();
    }

    @Override
    public boolean isActive() {
        return bagIndex < capacityNow();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // BagContainer 本身不做物品类型限制；禁背包类物品（原作 toolbelt/backpack 不能互相装），
        // 也禁双手持物品（箱子只能拿在双手格，不能塞进装备背包）。
        return bagIndex < capacityNow() && !stack.is(EquipmentTags.BAG)
                && !com.opamb.barotrauma.item.TwoHanded.is(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return bagIndex < capacityNow();
    }
}
