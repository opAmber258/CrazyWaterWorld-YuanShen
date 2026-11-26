package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Oriented interaction bounds measured from the R9 gun model, in model pixels. */
public final class CoilgunHitbox {
    public static final double MAX_OFFSET = 7.0;
    private static final Vec3 YAW_PIVOT = new Vec3(0, 0, -25.2);
    private static final Vec3 PITCH_PIVOT = new Vec3(0, 38.24, -23.52);
    private static final AABB[] CARRIAGE = {
            new AABB(-19, 9, -49, 19, 17, -1),
            new AABB(-23, 12, -46, -12, 43, -8),
            new AABB(12, 12, -46, 23, 43, -8)
    };
    private static final AABB[] GUN = {
            new AABB(-15.2, 27.5, -55.7, 15.2, 51.8, 7),
            new AABB(-14.4, 30.5, -26.9, -7.5, 54.1, 17.2),
            new AABB(7.5, 30.5, -26.9, 14.4, 54.1, 17.2)
    };
    private static final AABB[] BARREL = {
            new AABB(-6.7, 37.1, -8.4, 6.7, 49.6, 41.2),
            new AABB(-8.3, 37, 40.4, 8.3, 49.6, 48.72)
    };

    private CoilgunHitbox() {}

    /** Position a little below the pitch joint so dry dock and ship use the same entity anchor. */
    public static Vec3 anchor(BlockPos pos, Direction mount) {
        return Vec3.atBottomCenterOf(pos).add(0, mount == Direction.DOWN ? -1 : 2, 0);
    }

    // R9 fire_single: full travel is 7.2 model pixels, then a held stroke and eased return.
    public static double recoilPixels(double ticks) {
        if (ticks < 0 || ticks >= 23.334) return 0;
        if (ticks < 0.834) return 7.2 * ticks / 0.834;
        double t = Math.max(0, (ticks - 3.334) / 20.0);
        return 7.2 * (1 - t * t * (3 - 2 * t));
    }

    @Nullable
    public static Vec3 clip(BlockPos pos, Direction mount, float yaw, float pitch, double recoil,
                            UnaryOperator<Vec3> toWorld, Vec3 start, Vec3 end, double padding) {
        Pose pose = pose(pos, mount, yaw, pitch, recoil, toWorld);
        double hit = pose.carriage.firstHit(CARRIAGE, start, end, 0, padding);
        hit = Math.min(hit, pose.barrel.firstHit(GUN, start, end, 0, padding));
        hit = Math.min(hit, pose.barrel.firstHit(BARREL, start, end, recoil, padding));
        return hit <= 1 ? start.lerp(end, hit) : null;
    }

    public static Pose pose(BlockPos pos, Direction mount, float yaw, float pitch, double recoil,
                            UnaryOperator<Vec3> toWorld) {
        int sign = mount == Direction.DOWN ? -1 : 1;
        Vec3 deck = Vec3.atBottomCenterOf(pos).add(0, sign < 0 ? 1 : 0, 0);
        Vec3 flat = TurretBlockEntity.localLookVector(yaw, 0);
        Vec3 right = new Vec3(flat.z, 0, -flat.x).scale(sign);
        Vec3 vertical = new Vec3(0, sign, 0);
        Vec3 hinge = deck.add(vertical.scale(PITCH_PIVOT.y / 16))
                .add(flat.scale((PITCH_PIVOT.z - YAW_PIVOT.z) / 16));
        double angle = Math.toRadians(pitch);
        Vec3 up = flat.scale(Math.sin(angle)).add(0, Math.cos(angle), 0).scale(sign);
        Vec3 forward = TurretBlockEntity.localLookVector(yaw, pitch);
        Frame carriage = Frame.inWorld(toWorld, deck, right, vertical, flat, YAW_PIVOT);
        Frame barrel = Frame.inWorld(toWorld, hinge, right, up, forward, PITCH_PIVOT);
        return new Pose(carriage, barrel, recoil);
    }

    public record Pose(Frame carriage, Frame barrel, double recoil) {
        public List<VoxelShape> collisionShapes() {
            List<VoxelShape> shapes = new ArrayList<>(CARRIAGE.length + GUN.length + BARREL.length);
            for (AABB box : CARRIAGE) shapes.add(carriage.voxelize(box));
            for (AABB box : GUN) shapes.add(barrel.voxelize(box));
            for (AABB box : BARREL) shapes.add(barrel.voxelize(box.move(0, 0, -recoil)));
            return shapes;
        }
    }

