package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.medical.affliction.AfflictionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 玩家装备的护甲结算入口（MC 侧）。
 *
 * <p>数值表在 {@link ArmorModifierTable}（纯 Java，单测覆盖）：原作没有"护甲点数"，
 * 只有按病症分类的伤害倍率。这里负责把玩家身上的槽位读出来并连乘，
 * 以及把倍率写进物品 tooltip。</p>
 */
public final class ArmorModifiers {

    private ArmorModifiers() {
    }

    /** 这个槽位穿着的装备提供的倍率表（tooltip 用）。没有则返回空表。 */
    public static List<ArmorModifierTable.Modifier> forSlot(EquipmentSlotType slot) {
        return ArmorModifierTable.forSlot(slot);
    }

    /** 单件装备对某种病症的倍率；纯查表。 */
    public static double multiplierForSlot(EquipmentSlotType slot, AfflictionType type) {
        return ArmorModifierTable.multiplierForSlot(slot, type);
    }

    /**
     * 把倍率表写进物品 tooltip（原作 Wearable.AddTooltipInfo 的简化版）：
     * 例如"钝器伤 −70%"。
     */
    public static void appendTooltip(List<Component> tooltip, EquipmentSlotType slot) {
        for (ArmorModifierTable.Modifier modifier : forSlot(slot)) {
            int percent = (int) Math.round((1.0D - modifier.damageMultiplier()) * 100.0D);
            if (percent <= 0) {
                continue;
            }
            tooltip.add(Component.translatable("tooltip.barotrauma.equipment.resist",
                            Component.translatable("affliction.barotrauma." + modifier.type().id()),
                            percent)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 玩家身上所有穿戴装备叠乘后的倍率。
     * 没有装备数据时返回 1.0，绝不因为能力没挂上而改变伤害。
     */
    public static double damageMultiplier(Player player, AfflictionType type) {
        if (player == null || type == null) {
            return 1.0D;
        }
        EquipmentInventory inventory = EquipmentData.get(player);
        if (inventory == null) {
            return 1.0D;
        }
        double multiplier = 1.0D;
        for (EquipmentSlotType slot : EquipmentSlotType.values()) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            multiplier *= multiplierForSlot(slot, type);
        }
        return multiplier;
    }
}
