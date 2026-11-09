package com.opamb.barotrauma.world;

import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 每一轮任务的航路目标点。
 *
 * <p>2026-11-09 用户反馈"所有任务的目标点都是头顶那个继承过来的哨站"：原来的声纳
 * 目标就是副本站码头的复制品，永远在船正上方，所有任务看起来是同一个目标。
 * 现在按目的地节点给一个**独立方向**的航路浮标：</p>
 *
 * <ul>
 *   <li>目标点在实例锚点周围 40 格、船下方 20 格，按目的地节点分四个方位；</li>
 *   <li>浮标 = 3×3 站体装甲平台 + 中心海晶灯，海里能看到；</li>
 *   <li>声纳上的金色菱形指向它，不再指向头顶哨站。</li>
 * </ul>
 *
 * <p>只放非船体方块（{@code STATION_PLATING} 不在 {@code isShipBlock} 里），
 * 所以不会在装配时被当成船的一部分收走。</p>
 */
public final class MissionTarget {

    /** 目标点相对锚点的水平距离。 */
    private static final int RADIUS = 40;
    /** 目标点相对锚点的高度（船下方 20 格）。 */
    private static final int DEPTH = -20;

    private MissionTarget() {
    }

    /**
     * 目标点相对实例锚点的偏移（x, y, z）。
     *
     * <p>按目的地节点分方向：1 东、2 南、3 西、4 北；节点 0/未知按东。</p>
     */
    public static int[] offsetFor(int destination) {
        int index = destination <= 0 ? 0 : (destination - 1) % 4;
        return switch (index) {
            case 1 -> new int[]{0, DEPTH, RADIUS};
            case 2 -> new int[]{-RADIUS, DEPTH, 0};
            case 3 -> new int[]{0, DEPTH, -RADIUS};
            default -> new int[]{RADIUS, DEPTH, 0};
        };
    }

    /** 某一轮任务的航路目标点（世界坐标）。 */
    public static BlockPos targetFor(MissionInstance.Anchor anchor, int destination) {
        int[] offset = offsetFor(destination);
        return new BlockPos(anchor.x() + offset[0], anchor.y() + offset[1], anchor.z() + offset[2]);
    }

    /** 由存档里的实例锚点坐标还原出 {@link MissionInstance.Anchor}（slot 只用于定位间距）。 */
    public static MissionInstance.Anchor anchorForInstance(int instanceX, int instanceZ) {
        int slot = Math.max(0, Math.round((instanceX - MissionInstance.BASE_X)
                / (float) MissionInstance.SPACING));
        return new MissionInstance.Anchor(slot, instanceX, MissionInstance.ANCHOR_Y, instanceZ);
    }

    /**
     * 在目标点铺航路浮标（幂等）。已经铺过就只加载区块后返回。
     *
     * <p>只替换空气/水，绝不覆盖地形或玩家建筑；中心放海晶灯当灯标。</p>
     */
    public static void ensure(ServerLevel level, MissionInstance.Anchor anchor, int destination) {
        BlockPos target = targetFor(anchor, destination);
        level.getChunkAt(target);
        BlockState plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        // 幂等：中心灯已经在了就认为铺过
        if (level.getBlockState(target.above()).is(Blocks.SEA_LANTERN)) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos plate = target.offset(dx, 0, dz);
                if (replaceable(level, plate)) {
                    level.setBlock(plate, plating, 2);
                }
            }
        }
        if (replaceable(level, target.above())) {
            level.setBlock(target.above(), lamp, 2);
        }
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }
}
