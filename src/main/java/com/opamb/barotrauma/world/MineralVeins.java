package com.opamb.barotrauma.world;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.data.MissionSavedData;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionType;
import com.opamb.barotrauma.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 采矿任务的矿脉生成（对照原作 {@code Level.GenerateMissionResources}）。
 *
 * <p>原作在关卡载入时，沿 MainPath 的岩壁边缘生成一整簇矿物（{@code PlaceResources}
 * 把 Item 挂在墙上）。这里等价成：出发站→目的站的航线两侧找岩壁表面，按任务矿物
 * 放几簇矿脉方块。</p>
 *
 * <p>**必须在下潜时动态放**，不能只靠世界生成：旧存档里那片区块早就生成过了，
 * 世界生成不会再跑，玩家会看到"地图上没有矿点"。</p>
 */
public final class MineralVeins {

    /** 航线两侧多宽内找岩壁（航线 48 格内是开阔水道，矿脉放在海底/洞顶上）。 */
    private static final int CORRIDOR = 16;
    /** 离码头太近不放（站体/竖井附近）。 */
    private static final double SITE_CLEAR = 26.0D;
    /** 每簇矿脉最多几格。 */
    private static final int CLUSTER_BLOCKS = 3;

    private MineralVeins() {
    }

    /**
     * 下潜开始时调用：为本次接取的采矿任务在副本航线上放矿脉。
     *
     * @param booked 本次接取的任务 id（已 accept，目的地已绑定）
     */
    public static void spawnForTravel(ServerLevel level, MissionInstance.Anchor anchor,
                                      DockingBay.Site startSite, List<Integer> booked) {
        if (booked == null || booked.isEmpty()) {
            return;
        }
        MissionSavedData data = MissionSavedData.get(level);
        Set<String> minerals = new LinkedHashSet<>();
        for (int id : booked) {
            Mission mission = data.missions().get(id);
            if (mission != null && mission.type == MissionType.MINERAL
                    && mission.mineralItem() != null && !mission.mineralItem().isEmpty()) {
                minerals.add(mission.mineralItem());
            }
        }
        if (minerals.isEmpty()) {
            return;
        }
        // 先加载航线区块：生成器第一次跑过后 terrainSeed() 才是真实派生种子。
        preloadCorridor(level, anchor, startSite);
        // 必须用生成器实际使用的派生种子：SeaTerrain 的洞顶/海底噪声按它计算，
        // 用 level.getSeed() 会把候选放到错误的岩壁高度，最后被表面检查全部排除。
        // terrainSeed(level) 会在区块已全部存在、生成器缓存没初始化时从 RandomState 补算。
        long seed = level.getSeed();
        if (level.getChunkSource().getGenerator() instanceof DeepSeaChunkGenerator generator) {
            long derived = generator.terrainSeed(level);
            if (derived != Long.MIN_VALUE) {
                seed = derived;
            }
        }
        // 关卡参数：资源概率/隧道半径/开阔带中点；没有活动关卡时保持原来的 0.2/16/-240。
        com.opamb.barotrauma.world.level.LevelParams params = MissionInstance.activeParams();
        double chance = params == null ? 0.2D : params.content().resourceSpawnChance();
        int corridor = params == null ? CORRIDOR
                : Math.max(48, params.cavernHalfWidth());
        double midDepth = params == null ? -240.0D
                : (params.bandCeiling() + params.bandFloor()) / 2.0D;
        List<Candidate> candidates = collectCandidates(level, seed, anchor, startSite, corridor);
        if (candidates.isEmpty()) {
            BarotraumaMod.LOGGER.warn("[mining] 航线上找不到可放矿脉的岩壁：anchor=({},{})",
                    anchor.x(), anchor.z());
            return;
        }
        for (String mineral : minerals) {
            Block vein = veinFor(mineral);
            if (vein == null) {
                continue;
            }
            // 每簇最多 3 格、单格 1–2 个，按"一簇至少顶一个需求"保守下限；
            // 资源概率高于 0.3 时再按比例加密。
            double density = Math.max(1.0D, chance / 0.3D);
            int clusters = Math.max(4, (int) Math.ceil(
                    requiredCount(data, booked, mineral) * density));
            placeClusters(level, candidates, vein, clusters, mineral.hashCode(), midDepth);
        }
    }

