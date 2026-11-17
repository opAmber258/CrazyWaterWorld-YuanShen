package com.opamb.barotrauma.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 电池 cell（原作 {@code batterycell}）：手感电池，耐久就是电量。
 *
 * <p>原作里电池（{@code battery}）带 {@code batterycellrecharger} 标签，
 * 台面上三个槽能在电池自身电量 >1% 时给 cell 充电（{@code Condition="2.0"}，
 * 即每秒 +2 耐久）。满耐久 = 满电；用电器（手电、手持声纳等）在后续批次接。</p>
 */
public class BatteryCellItem extends Item {

    public BatteryCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getDamageValue() > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        int charge = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.literal("电量: " + charge + " / " + stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("放进电池台面的三个槽里可以充电")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
