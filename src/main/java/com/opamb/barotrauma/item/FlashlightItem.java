package com.opamb.barotrauma.item;

import com.opamb.barotrauma.equipment.EquipmentData;
import com.opamb.barotrauma.equipment.EquipmentSlotType;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 手持或戴在头部时自动亮，电池放在物品子格里。 */
public class FlashlightItem extends Item {

    public FlashlightItem(Properties properties) {
        super(properties);
    }

    public record Emitter(ItemStack stack, ItemStack battery, byte mount) {
        public byte state() {
            int charge = battery.getMaxDamage() - battery.getDamageValue();
            if (charge <= 0) {
                return FlashlightState.OFF;
            }
            return (byte) (mount | (charge == 1 ? FlashlightState.LOW_CHARGE : 0));
        }
    }

    @Nullable
    public static Emitter findEmitter(Player player) {
        if (player.isSpectator() || !player.isAlive()) {
            return null;
        }
        Emitter main = emitter(player.getMainHandItem(), FlashlightState.MAIN_HAND);
        if (main != null) {
            return main;
        }
        Emitter off = emitter(player.getOffhandItem(), FlashlightState.OFF_HAND);
        if (off != null) {
            return off;
        }
        return emitter(EquipmentData.getStack(player, EquipmentSlotType.HEADSET), FlashlightState.HEAD);
    }

    @Nullable
    private static Emitter emitter(ItemStack stack, byte mount) {
        if (!(stack.getItem() instanceof FlashlightItem)) {
            return null;
        }
        ItemStack battery = ItemSubSlot.get(stack);
        if (battery.getItem() instanceof BatteryCellItem
                && battery.getDamageValue() < battery.getMaxDamage()) {
            return new Emitter(stack, battery, mount);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        ItemStack battery = ItemSubSlot.get(stack);
        if (battery.isEmpty()) {
            tooltip.add(Component.translatable("gui.barotrauma.flashlight.no_battery")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            int charge = battery.getMaxDamage() - battery.getDamageValue();
            tooltip.add(Component.translatable("gui.barotrauma.flashlight.battery",
                    charge, battery.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("gui.barotrauma.flashlight.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
