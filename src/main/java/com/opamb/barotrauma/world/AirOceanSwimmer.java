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
        // 采样点必须和当前状态无关：以前按 alreadySwimming 取 0.46/0.52，
        // 净空夹在两个站立上限之间时会变成“站立算出来该游、游泳算出来该站”的双稳态。
        Vec3 torso = feet.add(0, height * 0.52D, 0);
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
        // 水淹到头：站着也会没入水面，直接游泳（原实现按 floor + 0.8×height 判）。
        if (OceanEnvironment.isOceanWaterAt(level, feet.add(0, height * 0.80D, 0))) {
            return true;
        }
        // 头顶净空：从脚底向上打一条身高长度的碰撞射线。没有命中 = 净空足够。
        var clearance = level.clip(new ClipContext(feet, feet.add(0, height, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double available = clearance.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY
                : clearance.getLocation().y - feet.y;
        return SwimClearance.decideSwim(available, height, alreadySwimming);
    }
}
