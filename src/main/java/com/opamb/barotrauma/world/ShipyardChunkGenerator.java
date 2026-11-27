package com.opamb.barotrauma.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 造船厂/试航维度使用的极简生成器：默认虚空，可选在底部铺一层石头。
 *
 * <p>船厂地块、平台、蓝图都由代码放置，不靠世界生成；这样不会把平台材料
 * 混进结构模板。{@code floor_y} 缺省时整块维度保持空气。</p>
 */
public class ShipyardChunkGenerator extends ChunkGenerator {

    public static final Codec<ShipyardChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    Codec.INT.optionalFieldOf("min_y", ModDimensions.SHIPYARD_MIN_Y)
                            .forGetter(generator -> generator.minY),
                    Codec.INT.optionalFieldOf("height", ModDimensions.SHIPYARD_HEIGHT)
                            .forGetter(generator -> generator.height),
                    Codec.INT.optionalFieldOf("floor_y", Integer.MIN_VALUE)
                            .forGetter(generator -> generator.floorY),
                    Codec.INT.optionalFieldOf("water_top_y", Integer.MIN_VALUE)
                            .forGetter(generator -> generator.waterTopY)
            ).apply(instance, ShipyardChunkGenerator::new));

    private final int minY;
    private final int height;
    /** 最低石头层的 y；{@link Integer#MIN_VALUE} 表示虚空。 */
    private final int floorY;
    /** 海水最高 y；{@link Integer#MIN_VALUE} 表示不生成水。 */
    private final int waterTopY;

    public ShipyardChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, ModDimensions.SHIPYARD_MIN_Y, ModDimensions.SHIPYARD_HEIGHT,
                Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public ShipyardChunkGenerator(BiomeSource biomeSource, int minY, int height, int floorY) {
        this(biomeSource, minY, height, floorY, Integer.MIN_VALUE);
    }

    public ShipyardChunkGenerator(BiomeSource biomeSource, int minY, int height, int floorY,
                                  int waterTopY) {
        super(biomeSource);
        this.minY = minY;
        this.height = height;
        this.floorY = floorY;
        this.waterTopY = waterTopY;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
                                                        RandomState randomState, StructureManager structureManager,
                                                        ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            int originX = chunk.getPos().getMinBlockX();
            int originZ = chunk.getPos().getMinBlockZ();
            // floorY == minY 也要铺满最低层；Integer.MIN_VALUE（虚空）< minY，仍然跳过。
            if (floorY >= minY) {
                BlockState stone = Blocks.STONE.defaultBlockState();
                int top = Math.min(floorY, chunk.getMaxBuildHeight() - 1);
                for (int y = chunk.getMinBuildHeight(); y <= top; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            chunk.setBlockState(new BlockPos(originX + x, y, originZ + z), stone, false);
                        }
                    }
                }
            }
            // 试航维度需要真实水体：VS2 浮力/进水/压力都依赖水方块。
            if (waterTopY >= minY) {
                BlockState water = Blocks.WATER.defaultBlockState();
                int bottom = Math.max(chunk.getMinBuildHeight(),
                        floorY >= minY ? floorY + 1 : chunk.getMinBuildHeight());
                int top = Math.min(waterTopY, chunk.getMaxBuildHeight() - 1);
                for (int y = bottom; y <= top; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            chunk.setBlockState(new BlockPos(originX + x, y, originZ + z), water, false);
                        }
                    }
                }
            }
            return chunk;
        }, executor);
    }

    @Override
    public void buildSurface(net.minecraft.server.level.WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        // 地表在 fillFromNoise 里一次铺好，这里不需要再做表层规则。
    }

    @Override
    public void applyCarvers(net.minecraft.server.level.WorldGenRegion region, long seed,
                             RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk,
                             GenerationStep.Carving carving) {
        // 船厂/试航不做洞穴雕刻。
    }

    @Override
    public void spawnOriginalMobs(net.minecraft.server.level.WorldGenRegion region) {
        // 船厂/试航不生成生物。
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getSeaLevel() {
        return waterTopY >= minY ? waterTopY : minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
                             RandomState randomState) {
        // 和 getBaseColumn/fillFromNoise 保持一致：有水时以水面为地表高度。
        if (waterTopY >= minY) {
            return waterTopY + 1;
        }
        return floorY >= minY ? floorY + 1 : minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[height];
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            states[i] = floorY >= y ? stone
                    : waterTopY >= y && waterTopY >= minY ? water : air;
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
    }
}
