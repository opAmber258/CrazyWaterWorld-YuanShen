package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 装备物品的独立注册表与创造栏。
 *
 * <p>潜水服和氧气瓶沿用 {@link ModItems} 里已有的 {@code diving_suit} /
 * {@code oxygen_tank}，不重复注册；这里只新增原作寒冷深穴里能拾到的基础装备。</p>
 */
public final class EquipmentItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BarotraumaMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BarotraumaMod.MOD_ID);

    /** ID 卡：只作身份识别，暂无效效果。 */
    public static final RegistryObject<Item> ID_CARD = ITEMS.register("id_card",
            () -> new EquipmentItem(new Item.Properties().stacksTo(1),
                    EquipmentSlotType.ID_CARD));

    /** 耳机：只作通讯装备，暂无效效果。 */
    public static final RegistryObject<Item> HEADSET = ITEMS.register("headset",
            () -> new EquipmentItem(new Item.Properties().stacksTo(1),
                    EquipmentSlotType.HEADSET));

    /** 防弹头盔（原作 ballistichelmet1）：钝器伤 ×0.3、出血 ×0.1。 */
    public static final RegistryObject<Item> BALLISTIC_HELMET = ITEMS.register("ballistic_helmet",
            () -> new EquipmentItem(new Item.Properties().stacksTo(1),
                    EquipmentSlotType.HELMET));

    /** 防弹衣（原作 bodyarmor）：钝器伤 ×0.3、出血 ×0.2、烧伤 ×0.9。 */
    public static final RegistryObject<Item> BODY_ARMOR = ITEMS.register("body_armor",
            () -> new EquipmentItem(new Item.Properties().stacksTo(1),
                    EquipmentSlotType.BODY_ARMOR));

    /** 工具带：腰带槽物品，暂不提供额外背包格。 */
    public static final RegistryObject<Item> TOOLBELT = ITEMS.register("toolbelt",
            () -> new EquipmentItem(new Item.Properties().stacksTo(1).fireResistant(),
                    EquipmentSlotType.BAG));

    /** 背包：腰带槽物品，和工具带互斥。 */
    public static final RegistryObject<Item> BACKPACK = ITEMS.register("backpack",
            () -> new EquipmentItem(new Item.Properties().stacksTo(1).fireResistant(),
                    EquipmentSlotType.BAG));

    /** 本模组的装备创造栏。主会话的 ModCreativeTabs 不需要改。 */
    public static final RegistryObject<CreativeModeTab> EQUIPMENT_TAB = TABS.register("equipment",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.barotrauma.equipment"))
                    .icon(() -> new ItemStack(ModItems.DIVING_SUIT.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DIVING_SUIT.get());
                        output.accept(ModItems.OXYGEN_TANK.get());
                        output.accept(ID_CARD.get());
                        output.accept(HEADSET.get());
                        output.accept(BALLISTIC_HELMET.get());
                        output.accept(BODY_ARMOR.get());
                        output.accept(TOOLBELT.get());
                        output.accept(BACKPACK.get());
                    })
                    .build());

    private EquipmentItems() {
    }

    /** 在 mod 总线上注册物品与创造栏。 */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
