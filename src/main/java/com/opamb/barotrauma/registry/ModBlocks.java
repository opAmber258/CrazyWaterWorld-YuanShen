package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.AdvancedTrainingDummyStationBlock;
import com.opamb.barotrauma.block.BallastPumpBlock;
import com.opamb.barotrauma.block.BalloonStationBlock;
import com.opamb.barotrauma.block.Cpu6700X3DBlock;
import com.opamb.barotrauma.block.DrainPumpBlock;
import com.opamb.barotrauma.block.FreeAimTurretBlock;
import com.opamb.barotrauma.block.HullBlock;
import com.opamb.barotrauma.block.IronLadderBlock;
import com.opamb.barotrauma.block.MissionTerminalBlock;
import com.opamb.barotrauma.block.TrackingTurretBlock;
import com.opamb.barotrauma.block.TrainingDummyStationBlock;
import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.block.TurretStationBlock;
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

    /**
     * 接线盒：墙上分线/过压保护节点。电网耗电 0；绑在它上面的设备共享一个
     * 全船电压，过压/泡水会掉耐久。
     */
    public static final RegistryObject<Block> JUNCTION_BOX = BLOCKS.register("junction_box",
            () -> new com.opamb.barotrauma.block.JunctionBoxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(8.0F, 300.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 电池（设备）：墙挂蓄电设备。原版 2000 kW·min / 最大输出 1000 kW /
     * 最大充电 500 kW；供电不足时补差，富余时充电。
     */
    public static final RegistryObject<Block> BATTERY = BLOCKS.register("battery",
            () -> new com.opamb.barotrauma.block.BatteryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(8.0F, 300.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 超级电容器（设备）：墙挂储能。原作 20 kW·min / 最大输出 20 MW /
     * 充电 1000 kW / 效率 0.1；瞬间脉冲供能。
     */
    public static final RegistryObject<Block> SUPERCAPACITOR = BLOCKS.register("supercapacitor",
            () -> new com.opamb.barotrauma.block.SupercapacitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(8.0F, 300.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /** 潜望镜：接管附近炮塔的世界坐标设备（和炮塔一样暂不随 VS2 船移动）。 */
    public static final RegistryObject<Block> PERISCOPE = BLOCKS.register("periscope",
            () -> new com.opamb.barotrauma.block.PeriscopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 120.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /** 潜望镜底座：纯装饰方块，可垫在潜望镜下面。 */
    public static final RegistryObject<Block> PERISCOPE_STAND = BLOCKS.register("periscope_stand",
            () -> new com.opamb.barotrauma.block.PeriscopeStandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 120.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    // ---- 加工台 / 解构仪（2026-11-16） ----
    /**
     * 加工台：3×3×2 落地多方块。原作 500 kW、MinVoltage 0.5，
     * 材料来自台面 5 格或玩家背包，成品进 1 格输出槽。
     */
    public static final RegistryObject<Block> FABRICATOR = BLOCKS.register("fabricator",
            () -> new com.opamb.barotrauma.block.FabricatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /** 解构仪：2×3×2 落地多方块。原作 500 kW，3 进 5 出，工作时播双刀轴动画。 */
    public static final RegistryObject<Block> DECONSTRUCTOR = BLOCKS.register("deconstructor",
            () -> new com.opamb.barotrauma.block.DeconstructorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 制氧机（临时单格版本）：原作是约 2.1×2.0 格的落地机器、1000 kW、5 个氧气瓶槽。
     *
     * <p>模型/多块/正式 GUI 还没做，先用普通立方体把"制氧机 → 舱室氧气"这条链路跑通；
     * 正式模型确认后按多方块重做。</p>
     */
    public static final RegistryObject<Block> OXYGEN_GENERATOR = BLOCKS.register("oxygen_generator",
            () -> new com.opamb.barotrauma.block.OxygenGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(20.0F, 1200.0F)
                    .sound(SoundType.METAL)));

    /**
     * 通风口（临时单格版本）：原作是 0.73 格见方的墙挂通风口，只负责把氧送进所在舱室。
     *
     * <p>先用普通立方体占位；制氧机按"有通风口的舱室"体积分配氧气。</p>
     */
    public static final RegistryObject<Block> VENT = BLOCKS.register("vent",
            () -> new com.opamb.barotrauma.block.VentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 200.0F)
                    .sound(SoundType.METAL)));

    // ---- 占位灯具（2026-11-23） ----
    /*
     * 正式模型未到，先用舱顶薄板占位。原版儒艮号的三类灯具：
     *   lamp           5 kW，主照明，可开关/调色；
     *   emergencylight 0 W，常亮暗红，断电也亮；
     *   lightfluorescentl01/l02/m04 5 kW，大舱室长条灯。
     */
    /** 吊灯（占位）：主照明。 */
    public static final RegistryObject<Block> LAMP = BLOCKS.register("lamp",
            () -> new com.opamb.barotrauma.block.LightBlock(
                    com.opamb.barotrauma.block.LightBlock.Kind.LAMP,
                    lightProps(com.opamb.barotrauma.block.LightBlock.Kind.LAMP)));

    /** 应急灯（占位）：常亮暗红。 */
    public static final RegistryObject<Block> EMERGENCY_LIGHT = BLOCKS.register("emergency_light",
            () -> new com.opamb.barotrauma.block.LightBlock(
                    com.opamb.barotrauma.block.LightBlock.Kind.EMERGENCY,
                    lightProps(com.opamb.barotrauma.block.LightBlock.Kind.EMERGENCY)));

    /** 荧光灯（占位）：大舱室长条灯。 */
    public static final RegistryObject<Block> FLUORESCENT_LIGHT = BLOCKS.register("fluorescent_light",
            () -> new com.opamb.barotrauma.block.LightBlock(
                    com.opamb.barotrauma.block.LightBlock.Kind.FLUORESCENT,
                    lightProps(com.opamb.barotrauma.block.LightBlock.Kind.FLUORESCENT)));

    /** 旧手电筒光源的存档兼容注册。保留原亮度以便移除时触发一次光照清理。 */
    public static final RegistryObject<Block> FLASHLIGHT_LIGHT = BLOCKS.register("flashlight_light",
            () -> new com.opamb.barotrauma.block.FlashlightLightBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .noOcclusion()
                            .replaceable()
                            .noLootTable()
                            .strength(-1.0F, 3600000.0F)
                            .lightLevel(state -> state.getValue(
                                    net.minecraft.world.level.block.LightBlock.LEVEL))
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)));

    private static BlockBehaviour.Properties lightProps(
            com.opamb.barotrauma.block.LightBlock.Kind kind) {
        return BlockBehaviour.Properties.of()
                .mapColor(kind.mapColor())
                .strength(2.0F, 6.0F)
                .sound(SoundType.METAL)
                // 薄板灯：不挡视线、不窒息、无碰撞，也不遮挡相邻方块的面。
                .noOcclusion()
                .noCollission()
                .lightLevel(state -> state.getValue(com.opamb.barotrauma.block.LightBlock.LIGHT))
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

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
                    .sound(SoundType.METAL)
                    // 中间是挖空的格栅（cutout），不能当实心遮挡面。
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

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

/** 舰载炮塔（原型）：右键进入炮塔视角，炮管由方块实体渲染。 */
    public static final RegistryObject<Block> TURRET = BLOCKS.register("turret",
            () -> new TurretBlock(turretProperties()));

    /** 自由瞄准炮塔（原型）：和舰载炮塔共用方块实体，客户端换成“拖动准心引导视角”。 */
    public static final RegistryObject<Block> FREE_AIM_TURRET = BLOCKS.register("free_aim_turret",
            () -> new FreeAimTurretBlock(turretProperties()));

    /** 跟随炮塔（原型）：视角自由，炮塔准心限速追视角中心。 */
    public static final RegistryObject<Block> TRACKING_TURRET = BLOCKS.register("tracking_turret",
            () -> new TrackingTurretBlock(turretProperties()));

    /** 电磁炮：复用炮塔瞄准，弹药箱装填 + 船电网 1000 kW。 */
    public static final RegistryObject<Block> COILGUN = BLOCKS.register("coilgun",
            () -> new com.opamb.barotrauma.block.CoilgunBlock(turretProperties()));

    /** 炮塔瞄准站（开发者工具）：绑定炮塔后右键接管瞄准视角。 */
    public static final RegistryObject<Block> TURRET_STATION = BLOCKS.register("turret_station",
            () -> new TurretStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    // 控制台不是整格方块（潜望柱/斜面收边），关掉遮挡剔除
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    private static BlockBehaviour.Properties turretProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(8.0F, 600.0F)
                .sound(SoundType.METAL)
                // 炮座不是整格方块，炮管还会伸出方块范围
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false);
    }

    /** 训练人偶充气台：放置后在上方生成 1 血、固定不动的训练人偶。 */
    public static final RegistryObject<Block> TRAINING_DUMMY_STATION = BLOCKS.register(
            "training_dummy_station", () -> new TrainingDummyStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F, 120.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)));

    /** 高级训练人偶充气台：放置后生成 200 血、无受击无敌帧的高级人偶。 */
    public static final RegistryObject<Block> ADVANCED_TRAINING_DUMMY_STATION = BLOCKS.register(
            "advanced_training_dummy_station", () -> new AdvancedTrainingDummyStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(5.0F, 120.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)));

    /** 训练气球充气台：六向放置，气球沿 FACING 生成在相邻格。 */
    public static final RegistryObject<Block> BALLOON_DUMMY_STATION = BLOCKS.register(
            "balloon_dummy_station", () -> new BalloonStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F, 120.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)));

    /** 高级训练气球充气台：同上，气球 200 血、无受击间隔。 */
    public static final RegistryObject<Block> ADVANCED_BALLOON_DUMMY_STATION = BLOCKS.register(
            "advanced_balloon_dummy_station",
            () -> new com.opamb.barotrauma.block.AdvancedBalloonStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(5.0F, 120.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isViewBlocking((state, level, pos) -> false)
                            .isSuffocating((state, level, pos) -> false)));

    // ---- 采矿任务用的岩壁矿脉（副本航线附近生成，等离子切割机切碎） ----
    /** 石英矿脉。 */
    public static final RegistryObject<Block> QUARTZ_VEIN = BLOCKS.register("quartz_vein",
            () -> new com.opamb.barotrauma.block.MineralVeinBlock(veinProperties(),
                    ModItems.QUARTZ));

    /** 磷氯铅矿脉。 */
    public static final RegistryObject<Block> PYROMORPHITE_VEIN = BLOCKS.register("pyromorphite_vein",
            () -> new com.opamb.barotrauma.block.MineralVeinBlock(veinProperties(),
                    ModItems.PYROMORPHITE));

    /** 铀矿脉。 */
    public static final RegistryObject<Block> URANIUM_VEIN = BLOCKS.register("uranium_vein",
            () -> new com.opamb.barotrauma.block.MineralVeinBlock(veinProperties(),
                    ModItems.URANIUM_ORE));

    /** 矿脉共用属性：硬，但切割机约 4 秒一块。 */
    private static BlockBehaviour.Properties veinProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(30.0F, 1200.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
