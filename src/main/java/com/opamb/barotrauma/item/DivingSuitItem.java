package com.opamb.barotrauma.item;

import com.opamb.barotrauma.equipment.ArmorModifiers;
import com.opamb.barotrauma.equipment.EquipmentSlotType;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.ship.ShipPressure;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 潜水服：穿在身上时出舱安全深度按原作 {@code PressureProtection=4000}（见
 * {@code ShipPressure#playerSafeDepth}），内部子格放氧气瓶（悬停弹出）。
 *
 * <p>原作里潜水服**没有护甲值**（{@code Content/Items/Diving/divinggear.xml}）：
 * 它给的是一组按病症的伤害倍率（钝器伤/出血 ×0.9、烧伤 ×0.75、辐射病 ×0.75），
 * 见 {@link ArmorModifiers}；本物品只负责"能进潜水服槽 + 耐压 + 氧气瓶 + 陆上减速"。</p>
 *
 * <p>REQUIREMENTS §5.1/P3 的 {@code diving_suit}：首批任务控制在 300m 内，
 * 更深的打捞/侦察配合「深潜许可」+ 潜水服完成。</p>
 */
public class DivingSuitItem extends Item {

    public DivingSuitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.slot",
                Component.translatable("tooltip.barotrauma.equipment.slot.diving_suit"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.pressure",
                ShipPressure.DIVING_SUIT_SAFE_DEPTH)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.slow_land")
                .withStyle(ChatFormatting.GRAY));
        ItemStack tank = ItemSubSlot.get(stack);
        if (tank.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.barotrauma.equipment.no_tank")
                    .withStyle(ChatFormatting.RED));
        } else {
            int left = Math.max(0, tank.getMaxDamage() - tank.getDamageValue());
            tooltip.add(Component.translatable("tooltip.barotrauma.equipment.tank_left",
                    left, tank.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        }
        ArmorModifiers.appendTooltip(tooltip, EquipmentSlotType.DIVING_SUIT);
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
