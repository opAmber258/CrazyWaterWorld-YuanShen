package com.opamb.barotrauma.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

/**
 * 任务海域实例（"副本"）：每轮出勤在深海维度远处的一条保留带里生成一片封闭洞穴。
 *
 * <p>地形完全由<b>锚点坐标 + 世界种子</b>确定性生成，不写任何存档区块差异；只有战役
 * 状态记下本次出勤的锚点（见 {@code RoundState#instanceX()}）。区块生成时按坐标判断
 * 是否落在某个锚点周围，因此：</p>
 * <ul>
 *   <li>第 n 轮用第 n 个 slot，锚点间距 {@link #SPACING}，互不重叠；</li>
 *   <li>同一轮重进/读档，地形完全一致；</li>
 *   <li>没去过的 slot 永远不会生成（区块惰性加载）。</li>
 * </ul>
 *
 * <p><b>为什么还在同一个维度：</b>VS2 只有一套船世界，跨维度传送物理船另有一套坑；
 * 先用"远处保留带 + 厚实洞壁"实现独立海域，把跨维度留给真正需要时再评估。</p>
 *
 * <p>形状：x/z 半径约 96 格的椭球形洞穴，洞顶 y≈-46..-54、洞底 y≈-122..-130，边缘带
 * 噪声摆动；洞壁不是薄壳，而是从洞底到洞顶填实的岩壁，洞顶/洞底再各加 6~8 格岩壳，
 * 保证不会和外面分层海域的天然空腔连通。洞内加钟乳石、石笋和洞顶冰脉，贴"寒冷深穴"。</p>
 */
public final class MissionInstance {

    /** 实例带起点 x，远离战役海图区域（0..960）。 */
    public static final int BASE_X = 20000;
    /** 每轮占用的 x 间距，必须大于 2*RX。 */
    public static final int SPACING = 1024;
    /** 洞穴水平半径。 */
    public static final int RX = 96;
    public static final int RZ = 96;
    /** 潜艇在本实例里的出生高度。 */
    public static final int ANCHOR_Y = -84;

    public static final int CEILING_BASE = -46;
    public static final int CEILING_WOBBLE = 8;
    public static final int FLOOR_BASE = -122;
    public static final int FLOOR_WOBBLE = 8;

    /** 洞壁外沿（相对半径）。1.0 是洞内空气，1.0~RIM 是实心岩壁。 */
    public static final double RIM = 1.12D;
    /** 出生点净空球半径，避免钟乳石长到潜艇身上。 */
    public static final int SPAWN_CLEAR_RADIUS = 12;

    /** 影响范围外沿（格）：超过这个距离的区块完全不动。 */
    public static final int OUTER = (int) Math.ceil(RX * RIM) + 2;

    private static final long SALT_WALL = 0x1A2B3C4DL;
    private static final long SALT_CEIL = 0x2B3C4D5EL;
    private static final long SALT_FLOOR = 0x3C4D5E6FL;
    private static final long SALT_FLOOR_DECO = 0x6F708192L;
    private static final long SALT_SPIKE = 0x4D5E6F70L;
    private static final long SALT_ICE = 0x5E6F7081L;

    /** 一次出勤对应的实例锚点。 */
    public record Anchor(int slot, int x, int y, int z) {
        /** 潜艇落点（船体中心）。 */
        public Vec3 spawn() {
            return new Vec3(x + 0.5D, y, z + 0.5D);
        }
    }

    private MissionInstance() {
    }

    /** 第 {@code roundIndex} 轮出勤的锚点；轮次从 0 开始（= 战役已完成的轮数）。 */
    public static Anchor anchorForRound(int roundIndex) {
        int slot = Math.max(0, roundIndex);
        return new Anchor(slot, BASE_X + slot * SPACING, ANCHOR_Y, 0);
    }

    /** 把世界种子和 slot 混成实例自己的种子；同一 slot 永远一致。 */
    public static long seedFor(long worldSeed, int slot) {
        return worldSeed ^ (0x5EED5EEDL + slot * 0x9E3779B97F4A7C15L);
    }

