package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoilgunHitboxTest {
    @Test
    void barrelRayFollowsTurnAndDoesNotSelectAirOnItsOldSide() {
        Vec3 from = new Vec3(2, 2.7, 4);
        Vec3 to = new Vec3(-1, 2.7, 4);
        Vec3 hit = clip(Direction.UP, 0, 0, 0, UnaryOperator.identity(), from, to);
        assertNotNull(hit);
        assertEquals(0.5 + 6.7 / 16, hit.x, 1.0e-6);
        assertNull(clip(Direction.UP, 90, 0, 0, UnaryOperator.identity(), from, to));
        assertNotNull(clip(Direction.UP, 90, 0, 0, UnaryOperator.identity(),
                new Vec3(-3, 2.7, -1), new Vec3(-3, 2.7, 2)));
    }

    @Test
    void ceilingBarrelUsesPitchAndTheShipsRigidTransform() {
        Vec3 from = new Vec3(2, 1.0, 3.1);
        Vec3 to = new Vec3(-1, 1.0, 3.1);
        assertNull(clip(Direction.DOWN, 0, 0, 0, UnaryOperator.identity(), from, to));
        Vec3 local = clip(Direction.DOWN, 0, -45, 0, UnaryOperator.identity(), from, to);
        assertNotNull(local);
        UnaryOperator<Vec3> ship = v -> new Vec3(48 + v.z, 14 + v.y, -30 - v.x);
        Vec3 world = clip(Direction.DOWN, 0, -45, 0, ship, ship.apply(from), ship.apply(to));
        assertNotNull(world);
        assertEquals(0.0, world.distanceTo(ship.apply(local)), 1.0e-6);
    }

    @Test
    void recoilPullsTheMuzzleOutOfItsPreviousHitPosition() {
        Vec3 from = new Vec3(2, 2.7, 4.93);
        Vec3 to = new Vec3(-1, 2.7, 4.93);
        assertNotNull(clip(Direction.UP, 0, 0, 0, UnaryOperator.identity(), from, to));
        assertEquals(7.2, CoilgunHitbox.recoilPixels(2), 1.0e-6);
        assertNull(clip(Direction.UP, 0, 0, CoilgunHitbox.recoilPixels(2), UnaryOperator.identity(), from, to));
        assertEquals(0, CoilgunHitbox.recoilPixels(24), 1.0e-6);
    }

    @Test
    void solidBarrelLeavesTheOldPositionAndFollowsItsShipsRotation() {
        Vec3 oldTip = new Vec3(0.5, 2.7, 4.4);
        var beforeTurn = CoilgunHitbox.pose(BlockPos.ZERO, Direction.UP, 0, 0, 0, UnaryOperator.identity());
        var afterTurn = CoilgunHitbox.pose(BlockPos.ZERO, Direction.UP, 45, 0, 0, UnaryOperator.identity());
        assertTrue(solidAt(beforeTurn, oldTip));
        assertFalse(solidAt(afterTurn, oldTip));
        Vec3 turned = new Vec3(0.5 - 3.9 / Math.sqrt(2), 2.7, 0.5 + 3.9 / Math.sqrt(2));
        assertTrue(solidAt(afterTurn, turned));
        UnaryOperator<Vec3> ship = v -> new Vec3(48 + v.z, 14 + v.y, -30 - v.x);
        var transformed = CoilgunHitbox.pose(BlockPos.ZERO, Direction.UP, 45, 0, 0, ship);
        assertTrue(solidAt(transformed, ship.apply(turned)));
        assertFalse(solidAt(transformed, ship.apply(oldTip)));
    }

    private static boolean solidAt(CoilgunHitbox.Pose pose, Vec3 point) {
        var sample = Shapes.create(new AABB(point, point).inflate(0.12));
        return pose.collisionShapes().stream().anyMatch(shape -> Shapes.joinIsNotEmpty(shape, sample, BooleanOp.AND));
    }

    private static Vec3 clip(Direction mount, float yaw, float pitch, double recoil,
                             UnaryOperator<Vec3> transform, Vec3 from, Vec3 to) {
        return CoilgunHitbox.clip(BlockPos.ZERO, mount, yaw, pitch, recoil, transform, from, to, 0);
    }
}
