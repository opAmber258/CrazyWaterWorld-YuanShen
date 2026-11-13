package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.CthulhuTriggerItem;
import com.opamb.barotrauma.item.DivingKnifeItem;
import com.opamb.barotrauma.item.DivingSuitItem;
import com.opamb.barotrauma.item.GuitarItem;
import com.opamb.barotrauma.item.InstrumentItem;
import com.opamb.barotrauma.item.MedicalDebuggerItem;
import com.opamb.barotrauma.item.OxygenTankItem;
import com.opamb.barotrauma.item.WeldingToolItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册表。
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BarotraumaMod.MOD_ID);

    /** 氧气瓶：耐久即氧气量，右键补满氧气。 */
    public static final RegistryObject<Item> OXYGEN_TANK = ITEMS.register("oxygen_tank",
            () -> new OxygenTankItem(new Item.Properties().stacksTo(1).durability(300)));

    /** 潜水刀：铁剑档位的近战武器。 */
    public static final RegistryObject<Item> DIVING_KNIFE = ITEMS.register("diving_knife",
            () -> new DivingKnifeItem(Tiers.IRON, 2, -2.2F, new Item.Properties()));

    /** 焊接工具：右键持续修船壳；燃料来自子格里的焊接油箱。 */
    public static final RegistryObject<Item> WELDING_TOOL = ITEMS.register("welding_tool",
            () -> new WeldingToolItem(new Item.Properties().stacksTo(1)));

    /** 焊接油箱：焊接工具的燃料，耐久 200（原作 weldingfueltank 同款）。 */
    public static final RegistryObject<Item> WELDING_FUEL_TANK = ITEMS.register("welding_fuel_tank",
            () -> new Item(new Item.Properties().stacksTo(1).durability(200)));

    /** 潜艇装甲板的方块物品。 */
    public static final RegistryObject<Item> SUBMARINE_PLATING =
            blockItem("submarine_plating", ModBlocks.SUBMARINE_PLATING);

    // ---- M3 潜艇方块 ----
    public static final RegistryObject<Item> HULL_GLASS = blockItem("hull_glass", ModShipBlocks.HULL_GLASS);
    /** 测试用透明船壳（调试专用）。 */
    public static final RegistryObject<Item> TEST_HULL = blockItem("test_hull", ModShipBlocks.TEST_HULL);
    public static final RegistryObject<Item> SONAR = blockItem("sonar", ModShipBlocks.SONAR);
    /** 导航终端：3×3×1 多方块驾驶台，右键打开声纳界面。 */
    public static final RegistryObject<Item> NAVIGATION_TERMINAL =
            blockItem("navigation_terminal", ModShipBlocks.NAVIGATION_TERMINAL);
    public static final RegistryObject<Item> STATION_PLATING = blockItem("station_plating", ModShipBlocks.STATION_PLATING);
    public static final RegistryObject<Item> HATCH = blockItem("hatch", ModShipBlocks.HATCH);
    /** 带梯舱口：舱口 + 铁梯做进同一个方块，梯子不随开合消失。 */
    public static final RegistryObject<Item> HATCH_LADDER = blockItem("hatch_ladder", ModShipBlocks.HATCH_LADDER);
    public static final RegistryObject<Item> BULKHEAD_DOOR =
            blockItem("bulkhead_door", ModShipBlocks.BULKHEAD_DOOR);

    /** 排水泵方块物品。 */
    public static final RegistryObject<Item> DRAIN_PUMP = blockItem("drain_pump", ModBlocks.DRAIN_PUMP);

    public static final RegistryObject<Item> SMALL_PUMP = blockItem("small_pump", ModBlocks.SMALL_PUMP);

    /** 压载泵方块物品（六格多块，放的时候自动铺满 3×2×1）。 */
    public static final RegistryObject<Item> BALLAST_PUMP = blockItem("ballast_pump", ModBlocks.BALLAST_PUMP);

    /** 反应堆方块物品（18 格多块，放的时候自动铺满 3×3×2）。 */
    public static final RegistryObject<Item> REACTOR = blockItem("reactor", ModBlocks.REACTOR);

    /** 引擎方块物品（18 格多块，放的时候自动铺满 3×2×3）。 */
    public static final RegistryObject<Item> ENGINE = blockItem("engine", ModBlocks.ENGINE);

    // ---- 潜渊症容器 ----
    public static final RegistryObject<Item> MEDIUM_STEEL_CABINET =
            blockItem("medium_steel_cabinet", ModBlocks.MEDIUM_STEEL_CABINET);
    public static final RegistryObject<Item> MEDIUM_WINDOWED_STEEL_CABINET =
            blockItem("medium_windowed_steel_cabinet", ModBlocks.MEDIUM_WINDOWED_STEEL_CABINET);
    public static final RegistryObject<Item> CRATE_SHELF =
            blockItem("crate_shelf", ModBlocks.CRATE_SHELF);
    public static final RegistryObject<Item> DIVING_SUIT_LOCKER =
            blockItem("diving_suit_locker", ModBlocks.DIVING_SUIT_LOCKER);
    public static final RegistryObject<Item> OXYGEN_TANK_SHELF =
            blockItem("oxygen_tank_shelf", ModBlocks.OXYGEN_TANK_SHELF);
    public static final RegistryObject<Item> DIVING_SUIT_STATION =
            blockItem("diving_suit_station", ModBlocks.DIVING_SUIT_STATION);

    // ---- 核燃料棒（耐久 = 原作 Condition 上限） ----
    /** 铀燃料棒：80 点。 */
    public static final RegistryObject<Item> URANIUM_FUEL_ROD = ITEMS.register("uranium_fuel_rod",
            () -> new Item(new Item.Properties().durability(80)));
    /** 钍燃料棒：100 点。 */
    public static final RegistryObject<Item> THORIUM_FUEL_ROD = ITEMS.register("thorium_fuel_rod",
            () -> new Item(new Item.Properties().durability(100)));
    /** 富尔古瑞姆燃料棒：150 点。 */
    public static final RegistryObject<Item> FULGURIUM_FUEL_ROD = ITEMS.register("fulgurium_fuel_rod",
            () -> new Item(new Item.Properties().durability(150)));

    public static final RegistryObject<Item> DRAIN_OUTLET = blockItem("drain_outlet", ModBlocks.DRAIN_OUTLET);

    public static final RegistryObject<Item> IRON_LADDER = blockItem("iron_ladder", ModBlocks.IRON_LADDER);

    /** 6700X3D 处理器彩蛋方块。 */
    public static final RegistryObject<Item> CPU_6700X3D = blockItem("cpu_6700x3d", ModBlocks.CPU_6700X3D);


    // ---- 任务/经济 ----
    public static final RegistryObject<Item> MISSION_TERMINAL =
            blockItem("mission_terminal", ModBlocks.MISSION_TERMINAL);
    public static final RegistryObject<Item> BLACK_BOX_BLOCK_ITEM =
            blockItem("black_box_block", ModBlocks.BLACK_BOX_BLOCK);
    /** 黑匣子（物品）：打捞任务的交付物，沉船里的 black_box_block 掉落。 */
    public static final RegistryObject<Item> BLACK_BOX = ITEMS.register("black_box",
            () -> new Item(new Item.Properties()));
    // ---- 原作货运箱：全部是双手持（Holdable slots="RightHand+LeftHand"） ----
    /** 金属箱（metalcrate，20 格）。 */
    public static final RegistryObject<Item> METAL_CRATE = crate("metalcrate", 20);
    /** 保险金属箱（securemetalcrate，20 格）。 */
    public static final RegistryObject<Item> SECURE_METAL_CRATE = crate("securemetalcrate", 20);
    /** 医疗箱（mediccrate，12 格）。 */
    public static final RegistryObject<Item> MEDIC_CRATE = crate("mediccrate", 12);
    /** 炸药收纳箱（explosivecrate，12 格）。 */
    public static final RegistryObject<Item> EXPLOSIVE_CRATE = crate("explosivecrate", 12);
    /** 化学药剂箱（chemicalcrate，12 格）。 */
    public static final RegistryObject<Item> CHEMICAL_CRATE = crate("chemicalcrate", 12);
    /** 密封供给箱（sealedsupplycrate）：原作没有容器，只能整箱搬运。 */
    public static final RegistryObject<Item> SEALED_SUPPLY_CRATE = crate("sealedsupplycrate", 0);
    /** 磁悬浮货箱（magneticsuspensioncrate）：11 格物品 + 1 个电池子格（电池格暂未做）。 */
    public static final RegistryObject<Item> MAGNETIC_SUSPENSION_CRATE = crate("magneticsuspensioncrate", 11);

    private static RegistryObject<Item> crate(String id, int capacity) {
        return ITEMS.register(id, () -> new com.opamb.barotrauma.item.CrateItem(
                new Item.Properties().stacksTo(1), capacity));
    }

    /**
     * 补给箱（物品）：旧版 DELIVERY 任务的货物（接任务发一个）。
     *
     * <p>货运改成原版规则后不再发放，保留注册只为兼容旧存档里已经存在的补给箱。</p>
     */
    public static final RegistryObject<Item> SUPPLY_CRATE = ITEMS.register("supply_crate",
            () -> new Item(new Item.Properties()));
    /** 潜水服（潜水服槽）：穿戴后出舱安全深度 300→400m；原作 fireproof，不可堆叠。 */
    public static final RegistryObject<Item> DIVING_SUIT = ITEMS.register("diving_suit",
            () -> new DivingSuitItem(new Item.Properties().stacksTo(1).fireResistant()));
    /** 深潜许可：解锁 320m 以深任务的接取（商店有售，不消耗）。 */
    public static final RegistryObject<Item> DEEP_PERMIT = ITEMS.register("deep_permit",
            () -> new Item(new Item.Properties()));

    /** "67"：彩蛋物品。只能拿在手上，不能放置，没有任何作用（致敬仓库里的 676767）。 */
    public static final RegistryObject<Item> SIXTY_SEVEN = ITEMS.register("67",
            () -> new Item(new Item.Properties()));

    // ---- 物品与材料（新页签） ----
    /** 小丑喇叭：右键吹一声，服务端 1 秒限流（不显示物品冷却 UI）。 */
    public static final RegistryObject<Item> CLOWN_HORN = ITEMS.register("clown_horn",
            () -> new InstrumentItem(new Item.Properties().stacksTo(1),
                    () -> ModSounds.CLOWN_HORN.get(), 20, 1.0F, 0.06F));

    /** 吉他：长按右键持续演奏占位曲目，松手停止。 */
    public static final RegistryObject<Item> GUITAR = ITEMS.register("guitar",
            () -> new GuitarItem(new Item.Properties().stacksTo(1)));

    /** 医疗调试器（开发者工具）：右键打开面板，自由改六部位/全身病症与活力。 */
    public static final RegistryObject<Item> MEDICAL_DEBUGGER = ITEMS.register("medical_debugger",
            () -> new MedicalDebuggerItem(new Item.Properties().stacksTo(1)));

    /** 克苏鲁的注视触发器（开发者工具）：右键打开面板，选预设 / 三档 / 时长。 */
    public static final RegistryObject<Item> CTHULHU_TRIGGER = ITEMS.register("cthulhu_trigger",
            () -> new CthulhuTriggerItem(new Item.Properties().stacksTo(1)));


    // ---- 潜渊症矿物（独立创造页签） ----

    /** 矿物注册顺序即页签展示顺序；贴图取自游戏原版 Minerals / AlienMaterials 图集。 */
    public static final java.util.List<RegistryObject<Item>> MINERAL_ITEMS = new java.util.ArrayList<>();

    public static final RegistryObject<Item> AMBLYGONITE = mineral("amblygonite");
    public static final RegistryObject<Item> ARAGONITE = mineral("aragonite");
    public static final RegistryObject<Item> BORNITE = mineral("bornite");
    public static final RegistryObject<Item> BROCKITE = mineral("brockite");
    public static final RegistryObject<Item> CASSITERITE = mineral("cassiterite");
    public static final RegistryObject<Item> CHALCOPYRITE = mineral("chalcopyrite");
    public static final RegistryObject<Item> CHAMOSITE = mineral("chamosite");
    public static final RegistryObject<Item> CHRYSOPRASE = mineral("chrysoprase");
    public static final RegistryObject<Item> CRYOLITE = mineral("cryolite");
    public static final RegistryObject<Item> DEMENTONITE = mineral("dementonite");
    public static final RegistryObject<Item> DIAMOND = mineral("diamond");
    public static final RegistryObject<Item> ESPERITE = mineral("esperite");
    public static final RegistryObject<Item> FULGURIUM = mineral("fulgurium");
    public static final RegistryObject<Item> GALENA = mineral("galena");
    public static final RegistryObject<Item> GRAPHITE = mineral("graphite");
    public static final RegistryObject<Item> HYDROXYAPATITE = mineral("hydroxyapatite");
    public static final RegistryObject<Item> ILMENITE = mineral("ilmenite");
    public static final RegistryObject<Item> INCENDIUM = mineral("incendium");
    public static final RegistryObject<Item> IRON_ORE = mineral("ironore");
    public static final RegistryObject<Item> LANGBEINITE = mineral("langbeinite");
    public static final RegistryObject<Item> LAZULITE = mineral("lazulite");
    public static final RegistryObject<Item> PARALYXIS = mineral("paralyxis");
    public static final RegistryObject<Item> PHYSICORIUM = mineral("physicorium");
    public static final RegistryObject<Item> POLYHALITE = mineral("polyhalite");
    public static final RegistryObject<Item> PYROMORPHITE = mineral("pyromorphite");
    public static final RegistryObject<Item> QUARTZ = mineral("quartz");
    public static final RegistryObject<Item> SPHALERITE = mineral("sphalerite");
    public static final RegistryObject<Item> STANNITE = mineral("stannite");
    public static final RegistryObject<Item> SYLVITE = mineral("sylvite");
    public static final RegistryObject<Item> THORIANITE = mineral("thorianite");
    public static final RegistryObject<Item> TITANITE = mineral("titanite");
    public static final RegistryObject<Item> TRIPHYLITE = mineral("triphylite");
    public static final RegistryObject<Item> URANIUM_ORE = mineral("uraniumore");
    public static final RegistryObject<Item> PIEZOCRYSTAL = mineral("piezocrystal");

    // ---- 材料（同页签，铜/铁/铅等） ----

    public static final RegistryObject<Item> CARBON = mineral("carbon");
    public static final RegistryObject<Item> IRON = mineral("iron");
    public static final RegistryObject<Item> TIN = mineral("tin");
    public static final RegistryObject<Item> LEAD = mineral("lead");
    public static final RegistryObject<Item> PHOSPHORUS = mineral("phosphorus");
    public static final RegistryObject<Item> COPPER = mineral("copper");
    public static final RegistryObject<Item> ZINC = mineral("zinc");
    public static final RegistryObject<Item> SODIUM = mineral("sodium");
    public static final RegistryObject<Item> SILICON = mineral("silicon");
    public static final RegistryObject<Item> CALCIUM = mineral("calcium");
    public static final RegistryObject<Item> ALUMINIUM = mineral("aluminium");
    public static final RegistryObject<Item> URANIUM = mineral("uranium");
    public static final RegistryObject<Item> POTASSIUM = mineral("potassium");
    public static final RegistryObject<Item> TITANIUM = mineral("titanium");
    public static final RegistryObject<Item> MAGNESIUM = mineral("magnesium");
    public static final RegistryObject<Item> LITHIUM = mineral("lithium");
    public static final RegistryObject<Item> CHLORINE = mineral("chlorine");
    public static final RegistryObject<Item> THORIUM = mineral("thorium");
    public static final RegistryObject<Item> STEEL = mineral("steel");
    public static final RegistryObject<Item> TITANIUM_ALUMINIUM_ALLOY = mineral("titaniumaluminiumalloy");
    public static final RegistryObject<Item> PLASTIC = mineral("plastic");
    public static final RegistryObject<Item> BALLISTIC_FIBER = mineral("ballisticfiber");
    public static final RegistryObject<Item> ORGANIC_FIBER = mineral("organicfiber");
    public static final RegistryObject<Item> RUBBER = mineral("rubber");
    public static final RegistryObject<Item> FLASH_POWDER = mineral("flashpowder");
    public static final RegistryObject<Item> SCRAP = mineral("scrap");

    // ---- 货运任务占位物品 ----
    // 原版 CargoMission 的箱内清单用到这些化学品/爆炸物：先只做占位（没有任何用途），
    // 保证任务箱内容和原版一致，等以后真正需要时再实现功能。

    /** 硫酸（原作 sulphuricacid）。 */
    public static final RegistryObject<Item> SULPHURIC_ACID = cargoPlaceholder("sulphuricacid");
    /** 窒死毒剂（原作 morbusine）。 */
    public static final RegistryObject<Item> MORBUSINE = cargoPlaceholder("morbusine");
    /** 水合氯醛（原作 chloralhydrate）。 */
    public static final RegistryObject<Item> CHLORAL_HYDRATE = cargoPlaceholder("chloralhydrate");
    /** 挥发性叠氮化合物（原作 volatilecompoundn）。 */
    public static final RegistryObject<Item> VOLATILE_COMPOUND_N =
            cargoPlaceholder("volatilecompoundn");
    /** 雷管（原作 detonator）。 */
    public static final RegistryObject<Item> DETONATOR = cargoPlaceholder("detonator");

    /** 货运占位物品（创造模式"材料"页签用）。 */
    public static final java.util.List<RegistryObject<Item>> CARGO_PLACEHOLDER_ITEMS =
            java.util.List.of(SULPHURIC_ACID, MORBUSINE, CHLORAL_HYDRATE,
                    VOLATILE_COMPOUND_N, DETONATOR);

    /** 注册一个货运占位物品：纯材料，默认堆叠 64，无任何功能。 */
    private static RegistryObject<Item> cargoPlaceholder(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    /** 注册一个矿物物品：纯材料，默认堆叠 64。 */
    private static RegistryObject<Item> mineral(String name) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new Item(new Item.Properties()));
        MINERAL_ITEMS.add(item);
        return item;
    }

    private ModItems() {
    }

    /** 为一个方块生成同名 BlockItem。 */
    private static RegistryObject<Item> blockItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
