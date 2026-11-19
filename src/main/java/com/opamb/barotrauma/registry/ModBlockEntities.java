package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.BallastPumpBlockEntity;
import com.opamb.barotrauma.blockentity.BulkheadDoorBlockEntity;
import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.blockentity.HatchBlockEntity;
import com.opamb.barotrauma.blockentity.NavigationTerminalBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.blockentity.SmallPumpBlockEntity;
import com.opamb.barotrauma.blockentity.SonarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块实体注册表。
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BarotraumaMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<SonarBlockEntity>> SONAR =
            BLOCK_ENTITIES.register("sonar",
                    () -> BlockEntityType.Builder.of(SonarBlockEntity::new, ModShipBlocks.SONAR.get()).build(null));

    /** 导航终端：3×3×1 多方块，只有下排中段有方块实体（GeckoLib 渲染 + 船核心 + 声纳全息）。 */
    public static final RegistryObject<BlockEntityType<NavigationTerminalBlockEntity>> NAVIGATION_TERMINAL =
            BLOCK_ENTITIES.register("navigation_terminal", () -> BlockEntityType.Builder
                    .of(NavigationTerminalBlockEntity::new, ModShipBlocks.NAVIGATION_TERMINAL.get())
                    .build(null));

    /** 普通排水泵：普通方块模型 + 流量/自动控制逻辑。 */
    public static final RegistryObject<BlockEntityType<DrainPumpBlockEntity>> DRAIN_PUMP =
            BLOCK_ENTITIES.register("drain_pump", () -> BlockEntityType.Builder
                    .of(DrainPumpBlockEntity::new, ModBlocks.DRAIN_PUMP.get())
                    .build(null));

    /** 小型泵：同一套逻辑，但方块实体额外实现 GeoBlockEntity（GeckoLib 体素模型）。 */
    public static final RegistryObject<BlockEntityType<SmallPumpBlockEntity>> SMALL_PUMP =
            BLOCK_ENTITIES.register("small_pump", () -> BlockEntityType.Builder
                    .of(SmallPumpBlockEntity::new, ModBlocks.SMALL_PUMP.get())
                    .build(null));

    /** 压载泵：六格多块，只有下排中段有方块实体（渲染 + 压载上报）。 */
    public static final RegistryObject<BlockEntityType<BallastPumpBlockEntity>> BALLAST_PUMP =
            BLOCK_ENTITIES.register("ballast_pump", () -> BlockEntityType.Builder
                    .of(BallastPumpBlockEntity::new, ModBlocks.BALLAST_PUMP.get())
                    .build(null));

    /** 反应堆：18 格多块，只有前层下排中段有方块实体（渲染 + 燃料/温度/发电）。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.ReactorBlockEntity>> REACTOR =
            BLOCK_ENTITIES.register("reactor", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.ReactorBlockEntity::new, ModBlocks.REACTOR.get())
                    .build(null));

    /** 引擎：18 格多块（3×2×3），只有前层下排中段有方块实体。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.EngineBlockEntity>> ENGINE =
            BLOCK_ENTITIES.register("engine", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.EngineBlockEntity::new, ModBlocks.ENGINE.get())
                    .build(null));

    /** 接线盒：耐久/过压/泡水损坏 + 电力面板。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity>> JUNCTION_BOX =
            BLOCK_ENTITIES.register("junction_box", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity::new,
                            ModBlocks.JUNCTION_BOX.get())
                    .build(null));

    /** 电池（设备）：存电量、充放电、四档电量屏。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.BatteryBlockEntity>> BATTERY =
            BLOCK_ENTITIES.register("battery", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.BatteryBlockEntity::new,
                            ModBlocks.BATTERY.get())
                    .build(null));

    /** 加工台：18 格多块（3×3×2），主方块在前层下排中列。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.FabricatorBlockEntity>> FABRICATOR =
            BLOCK_ENTITIES.register("fabricator", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.FabricatorBlockEntity::new,
                            ModBlocks.FABRICATOR.get())
                    .build(null));

    /** 解构仪：12 格多块（2×3×2），主方块在前层下排左列。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.DeconstructorBlockEntity>> DECONSTRUCTOR =
            BLOCK_ENTITIES.register("deconstructor", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.DeconstructorBlockEntity::new,
                            ModBlocks.DECONSTRUCTOR.get())
                    .build(null));

    /** 制氧机（临时单格版本）：5 个氧气瓶槽 + 向船电网登记制氧量。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.OxygenGeneratorBlockEntity>> OXYGEN_GENERATOR =
            BLOCK_ENTITIES.register("oxygen_generator", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.OxygenGeneratorBlockEntity::new,
                            ModBlocks.OXYGEN_GENERATOR.get())
                    .build(null));

    /** 通风口（临时单格版本）：只把所在舱室登记到氧气分配里。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.VentBlockEntity>> VENT =
            BLOCK_ENTITIES.register("vent", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.VentBlockEntity::new,
                            ModBlocks.VENT.get())
                    .build(null));

    /** 潜渊症容器：钢柜/带窗钢柜/货架/潜水服柜/氧气瓶架共用一套方块实体。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.CabinetBlockEntity>> CABINET =
            BLOCK_ENTITIES.register("cabinet", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.CabinetBlockEntity::new,
                            ModBlocks.MEDIUM_STEEL_CABINET.get(),
                            ModBlocks.MEDIUM_WINDOWED_STEEL_CABINET.get(),
                            ModBlocks.CRATE_SHELF.get(),
                            ModBlocks.DIVING_SUIT_LOCKER.get(),
                            ModBlocks.OXYGEN_TANK_SHELF.get(),
                            ModBlocks.DIVING_SUIT_STATION.get())
                    .build(null));

    /** 3 格宽舱口（普通舱口和带梯变种共用）；只有中间段有方块实体，渲染整扇模型。 */
    public static final RegistryObject<BlockEntityType<HatchBlockEntity>> HATCH =
            BLOCK_ENTITIES.register("hatch", () -> BlockEntityType.Builder
                    .of(HatchBlockEntity::new,
                            ModShipBlocks.HATCH.get(),
                            ModShipBlocks.HATCH_LADDER.get())
                    .build(null));

    /** 3 格高舱壁门；只有最下面一段有方块实体，渲染整扇模型。 */
    public static final RegistryObject<BlockEntityType<BulkheadDoorBlockEntity>> BULKHEAD_DOOR =
            BLOCK_ENTITIES.register("bulkhead_door", () -> BlockEntityType.Builder
                    .of(BulkheadDoorBlockEntity::new, ModShipBlocks.BULKHEAD_DOOR.get()).build(null));

    /** 舰载/自由瞄准炮塔（原型）：占用者/瞄准角/冷却 + 炮管渲染；两种炮塔共用同一个方块实体类型。 */
    public static final RegistryObject<BlockEntityType<com.opamb.barotrauma.blockentity.TurretBlockEntity>> TURRET =
            BLOCK_ENTITIES.register("turret", () -> BlockEntityType.Builder
                    .of(com.opamb.barotrauma.blockentity.TurretBlockEntity::new,
                            ModBlocks.TURRET.get(),
                            ModBlocks.FREE_AIM_TURRET.get(),
                            ModBlocks.TRACKING_TURRET.get())
                    .build(null));

    // 船核心方块已经合并进声纳台（SonarBlockEntity extends ShipCoreBlockEntity），
    // 不再有独立的 ship_core 方块实体。

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
