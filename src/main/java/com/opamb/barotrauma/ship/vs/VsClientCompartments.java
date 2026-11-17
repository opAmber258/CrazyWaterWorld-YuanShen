package com.opamb.barotrauma.ship.vs;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/**
 * 客户端物理船舱室查询（VS2 类型只允许出现在 {@code ship/vs/} 包里）。
 *
 * <p>连专用服务器时客户端没有服务端的 {@code ShipState}，只能读同步过来的水面盒
 * （装配坐标）再用客户端船的实时逆变换映射回去——航行中偏航非零时只减平移量
 * 会把整艘船算错。方块船没有物理刚体：{@code physical=false} 的包直接返回 false，
 * 潜行防掉边另走方块船干舱判定。</p>
 */
public final class VsClientCompartments {

    private VsClientCompartments() {
    }

    /**
     * 世界坐标 {@code pos} 是否落在某艘物理船的舱室里（干舱/进水舱都算）。
     *
     * @param data 客户端最近收到的水面状态包；{@code null}/方块船/找不到船都返回 false
     */
    public static boolean isInsideCompartment(Level level, Vec3 pos, ShipWaterStatePacket data) {
        if (pos == null || data == null || !(level instanceof ClientLevel clientLevel)) {
            return false;
        }
        float[] boxes = data.boxes();
        if (!data.physical() || data.shipId() < 0 || boxes.length < 7) {
            return false;
        }
        ClientShip ship = findShip(clientLevel, data.shipId());
        if (ship == null) {
            return false;
        }
        ShipTransform transform = ship.getTransform();
        Vector3dc positionInShip = transform.getPositionInShip();
        Vector3d local;
        try {
            local = new Matrix4d(transform.getShipToWorld()).invert()
                    .transformPosition(new Vector3d(pos.x, pos.y, pos.z), new Vector3d());
        } catch (Throwable ignored) {
            return false;
        }
        // 水盒是装配坐标：world = shipToWorld * (box + positionInShip - anchor)。
        double lx = local.x - positionInShip.x() + data.anchorX();
        double ly = local.y - positionInShip.y() + data.anchorY();
        double lz = local.z - positionInShip.z() + data.anchorZ();
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (lx >= boxes[i] && lx <= boxes[i + 3] + 1.0F
                    && ly >= boxes[i + 1] && ly <= boxes[i + 4] + 1.0F
                    && lz >= boxes[i + 2] && lz <= boxes[i + 5] + 1.0F) {
                return true;
            }
        }
        return false;
    }

    private static ClientShip findShip(ClientLevel level, long shipId) {
        for (Ship candidate : org.valkyrienskies.mod.common.VSGameUtilsKt
                .getShipObjectWorld(level).getAllShips()) {
            if (candidate.getId() == shipId && candidate instanceof ClientShip client) {
                return client;
            }
        }
        return null;
    }
}
