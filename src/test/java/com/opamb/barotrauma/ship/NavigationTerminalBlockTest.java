package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.block.NavigationTerminalLayout;
import com.opamb.barotrauma.block.NavigationTerminalLayout.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导航终端多方块的几何：9 段互不重叠、3 宽 × 3 高 × 1 深、
 * 任意一段都能反推出主方块（放在下排中列）。
 */
class NavigationTerminalBlockTest {

    @Test
    @DisplayName("9 段占满 3×3×1 且互不重叠")
    void footprintIsThreeByThreeByOne() {
        BlockPos master = new BlockPos(10, 64, 10);
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            Set<BlockPos> seen = new HashSet<>();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (Part part : Part.values()) {
                BlockPos pos = NavigationTerminalLayout.partPos(master, facing, part);
                assertEquals(true, seen.add(pos), "段位置重复: " + part);
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            assertEquals(9, seen.size());
            int spanX = maxX - minX + 1;
            int spanY = maxY - minY + 1;
            int spanZ = maxZ - minZ + 1;
            // 固定 3 格高；水平方向一个 3 格宽、一个 1 格深（宽轴随朝向旋转）
            assertEquals(3, spanY, "facing=" + facing);
            assertTrue((spanX == 3 && spanZ == 1) || (spanX == 1 && spanZ == 3),
                    "facing=" + facing + " spanX=" + spanX + " spanZ=" + spanZ);
        }
    }

    @Test
    @DisplayName("任意一段都能反推出同一个主方块")
    void everyPartResolvesBackToMaster() {
        BlockPos master = new BlockPos(-3, 70, 5);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Part part : Part.values()) {
                BlockPos pos = NavigationTerminalLayout.partPos(master, facing, part);
                assertEquals(master, NavigationTerminalLayout.masterPos(pos, facing, part),
                        "facing=" + facing + " part=" + part);
            }
        }
    }

    @Test
    @DisplayName("主方块就是下排中列")
    void masterIsLowerMiddle() {
        BlockPos master = new BlockPos(0, 50, 0);
        assertEquals(master, NavigationTerminalLayout.partPos(master, Direction.NORTH, Part.LOWER_MIDDLE));
        assertEquals(master, NavigationTerminalLayout.masterPos(master, Direction.NORTH, Part.LOWER_MIDDLE));
        // 上排中列在主方块正上方一格，顶层中列在正上方两格
        assertEquals(master.above(1),
                NavigationTerminalLayout.partPos(master, Direction.NORTH, Part.UPPER_MIDDLE));
        assertEquals(master.above(2),
                NavigationTerminalLayout.partPos(master, Direction.NORTH, Part.TOP_MIDDLE));
    }
}
