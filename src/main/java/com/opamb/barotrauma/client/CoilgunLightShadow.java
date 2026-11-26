package com.opamb.barotrauma.client;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A small angular depth map traced through world blocks and VS2 ship block shapes. */
final class CoilgunLightShadow implements AutoCloseable {
    static final float REACH = 50.0F;
    private static final int SIZE = 17;
    private static final double MAX_SLOPE = 1.0;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    private ClientLevel level;
    private TurretBlockEntity source;
    private DynamicTexture depth;
    private long sampledTick;
    private Vec3 origin, forward, right, up;

    void update(ClientLevel level, Entity viewer, TurretBlockEntity source, Vec3 origin, Vec3 direction) {
        long tick = level.getGameTime();
        if (this.level == level && this.source == source) {
            long elapsed = tick - sampledTick;
            boolean moved = this.origin.distanceToSqr(origin) > 0.20 * 0.20
                    || forward.dot(direction) < 0.999;
            if (elapsed == 0 || (elapsed > 0 && elapsed < 3 && !moved)) return;
        }
        if (depth == null) depth = new DynamicTexture(SIZE, SIZE, false);
        this.level = level;
        this.source = source;
        this.origin = origin;
        forward = direction;
        right = forward.cross(Math.abs(forward.y) < 0.98 ? WORLD_UP : new Vec3(0, 0, 1)).normalize();
        up = right.cross(forward);
        sampledTick = tick;
        var image = depth.getPixels();
        for (int y = 0; y < SIZE; y++) {
            Vec3 row = forward.add(up.scale(slope(y)));
            for (int x = 0; x < SIZE; x++) {
                Vec3 end = origin.add(row.add(right.scale(slope(x))).scale(REACH));
                var hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, viewer));
                double along = hit.getType() == HitResult.Type.MISS ? REACH
                        : hit.getLocation().subtract(origin).dot(forward);
                int encoded = encode(along);
                image.setPixelRGBA(x, y, 0xFF000000 | ((encoded & 255) << 8) | (encoded >>> 8));
            }
        }
        depth.upload();
    }

    private static double slope(int sample) {
        return (sample * 2.0 / (SIZE - 1) - 1.0) * MAX_SLOPE;
    }

    static int encode(double along) {
        return (int) Math.round(Math.max(0, Math.min(along, REACH)) / REACH * 65535);
    }

    Vec3 origin() { return origin; }
    Vec3 forward() { return forward; }
    Vec3 right() { return right; }
    Vec3 up() { return up; }
    int texture() { return depth.getId(); }

    @Override
    public void close() {
        if (depth != null) {
            depth.close();
            depth = null;
        }
        level = null;
        source = null;
    }
}
