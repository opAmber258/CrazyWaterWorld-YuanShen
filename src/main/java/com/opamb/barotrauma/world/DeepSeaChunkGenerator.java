package com.opamb.barotrauma.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.level.LevelParams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 深海维度生成器：一整根 1000 格高的岩柱，中间掏出一整片开阔的"潜渊症式"海域。
 *
 * <p>形状（对照原作 Cold Caverns 关卡参数）：</p>
 * <ul>
 *   <li><b>全局开阔海</b>：顶部岩壳 -44 起、底部岩床 -544 左右，中间是开阔水域，
 *       岩柱/钟乳石/浮岩岛从 2D 噪声长出来；</li>
 *   <li><b>航行关卡</b>：{@link RoutePath} 在岩体里挖出左右蜿蜒、上下起伏的大椭圆洞窟
 *       （横向最大 480、纵向最大 440），侧洞是首尾都接主航道的环路；潜艇看到的是
 *       看不到边的洞窟，而不是细管子；</li>
 *   <li><b>POI 净空</b>：沉船/中继站周围强制留空，不会被岩柱埋掉。</li>
 * </ul>
 *
 * <p>上一版的 Voronoi 细胞壁已按用户要求撤掉（方块状细胞墙太丑）；关卡地形完全由
 * {@link SeaTerrain} 的隧道剖面决定，不再叠加岩柱/岩墙/浮岩岛。</p>
 */
public class DeepSeaChunkGenerator extends ChunkGenerator {

