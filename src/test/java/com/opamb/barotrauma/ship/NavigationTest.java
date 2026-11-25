package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {
    private static final Vec3 FORWARD = new Vec3(1, 0, 0);

    @Test
    void brakesOverspeedForwardAndInReverse() {
        var forward = NavigationMotion.physical(4, 0, 0, 6, 0, 0, 1, 0, 6, 0.12, 0);
        var reverse = NavigationMotion.physical(-4, 0, 0, -6, 0, 0, 1, 0, 6, 0.12, 0);
        assertTrue(forward.forward() < 0);
        assertTrue(reverse.forward() > 0);
        assertFalse(forward.stopped());
        assertFalse(reverse.stopped());
    }

    @Test
    void caseOfTargetBelowDoesNotContinueDivingWhenDescentIsTooFast() {
        var approach = NavigationMotion.physical(0, -0.7, 0, 0, -2.0, 0, 1, 0, 6, 0.08, -0.2);
        assertTrue(approach.vertical() > 0);
        var holding = NavigationMotion.physical(0, 0, 0, 0, 0, 0, 1, 0, 6, 0.08, 0);
        assertTrue(holding.stopped());
    }

    @Test
    void anticipatesBrakingInAWeakEngineWithForceLag() {
        double x = 0.0D;
        double velocity = 3.0D;
        double actualForce = 0.0D;
        double target = 50.0D;
        double engineAcceleration = 0.08D;
        boolean stopped = false;
        for (int tick = 0; tick < 2400; tick++) {
            var command = NavigationMotion.physical(target - x, 0, 0, velocity, 0, 0, 1, 0, 6,
                    engineAcceleration, 0);
            if (command.stopped()) {
                stopped = true;
                break;
            }
            actualForce += (command.forward() - actualForce) * (1.0D - Math.exp(-0.5D));
            double acceleration = actualForce * engineAcceleration - velocity * Math.abs(velocity) * 0.01D;
            velocity += acceleration / 20.0D;
            x += velocity / 20.0D;
            assertTrue(x <= target + NavigationMotion.HORIZONTAL_STOP);
        }
        assertTrue(stopped);
        assertEquals(target, x, NavigationMotion.HORIZONTAL_STOP);
    }

    @Test
    void rotatesAndCancelsLateralDriftWhileApproaching() {
        double x = 0.0D;
        double z = 0.0D;
        double vx = 0.0D;
        double vz = 3.0D;
        double heading = 0.0D;
        double actualForce = 0.0D;
        for (int tick = 0; tick < 2400; tick++) {
            double ux = Math.cos(heading);
            double uz = Math.sin(heading);
            var command = NavigationMotion.physical(20 - x, 0, -z, vx, 0, vz, ux, uz, 6, 0.08, 0);
            if (command.stopped()) {
                assertTrue(Math.hypot(20 - x, z) <= NavigationMotion.HORIZONTAL_STOP);
                return;
            }
            double angle = command.yawError();
            if (Math.abs(angle) >= 0.06D) {
                double turn = Math.max(0.15D, Math.min(1.0D, (Math.abs(angle) - 0.06D) / 0.54D));
                heading -= Math.copySign(turn * Math.toRadians(15.0D) / 20.0D, angle);
            }
            actualForce += (command.forward() - actualForce) * (1.0D - Math.exp(-0.5D));
            double surge = vx * ux + vz * uz;
            double sway = vz * ux - vx * uz;
            double surgeAccel = actualForce * 0.08D - surge * Math.abs(surge) * 0.01D;
            double swayAccel = -sway * Math.abs(sway) * 0.05D;
            vx += (surgeAccel * ux - swayAccel * uz) / 20.0D;
            vz += (surgeAccel * uz + swayAccel * ux) / 20.0D;
            x += vx / 20.0D;
            z += vz / 20.0D;
        }
        fail("Did not settle after lateral drift: " + x + ", " + z);
    }

    @Test
    void arrivedShipRetainsTargetAndCorrectsLaterDrift() {
        ShipState ship = new ShipState(null, new MachineCount(0, 0, 0, 0, 0, 0, 0));
        BlockPos target = new BlockPos(40, -15, 19);
        Vec3 center = Vec3.atCenterOf(target);
        ship.setAutopilotTarget(target);
        for (int i = 0; i < 10; i++) {
            ship.autopilotStep(center, Vec3.ZERO, FORWARD, 0.12, 1, 0, 0, 0);
        }
        assertEquals(NavigationMode.HOLD, ship.navigationMode());
        assertEquals(target, ship.autopilotTarget());
        assertFalse(ship.throttled());

        ship.autopilotStep(center.add(-2, -1, 0), Vec3.ZERO, FORWARD, 0.12, 1, 0, 0, 0);
        assertEquals(NavigationMode.SETTLING, ship.navigationMode());
        assertEquals(target, ship.autopilotTarget());
        assertTrue(ship.engineThrottle(true) > 0);
        assertTrue(ship.verticalInput() > 0);
    }

    @Test
    void lockingWhileMovingBrakesWithoutReportingHold() {
        ShipState ship = new ShipState(null, new MachineCount(0, 0, 0, 0, 0, 0, 0));
        ship.holdAt(Vec3.ZERO);
        ship.autopilotStep(Vec3.ZERO, new Vec3(6.7 / 3.6, 0, 0), FORWARD, 0.08, 0.7, 0.08, 0, 0);
        assertEquals(NavigationMode.SETTLING, ship.navigationMode());
        assertEquals(-1, ship.helmForward());
        assertEquals(0.7, ship.engineThrottle(true));
        for (int i = 0; i < 20; i++) {
            ship.autopilotStep(Vec3.ZERO, Vec3.ZERO, FORWARD, 0.08, 0.7, 0.08, 0, 0);
        }
        assertEquals(NavigationMode.SETTLING, ship.navigationMode());
        for (int i = 0; i < 9; i++) {
            ship.autopilotStep(Vec3.ZERO, Vec3.ZERO, FORWARD, 0.08, 0.7, 0, 0, 0);
        }
        assertEquals(NavigationMode.SETTLING, ship.navigationMode());
        ship.autopilotStep(Vec3.ZERO, Vec3.ZERO, FORWARD, 0.08, 0.7, 0, 0, 0);
        assertEquals(NavigationMode.HOLD, ship.navigationMode());
    }

    @Test
    void manualLeaseExpiresEvenWhenAnotherObserverIsStillPresent() {
        ShipState ship = new ShipState(null, new MachineCount(0, 0, 0, 0, 0, 0, 0));
        UUID driver = UUID.randomUUID();
        UUID observer = UUID.randomUUID();
        ship.acceptManualInput(driver, 10, 0.55, -0.25, 0.30, true);
        ship.acceptManualInput(observer, 11, 0, 0, 0, true);
        assertEquals(0.55, ship.engineThrottle(true));
        assertEquals(-0.25, ship.verticalInput());
        assertFalse(ship.expireManualInput(29));
        assertTrue(ship.expireManualInput(30));
        assertFalse(ship.throttled());
        ship.acceptManualInput(observer, 31, 0.4, 0, 0, true);
        assertEquals(0.4, ship.engineThrottle(true));
    }
}
