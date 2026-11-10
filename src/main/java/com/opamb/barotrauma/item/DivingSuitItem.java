package com.opamb.barotrauma.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 潜水服：穿在身上时出舱安全深度 300→400m（见 {@code ShipPressure#playerSafeDepth}）。
 *
 * <p>新背包界面把潜水服放进独立的潜水服槽（{@code EquipmentSlotType.DIVING_SUIT}）；
 * 本物品仍保留 {@link ArmorItem} 的胸甲形态，旧存档/旧箱子里的潜水服不失效，
 * {@code ShipPressure} 同时读新槽和胸甲槽。</p>
 *
 * <p>REQUIREMENTS §5.1/P3 的 {@code diving_suit}：首批任务控制在 300m 内，
 * 更深的打捞/侦察配合「深潜许可」+ 潜水服完成。</p>
 */
public class DivingSuitItem extends ArmorItem {

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 400;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return 2;
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "barotrauma:diving_suit";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    public DivingSuitItem(Properties properties) {
        super(MATERIAL, ArmorItem.Type.CHESTPLATE, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.slot",
                Component.translatable("tooltip.barotrauma.equipment.slot.diving_suit"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.barotrauma.equipment.pressure",
                com.opamb.barotrauma.ship.ShipPressure.DIVING_SUIT_SAFE_DEPTH)
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
