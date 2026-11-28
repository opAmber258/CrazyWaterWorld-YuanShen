package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式物品栏分页。
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BarotraumaMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.barotrauma"))
                    .icon(() -> new ItemStack(ModItems.OXYGEN_TANK.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.OXYGEN_TANK.get());
                        output.accept(ModItems.DIVING_SUIT.get());
                        output.accept(ModItems.GLASS_DIVING_SUIT.get());
                        output.accept(ModItems.DEEP_PERMIT.get());
                        output.accept(ModItems.DIVING_KNIFE.get());
                        output.accept(ModItems.WELDING_TOOL.get());
                        output.accept(ModItems.WELDING_FUEL_TANK.get());
                        output.accept(ModItems.SCREWDRIVER.get());
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.PLASMA_CUTTER.get());
                        output.accept(ModItems.QUARTZ_VEIN.get());
                        output.accept(ModItems.PYROMORPHITE_VEIN.get());
                        output.accept(ModItems.URANIUM_VEIN.get());
                        output.accept(ModItems.SUBMARINE_PLATING.get());
                        // 两面潜渊症风格装饰墙：同时进原版"建筑方块"页签（用户要求 2026-11-28）
                        output.accept(ModItems.DARK_GREEN_MOSAIC_TILE_WALL.get());
                        output.accept(ModItems.SQUARE_WHITE_PANEL_WALL.get());
                        output.accept(ModItems.HULL_GLASS.get());
                        output.accept(ModItems.TEST_HULL.get());
                        output.accept(ModItems.SONAR.get());
                        output.accept(ModItems.NAVIGATION_TERMINAL.get());
                        output.accept(ModItems.STATION_PLATING.get());
                        output.accept(ModItems.HATCH.get());
                        output.accept(ModItems.HATCH_LADDER.get());
                        output.accept(ModItems.BULKHEAD_DOOR.get());
                        output.accept(ModItems.DRAIN_PUMP.get());
                        output.accept(ModItems.SMALL_PUMP.get());
                        output.accept(ModItems.BALLAST_PUMP.get());
                        output.accept(ModItems.REACTOR.get());
                        output.accept(ModItems.ENGINE.get());
                        output.accept(ModItems.JUNCTION_BOX.get());
                        output.accept(ModItems.BATTERY.get());
                        output.accept(ModItems.BATTERY_CELL.get());
                        output.accept(ModItems.FULGURIUM_BATTERY_CELL.get());
                        output.accept(ModItems.FLASHLIGHT.get());
                        output.accept(ModItems.SUPERCAPACITOR.get());
                        output.accept(ModItems.PERISCOPE.get());
                        output.accept(ModItems.PERISCOPE_STAND.get());
                        output.accept(ModItems.FABRICATOR.get());
                        output.accept(ModItems.DECONSTRUCTOR.get());
                        output.accept(ModItems.OXYGEN_GENERATOR.get());
                        output.accept(ModItems.VENT.get());
                        output.accept(ModItems.LAMP.get());
                        output.accept(ModItems.EMERGENCY_LIGHT.get());
                        output.accept(ModItems.FLUORESCENT_LIGHT.get());
                        output.accept(ModItems.FLUORESCENT_LIGHT_L01.get());
                        output.accept(ModItems.FLUORESCENT_LIGHT_L02.get());
                        output.accept(ModItems.MEDIUM_STEEL_CABINET.get());
                        output.accept(ModItems.MEDIUM_WINDOWED_STEEL_CABINET.get());
                        output.accept(ModItems.CRATE_SHELF.get());
                        output.accept(ModItems.METAL_CRATE.get());
                        output.accept(ModItems.SECURE_METAL_CRATE.get());
                        output.accept(ModItems.MEDIC_CRATE.get());
                        output.accept(ModItems.EXPLOSIVE_CRATE.get());
                        output.accept(ModItems.CHEMICAL_CRATE.get());
                        output.accept(ModItems.SEALED_SUPPLY_CRATE.get());
                        output.accept(ModItems.MAGNETIC_SUSPENSION_CRATE.get());
                        output.accept(ModItems.DIVING_SUIT_LOCKER.get());
                        // 氧气瓶架 / 组合柜：模型未完成，暂不进创造栏（避免出现缺失模型的紫黑块）
                        // output.accept(ModItems.OXYGEN_TANK_SHELF.get());
                        // output.accept(ModItems.DIVING_SUIT_STATION.get());
                        output.accept(ModItems.URANIUM_FUEL_ROD.get());
                        output.accept(ModItems.THORIUM_FUEL_ROD.get());
                        output.accept(ModItems.FULGURIUM_FUEL_ROD.get());
                        output.accept(ModItems.DRAIN_OUTLET.get());
                        output.accept(ModItems.IRON_LADDER.get());
                        output.accept(ModItems.COILGUN.get());
                        output.accept(ModItems.COILGUN_LOADER.get());
                        output.accept(ModItems.COILGUN_AMMO_BOX.get());
                        output.accept(ModItems.TURRET_BINDER.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.BANDAGE.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.ANTIBIOTIC_GLUE.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.MORPHINE.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.BLOOD_PACK.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.SALINE.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.NALOXONE.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.FENTANYL.get());
                        output.accept(com.opamb.barotrauma.medical.ModMedicalItems.ANTIBIOTICS.get());
                    })
                    .build());

    /** 物品与材料页签：乐器、材料等非装备类物品。 */
    public static final RegistryObject<CreativeModeTab> MATERIALS = TABS.register("materials",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.barotrauma.materials"))
                    .icon(() -> new ItemStack(ModItems.GUITAR.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.CLOWN_HORN.get());
                        output.accept(ModItems.GUITAR.get());
                        // 货运任务箱内清单用到的化学品/爆炸物（占位，无功能）
                        ModItems.CARGO_PLACEHOLDER_ITEMS.forEach(item -> output.accept(item.get()));
                    })
                    .build());

    /** 开发者工具页签：只在创造模式出现，给调试/制作流程用。 */
    public static final RegistryObject<CreativeModeTab> DEV = TABS.register("dev",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.barotrauma.dev"))
                    .icon(() -> new ItemStack(ModItems.MEDICAL_DEBUGGER.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MEDICAL_DEBUGGER.get());
                        output.accept(ModItems.BRIGHTNESS_DEBUGGER.get());
                        // 彩蛋物品从主页签挪到开发者页签（用户指令 2026-11-08）
                        output.accept(ModItems.SIXTY_SEVEN.get());
                        output.accept(ModItems.CPU_6700X3D.get());
                        // 克苏鲁的注视：只有一个触发器，预设与弱/中/强档位在面板里选
                        output.accept(ModItems.CTHULHU_TRIGGER.get());
// 舰载炮塔原型：右键进入炮塔视角
                        output.accept(ModItems.TURRET.get());
                        // 自由瞄准炮塔原型：拖动准心引导视角
                        output.accept(ModItems.FREE_AIM_TURRET.get());
                        // 跟随炮塔原型：视角自由，炮塔准心追视角
                        output.accept(ModItems.TRACKING_TURRET.get());
                        // 原型炮塔的可选遥控站。
                        output.accept(ModItems.TURRET_STATION.get());
                        // 耐久调节器：直接推设备耐久，验收修理窗口/损坏状态用
                        output.accept(ModItems.CONDITION_TOOL.get());
                        // 训练人偶充气台：炮塔/近战命中测试设施（临时模型与贴图）
                        output.accept(ModItems.TRAINING_DUMMY_STATION.get());
                        // 高级版：200 血、无受击无敌帧，测连发 DPS
                        output.accept(ModItems.ADVANCED_TRAINING_DUMMY_STATION.get());
                        // 气球版：六向吊挂、沿朝向共占 2 格；1 血 / 200 血
                        output.accept(ModItems.BALLOON_DUMMY_STATION.get());
                        output.accept(ModItems.ADVANCED_BALLOON_DUMMY_STATION.get());
                    })
                    .build());


    /** 矿物页签：潜渊症原版矿物/矿石，贴图取自游戏素材。 */
    public static final RegistryObject<CreativeModeTab> MINERALS = TABS.register("minerals",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.barotrauma.minerals"))
                    .icon(() -> new ItemStack(ModItems.DIAMOND.get()))
                    .displayItems((params, output) ->
                            ModItems.MINERAL_ITEMS.forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeTabs() {
    }

    /**
     * 原版"建筑方块"页签（CreativeModeTabs.BUILDING_BLOCKS）的内容注入。
     * 暗绿马赛克瓷砖墙 / 四方白皮墙按要求归入原版建筑分类，不进模组自有页签。
     */
    private static void onBuildVanillaTab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.DARK_GREEN_MOSAIC_TILE_WALL.get());
            event.accept(ModItems.SQUARE_WHITE_PANEL_WALL.get());
        }
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        modBus.addListener(ModCreativeTabs::onBuildVanillaTab);
    }
}
