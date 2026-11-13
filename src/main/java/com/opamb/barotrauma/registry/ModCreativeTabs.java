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
                        output.accept(ModItems.DEEP_PERMIT.get());
                        output.accept(ModItems.DIVING_KNIFE.get());
                        output.accept(ModItems.WELDING_TOOL.get());
                        output.accept(ModItems.WELDING_FUEL_TANK.get());
                        output.accept(ModItems.SUBMARINE_PLATING.get());
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
                        // 彩蛋物品从主页签挪到开发者页签（用户指令 2026-11-08）
                        output.accept(ModItems.SIXTY_SEVEN.get());
                        output.accept(ModItems.CPU_6700X3D.get());
                        // 克苏鲁的注视：只有一个触发器，预设与弱/中/强档位在面板里选
                        output.accept(ModItems.CTHULHU_TRIGGER.get());
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

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