    // ------------------------------------------------------------------
    // 区块生成入口
    // ------------------------------------------------------------------

    /** 区块生成时调用；不在实例影响范围就立刻返回。 */
    public static void generate(ChunkAccess chunk, long worldSeed) {
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int maxX = pos.getMaxBlockX();
        int minZ = pos.getMinBlockZ();
        int maxZ = pos.getMaxBlockZ();
        if (maxX < BASE_X - OUTER || maxZ < -RZ - OUTER || minZ > RZ + OUTER) {
            return;
        }
        int firstSlot = Math.floorDiv(minX - OUTER - BASE_X, SPACING);
        int lastSlot = Math.floorDiv(maxX + OUTER - BASE_X, SPACING);
        for (int slot = Math.max(0, firstSlot); slot <= lastSlot; slot++) {
            int ax = BASE_X + slot * SPACING;
            if (maxX < ax - OUTER || minX > ax + OUTER) {
                continue;
            }
            carve(chunk, seedFor(worldSeed, slot), new Anchor(slot, ax, ANCHOR_Y, 0));
        }
    }

    private static void carve(ChunkAccess chunk, long seed, Anchor anchor) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState tuff = Blocks.TUFF.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int lx = 0; lx < 16; lx++) {
            int x = minX + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = minZ + lz;
                double radius = radiusAt(seed, anchor, x, z);
                if (radius > RIM) {
                    continue;
                }
                int ceiling = ceilingY(seed, anchor, x, z);
                int floor = floorY(seed, anchor, x, z);
                BlockState rock = floor < -100 ? deepslate : stone;

                if (radius <= 1.0D) {
                    // 洞内：清空主体
                    for (int y = floor + 1; y <= ceiling; y++) {
                        setBlock(chunk, x, y, z, air);
                    }
                    // 洞顶壳：填 8 格，底下的天然分层空腔就钻不进来了
                    for (int y = ceiling + 1; y <= ceiling + 8; y++) {
                        setBlock(chunk, x, y, z, rock);
                    }
                    // 洞底壳：填 6 格
                    for (int y = floor - 6; y < floor; y++) {
                        setBlock(chunk, x, y, z, rock);
                    }
                    // 地面表层：凝灰岩 / 砂砾交替，别像一整块石头
                    double floorMix = DeepSeaNoise.fbm2(seed ^ SALT_FLOOR_DECO, x / 24.0D, z / 24.0D, 2);
                    BlockState surface = floorMix > 0.35D ? gravel : tuff;
                    setBlock(chunk, x, floor, z, surface);
                    // 洞顶冰脉：只贴在洞顶最下面两层，从洞里抬头能看到
                    if (DeepSeaNoise.fbm2(seed ^ SALT_ICE, x / 18.0D, z / 18.0D, 2) > 0.45D) {
                        setBlock(chunk, x, ceiling + 1, z, ice);
                        setBlock(chunk, x, ceiling + 2, z, ice);
                    }
                    // 钟乳石 / 石笋
                    double spike = DeepSeaNoise.fbm2(seed ^ SALT_SPIKE, x / 26.0D, z / 26.0D, 2);
                    if (spike > 0.34D) {
                        int length = (int) ((spike - 0.34D) * 60.0D);
                        for (int y = ceiling - 1; y > ceiling - 1 - length && y > floor; y--) {
                            setBlock(chunk, x, y, z, rock);
                        }
                    } else if (spike < -0.34D) {
                        int length = (int) ((-spike - 0.34D) * 50.0D);
                        for (int y = floor + 1; y < floor + 1 + length && y < ceiling; y++) {
                            setBlock(chunk, x, y, z, rock);
                        }
                    }
                } else {
                    // 洞壁：从洞底壳一直填到洞顶壳，整段实心
                    for (int y = floor - 6; y <= ceiling + 8; y++) {
                        setBlock(chunk, x, y, z, rock);
                    }
                }
                // 出生点净空最后处理：任何钟乳石/石笋都不许伸进停船球里
                clearSpawn(chunk, anchor, x, z, air);
            }
        }
    }

    private static void clearSpawn(ChunkAccess chunk, Anchor anchor, int x, int z, BlockState air) {
        double dx = x + 0.5D - (anchor.x() + 0.5D);
        double dz = z + 0.5D - (anchor.z() + 0.5D);
        double horizontal = dx * dx + dz * dz;
        double r = SPAWN_CLEAR_RADIUS;
        if (horizontal > r * r) {
            return;
        }
        int vertical = (int) Math.sqrt(r * r - horizontal);
        for (int y = anchor.y() - vertical; y <= anchor.y() + vertical; y++) {
            setBlock(chunk, x, y, z, air);
        }
    }

    // ------------------------------------------------------------------
    // 形状（纯函数，单元测试直接调）
    // ------------------------------------------------------------------

    /** 含边界摆动的归一化水平半径；<=1 是洞内空气，1~RIM 是岩壁。 */
    public static double radiusAt(long seed, Anchor anchor, int x, int z) {
        double dx = (x - anchor.x()) / (double) RX;
        double dz = (z - anchor.z()) / (double) RZ;
        double wobble = DeepSeaNoise.fbm2(seed ^ SALT_WALL, x / 40.0D, z / 40.0D, 3);
        return Math.sqrt(dx * dx + dz * dz) + wobble * 0.10D;
    }

    /** 洞顶 y（含噪声，范围 {@code CEILING_BASE-CEILING_WOBBLE .. CEILING_BASE}）。 */
    public static int ceilingY(long seed, Anchor anchor, int x, int z) {
        double n = DeepSeaNoise.fbm2(seed ^ SALT_CEIL, x / 64.0D, z / 64.0D, 3);
        return CEILING_BASE - (int) Math.round((n * 0.5D + 0.5D) * CEILING_WOBBLE);
    }

    /** 洞底 y（含噪声，范围 {@code FLOOR_BASE-FLOOR_WOBBLE .. FLOOR_BASE}）。 */
    public static int floorY(long seed, Anchor anchor, int x, int z) {
        double n = DeepSeaNoise.fbm2(seed ^ SALT_FLOOR, x / 64.0D, z / 64.0D, 3);
        return FLOOR_BASE - (int) Math.round((n * 0.5D + 0.5D) * FLOOR_WOBBLE);
    }

    /** 这个坐标是不是洞内空气（不含出生点净空；钟乳石由生成阶段处理）。 */
    public static boolean isCaveAir(long seed, Anchor anchor, int x, int y, int z) {
        return radiusAt(seed, anchor, x, z) <= 1.0D
                && y > floorY(seed, anchor, x, z)
                && y <= ceilingY(seed, anchor, x, z);
    }

    public static boolean isSpawnClearance(Anchor anchor, int x, int y, int z) {
        double dx = x + 0.5D - (anchor.x() + 0.5D);
        double dy = y - anchor.y();
        double dz = z + 0.5D - (anchor.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= SPAWN_CLEAR_RADIUS * SPAWN_CLEAR_RADIUS;
    }

    private static void setBlock(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        int index = (y - chunk.getMinBuildHeight()) >> 4;
        if (index < 0 || index >= chunk.getSections().length) {
            return;
        }
        chunk.getSection(index).setBlockState(x & 15, y & 15, z & 15, state, false);
    }

    // ------------------------------------------------------------------
    // 传送前预加载
    // ------------------------------------------------------------------

    /**
     * 把锚点周围的区块同步加载出来。
     *
     * <p>物理船传送过去之后，VS2 会在下一 tick 把船的 activeChunks 更新到新位置；
     * 如果那时区块还没加载，玩家一移动就会被 VS2 判定成"撞到未加载的船"并回弹
     * （日志里的 {@code moved while colliding with unloaded ships}）。所以先加载再传送。</p>
     */
    public static int preload(ServerLevel level, Anchor anchor, int chunkRadius) {
        int centerX = anchor.x() >> 4;
        int centerZ = anchor.z() >> 4;
        int loaded = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                level.getChunk(centerX + dx, centerZ + dz);
                loaded++;
            }
        }
        return loaded;
    }
}
