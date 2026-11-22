package com.opamb.barotrauma.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static com.opamb.barotrauma.network.ShipWaterStatePacket.SIDE_FLOW_STRIDE;
import static com.opamb.barotrauma.network.ShipWaterStatePacket.STREAM_STRIDE;

/** Swept, continuous water surfaces along the synchronized opening paths. */
final class ShipWaterFlowMesh {
    private static final float SHEET_MIN_STRENGTH = 0.10F;
    private static final double MIN_DROP = 0.16D;
    private static final double MIN_EXPOSED_DEPTH = 0.025D;
    private static final int ACROSS_STEPS = 4;

    private record Section(Vec3[] points, float[] thickness) {
    }

    private final ShipWaterMesh pool;
    private final float[] streams;
    private final float[] sideFlows;
    private final float seconds;
    private final Function<Vec3, Direction.Axis> hatchAxis;
    private final Set<Vec3> channelStarts;

    ShipWaterFlowMesh(ShipWaterMesh pool, float[] streams, float[] sideFlows, float seconds,
                      Function<Vec3, Direction.Axis> hatchAxis) {
        this.pool = pool;
        this.streams = streams;
        this.sideFlows = sideFlows;
        this.seconds = seconds;
        this.hatchAxis = hatchAxis;
        if (streams.length > STREAM_STRIDE) {
            channelStarts = new HashSet<>();
            for (int i = 0; i < streams.length; i += STREAM_STRIDE) {
                channelStarts.add(new Vec3(streams[i], streams[i + 1], streams[i + 2]));
            }
        } else {
            channelStarts = Set.of();
        }
    }

    boolean hasPaths() {
        return streams.length >= STREAM_STRIDE || sideFlows.length >= SIDE_FLOW_STRIDE;
    }

    int emit(ShipWaterMesh.VertexSink sink) {
        int triangles = 0;
        for (int i = 0; i < streams.length; i += STREAM_STRIDE) {
            float strength = streams[i + 6];
            if (strength < SHEET_MIN_STRENGTH) {
                continue;
            }
            Vec3 from = new Vec3(streams[i], streams[i + 1], streams[i + 2]);
            Vec3 to = new Vec3(streams[i + 3], streams[i + 4], streams[i + 5]);
            if (from.distanceToSqr(to) < 0.035D * 0.035D) {
                continue;
            }
            triangles += from.y - to.y >= MIN_DROP
                    ? emitFall(sink, from, to, strength)
                    : emitDeckFilm(sink, from, to, strength);
        }
        for (int i = 0; i < sideFlows.length; i += SIDE_FLOW_STRIDE) {
            if (sideFlows[i + 7] >= SHEET_MIN_STRENGTH) {
                triangles += emitSideFan(sink, i);
            }
        }
        return triangles;
    }

    static Vec3 fallAcross(Vec3 from, Vec3 to, Direction.Axis apertureAxis) {
        if (apertureAxis == Direction.Axis.Z) {
            return new Vec3(0, 0, 1);
        }
        if (apertureAxis == Direction.Axis.X) {
            return new Vec3(1, 0, 0);
        }
        Vec3 travel = new Vec3(from.z - to.z, 0.0D, to.x - from.x);
        if (travel.lengthSqr() > 0.015D) {
            return travel.normalize();
        }
        double x = to.x - Math.floor(to.x);
        double z = to.z - Math.floor(to.z);
        return Math.min(x, 1.0D - x) < Math.min(z, 1.0D - z)
                ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
    }

    Vec3 fallLanding(Vec3 from, Vec3 to) {
        if (channelStarts.contains(to)) {
            return to;
        }
        float surface = pool.surfaceNear(to.x, to.z, to.y, 0.55D);
        return Float.isFinite(surface) ? new Vec3(to.x, Math.min(from.y - 0.025D, surface - 0.020D), to.z) : to;
    }

    static double fallHalfWidth(float strength) {
        return 0.19D + 0.24D * strength;
    }

    private int emitFall(ShipWaterMesh.VertexSink sink, Vec3 from, Vec3 to, float strength) {
        Vec3 across = fallAcross(from, to, hatchAxis.apply(from));
        Vec3 down = fallLanding(from, to);
        Vec3 normal = new Vec3(-across.z, 0, across.x);
        double length = from.distanceTo(down);
        int steps = Math.min(9, Math.max(3, (int) Math.ceil(length * 3.0D)));
        double phase = seconds * (9.0D + strength * 5.0D) + from.y * 4.0D + from.x * 1.7D + from.z;
        double width = fallHalfWidth(strength);
        Section previous = null;
        int triangles = 0;
        for (int step = 0; step <= steps; step++) {
            double t = Math.pow(step / (double) steps, 1.30D);
            double churn = Math.sin(Math.PI * t);
            Vec3 center = from.lerp(down, t);
            double sway = churn * 0.047D * Math.sin(phase - 12.0D * t);
            Section next = section(center.add(normal.scale(sway)), across, normal,
                    width * (1.0D + churn * 0.13D * Math.sin(phase - 8.0D * t)),
                    0.045D * churn, phase - 15.0D * t, (0.06D + 0.23D * strength) * (1.0D - 0.32D * t), center);
            if (previous != null) {
                triangles += join(sink, previous, next, strength);
            }
            previous = next;
        }
        return triangles;
    }

