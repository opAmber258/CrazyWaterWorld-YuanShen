package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 船厂地块：每个玩家一块固定坐标的虚空平台。
 *
 * <p>首版提供空白与平台两种起始布局；模板布局在船库接入后由
 * {@link ShipyardStructureIO} 放置。</p>
 */
public final class ShipyardPlot {

    public enum Preset {
        BLANK,
        PLATFORM
    }

    private static final int CLEAR_RADIUS = 40;
    private static final int CLEAR_HEIGHT = 24;
    private static final int PLATFORM_HALF = 32;

    private ShipyardPlot() {
    }

    public static BlockPos center(ShipyardSavedData.Plot plot) {
        return new BlockPos(plot.originX(), plot.originY(), plot.originZ());
    }

    public static void prepare(ServerLevel level, ShipyardSavedData.Plot plot, Preset preset) {
        BlockPos center = center(plot);
        // 新地块在虚空里本来就是空气，不需要逐格清空；只预载平台覆盖的区块。
        int half = preset == Preset.PLATFORM ? PLATFORM_HALF : 4;
        loadChunks(level, center, half + 1);
        int y = center.getY();
        // 平台本身用原版石头，不会被船体扫描收走。
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean border = preset == Preset.PLATFORM
                        && (Math.abs(x) == half || Math.abs(z) == half);
                Block block = border ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
                set(level, center.offset(x, -1, z), block.defaultBlockState());
            }
        }
        // 5×5 船体板 + 声纳核心，给编辑器一个可立即扫描的起点。
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                set(level, center.offset(x, 0, z),
                        ModBlocks.SUBMARINE_PLATING.get().defaultBlockState());
            }
        }
        set(level, center.offset(0, 1, 0), ModShipBlocks.SONAR.get().defaultBlockState());
        set(level, center.offset(0, 2, 0), Blocks.AIR.defaultBlockState());
    }

    private static void clear(ServerLevel level, BlockPos center) {
        for (int x = -CLEAR_RADIUS; x <= CLEAR_RADIUS; x++) {
            for (int z = -CLEAR_RADIUS; z <= CLEAR_RADIUS; z++) {
                for (int y = -2; y <= CLEAR_HEIGHT; y++) {
                    set(level, center.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void loadChunks(ServerLevel level, BlockPos center, int radius) {
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunkAt(new BlockPos(cx << 4, center.getY(), cz << 4));
            }
        }
    }

    private static void set(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    public static int plotIndexAt(int x) {
        return Math.floorDiv(x - ModDimensions.SHIPYARD_ORIGIN_X, ModDimensions.SHIPYARD_PLOT_SPACING);
    }
}
