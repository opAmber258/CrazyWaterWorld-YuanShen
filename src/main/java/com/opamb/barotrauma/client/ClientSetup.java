package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
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

    /** 注册模组按键：战役地图与水中自然下沉开关。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(CampaignClient.OPEN_MAP);
        event.register(UnderwaterMovement.TOGGLE_NATURAL_SINK);
    }

    /** 客户端初始化：干燥区回调、渲染层与菜单界面注册。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            OceanEnvironment.setClientDryZone(new OceanEnvironment.DryZone() {
                @Override
                public boolean isDry(net.minecraft.world.level.Level level,
                                     net.minecraft.world.phys.Vec3 pos) {
                    return ShipWaterRenderer.isInsideDryZone(level, pos);
                }

                @Override
                public boolean isInsideCompartment(net.minecraft.world.level.Level level,
                                                   net.minecraft.world.phys.Vec3 pos) {
                    return ShipWaterRenderer.isInsideCompartment(level, pos);
                }
            });
            ItemBlockRenderTypes.setRenderLayer(ModShipBlocks.HULL_GLASS.get(), RenderType.cutout());
            // 测试船壳要真的透过去看见里面，cutout 只有全透明/全不透明两态，不够
            ItemBlockRenderTypes.setRenderLayer(ModShipBlocks.TEST_HULL.get(), RenderType.translucent());
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.SONAR.get(), SonarScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.MISSION_TERMINAL.get(), MissionScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.PUMP.get(), PumpScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.REACTOR.get(), ReactorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.ENGINE.get(), EngineScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.CABINET.get(), CabinetScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.opamb.barotrauma.registry.ModMenus.CRATE.get(), CrateScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SHIP_BUBBLE.get(), ShipBubbleParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHIP_SPRAY.get(), ShipSprayParticle.Provider::new);
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
        event.registerBlockEntityRenderer(ModBlockEntities.CABINET.get(),
                context -> new CabinetRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.HATCH.get(), context -> new HatchRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.BULKHEAD_DOOR.get(),
                context -> new BulkheadDoorRenderer());
        event.registerEntityRenderer(com.opamb.barotrauma.registry.ModEntities.VESSEL.get(), VesselRenderer::new);
        event.registerEntityRenderer(com.opamb.barotrauma.registry.ModEntities.NPC.get(), NpcRenderer::new);
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
