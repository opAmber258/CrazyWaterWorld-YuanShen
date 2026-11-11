package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.medical.affliction.AfflictionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 原作 {@code damagemodifier} 数值表（**零 MC 依赖**，单测直接跑）。
 *
 * <p>数值抄自本机 Barotrauma 1.13.5 的 Content：
 * <ul>
 *   <li>潜水服 {@code Content/Items/Diving/divinggear.xml:147-150}：
 *       blunttrauma/gunshotwound/bitewounds/lacerations/bleeding/explosiondamage ×0.9、
 *       burn ×0.75、radiationsickness ×0.75；huskinfection 是 probability ×0.5
 *       （本模组没有这个病症，所以这里没有概率项）。</li>
 *   <li>防弹衣 {@code Content/Items/Jobgear/Security/securityofficer_gear.xml:215}：
 *       blunttrauma/lacerations/gunshotwound ×0.3、bleeding ×0.2、burn ×0.9；
 *       bitewounds/explosiondamage ×0.5（我们的爆炸伤折进钝器伤，统一取 0.3）。</li>
 *   <li>防弹头盔（ballistichelmet1）同文件 :247：
 *       lacerations/gunshotwound ×0.2、bitewounds/blunttrauma ×0.3、bleeding ×0.1、
 *       concussion ×0（我们无脑震荡病症）。</li>
 * </ul>
 *
 * <p>原作 {@code Limb.AddDamage} 会把所有命中病症的 modifier **连乘**；这里同样处理，
 * 所以头盔 + 防弹衣同时穿时钝器伤是 0.3×0.3。</p>
 *
 * <p>XML 里的 {@code armorvalue="20.0"} 在 1.13.5 的代码中没有任何读取方，
 * 属于遗留属性，本模组不再实现"护甲值"。</p>
 */
public final class ArmorModifierTable {

    /** 一条原作 {@code damagemodifier}：对某种病症的伤害倍率（1 = 无影响）。 */
    public record Modifier(AfflictionType type, double damageMultiplier) {
    }

    private static final Map<EquipmentSlotType, List<Modifier>> BY_SLOT = buildTable();

    private ArmorModifierTable() {
    }

    private static Map<EquipmentSlotType, List<Modifier>> buildTable() {
        Map<EquipmentSlotType, List<Modifier>> table = new EnumMap<>(EquipmentSlotType.class);

        table.put(EquipmentSlotType.DIVING_SUIT, List.of(
                new Modifier(AfflictionType.BLUNT_TRAUMA, 0.9),
                new Modifier(AfflictionType.BLEEDING, 0.9),
                new Modifier(AfflictionType.BURN, 0.75),
                new Modifier(AfflictionType.RADIATION, 0.75)));

        table.put(EquipmentSlotType.BODY_ARMOR, List.of(
                new Modifier(AfflictionType.BLUNT_TRAUMA, 0.3),
                new Modifier(AfflictionType.BLEEDING, 0.2),
                new Modifier(AfflictionType.BURN, 0.9)));

        table.put(EquipmentSlotType.HELMET, List.of(
                new Modifier(AfflictionType.BLUNT_TRAUMA, 0.3),
                new Modifier(AfflictionType.BLEEDING, 0.1)));

        return table;
    }

    /** 这个槽位穿着的装备提供的倍率表（tooltip 用）。没有则返回空表。 */
    public static List<Modifier> forSlot(EquipmentSlotType slot) {
        return BY_SLOT.getOrDefault(slot, List.of());
    }

    /** 单件装备对某种病症的倍率；纯查表。 */
    public static double multiplierForSlot(EquipmentSlotType slot, AfflictionType type) {
        double multiplier = 1.0D;
        for (Modifier modifier : forSlot(slot)) {
            if (modifier.type() == type) {
                multiplier *= modifier.damageMultiplier();
            }
        }
        return multiplier;
    }
}
