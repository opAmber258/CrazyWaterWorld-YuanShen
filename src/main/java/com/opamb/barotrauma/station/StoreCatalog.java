package com.opamb.barotrauma.station;

import com.opamb.barotrauma.medical.ModMedicalItems;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 哨站商店目录。价格尽量用原作 {@code Content/Items/{子目录}/*.xml} 的 baseprice：
 * 医疗 30–240、氧气瓶 75、焊接工具 150、燃料罐 120、等离子切割机 150、
 * 潜水服 250、燃料棒 150–250、矿物 25–300。
 *
 * <p>{@code buyable=false} 的商品只用于收购（矿物/材料可以卖，但不是所有都在货架上）。</p>
 */
public final class StoreCatalog {

    /** 一个商品：完整物品 id、基础价、初始库存、分类、是否上架出售。 */
    public record Entry(String itemId, int basePrice, int baseStock, String category, boolean buyable) {
    }

    private static final Map<String, Integer> MINERAL_PRICES = Map.ofEntries(
            Map.entry("amblygonite", 200), Map.entry("aragonite", 80),
            Map.entry("bornite", 60), Map.entry("brockite", 150),
            Map.entry("cassiterite", 40), Map.entry("chalcopyrite", 60),
            Map.entry("chamosite", 130), Map.entry("chrysoprase", 40),
            Map.entry("cryolite", 80), Map.entry("diamond", 50),
            Map.entry("esperite", 100), Map.entry("galena", 70),
            Map.entry("graphite", 25), Map.entry("hydroxyapatite", 100),
            Map.entry("ilmenite", 100), Map.entry("ironore", 25),
            Map.entry("langbeinite", 200), Map.entry("lazulite", 25),
            Map.entry("polyhalite", 50), Map.entry("pyromorphite", 30),
            Map.entry("quartz", 100), Map.entry("sphalerite", 100),
            Map.entry("stannite", 50), Map.entry("sylvite", 70),
            Map.entry("thorianite", 300), Map.entry("titanite", 250),
            Map.entry("triphylite", 100), Map.entry("uraniumore", 200),
            Map.entry("dementonite", 200), Map.entry("fulgurium", 200),
            Map.entry("incendium", 200), Map.entry("physicorium", 200),
            Map.entry("paralyxis", 250), Map.entry("piezocrystal", 200),
            Map.entry("carbon", 10), Map.entry("iron", 8), Map.entry("tin", 11),
            Map.entry("lead", 18), Map.entry("phosphorus", 25), Map.entry("copper", 22),
            Map.entry("zinc", 26), Map.entry("sodium", 33), Map.entry("silicon", 17),
            Map.entry("calcium", 31), Map.entry("aluminium", 42), Map.entry("uranium", 50),
            Map.entry("potassium", 10), Map.entry("titanium", 65), Map.entry("magnesium", 65),
            Map.entry("lithium", 88), Map.entry("chlorine", 11), Map.entry("thorium", 125),
            Map.entry("steel", 28), Map.entry("titaniumaluminiumalloy", 150),
            Map.entry("plastic", 45), Map.entry("ballisticfiber", 160),
            Map.entry("organicfiber", 30), Map.entry("rubber", 20),
            Map.entry("flashpowder", 90), Map.entry("scrap", 5));

    /** 哪些矿物/材料在货架上（其余只能收购）。 */
    private static final List<String> STOCKED_MINERALS = List.of(
            "quartz", "pyromorphite", "uraniumore", "ironore", "graphite",
            "steel", "plastic", "rubber", "organicfiber", "aluminium");

    private static volatile List<Entry> cached;

    private StoreCatalog() {
    }

    public static List<Entry> entries() {
        List<Entry> entries = cached;
        if (entries != null) {
            return entries;
        }
        synchronized (StoreCatalog.class) {
            if (cached != null) {
                return cached;
            }
            List<Entry> list = new ArrayList<>();
            // 医疗
            add(list, ModMedicalItems.BANDAGE, 30, 10, "medical", true);
            add(list, ModMedicalItems.ANTIBIOTIC_GLUE, 220, 5, "medical", true);
            add(list, ModMedicalItems.MORPHINE, 100, 8, "medical", true);
            add(list, ModMedicalItems.BLOOD_PACK, 240, 4, "medical", true);
            add(list, ModMedicalItems.SALINE, 50, 10, "medical", true);
            add(list, ModMedicalItems.NALOXONE, 100, 6, "medical", true);
            add(list, ModMedicalItems.FENTANYL, 190, 4, "medical", true);
            add(list, ModMedicalItems.ANTIBIOTICS, 60, 6, "medical", true);
            // 潜水
            add(list, ModItems.OXYGEN_TANK, 75, 12, "diving", true);
            add(list, ModItems.WELDING_FUEL_TANK, 120, 8, "diving", true);
            add(list, ModItems.DIVING_SUIT, 250, 3, "diving", true);
            add(list, ModItems.GLASS_DIVING_SUIT, 350, 2, "diving", true);
            // 工具
            add(list, ModItems.WELDING_TOOL, 150, 4, "tools", true);
            add(list, ModItems.PLASMA_CUTTER, 150, 4, "tools", true);
            add(list, ModItems.DIVING_KNIFE, 40, 6, "tools", true);
            add(list, ModItems.FLASHLIGHT, 200, 4, "tools", true);
            add(list, ModItems.BATTERY_CELL, 125, 8, "tools", true);
            // 燃料
            add(list, ModItems.URANIUM_FUEL_ROD, 150, 4, "fuel", true);
            add(list, ModItems.THORIUM_FUEL_ROD, 200, 3, "fuel", true);
            add(list, ModItems.FULGURIUM_FUEL_ROD, 250, 2, "fuel", true);
            // 容器
            add(list, ModItems.METAL_CRATE, 100, 2, "container", true);
            add(list, ModItems.MEDIC_CRATE, 150, 2, "container", true);
            add(list, ModItems.SEALED_SUPPLY_CRATE, 200, 1, "container", true);
            add(list, ModItems.SECURE_METAL_CRATE, 200, 1, "container", false);
            add(list, ModItems.EXPLOSIVE_CRATE, 150, 1, "container", false);
            add(list, ModItems.CHEMICAL_CRATE, 150, 1, "container", false);
            add(list, ModItems.MAGNETIC_SUSPENSION_CRATE, 250, 1, "container", false);
            add(list, ModItems.SUPPLY_CRATE, 80, 1, "container", false);
            // 矿物/材料：有价格的都能收购；一部分上架
            for (RegistryObject<Item> item : ModItems.MINERAL_ITEMS) {
                Item value = item.get();
                var key = ForgeRegistries.ITEMS.getKey(value);
                if (key == null) {
                    continue;
                }
                String path = key.getPath();
                int price = MINERAL_PRICES.getOrDefault(path, 50);
                boolean stocked = STOCKED_MINERALS.contains(path);
                list.add(new Entry(key.toString(), price, stocked ? 3 : 0,
                        "mineral", stocked));
            }
            cached = List.copyOf(list);
            return cached;
        }
    }

    /** 按完整物品 id 查目录；找不到返回 null（不能卖）。 */
    public static Entry byItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (Entry entry : entries()) {
            if (entry.itemId().equals(itemId)) {
                return entry;
            }
        }
        return null;
    }

    private static void add(List<Entry> list, RegistryObject<Item> item, int price, int stock,
                            String category, boolean buyable) {
        var key = ForgeRegistries.ITEMS.getKey(item.get());
        if (key == null) {
            return;
        }
        list.add(new Entry(key.toString(), price, stock, category, buyable));
    }
}
