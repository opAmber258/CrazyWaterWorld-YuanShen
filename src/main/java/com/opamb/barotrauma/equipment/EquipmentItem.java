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
 * <p>护甲点数由本类携带，{@link EquipmentEffects} 在 {@code LivingHurtEvent}
 * 里统一结算；不依赖原版 {@code ArmorItem} 的四个护甲槽逻辑。</p>
 */
public class EquipmentItem extends Item {

    private final EquipmentSlotType slotType;
    private final int armorPoints;

    public EquipmentItem(Properties properties, EquipmentSlotType slotType, int armorPoints) {
        super(properties);
        this.slotType = slotType;
        this.armorPoints = armorPoints;
    }

    public EquipmentSlotType slotType() {
        return this.slotType;
    }

    public int armorPoints() {
        return this.armorPoints;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.slot",
                Component.translatable(this.slotType.slotNameKey())).withStyle(ChatFormatting.GRAY));
        if (this.armorPoints > 0) {
            tooltip.add(Component.translatable("tooltip.barotrauma.equipment.armor", this.armorPoints)
                    .withStyle(ChatFormatting.BLUE));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
