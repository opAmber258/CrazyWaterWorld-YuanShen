package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BallastPumpBlock;
import com.opamb.barotrauma.block.Cpu6700X3DBlock;
import com.opamb.barotrauma.block.DrainPumpBlock;
import com.opamb.barotrauma.block.HullBlock;
import com.opamb.barotrauma.block.IronLadderBlock;
import com.opamb.barotrauma.block.MissionTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册表。
 *
 * <p>注意：这里只注册 Block 本体，对应的 BlockItem 在 {@link ModItems} 里注册，
 * 这样方块物品能直接进创造模式物品栏。</p>
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BarotraumaMod.MOD_ID);

    /** 潜艇装甲板：高压环境下也不变形的船体钢板。 */
    public static final RegistryObject<Block> SUBMARINE_PLATING = BLOCKS.register("submarine_plating",
            () -> new HullBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(25.0F, 1200.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .isViewBlocking((state, level, pos) -> !HullBlock.isBreached(state))
                    .isSuffocating((state, level, pos) -> !HullBlock.isBreached(state))));

    /** 任务终端：接任务/交付/商店的界面入口（哨站和 B 中继站各一台）。 */
    public static final RegistryObject<Block> MISSION_TERMINAL = BLOCKS.register("mission_terminal",
            () -> new MissionTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(15.0F, 1200.0F)
                    .sound(SoundType.METAL)));

    /** 排水泵：把船内模拟水往外抽；右键菜单设开关与排水量。 */
    public static final RegistryObject<Block> DRAIN_PUMP = BLOCKS.register("drain_pump",
            () -> new DrainPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(10.0F, 600.0F)
                    .sound(SoundType.METAL)));

    /** 小型泵：船内大多数位置用的小功率排水泵（GeckoLib 体素模型）。 */
    public static final RegistryObject<Block> SMALL_PUMP = BLOCKS.register("small_pump",
            () -> new com.opamb.barotrauma.block.SmallPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 300.0F)
                    .sound(SoundType.METAL)
                    // 方块本身是 INVISIBLE、靠 GeckoLib 渲染；不关遮挡的话相邻方块
                    // 朝向它的面会被剔除，模型没遮住的地方就会露出“洞”（用户实测）。
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 压载泵：3 格宽 × 2 格高 × 1 格深的六格墙面泵，装在压载舱内壁。
     * 跟随舵台垂直指令注排压载舱水，水量进 ShipSimulation 的浮力因子。
     */
    public static final RegistryObject<Block> BALLAST_PUMP = BLOCKS.register("ballast_pump",
            () -> new BallastPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(12.0F, 800.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 反应堆：3 格宽 × 2.5 格高 × 2 格深的落地多方块（框架 3×3×2，顶排半格）。
     * 发电驱动全船；没堆/没开机 = 没动力（见 ShipSimulation 的 powerRatio）。
     */
    public static final RegistryObject<Block> REACTOR = BLOCKS.register("reactor",
            () -> new com.opamb.barotrauma.block.ReactorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 引擎：3 格宽 × 2 格高 × 3 格深的落地多方块（18 格，后层是螺旋桨护罩）。
     * 必须装在船上才提供推进；声纳油门控制目标力，耗电走 ShipSimulation。
     */
    public static final RegistryObject<Block> ENGINE = BLOCKS.register("engine",
            () -> new com.opamb.barotrauma.block.EngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    // ---- 潜渊症容器（2026-11-10） ----
    /** 中型钢柜：5×4 主格 + 3 个移动容器格。 */
    public static final RegistryObject<Block> MEDIUM_STEEL_CABINET = BLOCKS.register("medium_steel_cabinet",
            () -> new com.opamb.barotrauma.block.CabinetBlock(
                    com.opamb.barotrauma.block.CabinetBlock.Kind.MEDIUM_STEEL_CABINET, cabinetProps()));

    /** 中型带窗钢柜：同中型钢柜，正面带观察窗。 */
    public static final RegistryObject<Block> MEDIUM_WINDOWED_STEEL_CABINET =
            BLOCKS.register("medium_windowed_steel_cabinet",
                    () -> new com.opamb.barotrauma.block.CabinetBlock(
                            com.opamb.barotrauma.block.CabinetBlock.Kind.MEDIUM_WINDOWED_STEEL_CABINET,
                            cabinetProps()));

    /** 箱子货架：1×4 货箱位。 */
    public static final RegistryObject<Block> CRATE_SHELF = BLOCKS.register("crate_shelf",
            () -> new com.opamb.barotrauma.block.CabinetBlock(
                    com.opamb.barotrauma.block.CabinetBlock.Kind.CRATE_SHELF, cabinetProps()));

    /** 潜水服柜：1 个潜水服位。 */
    public static final RegistryObject<Block> DIVING_SUIT_LOCKER = BLOCKS.register("diving_suit_locker",
            () -> new com.opamb.barotrauma.block.CabinetBlock(
                    com.opamb.barotrauma.block.CabinetBlock.Kind.DIVING_SUIT_LOCKER, cabinetProps()));

    /** 氧气瓶架：潜水服柜下方的 3 个氧气瓶位。 */
    public static final RegistryObject<Block> OXYGEN_TANK_SHELF = BLOCKS.register("oxygen_tank_shelf",
            () -> new com.opamb.barotrauma.block.CabinetBlock(
                    com.opamb.barotrauma.block.CabinetBlock.Kind.OXYGEN_TANK_SHELF, cabinetProps()));

    /** 潜水服柜（组合）：原作装配比例，上层潜水服、下层氧气瓶架。 */
    public static final RegistryObject<Block> DIVING_SUIT_STATION = BLOCKS.register("diving_suit_station",
            () -> new com.opamb.barotrauma.block.CabinetBlock(
                    com.opamb.barotrauma.block.CabinetBlock.Kind.DIVING_SUIT_STATION, cabinetProps()));

    private static BlockBehaviour.Properties cabinetProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(8.0F, 300.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                // 多格柜形状随 part 变化：不让无上下文查询缓存空世界里的空形状。
                .dynamicShape()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                // 多格柜的分片自愈（CabinetBlock.randomTick）：清孤儿分片、补齐整列。
                .randomTicks();
    }

    /** 免支撑铁梯：整格宽、2px 厚的钢板梯，立在对接井中央，可攀爬、有碰撞。 */    public static final RegistryObject<Block> IRON_LADDER = BLOCKS.register("iron_ladder",
            () -> new IronLadderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // 透明（cutout）薄板模型：不关遮挡会让相邻方块朝它的面被剔除。
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 排水口：对照原版 duct block（地板通水格栅）。
     *
     * <p>不是泵，没有方块实体；水可以穿过这一格流向相邻舱室（见
     * {@link ModShipBlocks#isDoorway} / {@link ModShipBlocks#isOpenDoorway}）。</p>
     */
    public static final RegistryObject<Block> DRAIN_OUTLET = BLOCKS.register("drain_outlet",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 200.0F)
                    .sound(SoundType.METAL)));

    /** 黑匣子方块：沉船船舱里那一格，打掉掉落 black_box（打捞任务的交付物）。 */
    public static final RegistryObject<Block> BLACK_BOX_BLOCK = BLOCKS.register("black_box_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)));

    /** 6700X3D 处理器（彩蛋方块）：12×12×3 像素的小摆件，纯装饰、无功能。 */
    public static final RegistryObject<Block> CPU_6700X3D = BLOCKS.register("cpu_6700x3d",
            () -> new Cpu6700X3DBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F, 6.0F)
                    .sound(SoundType.METAL)
                    // 不是整格方块：关掉遮挡剔除，避免相邻面被误剔
                    .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
