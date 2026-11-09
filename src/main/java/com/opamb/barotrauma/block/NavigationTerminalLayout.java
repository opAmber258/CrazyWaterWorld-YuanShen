package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 导航终端 3 格宽 × 3 格高 × 1 格深多方块的纯几何（不碰 Block/注册表）。
 *
 * <p>单独抽出来是为了单元测试能直接跑——{@link NavigationTerminalBlock} 继承
 * {@code BaseEntityBlock}，类初始化会触发 {@code BuiltInRegistries} 引导，
 * 普通 JUnit 测试加载不了。</p>
 */
public final class NavigationTerminalLayout {

    /** 9 段：3 列 × 3 行（模型高 2.375 格，3 格高方块完整容纳）。 */
    public enum Part implements StringRepresentable {
        LOWER_LEFT(0, 0),
        LOWER_MIDDLE(1, 0),
        LOWER_RIGHT(2, 0),
        UPPER_LEFT(0, 1),
        UPPER_MIDDLE(1, 1),
        UPPER_RIGHT(2, 1),
        TOP_LEFT(0, 2),
        TOP_MIDDLE(1, 2),
        TOP_RIGHT(2, 2);

        private final int column;
        private final int row;

        Part(int column, int row) {
            this.column = column;
            this.row = row;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public int column() {
            return column;
        }

        public int row() {
            return row;
        }
    }

    private NavigationTerminalLayout() {
    }

    /** 主方块（下排中列）位置；任何一段都能反推。 */
    public static BlockPos masterPos(BlockPos pos, Direction facing, Part part) {
        Direction axis = facing.getClockWise();
        return pos.relative(axis, -(part.column() - 1)).below(part.row());
    }

    /** 某一段相对主方块的坐标。 */
    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        Direction axis = facing.getClockWise();
        return master.relative(axis, part.column() - 1).above(part.row());
    }

    /** 调试/测试用：主方块相对偏移表。 */
    public static Map<Part, BlockPos> footprint(BlockPos master, Direction facing) {
        Map<Part, BlockPos> out = new EnumMap<>(Part.class);
        for (Part part : Part.values()) {
            out.put(part, partPos(master, facing, part));
        }
        return out;
    }
}
