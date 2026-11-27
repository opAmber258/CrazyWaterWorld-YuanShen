package com.opamb.barotrauma.ship.vs;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.function.Consumer;
import java.util.function.BiConsumer;

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

    public static void forEachShadowShip(ClientLevel level, AABB bounds, BiConsumer<AABB, Matrix4dc> draw) {
        if (!VsSupport.available()) return;
        var ships = VSGameUtilsKt.getShipObjectWorld(level);
        if (ships == null) return;
        Vec3 center = bounds.getCenter();
        double radius = bounds.getXsize() / 2.0;
        for (ClientShip ship : ships.getLoadedShips()) {
            var transform = ship.getRenderTransform();
            var inverse = transform.getWorldToShip();
            var local = inverse.transformPosition(new Vector3d(center.x, center.y, center.z));
            // Bound the world cube after transforming it into the shipyard.
            double rx = radius * (Math.abs(inverse.m00()) + Math.abs(inverse.m10()) + Math.abs(inverse.m20()));
            double ry = radius * (Math.abs(inverse.m01()) + Math.abs(inverse.m11()) + Math.abs(inverse.m21()));
            double rz = radius * (Math.abs(inverse.m02()) + Math.abs(inverse.m12()) + Math.abs(inverse.m22()));
            ship.getActiveChunksSet().forEach((x, z) -> {
                double minX = x * 16.0;
                double minZ = z * 16.0;
                if (minX <= local.x + rx && minX + 16 > local.x - rx
                        && minZ <= local.z + rz && minZ + 16 > local.z - rz) {
                    draw.accept(new AABB(minX, local.y - ry, minZ, minX + 15, local.y + ry, minZ + 15),
                            transform.getShipToWorld());
                }
            });
        }
    }
}
