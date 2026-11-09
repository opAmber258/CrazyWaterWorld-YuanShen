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

    // 船核心方块已经合并进声纳台（SonarBlockEntity extends ShipCoreBlockEntity），
    // 不再有独立的 ship_core 方块实体。

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
