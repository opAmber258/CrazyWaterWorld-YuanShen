package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.network.SwimStatePacket;
import com.opamb.barotrauma.ship.vs.VsClientDrag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端游泳状态缓存（{@link SwimStatePacket} 写入，水中移动的入口判定用）。
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSwimState {

    private static volatile boolean known;
    private static volatile boolean swimMovement;
    private static volatile long dragShipId = -1L;

    private ClientSwimState() {
    }

    /** 渲染线程调用（包处理器 enqueueWork 后已经在客户端主线程）。 */
    public static void accept(SwimStatePacket packet) {
        known = true;
        swimMovement = packet.swimMovement();
        dragShipId = packet.dragShipId();
    }

    /** 服务端是否已经推过状态（没推过时客户端用本地判定兜底）。 */
    public static boolean known() {
        return known;
    }

    /** 当前水深允许游泳。 */
    public static boolean swimMovement() {
        return swimMovement;
    }

    /** 服务端权威随船拖拽：船 id；-1 = 不拖。 */
    public static long dragShipId() {
        return dragShipId;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        known = false;
        swimMovement = false;
        dragShipId = -1L;
        VsClientDrag.reset();
    }
}
