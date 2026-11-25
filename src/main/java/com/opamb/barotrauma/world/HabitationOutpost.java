package com.opamb.barotrauma.world;

import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/** 寒冷深穴的小型居民站。各舱的重叠处自然连通，气闸沿用标准接头。 */
final class HabitationOutpost {
    private static final int HALF_X = 23;
    private static final int MIN_Z = -7;
    private static final int MAX_Z = 15;
    private static final int CREW_DECK = -55;
    private static final int LADDER_X = -3;
    private static final int LADDER_Z = 2;

    private record Module(int minX, int maxX, int lower, int upper, int minZ, int maxZ) {
        boolean contains(int x, int y, int z, int wall) {
            return x >= minX + wall && x <= maxX - wall && y >= lower + wall && y <= upper - wall
                    && z >= minZ + wall && z <= maxZ - wall;
        }
    }

    private static final Module DOCK_LOBBY = new Module(-8, 8, -64, -52, -6, 6);
    private static final Module CONCOURSE = new Module(-12, 12, -64, -57, -3, 3);
    private static final Module MARKET = new Module(-23, -10, -64, -53, -7, 7);
    private static final Module CLINIC = new Module(10, 23, -64, -54, -7, 7);
    private static final Module ENGINEERING = new Module(-7, 7, -64, -54, 4, 15);
    private static final Module CREW = new Module(-12, 8, CREW_DECK, -44, -6, 6);
    private static final List<Module> MODULES =
            List.of(DOCK_LOBBY, CONCOURSE, MARKET, CLINIC, ENGINEERING, CREW);

    private HabitationOutpost() {
    }

    static boolean intersects(ChunkPos chunk, DockingBay.Site site) {
        return chunk.getMaxBlockX() >= site.x() - HALF_X && chunk.getMinBlockX() <= site.x() + HALF_X
                && chunk.getMaxBlockZ() >= site.z() + MIN_Z && chunk.getMinBlockZ() <= site.z() + MAX_Z;
    }

    /** 干燥边界用实际舱室的并集，伸出的房间之间仍是外海。 */
    static boolean inside(int x, int y, int z) {
        for (Module module : MODULES) {
            if (module.contains(x, y, z, 1)) {
                return true;
            }
        }
        return false;
    }

    static void build(ChunkAccess chunk, DockingBay.Site site) {
        ChunkPos pos = chunk.getPos();
        int x0 = Math.max(-HALF_X, pos.getMinBlockX() - site.x());
        int x1 = Math.min(HALF_X, pos.getMaxBlockX() - site.x());
        int z0 = Math.max(MIN_Z, pos.getMinBlockZ() - site.z());
        int z1 = Math.min(MAX_Z, pos.getMaxBlockZ() - site.z());
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = OutpostBuilder.MIN_Y; y <= OutpostBuilder.MAX_Y; y++) {
                    BlockState state = stateAt(x, y, z);
                    if (state != null) {
                        OutpostBuilder.setWithBe(chunk, site.at(x, y, z), state);
                    }
                }
            }
        }
        DockingBay.buildStation(chunk, site);
    }

    private static BlockState stateAt(int x, int y, int z) {
        if (x == LADDER_X && z == LADDER_Z && y >= -62 && y <= CREW_DECK + 1) {
            return ModBlocks.IRON_LADDER.get().defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.EAST);
        }
        if (y == CREW_DECK && CREW.contains(x, y, z, 0)) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (inside(x, y, z)) {
            if (y == -63 || y == CREW_DECK + 1) {
                return (z == 0 ? Blocks.YELLOW_TERRACOTTA : Blocks.SMOOTH_STONE).defaultBlockState();
            }
            if (y == -62 && z == -5 && x <= -14 && (x & 1) == 0) {
                return Blocks.BARREL.defaultBlockState();
            }
            if (y == -62 && x >= 14 && z == -5) {
                return Blocks.QUARTZ_SLAB.defaultBlockState();
            }
            if (y == -62 && z == 13 && Math.abs(x) < 5) {
                return (x % 3 == 0 ? Blocks.BLAST_FURNACE : Blocks.IRON_BLOCK).defaultBlockState();
            }
            if ((y == -58 || y == -46) && z == 0 && Math.floorMod(x, 5) == 0) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }
        for (Module module : MODULES) {
            if (!module.contains(x, y, z, 0)) {
                continue;
            }
            if (y >= -60 && y <= -58 && z == -7 && Math.abs(x) >= 13 && Math.abs(x) <= 21) {
                return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            }
            if (y == module.lower() || y == module.upper()) {
                return ModShipBlocks.STATION_PLATING.get().defaultBlockState();
            }
            return (y == module.lower() + 1 || y == module.upper() - 1
                    ? Blocks.POLISHED_DEEPSLATE : ModShipBlocks.STATION_PLATING.get()).defaultBlockState();
        }
        return null;
    }
}
