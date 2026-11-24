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
 * 电磁炮弹药箱：一箱 200 发（原版弹箱 {@code condition -0.5/发}、100 耐久）。
 *
 * <p>装备到电磁炮后，每次开火消耗 1 点耐久；打空后箱子消失。复用原版
 * durability 条显示剩余弹药。</p>
 */
public class CoilgunAmmoBoxItem extends Item {

    /** 一箱发数。 */
    public static final int SHOTS = 200;

    public CoilgunAmmoBoxItem(Properties properties) {
        super(properties.stacksTo(1).durability(SHOTS));
    }

    public static int shotsLeft(ItemStack stack) {
        if (!(stack.getItem() instanceof CoilgunAmmoBoxItem) || stack.isEmpty()) {
            return 0;
        }
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFFA21E;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.barotrauma.coilgun_ammo_box.shots",
                shotsLeft(stack), SHOTS).withStyle(ChatFormatting.GRAY));
    }
}
