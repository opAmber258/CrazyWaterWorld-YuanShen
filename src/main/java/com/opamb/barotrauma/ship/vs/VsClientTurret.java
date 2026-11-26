package com.opamb.barotrauma.ship.vs;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.function.Consumer;

public final class VsClientTurret {
    private VsClientTurret() {
    }

    public static Vec3 toWorld(ClientLevel level, BlockPos pos, Vec3 point) {
        if (!VsSupport.available()) return point;
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship != null) {
            var world = ship.getRenderTransform().getShipToWorld()
                    .transformPosition(new Vector3d(point.x, point.y, point.z));
            return new Vec3(world.x, world.y, world.z);
        }
        return point;
    }

    public static Vec3 directionToWorld(ClientLevel level, BlockPos pos, Vec3 direction) {
        if (!VsSupport.available()) return direction;
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship == null) return direction;
        Vector3d world = ship.getRenderTransform().getShipToWorld()
                .transformDirection(new Vector3d(direction.x, direction.y, direction.z)).normalize();
        return new Vec3(world.x, world.y, world.z);
    }

    public static void nearbyYardCenters(ClientLevel level, Vec3 point, Consumer<BlockPos> scan) {
        if (!VsSupport.available()) return;
        for (ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            var transform = ship.getRenderTransform();
            if (transform.getPositionInWorld().distanceSquared(point.x, point.y, point.z) > 160 * 160) continue;
            var local = transform.getWorldToShip().transformPosition(new Vector3d(point.x, point.y, point.z));
            scan.accept(BlockPos.containing(local.x, local.y, local.z));
        }
    }
}
