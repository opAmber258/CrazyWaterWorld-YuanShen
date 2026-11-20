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
        double[] local = toLocal(level, pos, data);
        if (local == null) {
            return false;
        }
        float[] boxes = data.boxes();
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (local[0] >= boxes[i] && local[0] <= boxes[i + 3] + 1.0F
                    && local[1] >= boxes[i + 1] && local[1] <= boxes[i + 4] + 1.0F
                    && local[2] >= boxes[i + 2] && local[2] <= boxes[i + 5] + 1.0F) {
                return true;
            }
        }
        return false;
    }

    /**
     * 世界坐标 → 水盒坐标（装配坐标）。
     *
     * <p>必须用实时逆变换：只减平移量在偏航非零时会把整艘船算错，
     * 舱内位置会跑到盒子外，客户端就会误判成“在海里”（用户实测的干舱滤镜）。</p>
     */
    private static double[] toLocal(Level level, Vec3 pos, ShipWaterStatePacket data) {
        if (pos == null || data == null || !(level instanceof ClientLevel clientLevel)) {
            return null;
        }
        float[] boxes = data.boxes();
        if (!data.physical() || data.shipId() < 0 || boxes.length < 7) {
            return null;
        }
        ClientShip ship = findShip(clientLevel, data.shipId());
        if (ship == null) {
            return null;
        }
        ShipTransform transform = ship.getTransform();
        Vector3dc positionInShip = transform.getPositionInShip();
        try {
            Vector3d local = new Matrix4d(transform.getShipToWorld()).invert()
                    .transformPosition(new Vector3d(pos.x, pos.y, pos.z), new Vector3d());
            // 水盒是装配坐标：world = shipToWorld * (box + positionInShip - anchor)。
            return new double[]{
                    local.x - positionInShip.x() + data.anchorX(),
                    local.y - positionInShip.y() + data.anchorY(),
                    local.z - positionInShip.z() + data.anchorZ()};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 客户端物理船：这个位置是否在未进水的舱室里。
     *
     * <p>和 {@code ShipWaterRenderer} 的盒内逻辑一致（干燥盒顶 +1、湿盒要高于
     * 水面），但坐标用实时逆变换，偏航后也不会把整艘船算错。</p>
     */
    public static boolean isInsideDryZone(Level level, Vec3 pos, ShipWaterStatePacket data) {
        double[] local = toLocal(level, pos, data);
        if (local == null) {
            return false;
        }
        float[] boxes = data.boxes();
        double px = local[0];
        double py = local[1];
        double pz = local[2];
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            float minX = boxes[i];
            float minY = boxes[i + 1];
            float minZ = boxes[i + 2];
            float maxX = boxes[i + 3] + 1.0F;
            float maxY = boxes[i + 4] + 1.0F;
            float maxZ = boxes[i + 5] + 1.0F;
            float surface = boxes[i + 6];
            boolean dryBox = surface <= minY + 0.01F;
            float top = maxY + (dryBox ? 1.0F : 0.0F);
            if (px >= minX && px <= maxX && py >= minY && py <= top
                    && pz >= minZ && pz <= maxZ
                    && (dryBox || py > surface + 0.01F)) {
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
