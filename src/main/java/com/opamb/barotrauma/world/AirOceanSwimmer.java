package com.opamb.barotrauma.world;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 空气海域的浸没状态和疾游姿态分开维护。 */
public interface AirOceanSwimmer {

    void barotrauma$setAirOceanSwimState(boolean immersed, boolean fast);

    boolean barotrauma$isInAirOceanWater();

    boolean barotrauma$isAirOceanSwimming();

    static boolean canSwim(Player player) {
        return player.isAlive() && !player.isPassenger() && !player.isSpectator()
                && !player.getAbilities().flying && !player.isSleeping() && !player.isFallFlying()
                && !player.isAutoSpinAttack() && player.getForcedPose() == null;
    }

    static boolean canUseAirOceanMovement(Player player) {
        return canSwim(player) && player.getMaxHeightFluidType().isAir()
                && OceanEnvironment.isInOcean(player.level(), player.position());
    }

    static boolean canSwimAtDepth(Player player, boolean alreadySwimming) {
        double height = player.getDimensions(Pose.STANDING).height;
        var level = player.level();
        Vec3 feet = player.position();
        Vec3 torso = feet.add(0, height * (alreadySwimming ? 0.46D : 0.52D), 0);
        if (player.onClimbable() || !OceanEnvironment.isOceanWaterAt(level, torso)) {
            return false;
        }
        if (!OceanEnvironment.isInsideShipCompartment(level, torso)) {
            return true;
        }

        // VS2 extends Level.clip to the live ship. The floor can be a step or a device, not the water box bottom.
        var floor = level.clip(new ClipContext(feet.add(0, 0.10D, 0), feet.add(0, -height * 0.36D, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (floor.getType() == HitResult.Type.MISS) {
            return true;
        }
        double standingLimit = floor.getLocation().y + height * (alreadySwimming ? 0.73D : 0.80D);
        Vec3 limit = new Vec3(feet.x, standingLimit, feet.z);
        if (standingLimit <= torso.y || OceanEnvironment.isOceanWaterAt(level, limit)) {
            return true;
        }
        // A submerged torso can also swim through a low opening where standing would not fit.
        return level.clip(new ClipContext(torso, limit, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;
    }
}
