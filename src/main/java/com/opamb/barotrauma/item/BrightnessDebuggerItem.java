package com.opamb.barotrauma.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BrightnessDebuggerItem extends Item {
    public BrightnessDebuggerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.barotrauma.brightness_debugger.controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.barotrauma.brightness_debugger.fog_controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.barotrauma.brightness_debugger.scope").withStyle(ChatFormatting.DARK_GRAY));
    }
}
