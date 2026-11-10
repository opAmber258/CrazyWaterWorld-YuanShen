package com.opamb.barotrauma.world;

import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionType;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/**
 * 深海世界的兴趣点（POI）与任务池：全部**确定性生成**，不写任何存档。
 *
 * <p>思路：坐标写死，每个区块生成时只写和自己相交的部分，
 * 跨区块无缝拼接、重复生成幂等（后写的覆盖先写的，结果一致）。</p>
 *
 * <ul>
 *   <li>3 艘沉船（任务 1/2/3 的打捞点，船舱里各有一个黑匣子方块）；</li>
 *   <li>1 个侦察点（任务 4，无方块，只有坐标）；</li>
 *   <li>B 中继站（任务 5 的交付点：小气密盒 + 任务终端，在 320, -300 附近）。</li>
 * </ul>
 */
public final class PoiNetwork {

    // ---- 沉船（9×5×7 壳，中心 = 壳几何中心）----
    public static final BlockPos WRECK_A = new BlockPos(80, -122, 40);
    public static final BlockPos WRECK_B = new BlockPos(-150, -302, 90);
    public static final BlockPos WRECK_C = new BlockPos(200, -470, -120);

    /** 侦察点（无方块）。 */
    public static final BlockPos RECON_POINT = new BlockPos(-60, -452, 150);

    // ---- B 中继站（9×5×9 气密盒 + 终端）----
    public static final int POST_B_MIN_X = 316;
    public static final int POST_B_MAX_X = 324;
    public static final int POST_B_MIN_Z = -4;
    public static final int POST_B_MAX_Z = 4;
    public static final int POST_B_MIN_Y = -303;
    public static final int POST_B_MAX_Y = -299;
    /** 终端位置（也是任务 5 的交付点）。 */
    public static final BlockPos POST_B_TERMINAL = new BlockPos(320, -301, 0);
    /** B 中继站内部干燥区（呼吸判定用，见 OceanEnvironment）。 */
    public static final int POST_B_INNER_MIN_X = 317;
    public static final int POST_B_INNER_MAX_X = 323;
    public static final int POST_B_INNER_MIN_Z = -3;
    public static final int POST_B_INNER_MAX_Z = 3;
    public static final int POST_B_INNER_MIN_Y = -302;
    public static final int POST_B_INNER_MAX_Y = -300;

    private PoiNetwork() {
    }

    // ------------------------------------------------------------------ 任务池

