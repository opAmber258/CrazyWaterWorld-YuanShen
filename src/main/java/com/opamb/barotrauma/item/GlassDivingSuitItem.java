package com.opamb.barotrauma.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 透明潜水服：v2 半透明面罩美术资产（{@code art/models/diving_suit_v2_glass_r4_20261112}）。
 *
 * <p>玩法上与普通潜水服完全一致（同一个潜水服槽、耐压、氧气瓶、陆上减速），
 * 只是模型/贴图不同、面罩走半透明渲染。单独一个类是为了让客户端渲染器和
 * 同步逻辑能用 {@code instanceof} 区分变体，不去依赖注册表实例。</p>
 */
public class GlassDivingSuitItem extends DivingSuitItem {

    public GlassDivingSuitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.glass_diving_suit")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