    public static final Codec<DeepSeaChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
                    .apply(instance, DeepSeaChunkGenerator::new));

    private static final int STEP = 4;
    private static final int GRID_XZ = 16 / STEP + 1;
    private static final int GRID_Y = ModDimensions.HEIGHT / STEP + 1;

    /** 岩柱/钟乳石/浮岩岛/岩墙的形状噪声（高度场本体在 {@link SeaTerrain}）。 */
    private static final long SALT_WALL = 0x3C6EF372L;
    private static final long SALT_PILLAR = 0xA54FF53AL;
    private static final long SALT_HANG = 0x510E527FL;
    private static final long SALT_ISLAND = 0x1F83D9ABL;
    private static final long SALT_ISLAND_Y = 0x5BE0CD19L;

    private long cachedWorldSeed = Long.MIN_VALUE;

    public DeepSeaChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------
    // 生成
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        long seed = worldSeed(randomState);
        return CompletableFuture.supplyAsync(() -> {
            generate(chunk, seed);
            return chunk;
        }, executor);
    }

    private void generate(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int originX = chunkPos.getMinBlockX();
        int originZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        // 本轮航行关卡的参数；没有活动关卡就是 null（走原来的全局地形）。
        LevelParams params = MissionInstance.activeParams();

        // 海域用空气而不是真实水：水下效果由客户端雾/滤镜表现，进水走数值模拟。
        BlockState ocean = Blocks.AIR.defaultBlockState();
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState tuff = Blocks.TUFF.defaultBlockState();

        // 1) 整根岩柱：上冰盖、中石头、深处深板岩
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            int sectionY = minY + i * 16;
            BlockState fill;
            if (sectionY >= ModDimensions.CEILING_BOTTOM_Y) {
                fill = ice;
            } else if (sectionY <= -704) {
                fill = deepslate;
            } else {
                fill = stone;
            }
            fillSection(sections[i], fill);
        }

        // 2) 海床表面掺杂凝灰岩
        for (int y = minY; y <= ModDimensions.SEABED_TOP_Y; y++) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    boolean patch = ((lx * 31 + lz * 17 + y * 13) & 7) == 0;
                    if (patch) {
                        setBlock(chunk, originX + lx, y, originZ + lz, tuff);
                    }
                }
            }
        }

        // 3) 海域：全局是上岩壳/下岩床之间的开阔水；活动关卡是 330–600 格宽、
        //    330–560 格高的随机游走大洞窟。洞窟中央保持通畅，外圈按大尺度噪声长
        //    岩柱/钟乳石/浮岩岛——这是原作洞穴里的岩体特征，但不再是难看的细胞墙。
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = originX + lx;
                int z = originZ + lz;
                int ceiling = SeaTerrain.ceilingY(seed, x, z);
                int floor = SeaTerrain.floorY(seed, x, z);
                int open = ceiling - floor;
                if (open <= 0) {
                    continue; // 整列都是岩（噪声把洞顶压到洞底以下），保持原样
                }

                double routeDistance = params == null ? Double.MAX_VALUE
                        : MissionInstance.distanceToActiveRoute(x, z);
                boolean inLevel = params != null
                        && routeDistance <= SeaTerrain.levelInfluence(params);
                boolean clear = !inLevel && SeaTerrain.inClearZone(x, z);

                // 关卡内的大岩体：只长在洞窟外圈（离隧道中心 > 0.45×半宽），
                // 中央航道永远留空。岩柱从洞底长、钟乳石从洞顶垂、浮岩岛在中间。
                int formationTop = Integer.MIN_VALUE;
                int formationBottom = Integer.MIN_VALUE;
                if (inLevel && params != null
                        && routeDistance > params.cavernHalfWidth() * 0.45D) {
                    double islandNoise = DeepSeaNoise.fbm2(
                            seed ^ SALT_ISLAND, x / 110.0D, z / 110.0D, 2);
                    if (islandNoise > 0.52D) {
                        double islandYNoise = DeepSeaNoise.fbm2(
                                seed ^ SALT_ISLAND_Y, x / 70.0D, z / 70.0D, 2);
                        int centerY = floor + (int) ((0.5D + islandYNoise * 0.30D) * open);
                        int half = (int) ((islandNoise - 0.52D) * open * 0.60D) + 16;
                        formationTop = centerY + half;
                        formationBottom = centerY - half;
                    }
                    double pillarNoise = DeepSeaNoise.fbm2(
                            seed ^ SALT_PILLAR, x / 90.0D, z / 90.0D, 2);
                    if (pillarNoise > 0.56D) {
                        formationTop = floor + (int) ((pillarNoise - 0.56D) * open * 0.50D) + 4;
                        formationBottom = floor;
                    }
                    double hangNoise = DeepSeaNoise.fbm2(
                            seed ^ SALT_HANG, x / 90.0D, z / 90.0D, 2);
                    if (hangNoise > 0.56D) {
                        formationBottom = ceiling - (int) ((hangNoise - 0.56D) * open * 0.50D) - 4;
                        formationTop = ceiling;
                    }
                }

                int pillarTop = floor;
                int hangBottom = ceiling;
                int islandBottom = Integer.MIN_VALUE;
                int islandTop = Integer.MIN_VALUE;
                boolean wall = false;
                if (!inLevel && !clear) {
                    double pillarThreshold = 0.34D;
                    double wallThreshold = 0.58D;
                    double islandThreshold = 0.55D;
                    wall = DeepSeaNoise.fbm2(seed ^ SALT_WALL, x / 110.0D, z / 110.0D, 3)
                            > wallThreshold;
                    double pillarNoise = DeepSeaNoise.fbm2(seed ^ SALT_PILLAR, x / 48.0D, z / 48.0D, 2);
                    double hangNoise = DeepSeaNoise.fbm2(seed ^ SALT_HANG, x / 46.0D, z / 46.0D, 2);
                    double islandNoise = DeepSeaNoise.fbm2(seed ^ SALT_ISLAND, x / 130.0D, z / 130.0D, 2);
                    double islandYNoise = DeepSeaNoise.fbm2(seed ^ SALT_ISLAND_Y, x / 70.0D, z / 70.0D, 2);

                    // 岩柱/钟乳石只长到水域高度的 18%：用户反馈三成仍然太高。
                    pillarTop = pillarNoise > pillarThreshold
                            ? floor + (int) ((pillarNoise - pillarThreshold) * open * 0.18D) : floor;
                    hangBottom = hangNoise > pillarThreshold
                            ? ceiling - (int) ((hangNoise - pillarThreshold) * open * 0.18D) : ceiling;
                    if (islandNoise > islandThreshold) {
                        int centerY = floor + (int) ((0.5D + islandYNoise * 0.30D) * open);
                        int half = (int) ((islandNoise - islandThreshold) * 24.0D) + 2;
                        islandBottom = centerY - half;
                        islandTop = centerY + half;
                    }
                }

                for (int y = floor + 1; y < ceiling; y++) {
                    if (inLevel) {
                        boolean rock = y <= formationTop && y >= formationBottom;
                        setBlock(chunk, x, y, z, rock ? stone : ocean);
                        continue;
                    }
                    if (clear) {
                        setBlock(chunk, x, y, z, ocean);
                        continue;
                    }
                    boolean rock = wall
                            || y <= pillarTop
                            || y >= hangBottom
                            || (y >= islandBottom && y <= islandTop);
                    setBlock(chunk, x, y, z, rock ? stone : ocean);
                }

                // 岩床表面掺凝灰岩，别让整片海底是一块纯石头
                if (!clear) {
                    for (int y = floor; y > floor - 3; y--) {
                        if (((x * 31 + z * 17 + y * 13) & 7) == 0) {
                            setBlock(chunk, x, y, z, tuff);
                        }
                    }
                }
            }
        }

        // 6a) 采矿任务：副本航线附近的海底矿脉（按坐标 + 世界种子确定性生成）
        placeVeins(chunk, seed, originX, originZ, minY);

        // 6b) 任务 POI：沉船/侦察点/B 中继站（确定性，幂等）
        PoiNetwork.buildInto(chunk);

        // 6c) 任务海域实例不单独挖洞穴：主航道隧道和两端站点空腔都由
        //     SeaTerrain / RoutePath 生成，站体直接挂在空腔顶板上。

        // 6d) 哨站：主站 + 本轮关卡两端的站（无人航路点两端不建站，和原作一致）。
        for (DockingBay.Site site : DockingBay.sitesNear(chunkPos)) {
            if (!MissionInstance.outpostExpected(site)) {
                continue;
            }
            if (DockingBay.Site.MAIN.equals(site)) {
                if (OutpostBuilder.intersectsCavern(chunkPos, site)) {
                    OutpostBuilder.carveCavern(chunk, site);
                }
            } else if (OutpostBuilder.intersectsWell(chunkPos, site)) {
                // 副本站只挖码头竖井，站体挂在压平的洞顶上。
                OutpostBuilder.carveDockWell(chunk, site);
            }
            if (OutpostBuilder.intersects(chunkPos, site)) {
                OutpostBuilder.build(chunk, site);
            }
        }

        // 7) 高度图
        Heightmap.primeHeightmaps(chunk,
                EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    /**
     * 在副本航线附近的岩壁/海底表面撒矿脉。
     *
     * <p>矿脉是普通方块，玩家把潜艇停在附近、穿潜水服出舱，用等离子切割机
     * 切碎后带回。位置完全由（世界种子, x, z）决定，同一存档重进不会变；
     * 生成时避开码头正下方，也不会替换掉站体/沉船（那些在 6b/6d 之后覆盖）。</p>
     *
     * <p>除了海底，还会在岩柱/浮岩岛的表面按散列撒少量矿脉，这样不用一路潜到
     * 500 格海底也能采矿。</p>
     */
    private static void placeVeins(ChunkAccess chunk, long seed, int originX, int originZ, int minY) {
        if (!MissionInstance.inMiningZone(originX + 8, originZ + 8)
                && !MissionInstance.inMiningZone(originX, originZ)
                && !MissionInstance.inMiningZone(originX + 15, originZ + 15)) {
            return;
        }
        // 关卡参数：海底/岩壁密度和盐只对本槽位的关卡生效，旧槽位保持原密度。
        int slot = Math.max(0, Math.round((originX - MissionInstance.BASE_X)
                / (float) MissionInstance.SPACING));
        LevelParams params = MissionInstance.paramsForSlot(slot);
        int floorMask = params == null ? 1023 : params.seafloorVeinMask();
        int wallMask = params == null ? 255 : params.wallVeinMask();
        long veinSeed = params == null ? seed
                : seed ^ MissionInstance.routeLevel().seed();
        // 环境矿脉：原作资源点间隔 96–192 格 × 0.2–0.3 概率；这里按区块门控，
        // 一个区块最多一簇，避免声纳上一扫全是矿。
        int gate = params == null ? 16 : params.ambientChunkGate();
        long chunkGate = veinHash(veinSeed, originX >> 4, originZ >> 4);
        if (Math.floorMod(chunkGate, gate) != 0) {
            return;
        }
        boolean[] placed = {false};
        BlockState[] veins = {
                com.opamb.barotrauma.registry.ModBlocks.QUARTZ_VEIN.get().defaultBlockState(),
                com.opamb.barotrauma.registry.ModBlocks.PYROMORPHITE_VEIN.get().defaultBlockState(),
                com.opamb.barotrauma.registry.ModBlocks.URANIUM_VEIN.get().defaultBlockState()
        };
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = originX + lx;
                int z = originZ + lz;
                if (!MissionInstance.inMiningZone(x, z)) {
                    continue;
                }
                long hash = veinHash(veinSeed, x, z);
                int floor = SeaTerrain.floorY(seed, x, z);
                int ceiling = SeaTerrain.ceilingY(seed, x, z);
                // 整列是岩（关卡隧道之外）：没有可采矿壁。
                if (ceiling <= floor) {
                    continue;
                }
                // 海底表面：密度由关卡 resourceSpawnChance 决定（basic 0.2 → 1/1024）。
                if (!placed[0] && (hash & floorMask) == 0L && floor > minY
                        && floor < ModDimensions.CEILING_BOTTOM_Y) {
                    BlockState vein = veins[(int) Math.floorMod(hash >>> 10, 3L)];
                    setBlock(chunk, x, floor, z, vein);
                    placed[0] = true;
                    // 一半概率向 x 方向再带一格，让矿脉看着是“一簇”而不是单点。
                    if (((hash >>> 11) & 1L) == 0L) {
                        int nx = lx < 15 ? lx + 1 : lx - 1;
                        if (nx >= 0) {
                            setBlock(chunk, originX + nx, floor, z, vein);
                        }
                    }
                }
                // 岩壁表面：每列抽 2 个深度，命中岩壁才长。
                if (ceiling - floor <= 8) {
                    continue;
                }
                for (int sample = 0; sample < 2 && !placed[0]; sample++) {
                    long sampleHash = veinHash(hash ^ (0x632BE59BD9B4E019L * (sample + 1L)), x, z);
                    if ((sampleHash & wallMask) != 0L) {
                        continue;
                    }
                    int y = floor + 3 + (int) Math.floorMod(sampleHash >>> 8,
                            Math.max(1, ceiling - floor - 6));
                    if (y <= minY || y >= ModDimensions.CEILING_BOTTOM_Y) {
                        continue;
                    }
                    if (!isRock(chunk, x, y, z) || !touchesAir(chunk, x, y, z, lx, lz)) {
                        continue;
                    }
                    setBlock(chunk, x, y, z, veins[(int) Math.floorMod(sampleHash >>> 20, 3L)]);
                    placed[0] = true;
                }
            }
        }
    }

    /** 世界生成阶段的岩层判断（矿脉只会替换这三种石头）。 */
    private static boolean isRock(ChunkAccess chunk, int x, int y, int z) {
        BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF);
    }

    /** 这一格岩壁是否贴着空气（只在同一区块内查，避免触发邻区块生成）。 */
    private static boolean touchesAir(ChunkAccess chunk, int x, int y, int z, int lx, int lz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                        continue;
                    }
                    int nx = lx + dx;
                    int nz = lz + dz;
                    if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
                        continue;
                    }
                    if (chunk.getBlockState(new BlockPos(x + dx, y + dy, z + dz)).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 矿脉位置/类型的确定性散列。 */
    private static long veinHash(long seed, int x, int z) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    private static void fillSection(LevelChunkSection section, BlockState state) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    section.setBlockState(x, y, z, state, false);
                }
            }
        }
    }

    private static void setBlock(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        int sectionIndex = (y - chunk.getMinBuildHeight()) >> 4;
        chunk.getSection(sectionIndex).setBlockState(x & 15, y & 15, z & 15, state, false);
    }

    private long worldSeed(RandomState randomState) {
        if (cachedWorldSeed == Long.MIN_VALUE) {
            RandomSource random = randomState
                    .getOrCreateRandomFactory(new ResourceLocation(BarotraumaMod.MOD_ID, "deep_sea"))
                    .fromHashOf("seed");
            cachedWorldSeed = random.nextLong();
        }
        return cachedWorldSeed;
    }

    // ------------------------------------------------------------------
    // ChunkGenerator 必须实现的部分
    // ------------------------------------------------------------------

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
                             ChunkAccess chunk) {
        // 地形在 fillFromNoise 里一次写完。
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        // 隧道由本生成器自己雕刻。
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 深海生物后续单独实现。
    }

    @Override
    public int getGenDepth() {
        return ModDimensions.HEIGHT;
    }

    @Override
    public int getMinY() {
        return ModDimensions.MIN_Y;
    }

    @Override
    public int getSeaLevel() {
        return ModDimensions.CEILING_BOTTOM_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return type == Heightmap.Types.OCEAN_FLOOR_WG
                ? ModDimensions.WATER_BOTTOM_Y
                : ModDimensions.CEILING_BOTTOM_Y + 15;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[ModDimensions.HEIGHT];
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < states.length; i++) {
            int y = ModDimensions.MIN_Y + i;
            states[i] = y >= ModDimensions.CEILING_BOTTOM_Y ? ice : stone;
        }
        return new NoiseColumn(ModDimensions.MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Barotrauma depth: " + ModDimensions.toMeters(pos.getY()) + " m");
    }
}
