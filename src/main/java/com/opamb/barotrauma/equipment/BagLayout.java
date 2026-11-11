package com.opamb.barotrauma.equipment;

/**
 * 工具带/背包在背包界面里的格子布局。
 *
 * <p>**零 MC 依赖**，纯数学，可直接单测。格子画在原版 3×9 主背包区域
 * （GUI 局部坐标 x 8..152、y 84..138）：背包 4 列×3 行，工具带 3 列×2 行，居中。</p>
 */
public final class BagLayout {

    /** 工具带/背包最多提供的格子数（背包 12）。 */
    public static final int MAX_SLOTS = 12;
    /** 单个格子的像素间距（含 1px 间隔，和原版槽位一致）。 */
    public static final int SLOT_SIZE = 18;

    public static final int AREA_X = 8;
    public static final int AREA_Y = 84;
    /** 原版主背包 9 列 × 18px = 160px（两端的 1px 边框另算）。 */
    public static final int AREA_WIDTH = 160;
    public static final int AREA_HEIGHT = 54;

    /** 创造界面"物品栏"标签页的主背包区域（槽位从 (9,54) 开始，同样 160×54）。 */
    public static final int CREATIVE_AREA_X = 9;
    public static final int CREATIVE_AREA_Y = 54;

    /** 一次布局：容量、列/行数、左上角（GUI 局部坐标）。 */
    public record Grid(int capacity, int columns, int rows, int originX, int originY) {

        public int width() {
            return columns * SLOT_SIZE;
        }

        public int height() {
            return rows * SLOT_SIZE;
        }

        public int slotX(int index) {
            return originX + (index % columns) * SLOT_SIZE;
        }

        public int slotY(int index) {
            return originY + (index / columns) * SLOT_SIZE;
        }
    }

    private BagLayout() {
    }

    /**
     * 按容量算布局。0 = 没装备背包（返回空布局）；6 = 工具带 3×2；12 = 背包 4×3。
     * 其它容量按“最多 4 列、尽量方”排。
     */
    public static Grid forCapacity(int capacity) {
        return forCapacityIn(capacity, AREA_X, AREA_Y, AREA_WIDTH, AREA_HEIGHT);
    }

    /** 指定区域版本（创造界面的主背包区域比生存界面高 30px）。 */
    public static Grid forCapacityIn(int capacity, int areaX, int areaY, int areaWidth, int areaHeight) {
        int cap = Math.max(0, Math.min(MAX_SLOTS, capacity));
        if (cap == 0) {
            return new Grid(0, 0, 0, areaX, areaY);
        }
        int columns;
        if (cap <= 3) {
            columns = cap;
        } else if (cap <= 6) {
            columns = 3;
        } else {
            columns = 4;
        }
        int rows = (cap + columns - 1) / columns;
        int width = columns * SLOT_SIZE;
        int height = rows * SLOT_SIZE;
        int originX = areaX + (areaWidth - width) / 2;
        int originY = areaY + (areaHeight - height) / 2;
        return new Grid(cap, columns, rows, originX, originY);
    }
}