    /**
     * 任务池：从 {@link MissionCatalog#offers()} 生成（id 即列表序 + 1）。
     *
     * <p>目前只有“货运”和“洞穴遗物打捞”两类功能的依赖已实装，所以终端只发布这两类；
     * 其余原版任务仍登记在目录里，等生物/信标/沉船遗迹等系统落地后再打开。</p>
     */
    public static List<Mission> definitions() {
        List<Mission> out = new java.util.ArrayList<>();
        int id = 1;
        for (com.opamb.barotrauma.mission.MissionSpec spec
                : com.opamb.barotrauma.mission.MissionCatalog.offers()) {
            switch (spec.kind()) {
                case CARGO -> {
                    // 挥发性化学品按原版“危险货物”处理成限时紧急运送（30 分钟）。
                    boolean urgent = "cargovolatilecompoundn".equals(spec.id());
                    int reward = Math.max(urgent ? 800 : 500, spec.reward());
                    if (urgent) {
                        out.add(new Mission(id++, MissionType.URGENT_DELIVERY,
                                POST_B_TERMINAL, 12, reward, 24000L));
                    } else {
                        out.add(new Mission(id++, MissionType.DELIVERY,
                                POST_B_TERMINAL, 12, reward));
                    }
                }
                case SALVAGE -> out.add(new Mission(id++, MissionType.SALVAGE,
                        WRECK_A, 20, Math.max(1, spec.reward())));
                default -> {
                    // 目录里已登记、但对应功能还没实装的任务不生成
                }
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ 区块写入

    /** 世界生成时调用：把所有 POI 与该区块相交的部分写进去。 */
    public static void buildInto(ChunkAccess chunk) {
        buildWreck(chunk, WRECK_A, Direction.WEST);
        buildWreck(chunk, WRECK_B, Direction.NORTH);
        buildWreck(chunk, WRECK_C, Direction.SOUTH);
        buildOutpostB(chunk);
    }

    /**
     * 沉船：9×5×7 装甲板壳（X×Y×Z），内部中空，中心一格放黑匣子方块。
     * 打掉它就得到 {@code black_box}（任务 1/2/3 的交付物）。
     */
    private static void buildWreck(ChunkAccess chunk, BlockPos center, Direction facing) {
        BlockState plating = ModBlocks.SUBMARINE_PLATING.get().defaultBlockState();
        BlockState glass = ModShipBlocks.HULL_GLASS.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int hx = 4;
        int hy = 2;
        int hz = 3;
        int x0 = center.getX() - hx;
        int y0 = center.getY() - hy;
        int z0 = center.getZ() - hz;
        for (int dx = 0; dx <= hx * 2; dx++) {
            for (int dy = 0; dy <= hy * 2; dy++) {
                for (int dz = 0; dz <= hz * 2; dz++) {
                    boolean shell = dx == 0 || dx == hx * 2 || dy == 0 || dy == hy * 2
                            || dz == 0 || dz == hz * 2;
                    boolean window = dy == hy * 2 && dx >= 3 && dx <= 5 && dz >= 2 && dz <= 4;
                    if (inChunkXZ(chunk, x0 + dx, z0 + dz)) {
                        chunk.setBlockState(new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                shell ? (window ? glass : plating) : air, false);
                    }
                }
            }
        }
        // 黑匣子：船舱正中
        if (inChunkXZ(chunk, center.getX(), center.getZ())) {
            chunk.setBlockState(center, ModBlocks.BLACK_BOX_BLOCK.get().defaultBlockState(), false);
        }
        // 船头破口示意：朝 facing 那一面开一个 2×2 的洞（视觉上是沉没原因）
        // 简化处理：把 facing 方向的端面中心 2×2 挖掉
        int holeX = center.getX() + facing.getStepX() * (hx + 1);
        int holeZ = center.getZ() + facing.getStepZ() * (hz + 1);
        for (int dy = -1; dy <= 0; dy++) {
            for (int side = -1; side <= 0; side++) {
                int sx = facing.getStepX() == 0 ? side : 0;
                int sz = facing.getStepZ() == 0 ? side : 0;
                if (inChunkXZ(chunk, holeX + sx, holeZ + sz)) {
                    chunk.setBlockState(new BlockPos(holeX + sx, center.getY() + dy, holeZ + sz),
                            air, false);
                }
            }
        }
    }

    /**
     * B 中继站：9×5×9 装甲盒，内部中空，中央放任务终端。
     * 朝 -Z 的一面留 2×2 出入口（没有门，海水不进不了——这里是气密盒）。
     */
    private static void buildOutpostB(ChunkAccess chunk) {
        BlockState plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = POST_B_MIN_X; x <= POST_B_MAX_X; x++) {
            for (int z = POST_B_MIN_Z; z <= POST_B_MAX_Z; z++) {
                for (int y = POST_B_MIN_Y; y <= POST_B_MAX_Y; y++) {
                    if (!inChunkXZ(chunk, x, z)) {
                        continue;
                    }
                    boolean shell = x == POST_B_MIN_X || x == POST_B_MAX_X
                            || z == POST_B_MIN_Z || z == POST_B_MAX_Z
                            || y == POST_B_MIN_Y || y == POST_B_MAX_Y;
                    boolean entrance = z == POST_B_MIN_Z && x >= 319 && x <= 320
                            && (y == -302 || y == -301);
                    boolean lamp = y == POST_B_MAX_Y - 1
                            && (x - POST_B_MIN_X) % 4 == 0 && (z - POST_B_MIN_Z) % 4 == 0;
                    BlockState state;
                    if (!shell) {
                        state = lamp ? Blocks.SEA_LANTERN.defaultBlockState() : air;
                    } else if (entrance) {
                        state = air;
                    } else {
                        state = plating;
                    }
                    chunk.setBlockState(new BlockPos(x, y, z), state, false);
                }
            }
        }
        // B 中继站的任务终端已弃用；POST_B_TERMINAL 仍作为交付判定的坐标锚点。
    }

    private static boolean inChunkXZ(ChunkAccess chunk, int x, int z) {
        ChunkPos pos = chunk.getPos();
        return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
                && z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    /** 供 OceanEnvironment 使用：这个位置是否在 B 中继站的内部干燥区。 */
    public static boolean isInsideOutpostBDryZone(BlockPos pos) {
        return pos.getX() >= POST_B_INNER_MIN_X && pos.getX() <= POST_B_INNER_MAX_X
                && pos.getZ() >= POST_B_INNER_MIN_Z && pos.getZ() <= POST_B_INNER_MAX_Z
                && pos.getY() >= POST_B_INNER_MIN_Y && pos.getY() <= POST_B_INNER_MAX_Y;
    }
}
