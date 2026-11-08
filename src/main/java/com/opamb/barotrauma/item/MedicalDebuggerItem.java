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
 * 医疗调试器（开发者工具，只放在创造模式「开发者工具」页签）。
 *
 * <p>右键打开调试面板：自由摆布六个部位 + 全身的病症强度、活力覆盖值
 * （可以把活力设到负数验证倒地/真死边界）。面板数据来自服务端同步，
 * 编辑走 {@code MedicalDebugPacket}，服务端校验创造模式/管理员权限。</p>
 */
public class MedicalDebuggerItem extends Item {

    public MedicalDebuggerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.opamb.barotrauma.client.MedicalDebugScreen.open());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("右键打开医疗调试面板").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("仅创造模式/管理员可编辑").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("带在身上时创造模式不会自动清空病况").withStyle(ChatFormatting.DARK_GRAY));
    }
}