    private int emitDeckFilm(ShipWaterMesh.VertexSink sink, Vec3 from, Vec3 to, float strength) {
        Vec3 across = fallAcross(from, to, null);
        Vec3 up = new Vec3(0, 1, 0);
        double width = 0.12D + 0.19D * strength;
        double thickness = 0.020D + strength * 0.065D;
        Section start = section(from.add(0, 0.014D, 0), across, up, width * 0.85D,
                0.007D, seconds * 5.0D + from.x, thickness, from);
        Section end = section(to.add(0, 0.014D, 0), across, up, width,
                0.007D, seconds * 5.0D + to.x, thickness, to);
        return join(sink, start, end, strength);
    }

    private int emitSideFan(ShipWaterMesh.VertexSink sink, int i) {
        double x = sideFlows[i];
        double sill = sideFlows[i + 1];
        double z = sideFlows[i + 2];
        double dirX = sideFlows[i + 3];
        double dirZ = sideFlows[i + 4];
        double top = sideFlows[i + 5];
        double receiver = sideFlows[i + 6];
        float strength = sideFlows[i + 7];
        float source = pool.surfaceNear(x - dirX * 0.62D, z - dirZ * 0.62D, top, 0.22D);
        if (Float.isFinite(source) && source > sill) {
            top = Math.min(top + 0.06D, source);
        }
        double exposed = top - Math.max(sill, receiver);
        if (exposed <= MIN_EXPOSED_DEPTH) {
            return 0;
        }

        double run = 0.64D + 0.47D * strength;
        float receivingSurface = pool.surfaceNear(x + dirX * run, z + dirZ * run, receiver, 0.38D);
        double landing = Float.isFinite(receivingSurface) ? receivingSurface - 0.012D : receiver + 0.032D;
        landing = Math.min(top - 0.021D, Math.max(sill - 0.97D, landing));
        Vec3 across = new Vec3(-dirZ, 0, dirX);
        Vec3 up = new Vec3(0, 1, 0);
        double width = 0.18D + 0.19D * strength;
        double phase = seconds * (8.0D + strength * 4.0D) + x * 1.6D + z * 1.9D;
        Section previous = null;
        int triangles = 0;
        for (int step = 0; step <= 7; step++) {
            double t = step / 7.0D;
            double roll = Math.sin(phase - 10.0D * t);
            double along = -0.16D + (run + 0.16D + roll * 0.053D * t) * t;
            double height = top + (landing - top) * t * t;
            Vec3 center = new Vec3(x + dirX * along, height, z + dirZ * along);
            Section next = section(center, across, up, width * (1.0D + 0.46D * t),
                    0.07D * Math.sin(Math.PI * t), phase - 10.0D * t,
                    Math.min(0.36D, exposed) * (0.93D - 0.69D * t), null);
            if (previous != null) {
                triangles += join(sink, previous, next, strength);
            }
            previous = next;
        }
        return triangles;
    }

    private static Section section(Vec3 center, Vec3 across, Vec3 normal, double width, double curl,
                                   double phase, double thickness, Vec3 wallLine) {
        Vec3[] points = new Vec3[ACROSS_STEPS + 1];
        float[] depth = new float[points.length];
        for (int i = 0; i < points.length; i++) {
            double u = 2.0D * i / ACROSS_STEPS - 1.0D;
            double middle = 1.0D - u * u;
            double swell = Math.sin(phase + u * 2.3D);
            Vec3 point = center.add(across.scale(width * u)).add(normal.scale(curl * (middle + 0.55D * swell)));
            points[i] = wallLine == null ? point
                    : new Vec3(insideWallLine(wallLine.x, point.x), point.y, insideWallLine(wallLine.z, point.z));
            depth[i] = (float) Math.max(0.004D, thickness * (0.04D + 0.96D * middle) * (0.88D + 0.12D * swell));
        }
        return new Section(points, depth);
    }

    private static double insideWallLine(double anchor, double value) {
        double low = Math.floor(anchor);
        double fraction = anchor - low;
        if (fraction < 0.09D) {
            return Math.max(value, low + 0.017D);
        }
        return fraction > 0.91D ? Math.min(value, low + 0.983D) : value;
    }

    private static int join(ShipWaterMesh.VertexSink sink, Section a, Section b, float strength) {
        for (int i = 0; i < ACROSS_STEPS; i++) {
            vertex(sink, a, i, strength);
            vertex(sink, a, i + 1, strength);
            vertex(sink, b, i + 1, strength);
            vertex(sink, a, i, strength);
            vertex(sink, b, i + 1, strength);
            vertex(sink, b, i, strength);
        }
        return 2 * ACROSS_STEPS;
    }

    private static void vertex(ShipWaterMesh.VertexSink sink, Section section, int index, float strength) {
        Vec3 point = section.points[index];
        // Negative depth identifies a moving sheet and stores its optical thickness.
        sink.put((float) point.x, (float) point.y, (float) point.z, -section.thickness[index], strength);
    }
}
