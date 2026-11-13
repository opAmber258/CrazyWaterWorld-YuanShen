package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.mission.MissionNpc;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 站务员 NPC 的保障：每 2 秒检查一次玩家所在的哨站，没有 NPC 就补一个。
 *
 * <p>不放在世界生成里：实体没法直接写进 {@code ChunkAccess}，而且这样连
 * 旧存档 / 读档 / 靠泊后都能自动补齐。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcEvents {

    private NpcEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 40L != 0L) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel deep = server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) {
            return;
        }
        MissionNpc.ensureStations(deep);
    }
}