    private static int requiredCount(MissionSavedData data, List<Integer> booked, String mineral) {
        // 同一矿物可能被多条任务同时接取（不同站点/模板），需求必须累加。
        int total = 0;
        for (int id : booked) {
            Mission mission = data.missions().get(id);
            if (mission != null && mineral.equals(mission.mineralItem())) {
                total += Math.max(0, mission.mineralCount());
            }
        }
        return total > 0 ? total : 8;
    }

    private static Block veinFor(String mineral) {
        return switch (mineral) {
            case "barotrauma:quartz" -> ModBlocks.QUARTZ_VEIN.get();
            case "barotrauma:pyromorphite" -> ModBlocks.PYROMORPHITE_VEIN.get();
            case "barotrauma:uraniumore" -> ModBlocks.URANIUM_VEIN.get();
            default -> null;
        };
    }

    /** 把航线经过的区块先加载出来，否则 setBlock 只会写进未加载的"空"区块。 */
    private static void preloadCorridor(ServerLevel level, MissionInstance.Anchor anchor,
                                        DockingBay.Site startSite) {
        RoutePath path = MissionInstance.activePath();
        // 采样数按航线长度算：每个采样点带 1 区块半径（3×3 区块），
        // 步长 24 格保证相邻采样范围相接，不会在长航线上留下未加载的空洞。
        double length = 0.0D;
        if (path != null) {
            RoutePath.Point[] points = path.points();
            for (int i = 1; i < points.length; i++) {
                RoutePath.Point a = points[i - 1];
                RoutePath.Point b = points[i];
                length += Math.hypot(b.x() - a.x(), b.z() - a.z());
            }
        } else {
            length = Math.hypot(anchor.x() - startSite.x(), anchor.z() - startSite.z());
        }
        int samples = Math.max(12, (int) Math.ceil(length / 24.0D));
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            int x;
            int z;
            if (path != null) {
                RoutePath.Point point = path.pointAt(t);
                x = (int) Math.round(point.x());
                z = (int) Math.round(point.z());
            } else {
                x = (int) Math.round(startSite.x() + (anchor.x() - startSite.x()) * t);
                z = (int) Math.round(startSite.z() + (anchor.z() - startSite.z()) * t);
            }
            MissionInstance.preloadNear(level, new BlockPos(x, anchor.y(), z), 1);
        }
    }

    /** 航线上岩壁表面的候选格。 */
    private record Candidate(BlockPos pos, double along, double distance, int y) {
    }

    private static List<Candidate> collectCandidates(ServerLevel level, long seed,
                                                     MissionInstance.Anchor anchor,
                                                     DockingBay.Site startSite,
                                                     int corridor) {
        List<Candidate> out = new ArrayList<>();
        RoutePath path = MissionInstance.activePath();
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        if (path != null) {
            minX = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            minZ = Integer.MAX_VALUE;
            maxZ = Integer.MIN_VALUE;
            for (RoutePath.Point point : path.points()) {
                minX = Math.min(minX, (int) Math.floor(point.x()));
                maxX = Math.max(maxX, (int) Math.ceil(point.x()));
                minZ = Math.min(minZ, (int) Math.floor(point.z()));
                maxZ = Math.max(maxZ, (int) Math.ceil(point.z()));
            }
            minX -= corridor;
            maxX += corridor;
            minZ -= corridor;
            maxZ += corridor;
        } else {
            minX = Math.min(anchor.x(), startSite.x()) - corridor;
            maxX = Math.max(anchor.x(), startSite.x()) + corridor;
            minZ = Math.min(anchor.z(), startSite.z()) - corridor;
            maxZ = Math.max(anchor.z(), startSite.z()) + corridor;
        }
        double segX = anchor.x() - startSite.x();
        double segZ = anchor.z() - startSite.z();
        double segLenSq = segX * segX + segZ * segZ;
        for (int x = minX; x <= maxX; x += 4) {
            for (int z = minZ; z <= maxZ; z += 4) {
                if (Math.hypot(x - anchor.x(), z - anchor.z()) < SITE_CLEAR
                        || Math.hypot(x - startSite.x(), z - startSite.z()) < SITE_CLEAR) {
                    continue;
                }
                double t;
                double distance;
                if (path != null) {
                    // 主航道是蜿蜒折线：按最近点算沿线进度和距离。
                    RoutePath.Nearest nearest = path.nearest(x, z);
                    t = nearest.along();
                    distance = nearest.distance();
                } else {
                    t = segLenSq <= 1.0E-6D ? 0.0D
                            : ((x - startSite.x()) * segX + (z - startSite.z()) * segZ) / segLenSq;
                    t = Math.max(0.0D, Math.min(1.0D, t));
                    double px = startSite.x() + segX * t;
                    double pz = startSite.z() + segZ * t;
                    distance = Math.hypot(x - px, z - pz);
                }
                if (distance > corridor) {
                    continue;
                }
                int floor = SeaTerrain.floorY(seed, x, z);
                int ceiling = SeaTerrain.ceilingY(seed, x, z);
                if (ceiling - floor < 24) {
                    continue;
                }
                // 海底 + 洞顶 + 4 个散列深度：覆盖岩柱/钟乳石/浮岩岛的表面。
                addIfSurface(level, out, x, floor, z, t, distance);
                addIfSurface(level, out, x, ceiling, z, t, distance);
                long hash = hash(seed, x, z);
                for (int i = 0; i < 4; i++) {
                    // floor 在下面（更负），ceiling 在上面：跨度必须 ceiling - floor。
                    int span = ceiling - floor - 12;
                    if (span <= 0) {
                        break;
                    }
                    int y = floor + 6 + (int) Math.floorMod(hash >>> (8 + i * 7), span);
                    addIfSurface(level, out, x, y, z, t, distance);
                }
            }
        }
        return out;
    }

    private static void addIfSurface(ServerLevel level, List<Candidate> out, int x, int y, int z,
                                     double along, double distance) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!isRock(state)) {
            return;
        }
        if (!touchesAir(level, pos)) {
            return;
        }
        out.add(new Candidate(pos.immutable(), along, distance, y));
    }

    private static boolean isRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.PACKED_ICE);
    }

    private static boolean touchesAir(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) {
                continue;
            }
            if (level.getBlockState(neighbor).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 沿航线分桶放矿脉：每桶挑一个离"中间深度"最近的候选，保证矿脉分散在整段航线上，
     * 不会全堆在目的站门口。
     */
    private static void placeClusters(ServerLevel level, List<Candidate> candidates,
                                      Block vein, int clusters, int salt, double midDepth) {
        List<Candidate> pool = new ArrayList<>(candidates);
        pool.sort(Comparator.comparingDouble(Candidate::along)
                .thenComparingDouble(c -> Math.abs(c.y() - midDepth)));
        int placed = 0;
        for (int i = 0; i < clusters; i++) {
            double target = (i + 0.5D) / clusters;
            Candidate best = null;
            double bestScore = Double.MAX_VALUE;
            for (Candidate candidate : pool) {
                double score = Math.abs(candidate.along() - target) * 3.0D
                        + Math.abs(candidate.y() - midDepth) / 220.0D
                        + candidate.distance() / 80.0D;
                // 同一格不要重复选中。
                if (score < bestScore && !isOccupied(level, candidate.pos())) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            placeCluster(level, best.pos(), vein, salt + i);
            placed++;
        }
        BarotraumaMod.LOGGER.info("[mining] 航线矿脉：候选={} 放置={} 簇", candidates.size(), placed);
    }

    private static boolean isOccupied(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof com.opamb.barotrauma.block.MineralVeinBlock;
    }

    /** 一格矿脉 + 最多两格贴着的岩壁，让声纳/肉眼都能看到"一簇"。 */
    private static void placeCluster(ServerLevel level, BlockPos pos, Block vein, int salt) {
        level.setBlock(pos, vein.defaultBlockState(), 3);
        int placed = 1;
        for (Direction dir : Direction.values()) {
            if (placed >= CLUSTER_BLOCKS) {
                break;
            }
            BlockPos neighbor = pos.relative(dir);
            if (!level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) {
                continue;
            }
            BlockState state = level.getBlockState(neighbor);
            if (isRock(state) && touchesAir(level, neighbor)
                    && Math.floorMod(salt * 31L + placed, 2) == 0) {
                level.setBlock(neighbor, vein.defaultBlockState(), 3);
                placed++;
            }
        }
    }

    private static long hash(long seed, int x, int z) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }
}
