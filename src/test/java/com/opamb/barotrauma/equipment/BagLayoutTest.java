package com.opamb.barotrauma.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备背包的格子布局（纯数学）。
 *
 * <p>背包 12 格 = 4×3，工具带 6 格 = 3×2，都居中在原版 3×9 主背包区域
 * （x 8..152、y 84..138）；没装备背包时容量 0。</p>
 */
class BagLayoutTest {

    @Test
    void 工具带是3列2行且居中() {
        BagLayout.Grid grid = BagLayout.forCapacity(6);
        assertEquals(6, grid.capacity());
        assertEquals(3, grid.columns());
        assertEquals(2, grid.rows());
        assertEquals(54, grid.width());
        assertEquals(36, grid.height());
        // 水平居中：8 + (144-54)/2 = 53；垂直居中：84 + (54-36)/2 = 93
        assertEquals(61, grid.originX());
        assertEquals(93, grid.originY());
    }

    @Test
    void 背包是4列3行且居中() {
        BagLayout.Grid grid = BagLayout.forCapacity(12);
        assertEquals(12, grid.capacity());
        assertEquals(4, grid.columns());
        assertEquals(3, grid.rows());
        assertEquals(52, grid.originX());
        assertEquals(84, grid.originY());
    }

    @Test
    void 没装备背包时没有格子() {
        BagLayout.Grid grid = BagLayout.forCapacity(0);
        assertEquals(0, grid.capacity());
        assertEquals(0, grid.columns());
        assertEquals(0, grid.rows());
    }

    @Test
    void 格子坐标按行列展开() {
        BagLayout.Grid backpack = BagLayout.forCapacity(12);
        assertEquals(52, backpack.slotX(0));
        assertEquals(84, backpack.slotY(0));
        assertEquals(70, backpack.slotX(1));
        assertEquals(52, backpack.slotX(4));
        assertEquals(102, backpack.slotY(4));
        assertEquals(106, backpack.slotX(11));
        assertEquals(120, backpack.slotY(11));

        BagLayout.Grid belt = BagLayout.forCapacity(6);
        assertEquals(61, belt.slotX(0));
        assertEquals(93, belt.slotY(0));
        assertEquals(79, belt.slotX(1));
        assertEquals(61, belt.slotX(3));
        assertEquals(111, belt.slotY(3));
    }

    @Test
    void 所有格子都落在主背包区域内() {
        for (int capacity : new int[]{6, 12}) {
            BagLayout.Grid grid = BagLayout.forCapacity(capacity);
            assertTrue(grid.originX() >= BagLayout.AREA_X);
            assertTrue(grid.originY() >= BagLayout.AREA_Y);
            assertTrue(grid.originX() + grid.width() <= BagLayout.AREA_X + BagLayout.AREA_WIDTH);
            assertTrue(grid.originY() + grid.height() <= BagLayout.AREA_Y + BagLayout.AREA_HEIGHT);
        }
    }
}
