package com.opamb.barotrauma.ship.vs;

import com.opamb.barotrauma.network.ShipWaterStatePacket;
import com.opamb.barotrauma.ship.ShipWaterBoxes;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/** 用物理船的逆变换查询船坞方块和装配坐标下的水盒。 */
public final class VsClientCompartments {

    private VsClientCompartments() {
    }

    public static boolean isInsideCompartment(Level level, Vec3 pos, ShipWaterStatePacket data) {
        return sample(level, pos, data, false) != null;
    }

    @Nullable
    public static Boolean dryAt(Level level, Vec3 pos, ShipWaterStatePacket data) {
        return sample(level, pos, data, true);
    }

    @Nullable
    private static Boolean sample(Level level, Vec3 pos, ShipWaterStatePacket data, boolean camera) {
        if (!(level instanceof ClientLevel clientLevel) || !data.physical() || data.shipId() < 0) {
            return null;
        }
        ClientShip ship = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(clientLevel)
                .getLoadedShips().getById(data.shipId());
        if (ship == null) {
            return null;
        }
        ShipTransform transform = camera ? ship.getRenderTransform() : ship.getTransform();
        Vector3d local = new Matrix4d(transform.getShipToWorld()).invert()
                .transformPosition(new Vector3d(pos.x, pos.y, pos.z), new Vector3d());
        Vec3 yard = new Vec3(local.x, local.y, local.z);
        if (OceanEnvironment.insideCollision(level, BlockPos.containing(yard), yard)) {
            return camera ? Boolean.TRUE : null;
        }
        var origin = transform.getPositionInShip();
        return ShipWaterBoxes.dryAt(data.boxes(), local.x - origin.x() + data.anchorX(),
                local.y - origin.y() + data.anchorY(), local.z - origin.z() + data.anchorZ());
    }
}
