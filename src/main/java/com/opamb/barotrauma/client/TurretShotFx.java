package com.opamb.barotrauma.client;

import com.opamb.barotrauma.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 一发炮弹的客户端特效状态机。
 *
 * <p>开火时由 {@link TurretClient} 占用一个槽位；每帧沿弹道推进，在空气中撒细光点、
 * 在水里撒“白蓝亮点 + 气泡”，穿过水面时补一圈气泡，到达落点后交给
 * {@link TurretClient#spawnImpactEffects} 按命中类型分支。</p>
 *
 * <p>槽位是复用的，所以两发（最多四发）炮弹同时在飞时互不覆盖。</p>
 */
final class TurretShotFx {

    /** 弹道视觉推进速度（格/秒），和服务端 hitscan 的命中时刻保持一致。 */
    private static final float SPEED = 24.0F;

    /** 空气中的光点间距。 */
    private static final float AIR_SPACING = 0.12F;
    /** 水下的气泡间距：比空气稀疏，给并发留粒子预算。 */
    private static final float WATER_SPACING = 0.24F;

    private boolean active;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 direction = Vec3.ZERO;
    private float length;
    private float travel;
    private long lastNanos;
    private boolean underwater;
    private TurretClient.ImpactPrediction impact;

    boolean isActive() {
        return active;
    }

    /** 启动一发新弹道；调用方保证槽位空闲。 */
    void start(Vec3 origin, Vec3 direction, float length, TurretClient.ImpactPrediction impact) {
        this.active = true;
        this.origin = origin;
        this.direction = direction;
        this.length = length;
        this.travel = 0.0F;
        this.lastNanos = System.nanoTime();
        this.underwater = TurretClient.isUnderwater(origin);
        this.impact = impact;
    }

    void clear() {
        active = false;
        impact = null;
    }

    /** 每帧推进一步；到达落点时生成命中特效并释放槽位。 */
    void step() {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        // 上限放到 0.25s：低帧率时仍按真实时间推进，不会让炮弹在半路变慢；
        // 只有长时间卡顿才截断，避免一次跳完整条弹道。
        float deltaSeconds = Mth.clamp((now - lastNanos) / 1.0E9F, 0.0F, 0.25F);
        lastNanos = now;

        float previous = travel;
        travel = Math.min(length, travel + SPEED * deltaSeconds);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            // 按这一帧弹道段的中点判介质，避免逐粒子查询水位。
            Vec3 middle = origin.add(direction.scale((previous + travel) * 0.5D));
            boolean nowUnderwater = TurretClient.isUnderwater(middle);
            if (nowUnderwater != underwater) {
                // 二分找这一帧真正的水线位置，别把水花撒在段中点（长帧会偏离约 1 格）。
                TurretClient.spawnSurfaceCrossing(
                        findCrossing(previous, travel, nowUnderwater));
                underwater = nowUnderwater;
            }
            float spacing = underwater ? WATER_SPACING : AIR_SPACING;
            int steps = Math.max(1, (int) Math.ceil((travel - previous) / spacing));
            for (int i = 1; i <= steps; i++) {
                double distance = Mth.lerp(i / (double) steps, previous, travel);
                Vec3 point = origin.add(direction.scale(distance));
                if (underwater) {
                    // 能量核心：白蓝细光点，进水后仍然看得到弹头。
                    minecraft.level.addParticle(ModParticles.TURRET_TRACER.get(),
                            point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
                    // 气泡尾：给一点横向速度，像被水拖散。
                    minecraft.level.addParticle(ModParticles.TURRET_BUBBLE.get(),
                            point.x, point.y, point.z,
                            (minecraft.level.random.nextDouble() - 0.5D) * 0.08D,
                            0.02D + minecraft.level.random.nextDouble() * 0.05D,
                            (minecraft.level.random.nextDouble() - 0.5D) * 0.08D);
                } else {
                    // 空气中不抖动、不给速度：整束是一条笔直的细线。
                    minecraft.level.addParticle(ModParticles.TURRET_TRACER.get(),
                            point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        if (travel >= length) {
            TurretClient.spawnImpactEffects(impact, direction);
            active = false;
            impact = null;
        }
    }

    /** 在 from…to 之间二分介质切换点；这一段里最多穿一次水面。 */
    private Vec3 findCrossing(float from, float to, boolean targetUnderwater) {
        for (int i = 0; i < 6; i++) {
            float middle = (from + to) * 0.5F;
            Vec3 point = origin.add(direction.scale(middle));
            if (TurretClient.isUnderwater(point) == targetUnderwater) {
                to = middle;
            } else {
                from = middle;
            }
        }
        return origin.add(direction.scale((from + to) * 0.5D));
    }
}
