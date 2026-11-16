package com.opamb.barotrauma.client;

import com.opamb.barotrauma.block.MineralVeinBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 客户端采矿进度：等离子切割机正在切的那一格矿脉。
 *
 * <p>服务端才是权威进度，但客户端自己也在 {@code onUseTick} 里按同样节拍累计，
 * 用来画方块上方的耐久条（和船壳血条同一套渲染）。切碎/移开视线就清空。</p>
 */
public final class MineralVeinClient {

    /** 和 PlasmaCutterItem 的 VEIN_CUT_APPLICATIONS 一致：每 2 tick 加 1/40。 */
    private static final float STEP = 1.0F / 40.0F;
    private static final double MAX_DISTANCE = 40.0D;

    private static BlockPos current;
    private static float progress;
    /** 最近一次更新的客户端 gameTime；停手后进度条不能一直挂着。 */
    private static long lastUpdate = Long.MIN_VALUE;

    private MineralVeinClient() {
    }

    /** 客户端每 2 tick 调一次：瞄准矿脉就累计，移开就清空。 */
    public static void tick(@Nullable BlockPos vein) {
        lastUpdate = gameTime();
        if (vein == null) {
            current = null;
            progress = 0.0F;
            return;
        }
        if (!vein.equals(current)) {
            current = vein.immutable();
            progress = 0.0F;
        }
        progress = Math.min(1.0F, progress + STEP);
    }

    /** 客户端从玩家视线找矿脉并累计进度。 */
    public static void tickFromPlayer(net.minecraft.world.level.Level level,
                                      net.minecraft.world.entity.player.Player player,
                                      double reach) {
        BlockPos vein = null;
        net.minecraft.world.phys.HitResult hit = player.pick(reach, 1.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (level.getBlockState(pos).getBlock() instanceof MineralVeinBlock) {
                vein = pos;
            }
        }
        tick(vein);
    }

    /** 松开右键/切完时清空。 */
    public static void reset() {
        current = null;
        progress = 0.0F;
        lastUpdate = Long.MIN_VALUE;
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
    }

    /** 给血条渲染器用的条目；方块没了/太远/停手超过 6 tick 就自动清空。 */
    public static List<HullDamageRenderer.Target> bars(Vec3 cameraPos) {
        BlockPos pos = current;
        if (pos == null) {
            return List.of();
        }
        long now = gameTime();
        if (now != Long.MIN_VALUE && lastUpdate != Long.MIN_VALUE
                && now - lastUpdate > 6L) {
            current = null;
            progress = 0.0F;
            lastUpdate = Long.MIN_VALUE;
            return List.of();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !(minecraft.level.getBlockState(pos).getBlock() instanceof MineralVeinBlock)) {
            current = null;
            progress = 0.0F;
            return List.of();
        }
        Vec3 center = Vec3.atCenterOf(pos);
        double distance = Math.sqrt(center.distanceToSqr(cameraPos));
        if (distance > MAX_DISTANCE) {
            return List.of();
        }
        return List.of(new HullDamageRenderer.Target(pos, center, 1.0F - progress, 0.0F, distance));
    }
}
