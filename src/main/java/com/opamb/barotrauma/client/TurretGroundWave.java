package com.opamb.barotrauma.client;

import com.opamb.barotrauma.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 命中海床时的“地波”：一圈气泡和泥沙贴着地面向外扩散。
 *
 * <p>环上每个点的地面高度都单独向下采样，所以环遇到起伏、台阶、斜坡时会顺着
 * 地形走，而不是浮在一个水平面上；没有地面的方向直接跳过，环在沟壑处会断开。</p>
 *
 * <p>由 {@link TurretClient#onCameraAngles} 每帧推进，退出炮塔后仍会把剩下的一点
 * 波推完。半径每推进 {@link #RADIUS_STEP} 格才补一圈粒子，避免每帧都撒满。</p>
 */
final class TurretGroundWave {

    private static final List<TurretGroundWave> ACTIVE = new ArrayList<>();

    /** 最大半径（格）与从命中到消散的总时长（秒）。 */
    private static final double MAX_RADIUS = 5.0D;
    private static final double DURATION = 0.6D;

    /** 环上粒子的弧长间距（格）。 */
    private static final double ARC_SPACING = 0.7D;
    /** 半径每推进这么多格补一圈粒子。 */
    private static final double RADIUS_STEP = 0.5D;

    /** 从中心向下扫描地面的范围（格）：只跟随一个台阶以内的起伏。 */
    private static final int GROUND_SCAN_UP = 1;
    private static final int GROUND_SCAN_DOWN = 1;

    private final Level level;
    private final Vec3 center;
    private double radius = 0.0D;
    private double nextBandRadius = RADIUS_STEP;
    private double age = 0.0D;
    private long lastNanos = System.nanoTime();

    private TurretGroundWave(Level level, Vec3 center) {
        this.level = level;
        this.center = center;
    }

    /**
     * 命中点贴着海底时启动一圈地波；打墙、打空或离地太远都不生成。
     *
     * <p>调用方已确认命中点在水下；这里只负责判断“脚下有没有地面”。</p>
     */
    static void spawnFromImpact(Vec3 impact) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 ground = groundBelow(minecraft.level, impact);
        if (ground == null || Math.abs(ground.y - impact.y) > 0.9D) {
            return;
        }
        ACTIVE.add(new TurretGroundWave(minecraft.level, ground));
    }

    /** 每帧推进所有地波；由炮塔相机事件统一调用，和是否还在炮塔里无关。 */
    static void tickFrame() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Iterator<TurretGroundWave> iterator = ACTIVE.iterator(); iterator.hasNext(); ) {
            if (iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    /** @return true 表示这一圈已经推完，可以移除。 */
    private boolean tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level) {
            return true;
        }
        long now = System.nanoTime();
        double deltaSeconds = Mth.clamp((now - lastNanos) / 1.0E9D, 0.0D, 0.1D);
        lastNanos = now;
        age += deltaSeconds;
        if (age >= DURATION) {
            return true;
        }
        // 先快后慢：冲击波刚出现时扩得最快，接近最大半径时收住。
        double progress = age / DURATION;
        radius = MAX_RADIUS * (1.0D - (1.0D - progress) * (1.0D - progress));
        while (nextBandRadius <= radius) {
            spawnBand(nextBandRadius);
            nextBandRadius += RADIUS_STEP;
        }
        return false;
    }

    /** 在半径 {@code bandRadius} 处补一圈粒子；每个方向都重新贴一次地。 */
    private void spawnBand(double bandRadius) {
        int samples = Math.max(8, (int) Math.ceil(Math.PI * 2.0D * bandRadius / ARC_SPACING));
        RandomSource random = level.random;
        for (int i = 0; i < samples; i++) {
            double angle = Math.PI * 2.0D * i / samples + random.nextDouble() * 0.10D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = center.x + cos * bandRadius;
            double z = center.z + sin * bandRadius;
            Vec3 surface = groundAt(x, z);
            if (surface == null) {
                continue;
            }
            double outward = 0.35D + random.nextDouble() * 0.45D;
            level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                    surface.x, surface.y + 0.03D, surface.z,
                    cos * outward, 0.18D + random.nextDouble() * 0.22D, sin * outward);
            if (i % 3 == 0) {
                level.addParticle(ModParticles.TURRET_SEDIMENT.get(),
                        surface.x, surface.y + 0.02D, surface.z,
                        cos * outward * 0.7D, 0.10D + random.nextDouble() * 0.12D,
                        sin * outward * 0.7D);
            }
        }
    }

    /** 在 (x,z) 附近找一颗可站立方块的顶面；没有就返回 null。 */
    @Nullable
    private Vec3 groundAt(double x, double z) {
        return scanGround(x, z,
                Mth.floor(center.y + GROUND_SCAN_UP),
                Mth.floor(center.y - GROUND_SCAN_DOWN));
    }

    @Nullable
    private static Vec3 groundBelow(Level level, Vec3 point) {
        return scanGround(level, point.x, point.z,
                Mth.floor(point.y + 0.2D), Mth.floor(point.y - 1.4D));
    }

    @Nullable
    private static Vec3 scanGround(double x, double z, int startY, int endY) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null
                ? null
                : scanGround(minecraft.level, x, z, startY, endY);
    }

    /** 从 startY 往下扫到 endY，返回找到的地面中心点（顶面 +3cm）。 */
    @Nullable
    private static Vec3 scanGround(Level level, double x, double z, int startY, int endY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        for (int y = startY; y >= endY; y--) {
            pos.set(blockX, y, blockZ);
            BlockState state = level.getBlockState(pos);
            if (state.isFaceSturdy(level, pos, Direction.UP)) {
                return new Vec3(x, y + 1.03D, z);
            }
        }
        return null;
    }
}
