package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 装备槽接受物品的 item tag。
 *
 * <p>槽位判定走标签而不是写死 item，方便数据包给槽位加别的装备（例如以后加
 * 深渊潜水服、战斗潜水服）。对应的 JSON 在
 * {@code data/barotrauma/tags/items/equipment/*.json}。</p>
 */
public final class EquipmentTags {

    public static final TagKey<Item> ID_CARD = tag("id_card");
    public static final TagKey<Item> HEADSET = tag("headset");
    public static final TagKey<Item> HELMET = tag("helmet");
    public static final TagKey<Item> BODY_ARMOR = tag("body_armor");
    public static final TagKey<Item> BAG = tag("bag");
    public static final TagKey<Item> DIVING_SUIT = tag("diving_suit");
    public static final TagKey<Item> OXYGEN_TANK = tag("oxygen_tank");

    private EquipmentTags() {
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM,
                new ResourceLocation(BarotraumaMod.MOD_ID, "equipment/" + path));
    }
}