    private static final class CollisionShape extends ArrayVoxelShape {
        CollisionShape(BitSetDiscreteVoxelShape voxels, double[] xs, double[] ys, double[] zs) {
            super(voxels, xs, ys, zs);
        }
    }

    private record Frame(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward, Vec3 pivot) {
        static Frame inWorld(UnaryOperator<Vec3> transform, Vec3 origin,
                             Vec3 right, Vec3 up, Vec3 forward, Vec3 pivot) {
            Vec3 world = transform.apply(origin);
            return new Frame(world, transform.apply(origin.add(right)).subtract(world),
                    transform.apply(origin.add(up)).subtract(world),
                    transform.apply(origin.add(forward)).subtract(world), pivot);
        }

        private Vec3 toWorld(Vec3 model) {
            Vec3 delta = model.subtract(pivot).scale(1.0 / 16);
            return origin.add(right.scale(delta.x)).add(up.scale(delta.y)).add(forward.scale(delta.z));
        }

        VoxelShape voxelize(AABB box) {
            Vec3 center = toWorld(box.getCenter());
            Vec3 halfX = right.scale(box.getXsize() / 32);
            Vec3 halfY = up.scale(box.getYsize() / 32);
            Vec3 halfZ = forward.scale(box.getZsize() / 32);
            double rx = Math.abs(halfX.x) + Math.abs(halfY.x) + Math.abs(halfZ.x);
            double ry = Math.abs(halfX.y) + Math.abs(halfY.y) + Math.abs(halfZ.y);
            double rz = Math.abs(halfX.z) + Math.abs(halfY.z) + Math.abs(halfZ.z);
            double[] xs = grid(center.x, rx);
            double[] ys = grid(center.y, ry);
            double[] zs = grid(center.z, rz);
            BitSetDiscreteVoxelShape voxels = new BitSetDiscreteVoxelShape(xs.length - 1, ys.length - 1,
                    zs.length - 1);
            double scaleX = 16 / right.lengthSqr();
            double scaleY = 16 / up.lengthSqr();
            double scaleZ = 16 / forward.lengthSqr();
            for (int x = 0; x + 1 < xs.length; x++) {
                double dx = midpoint(xs, x) - origin.x;
                for (int y = 0; y + 1 < ys.length; y++) {
                    double dy = midpoint(ys, y) - origin.y;
                    for (int z = 0; z + 1 < zs.length; z++) {
                        double dz = midpoint(zs, z) - origin.z;
                        double mx = pivot.x + scaleX * (dx * right.x + dy * right.y + dz * right.z);
                        double my = pivot.y + scaleY * (dx * up.x + dy * up.y + dz * up.z);
                        double mz = pivot.z + scaleZ * (dx * forward.x + dy * forward.y + dz * forward.z);
                        if (box.contains(mx, my, mz)) voxels.fill(x, y, z);
                    }
                }
            }
            return new CollisionShape(voxels, xs, ys, zs);
        }

        private static double[] grid(double center, double radius) {
            int steps = Math.max(1, (int) Math.ceil(radius * 16));
            double[] values = new double[steps + 1];
            for (int i = 0; i <= steps; i++) values[i] = center - radius + 2 * radius * i / steps;
            return values;
        }

        private static double midpoint(double[] row, int index) {
            return (row[index] + row[index + 1]) * 0.5;
        }

        Vec3 toModel(Vec3 world) {
            Vec3 offset = world.subtract(origin);
            return pivot.add(project(offset, right), project(offset, up), project(offset, forward));
        }

        private static double project(Vec3 offset, Vec3 axis) {
            return 16 * offset.dot(axis) / axis.lengthSqr();
        }

        double firstHit(AABB[] boxes, Vec3 start, Vec3 end, double recoil, double padding) {
            Vec3 from = toModel(start).add(0, 0, recoil);
            Vec3 to = toModel(end).add(0, 0, recoil);
            double best = Double.POSITIVE_INFINITY;
            for (AABB bounds : boxes) {
                AABB box = padding == 0 ? bounds : bounds.inflate(padding * 16);
                if (box.contains(from)) return 0;
                Vec3 hit = box.clip(from, to).orElse(null);
                if (hit != null) best = Math.min(best, from.distanceToSqr(hit));
            }
            return Math.sqrt(best / from.distanceToSqr(to));
        }
    }
}
