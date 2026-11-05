package com.opamb.barotrauma.campaign;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CampaignEvents {
    private CampaignEvents() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event); }
    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CampaignManager.sync(player);
    }
}
