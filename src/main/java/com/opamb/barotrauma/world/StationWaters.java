package com.opamb.barotrauma.world;

import com.opamb.barotrauma.BarotraumaMod;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 开阔的独立站区。建筑贴着冰盖，艇下方留出整段水域。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID)
public final class StationWaters {
    private static final int HALF_WIDTH = 390;
    private static final int HALF_DEPTH = 208;
    private static final int MIGRATION_RADIUS = 8;

    private StationWaters() {}

    private record Column(int ceiling, int floor) {}

    private static Column column(DockingBay.Site station, long seed, int x, int z) {
        double dx = x - station.x();
        double dz = z - station.z();
        double margin = Math.min(HALF_WIDTH - Math.abs(dx), HALF_DEPTH - Math.abs(dz));
        if (margin <= 0) return new Column(0, 0);
        double edge = SeaTerrain.smooth(margin / 68.0D);
        double outsideStation = SeaTerrain.smooth((Math.hypot(dx, dz) - 38.0D) / 75.0D);
        double roof = -44 + 21 * outsideStation * DeepSeaNoise.fbm2(seed ^ 0x4CE1L, x / 74.0, z / 74.0, 3);
        double floor = -225 + 34 * DeepSeaNoise.fbm2(seed ^ 0x7465L, x / 100.0, z / 100.0, 3);
        double middle = (roof + floor) * 0.5D;
        return new Column(station.y((int) Math.round(middle + (roof - middle) * edge)),
                station.y((int) Math.round(middle + (floor - middle) * edge)));
    }

    static void carve(ChunkAccess chunk, DockingBay.Site station, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                Column water = column(station, seed, x, z);
                for (int y = water.floor() + 1; y <= water.ceiling(); y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void repairNearbyStation(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.THE_DEEP)) return;
        for (var player : level.players()) {
            if (CampaignLevels.regionStation(player.getBlockX(), player.getBlockZ()) != null
                    && repairNextChunk(level, player.chunkPosition())) return;
        }
    }

    private static boolean repairNextChunk(ServerLevel level, ChunkPos center) {
        var repaired = level.getDataStorage().computeIfAbsent(Repaired::load, Repaired::new, "barotrauma_station_waters");
        // 优先修最近的已加载区块。每 tick 最多一块，且每块只迁移一次。
        for (int radius = 0; radius <= MIGRATION_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                    if (chunk != null && !repaired.chunks.contains(chunk.getPos().toLong())
                            && repairLegacyChunk(level, chunk)) {
                        repaired.chunks.add(chunk.getPos().toLong());
                        repaired.setDirty();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 旧站区只打开原先留成实心的天然岩层，保留原水池和人工设施。 */
    public static boolean repairLegacyChunk(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        var station = CampaignLevels.regionStation(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
        if (station == null) return false;
        long seed = ((DeepSeaChunkGenerator) level.getChunkSource().getGenerator()).terrainSeed(level);
        // y=-132 落在旧椭球以下、全新水池内。只有该层五个样点全是旧岩层才做迁移。
        int probeY = station.y(-132);
        for (int[] sample : new int[][]{{2, 2}, {13, 2}, {8, 8}, {2, 13}, {13, 13}}) {
            int x = chunkPos.getBlockX(sample[0]);
            int z = chunkPos.getBlockZ(sample[1]);
            Column water = column(station, seed, x, z);
            if (probeY <= water.floor() || probeY > water.ceiling()
                    || !chunk.getBlockState(new BlockPos(x, probeY, z)).is(Blocks.STONE)) return true;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                Column water = column(station, seed, x, z);
                double dx = (x - station.x()) / 34.0D;
                double dz = (z - station.z()) / 34.0D;
                double oldDistance = dx * dx + dz * dz;
                for (int y = water.floor() + 1; y <= water.ceiling(); y++) {
                    double oldHeight = (y - station.y(-72)) / 26.0D;
                    if (oldDistance + oldHeight * oldHeight < 1.0D) continue;
                    pos.set(x, y, z);
                    var rock = chunk.getBlockState(pos);
                    if (rock.is(Blocks.STONE)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }

    private static final class Repaired extends SavedData {
        private final LongOpenHashSet chunks = new LongOpenHashSet();

        static Repaired load(CompoundTag tag) {
            Repaired repaired = new Repaired();
            for (long chunk : tag.getLongArray("Chunks")) repaired.chunks.add(chunk);
            return repaired;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("Chunks", chunks.toLongArray());
            return tag;
        }
    }
}
