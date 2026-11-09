package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 导航终端方块实体（只有主方块「下排中列」有）。
 *
 * <p>和 {@link SonarBlockEntity} 一样，它同时是船脑：继承
 * {@link ShipCoreBlockEntity} 的船体扫描、分舱、水位模拟和推进逻辑；额外负责
 * 以终端为中心的 3D 点云扫描（给 {@code NavigationTerminalRenderer} 画全息）。
 * 模型由 GeckoLib 渲染，所以实现 {@link GeoBlockEntity}（静态模型，无控制器）。</p>
 */
public class NavigationTerminalBlockEntity extends ShipCoreBlockEntity implements GeoBlockEntity {

    /** 点云扫描半径（格）。 */
    public static final int RADIUS = 40;
    /** 采样步长。 */
    public static final int STRIDE = 4;
    /** 点云上限，超了按比例抽稀。 */
    private static final int MAX_POINTS = 2400;
    /** 重新扫描间隔（tick）。 */
    private static final int SCAN_INTERVAL = 20;

    private final List<BlockPos> points = new ArrayList<>();
    private long lastScan = Long.MIN_VALUE;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NavigationTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAVIGATION_TERMINAL.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                  NavigationTerminalBlockEntity be) {
        long now = level.getGameTime();
        if (now - be.lastScan < SCAN_INTERVAL) {
            return;
        }
        be.lastScan = now;
        be.scan(level, pos);
    }

    private void scan(Level level, BlockPos center) {
        points.clear();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx += STRIDE) {
            for (int dy = -RADIUS; dy <= RADIUS; dy += STRIDE) {
                for (int dz = -RADIUS; dz <= RADIUS; dz += STRIDE) {
                    double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distSq > (double) RADIUS * RADIUS) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.WATER)) {
                        continue;
                    }
                    points.add(cursor.immutable());
                }
            }
        }
        if (points.size() > MAX_POINTS) {
            int keep = MAX_POINTS;
            int step = (int) Math.ceil(points.size() / (double) keep);
            List<BlockPos> thinned = new ArrayList<>(keep);
            for (int i = 0; i < points.size(); i += step) {
                thinned.add(points.get(i));
            }
            points.clear();
            points.addAll(thinned);
        }
    }

    public List<BlockPos> points() {
        return points;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：没有动画控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
