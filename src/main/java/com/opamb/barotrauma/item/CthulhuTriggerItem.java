package com.opamb.barotrauma.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

/**
 * 克苏鲁的注视触发器（开发者工具）。
 *
 * <p>右键打开预设面板：选预设 + 选弱/中/强三档，全部从这一个触发器里选，
 * 不为每个档位单独造物品。实际生效走 {@code CthulhuGazePacket}，服务端校验
 * 创造模式/权限 ≥ 2。</p>
 */
public class CthulhuTriggerItem extends Item {

    public CthulhuTriggerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.opamb.barotrauma.client.CthulhuGazeScreen.open());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键打开预设面板").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("每个预设可选 弱 / 中 / 强 三档").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("仅创造模式/管理员生效").withStyle(ChatFormatting.DARK_GRAY));
    }
}
