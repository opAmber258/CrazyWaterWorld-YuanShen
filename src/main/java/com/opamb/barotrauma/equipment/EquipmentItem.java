package com.opamb.barotrauma.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 可装备到自定义装备槽的物品。
 *
 * <p>不提供"护甲点数"：原作护甲是 {@link ArmorModifiers} 里的按病症伤害倍率，
 * 由 {@code MedicalEvents} 在把伤害换算成病症时统一结算。</p>
 */
public class EquipmentItem extends Item {

    private final EquipmentSlotType slotType;

    public EquipmentItem(Properties properties, EquipmentSlotType slotType) {
        super(properties);
        this.slotType = slotType;
    }

    public EquipmentSlotType slotType() {
        return this.slotType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.slot",
                Component.translatable(this.slotType.slotNameKey())).withStyle(ChatFormatting.GRAY));
        ArmorModifiers.appendTooltip(tooltip, this.slotType);
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
