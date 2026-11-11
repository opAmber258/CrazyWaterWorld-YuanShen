package com.opamb.barotrauma.equipment;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 槽位接受规则与"快速移动找槽"的公共逻辑。
 */
public final class EquipmentSlots {

    private EquipmentSlots() {
    }

    /** 这个槽收不收这件物品。 */
    public static boolean accepts(EquipmentSlotType type, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (type) {
            case ID_CARD -> stack.is(EquipmentTags.ID_CARD);
            case HEADSET -> stack.is(EquipmentTags.HEADSET);
            case HELMET -> stack.is(EquipmentTags.HELMET);
            case BODY_ARMOR -> stack.is(EquipmentTags.BODY_ARMOR);
            case BAG -> stack.is(EquipmentTags.BAG);
            case DIVING_SUIT -> stack.is(EquipmentTags.DIVING_SUIT);
        };
    }

    /** 按装备容器下标判断。 */
    public static boolean accepts(int slotIndex, ItemStack stack) {
        EquipmentSlotType type = EquipmentSlotType.byIndex(slotIndex);
        return type != null && accepts(type, stack);
    }

    /** 在装备容器里找第一个空的、能收下这件物品的槽；找不到返回 -1。 */
    public static int findMatchingEmptySlot(EquipmentInventory inventory, ItemStack stack) {
        for (EquipmentSlotType type : EquipmentSlotType.values()) {
            if (accepts(type, stack) && inventory.getItem(type.slotIndex()).isEmpty()) {
                return type.slotIndex();
            }
        }
        return -1;
    }

    /** 在菜单槽位列表里找第一个空的、能收下这件物品的装备槽；找不到返回 -1。 */
    public static int findMatchingEmptySlotMenuIndex(List<Slot> slots, ItemStack stack) {
        for (EquipmentSlotType type : EquipmentSlotType.values()) {
            int menuIndex = type.menuIndex();
            if (menuIndex >= slots.size()) {
                continue;
            }
            Slot slot = slots.get(menuIndex);
            if (slot instanceof EquipmentSlot && !slot.hasItem() && slot.mayPlace(stack)) {
                return menuIndex;
            }
        }
        return -1;
    }
}
