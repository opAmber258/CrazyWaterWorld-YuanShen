package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 菜单注册。
 *
 * <p>旧的 {@code ship_console} 菜单已经删掉了：它是早期"独立的船控制台界面"的遗留物，
 * 后来掌舵和状态全都并进了声纳台，没有任何方块再去打开它，属于纯死代码。</p>
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, BarotraumaMod.MOD_ID);

    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.SonarMenu>> SONAR =
            MENUS.register("sonar", () -> IForgeMenuType.create(com.opamb.barotrauma.menu.SonarMenu::new));

    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.MissionMenu>> MISSION_TERMINAL =
            MENUS.register("mission_terminal", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.MissionMenu(id,
                            // 原版 openMenu 不写额外数据时 buf 为 null，直接 readBlockPos 会 NPE。
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    /** 高级训练设施设置：血量 / 自发光 / 重生间隔。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.DummyStationMenu>> DUMMY_STATION =
            MENUS.register("dummy_station", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.DummyStationMenu(id, inv, buf)));

    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.PumpMenu>> PUMP =
            MENUS.register("drain_pump", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.PumpMenu(id, inv,
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.ReactorMenu>> REACTOR =
            MENUS.register("reactor", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.ReactorMenu(id, inv,
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.EngineMenu>> ENGINE =
            MENUS.register("engine", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.EngineMenu(id, inv,
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    /** 电池面板：电量条 + 充电速率滑条 + 耐久。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.BatteryMenu>> BATTERY =
            MENUS.register("battery", () -> IForgeMenuType.create(
                    com.opamb.barotrauma.menu.BatteryMenu::new));

    /** 超级电容器面板：电量条 + 充电速率滑条 + 耐久。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.SupercapacitorMenu>> SUPERCAPACITOR =
            MENUS.register("supercapacitor", () -> IForgeMenuType.create(
                    com.opamb.barotrauma.menu.SupercapacitorMenu::new));

    /** 制氧机面板：5 个氧气瓶槽 + 产氧量/电压/耐久读数。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.OxygenGeneratorMenu>> OXYGEN_GENERATOR =
            MENUS.register("oxygen_generator", () -> IForgeMenuType.create(
                    com.opamb.barotrauma.menu.OxygenGeneratorMenu::new));

    /** 加工台：5 进 1 出 + 配方列表。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.FabricatorMenu>> FABRICATOR =
            MENUS.register("fabricator", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.FabricatorMenu(id, inv,
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    /** 解构仪：3 进 5 出 + 拆解进度。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.DeconstructorMenu>> DECONSTRUCTOR =
            MENUS.register("deconstructor", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.DeconstructorMenu(id, inv,
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    /**
     * 修理窗口（原作 {@code Repairable} 的独立 GUI 帧）：手持工具右键设备时打开，
     * 和泵/反应堆/引擎/电池/接线盒/声纳/门舱口共用。
     */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.RepairMenu>> REPAIR =
            MENUS.register("repair", () -> IForgeMenuType.create(
                    (id, inv, buf) -> new com.opamb.barotrauma.menu.RepairMenu(id, inv,
                            buf == null ? net.minecraft.core.BlockPos.ZERO : buf.readBlockPos())));

    /** 潜渊症容器：钢柜/货架/潜水服柜/氧气瓶架共用。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.CabinetMenu>> CABINET =
            MENUS.register("cabinet", () -> IForgeMenuType.create(
                    // 缓冲里是 pos + 种类；种类由 CabinetMenu 的 buf 构造器解析。
                    com.opamb.barotrauma.menu.CabinetMenu::new));

    /** 货运箱内部容器：右键箱子打开（左边玩家背包、右边箱子内容）。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.CrateMenu>> CRATE =
            MENUS.register("crate", () -> IForgeMenuType.create(
                    com.opamb.barotrauma.menu.CrateMenu::new));

    /** 灯具面板：一盏灯的 RGB 滑条 + 开关。 */
    public static final RegistryObject<MenuType<com.opamb.barotrauma.menu.LightMenu>> LIGHT =
            MENUS.register("light", () -> IForgeMenuType.create(
                    com.opamb.barotrauma.menu.LightMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
