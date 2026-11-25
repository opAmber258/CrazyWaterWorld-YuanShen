package com.opamb.barotrauma.ship;

/** Desired velocities and braking for a local navigation target. */
final class NavigationMotion {
    private NavigationMotion() {}

    static final double HORIZONTAL_STOP = 0.65D;
    static final double VERTICAL_STOP = 0.45D;
    private static final double HORIZONTAL_SETTLED_SPEED = 0.10D;
    private static final double VERTICAL_SETTLED_SPEED = 0.08D;
    private static final double ENGINE_RESPONSE = 0.35D;

    static double desiredSpeed(double error, double cap, double acceleration, double deadZone) {
        return desiredSpeed(error, cap, acceleration, deadZone, 0.0D);
    }

    private static double desiredSpeed(double error, double cap, double acceleration, double deadZone,
                                       double responseSeconds) {
        double distance = Math.max(0.0D, Math.abs(error) - deadZone);
        // Invert d = v * responseSeconds + v² / (2a) to get the approach speed.
        double response = acceleration * responseSeconds;
        double safeSpeed = Math.sqrt(response * response + 2.0D * acceleration * distance) - response;
        double speed = Math.min(cap, Math.min(distance * 0.8D, safeSpeed));
        return Math.copySign(speed, error);
    }

    static double thrust(double desired, double measured, double gain) {
        double error = desired - measured;
        return Math.abs(error) < 0.04D ? 0.0D : Math.max(-1.0D, Math.min(1.0D, error * gain));
    }

    /** Includes forward and reverse overspeed, including when the destination is already under the boat. */
    static PhysicalCommand physical(double dx, double dy, double dz, double vx, double vy, double vz,
                                    double forwardX, double forwardZ, double horizontalCap,
                                    double brakeAcceleration, double verticalAcceleration) {
        double horizontal = Math.hypot(dx, dz);
        double speed = Math.hypot(vx, vz);
        double verticalSpeed = desiredSpeed(dy, 1.5D, 0.20D, 0.12D, 1.0D);
        double vertical = thrust(verticalSpeed, vy + verticalAcceleration * 0.75D, 1.3D);
        if (horizontal <= HORIZONTAL_STOP && Math.abs(dy) <= VERTICAL_STOP
                && speed <= HORIZONTAL_SETTLED_SPEED && Math.abs(vy) <= VERTICAL_SETTLED_SPEED
                && Math.abs(verticalAcceleration) <= 0.06D) {
            return new PhysicalCommand(0.0D, 0.0D, 0.0D, true);
        }

        double distance = Math.max(0.0D, horizontal - HORIZONTAL_STOP * 0.5D);
        double stoppingDistance = speed * ENGINE_RESPONSE
                + speed * speed / (2.0D * Math.max(1.0E-6D, brakeAcceleration));
        boolean moving = speed > HORIZONTAL_SETTLED_SPEED;
        boolean brake = horizontal <= HORIZONTAL_STOP * 0.5D || moving && vx * dx + vz * dz > 0.0D
                && distance <= stoppingDistance;
        double desired = brake ? 0.0D : desiredSpeed(horizontal, horizontalCap,
                brakeAcceleration * 0.7D, HORIZONTAL_STOP * 0.5D, ENGINE_RESPONSE);
        // Steer toward the needed change in velocity. The stern can brake without turning the boat
        // around, and a sideways drift needs a different heading than the bearing to the target.
        double scale = desired / Math.max(1.0E-6D, horizontal);
        double correctionX = dx * scale - vx;
        double correctionZ = dz * scale - vz;
        double correction = Math.hypot(correctionX, correctionZ);
        if (brakeAcceleration <= 0.0D || correction < 0.04D) {
            return new PhysicalCommand(0.0D, 0.0D, vertical, false);
        }
        double along = correctionX * forwardX + correctionZ * forwardZ;
        double across = forwardZ * correctionX - forwardX * correctionZ;
        double yaw = Math.atan2(along < 0.0D ? -across : across, Math.abs(along));
        double alignment = Math.abs(along) / correction;
        double forward = thrust(0.0D, -along, 1.0D / Math.max(0.18D, brakeAcceleration)) * alignment;
        return new PhysicalCommand(forward, yaw, vertical, false);
    }

    record PhysicalCommand(double forward, double yawError, double vertical, boolean stopped) {}
}
