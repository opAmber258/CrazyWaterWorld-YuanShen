package com.opamb.barotrauma.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 潜水服（胸甲槽）：穿在身上时出舱安全深度 300→400m（见 {@code ShipPressure#playerSafeDepth}）。
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
}
