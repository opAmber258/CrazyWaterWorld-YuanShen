package com.opamb.barotrauma.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.opamb.barotrauma.BarotraumaMod;
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
 * <p>形状（对照原作 Cold Caverns 关卡参数：关卡高度远大于隧道直径）：</p>
 * <ul>
 *   <li><b>顶部岩壳</b>：压平在 y=-44（哨站屋顶严丝合缝），向噪声深处最厚到 y=-240；</li>
 *   <li><b>底部岩床</b>：基准 y=-1000，噪声抬升最高约 180 格；</li>
 *   <li><b>开阔水域</b>：夹在两者之间，最高接近 1000 格；</li>
 *   <li><b>岩柱 / 钟乳石 / 浮岩岛 / 隔断岩墙</b>：都从 2D 噪声长出来，潜艇要绕行；</li>
 *   <li><b>哨站台地</b>：哨站下方压出一块 -98 的岩台当水池底，外圈 140 格渐变到世界岩床；</li>
 *   <li><b>POI 净空</b>：沉船/中继站周围强制留空，不会被岩柱埋掉。</li>
 * </ul>
 *
 * <p>上一版的"每 120 格一层岩板 + TrunkNetwork 竖井"已按用户要求撤掉：那些岩板把海域切成
 * 一层层，竖井又像电梯井。现在上下移动靠岩床起伏和天然开口，不再有竖直井道。</p>
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

        // 3) 开阔海域：上岩壳、下岩床，中间整片开放（最高接近 1000 格），
        //    水里再长岩柱/钟乳石/浮岩岛/隔断岩墙。没有分层岩板，也没有竖井。
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

                boolean clear = SeaTerrain.inClearZone(x, z);
                boolean wall = DeepSeaNoise.fbm2(seed ^ SALT_WALL, x / 110.0D, z / 110.0D, 3) > 0.58D;
                double pillarNoise = DeepSeaNoise.fbm2(seed ^ SALT_PILLAR, x / 48.0D, z / 48.0D, 2);
                double hangNoise = DeepSeaNoise.fbm2(seed ^ SALT_HANG, x / 46.0D, z / 46.0D, 2);
                double islandNoise = DeepSeaNoise.fbm2(seed ^ SALT_ISLAND, x / 130.0D, z / 130.0D, 2);
                double islandYNoise = DeepSeaNoise.fbm2(seed ^ SALT_ISLAND_Y, x / 70.0D, z / 70.0D, 2);

                // 岩柱/钟乳石只长到水域高度的 18%：用户反馈三成仍然太高。
                int pillarTop = pillarNoise > 0.34D
                        ? floor + (int) ((pillarNoise - 0.34D) * open * 0.18D) : floor;
                int hangBottom = hangNoise > 0.34D
                        ? ceiling - (int) ((hangNoise - 0.34D) * open * 0.18D) : ceiling;
                int islandBottom = Integer.MIN_VALUE;
                int islandTop = Integer.MIN_VALUE;
                if (islandNoise > 0.55D) {
                    int centerY = floor + (int) ((0.5D + islandYNoise * 0.30D) * open);
                    int half = (int) ((islandNoise - 0.55D) * 24.0D) + 2;
                    islandBottom = centerY - half;
                    islandTop = centerY + half;
                }

                for (int y = floor + 1; y < ceiling; y++) {
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

        // 6b) 任务 POI：沉船/侦察点/B 中继站（确定性，幂等）
        PoiNetwork.buildInto(chunk);

        // 6c) 任务海域实例不再单独挖洞穴：副本站周围直接复用主世界开阔海域地形
        //     （SeaTerrain 会在锚点附近压平洞顶并留出 64 格机动净空）。

        // 6d) 哨站：主站 + 每个任务副本顶部各一座。
        for (DockingBay.Site site : DockingBay.sitesNear(chunkPos)) {
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
