package com.opamb.barotrauma.ship.vs;

import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * 客户端跟随服务端的随船拖拽决定。
 *
 * <p>客户端不再自己判断"在不在舱里/船动没动"：服务端把 {@code dragShipId} 放在
 * {@code SwimStatePacket} 里下发，客户端只负责把它写到 VS2 的
 * {@code EntityDraggingInformation}。两边用同一个船 id，才不会出现客户端把玩家
 * 当船上、服务端不拖（或反过来）导致的位置回拉。</p>
 */
public final class VsClientDrag {

    private VsClientDrag() {
    }

    public static void detachForDock(Player player) {
        if (VsSupport.available() && player != null) {
            VsServerDrag.detach(player);
        }
        reset();
    }

    /** 本客户端上次写入的船 id；-1 = 没写过/已清。 */
    private static long appliedShipId = -1L;

    /**
     * 按服务端决定设置/清除随船标记。
     *
     * @param shipId 服务端给的船 id；负数 = 不在任何物理船里
     */
    public static void apply(Player player, long shipId) {
        if (!VsSupport.available() || player == null) {
            return;
        }
        if (!(player instanceof IEntityDraggingInformationProvider provider)) {
            return;
        }
        EntityDraggingInformation info = provider.getDraggingInformation();
        if (shipId >= 0L) {
            info.setLastShipStoodOn(shipId);
            info.setTicksSinceStoodOnShip(0);
            appliedShipId = shipId;
            return;
        }
        // 只清我们自己写进去的那艘船；VS2 给"站在船上"玩家设的状态不动。
        if (appliedShipId >= 0L) {
            Long current = info.getLastShipStoodOn();
            if (current == null || current == appliedShipId) {
                VsServerDrag.detach(player);
            }
            appliedShipId = -1L;
        }
    }

    /** 退出世界时重置客户端记忆，避免下一次进世界误清。 */
    public static void reset() {
        appliedShipId = -1L;
    }
}
