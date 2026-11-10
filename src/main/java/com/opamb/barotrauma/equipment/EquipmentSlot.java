package com.opamb.barotrauma.equipment;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 装备槽：每个槽只放一件，是否接受由 {@link EquipmentSlots} 的标签规则决定。
 */
public class EquipmentSlot extends Slot {

    private final EquipmentSlotType type;

    public EquipmentSlot(Container container, int slot, int x, int y, EquipmentSlotType type) {
        super(container, slot, x, y);
        this.type = type;
    }

    public EquipmentSlotType type() {
        return this.type;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return EquipmentSlots.accepts(this.type, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
