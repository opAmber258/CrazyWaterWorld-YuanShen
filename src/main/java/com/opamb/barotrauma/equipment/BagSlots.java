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

    private static final int OFFSCREEN = -10000;

    private BagSlots() {
    }

    /** 菜单构造时安装（服务端/客户端都跑）。 */
    public static void install(AbstractContainerMenu menu, Player owner) {
        BagContainer container = BagManager.container(owner);
        int capacity = BagManager.capacity(owner);
        BagLayout.Grid grid = BagLayout.forCapacity(capacity);
        for (int i = 0; i < BagLayout.MAX_SLOTS; i++) {
            int menuIndex = START + i;
            int x = i < capacity ? grid.slotX(i) : OFFSCREEN;
            int y = i < capacity ? grid.slotY(i) : OFFSCREEN;
            BagSlot slot = new BagSlot(container, i, x, y, () -> BagManager.capacity(owner), capacity);
            menu.slots.set(menuIndex, slot);
            slot.index = menuIndex;
        }
        SimpleContainer dummy = new SimpleContainer(1);
        for (int i = DISABLED_START; i < DISABLED_END; i++) {
            DisabledSlot slot = new DisabledSlot(dummy, 0, OFFSCREEN - i, OFFSCREEN);
            menu.slots.set(i, slot);
            slot.index = i;
        }
    }

    /** 客户端：装备的背包换了（容量变化）就重建 9..20 的位置。 */
    public static void rebuildClient(AbstractContainerMenu menu, Player owner) {
        int capacity = BagManager.capacity(owner);
        if (!(menu.slots.get(START) instanceof BagSlot first) || first.layoutCapacity() == capacity) {
            return;
        }
        BagContainer container = BagManager.container(owner);
        BagLayout.Grid grid = BagLayout.forCapacity(capacity);
        for (int i = 0; i < BagLayout.MAX_SLOTS; i++) {
            int menuIndex = START + i;
            int x = i < capacity ? grid.slotX(i) : OFFSCREEN;
            int y = i < capacity ? grid.slotY(i) : OFFSCREEN;
            BagSlot slot = new BagSlot(container, i, x, y, () -> BagManager.capacity(owner), capacity);
            menu.slots.set(menuIndex, slot);
            slot.index = menuIndex;
        }
    }
}
