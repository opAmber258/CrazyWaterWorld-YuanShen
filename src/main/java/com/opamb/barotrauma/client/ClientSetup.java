package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModEntities;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化：渲染层、方块实体渲染器。
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    /** 注册模组按键：战役地图、水中自然下沉、原版光照切换等。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(CampaignClient.OPEN_MAP);
        event.register(UnderwaterMovement.TOGGLE_NATURAL_SINK);
        event.register(PowerClient.OPEN_POWER);
        event.register(TurretClient.TOGGLE_FIRE_MODE);
        event.register(VanillaLightingClient.TOGGLE);
    }

    /** 客户端初始化：干燥区回调、渲染层与菜单界面注册。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            OceanEnvironment.setClientDryZone(ShipWaterRenderer::dryAt);
            ItemBlockRenderTypes.setRenderLayer(ModShipBlocks.HULL_GLASS.get(), RenderType.cutout());
            // 测试船壳要真的透过去看见里面，cutout 只有全透明/全不透明两态，不够
            ItemBlockRenderTypes.setRenderLayer(ModShipBlocks.TEST_HULL.get(), RenderType.translucent());
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.COILGUN_LOADER.get(), CoilgunLoaderScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.SONAR.get(), SonarScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.MISSION_TERMINAL.get(), MissionScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.DUMMY_STATION.get(), DummyStationScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.PUMP.get(), PumpScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.REACTOR.get(), ReactorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.ENGINE.get(), EngineScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.BATTERY.get(), BatteryScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.SUPERCAPACITOR.get(),
                    SupercapacitorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.OXYGEN_GENERATOR.get(),
                    OxygenGeneratorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.CABINET.get(), CabinetScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.CRATE.get(), CrateScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.REPAIR.get(), RepairScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.opamb.barotrauma.registry.ModMenus.JUNCTION_BOX.get(), JunctionBoxScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.FABRICATOR.get(), FabricatorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.DECONSTRUCTOR.get(),
                    DeconstructorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.LIGHT.get(), LightScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(
            net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        // 每盏灯按坐标取色：Colorful Lighting 采样白色发光面板后乘这个 tint。
        event.register(LightClientColors::tint,
                com.opamb.barotrauma.registry.ModBlocks.LAMP.get(),
                com.opamb.barotrauma.registry.ModBlocks.EMERGENCY_LIGHT.get(),
                com.opamb.barotrauma.registry.ModBlocks.FLUORESCENT_LIGHT.get());
        // 手电筒的隐形光源：固定纯白，别让自动取色把不可见方块当成黑色。
        event.register((state, level, pos, tintIndex) -> 0xFFFFFF,
                com.opamb.barotrauma.registry.ModBlocks.FLASHLIGHT_LIGHT.get());
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SHIP_BUBBLE.get(), ShipBubbleParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHIP_SPRAY.get(), ShipSprayParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MARINE_SNOW.get(), MarineSnowParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TURRET_TRACER.get(), TurretTracerParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TURRET_BUBBLE.get(), TurretBubbleParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TURRET_SEDIMENT.get(), TurretSedimentParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        ShipWaterMaterial.register(event);
        FlashlightRenderer.register(event);
        DivingVisorOverlay.registerShader(event);
    }


    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TrainingDummyModel.LAYER, TrainingDummyModel::createBodyLayer);
        event.registerLayerDefinition(BalloonDummyModel.LAYER, BalloonDummyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SONAR.get(), SonarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NAVIGATION_TERMINAL.get(),
                context -> new NavigationTerminalRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.SMALL_PUMP.get(), context -> new SmallPumpRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.BALLAST_PUMP.get(),
                context -> new BallastPumpRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.REACTOR.get(),
                context -> new ReactorRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.ENGINE.get(),
                context -> new EngineRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.JUNCTION_BOX.get(),
                context -> new JunctionBoxRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY.get(),
                context -> new BatteryRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.SUPERCAPACITOR.get(),
                context -> new SupercapacitorRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.PERISCOPE.get(),
                context -> new PeriscopeRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.PERISCOPE_STAND.get(),
                context -> new PeriscopeStandRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.FABRICATOR.get(),
                context -> new FabricatorRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.DECONSTRUCTOR.get(),
                context -> new DeconstructorRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.CABINET.get(),
                context -> new CabinetRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.HATCH.get(), context -> new HatchRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.BULKHEAD_DOOR.get(),
                context -> new BulkheadDoorRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.TURRET.get(), TurretRenderer::new);
        event.registerEntityRenderer(com.opamb.barotrauma.registry.ModEntities.VESSEL.get(), VesselRenderer::new);
        event.registerEntityRenderer(com.opamb.barotrauma.registry.ModEntities.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.COILGUN_BOLT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.COILGUN_INTERACTION.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.TRAINING_DUMMY.get(), TrainingDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.ADVANCED_TRAINING_DUMMY.get(),
                AdvancedTrainingDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.BALLOON_DUMMY.get(),
                context -> new BalloonDummyRenderer<>(context, new net.minecraft.resources.ResourceLocation(
                        BarotraumaMod.MOD_ID, "textures/entity/balloon_dummy.png")));
        event.registerEntityRenderer(ModEntities.ADVANCED_BALLOON_DUMMY.get(),
                context -> new BalloonDummyRenderer<>(context, new net.minecraft.resources.ResourceLocation(
                        BarotraumaMod.MOD_ID, "textures/entity/advanced_balloon_dummy.png")));
    }

    /** 头盔遮罩贴着场景，炮塔 HUD 叠在热键栏之上。 */
    @SubscribeEvent
    public static void onRegisterOverlays(
            net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("diving_visor", DivingVisorOverlay::render);
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(),
                "turret_hud", new TurretHud());
    }

    /**
     * 给所有玩家渲染器追加潜水服护甲层（经典/slim 两种皮肤各一份）。
     *
     * <p>潜水服在自定义装备槽里，原版护甲层不会渲染它，只能自己挂一层。</p>
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.EntityRenderer<? extends net.minecraft.world.entity.player.Player> renderer =
                    event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new DivingSuitLayer(playerRenderer));
            }
        }
    }
}
