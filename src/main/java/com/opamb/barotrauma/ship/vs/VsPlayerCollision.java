package com.opamb.barotrauma.ship.vs;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;

/** Tests the top of an expanding player hitbox against the actual moving ship collision shapes. */
public final class VsPlayerCollision {
    private VsPlayerCollision() {
    }

    public static boolean canExpandPose(Player player, Pose pose) {
        AABB current = player.getBoundingBox();
        AABB next = player.getDimensions(pose).makeBoundingBox(player.position());
        double rise = next.maxY - current.maxY - 0.001D;
        if (rise <= 0.0D) {
            return true;
        }
        AABB top = new AABB(next.minX + 0.001D, current.maxY - 0.005D, next.minZ + 0.001D,
                next.maxX - 0.001D, current.maxY, next.maxZ - 0.001D);
        Vec3 allowed = EntityShipCollisionUtils.INSTANCE.adjustEntityMovementForShipCollisions(
                player, new Vec3(0.0D, rise, 0.0D), top, player.level());
        return allowed.y >= rise - 0.001D;
    }
}
