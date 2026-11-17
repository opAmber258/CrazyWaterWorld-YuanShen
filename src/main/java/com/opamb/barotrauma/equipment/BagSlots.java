package com.opamb.barotrauma.equipment;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 把菜单里的 9..35（原版 27 格主背包）换成"装备背包 + 禁用占位"。
 *
 * <p>9..20 = {@link BagSlot}（容量 0/6/12 动态生效），21..35 = {@link DisabledSlot}。
 * 槽位下标保持不变，所以 36..45 热键栏/副手和 46 潜水服槽都不会错位。</p>
 *
 * <p>{@link net.minecraft.world.inventory.Slot#x}/{@code y} 是 final，容量变化时
 * 只能重建 Slot 对象；服务端不需要位置（只用到 mayPlace/isActive），
 * 客户端在渲染前调 {@link #rebuildClient}。</p>
 */
public final class BagSlots {

    public static final int START = 9;
    public static final int DISABLED_START = 21;
    public static final int DISABLED_END = 36;
    /** 27 格里装备背包最多 12 个，剩下 15 个全部禁用。 */
    public static final int DISABLED_COUNT = 27 - BagLayout.MAX_SLOTS;

    private static final int OFFSCREEN = -10000;

    private BagSlots() {
    }

    /** 菜单构造时安装（服务端/客户端都跑）。 */
    public static void install(AbstractContainerMenu menu, Player owner) {
        installAt(menu, owner, START, BagLayout.AREA_X, BagLayout.AREA_Y,
                BagLayout.AREA_WIDTH, BagLayout.AREA_HEIGHT);
    }

    /**
     * 任意菜单版本：把 {@code [start, start+27)} 这 27 个"主物品栏"格换成
     * 装备背包 + 禁用占位，格子按给定区域居中。
     *
     * <p>容器界面（箱子、机器）也一样处理，这样它们左下的 3×9 区域和玩家
     * 物品栏看起来完全一致，不会再额外多画一块背包面板。</p>
     */
    public static void installAt(AbstractContainerMenu menu, Player owner, int start,
                                 int areaX, int areaY, int areaWidth, int areaHeight) {
        BagContainer container = BagManager.container(owner);
        int capacity = BagManager.capacity(owner);
        BagLayout.Grid grid = BagLayout.forCapacityIn(capacity, areaX, areaY, areaWidth, areaHeight);
        for (int i = 0; i < BagLayout.MAX_SLOTS; i++) {
            int menuIndex = start + i;
            int x = i < capacity ? grid.slotX(i) : OFFSCREEN;
            int y = i < capacity ? grid.slotY(i) : OFFSCREEN;
            BagSlot slot = new BagSlot(container, i, x, y, () -> BagManager.capacity(owner), capacity);
            menu.slots.set(menuIndex, slot);
            slot.index = menuIndex;
        }
        SimpleContainer dummy = new SimpleContainer(1);
        for (int i = BagLayout.MAX_SLOTS; i < BagLayout.MAX_SLOTS + DISABLED_COUNT; i++) {
            int menuIndex = start + i;
            DisabledSlot slot = new DisabledSlot(dummy, 0, OFFSCREEN - menuIndex, OFFSCREEN);
            menu.slots.set(menuIndex, slot);
            slot.index = menuIndex;
        }
    }

    /** 客户端：装备的背包换了（容量变化）就重建 9..20 的位置。 */
    public static void rebuildClient(AbstractContainerMenu menu, Player owner) {
        rebuildClientAt(menu, owner, START, BagLayout.AREA_X, BagLayout.AREA_Y,
                BagLayout.AREA_WIDTH, BagLayout.AREA_HEIGHT);
    }

    /** 任意菜单版本的容量变化重建（只在容量不同的时候重建，不每帧 new）。 */
    public static void rebuildClientAt(AbstractContainerMenu menu, Player owner, int start,
                                       int areaX, int areaY, int areaWidth, int areaHeight) {
        int capacity = BagManager.capacity(owner);
        if (start < 0 || start >= menu.slots.size()
                || !(menu.slots.get(start) instanceof BagSlot first)
                || first.layoutCapacity() == capacity) {
            return;
        }
        BagContainer container = BagManager.container(owner);
        BagLayout.Grid grid = BagLayout.forCapacityIn(capacity, areaX, areaY, areaWidth, areaHeight);
        for (int i = 0; i < BagLayout.MAX_SLOTS; i++) {
            int menuIndex = start + i;
            int x = i < capacity ? grid.slotX(i) : OFFSCREEN;
            int y = i < capacity ? grid.slotY(i) : OFFSCREEN;
            BagSlot slot = new BagSlot(container, i, x, y, () -> BagManager.capacity(owner), capacity);
            menu.slots.set(menuIndex, slot);
            slot.index = menuIndex;
        }
    }
}
