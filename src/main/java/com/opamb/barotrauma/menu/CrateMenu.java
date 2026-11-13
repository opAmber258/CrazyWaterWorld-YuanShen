package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.equipment.BagContainer;
import com.opamb.barotrauma.equipment.BagLayout;
import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.BagSlot;
import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.EquipmentData;
import com.opamb.barotrauma.equipment.EquipmentInventory;
import com.opamb.barotrauma.equipment.EquipmentSlot;
import com.opamb.barotrauma.equipment.EquipmentSlotType;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.item.CrateItem;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * 货运箱的容器界面（原作点容器弹出的那个窗口）。
 *
 * <p>布局按潜渊症：左边是玩家物品栏（和按 E 打开的一样：装备槽 + 装备背包 +
 * 双手格 + 热键栏 + 人物预览，但没有合成栏/合成书），右边是箱子内部。
 * 箱子格子照原作 {@code ItemContainer.SlotsPerRow}：**每行 5 格**，
 * 最后一行不满时水平居中（对应 {@code Inventory.CreateSlots} 的排法）。</p>
 *
 * <p>容器绑定的是背包菜单里某个槽位上的箱子物品：服务端由
 * {@link #open(ServerPlayer, int)} 校验后开界面，客户端按同一下标从
 * {@code player.inventoryMenu} 取同一件物品，因此两边看到的是同一份 NBT。
 * 箱子被移走/替换后 {@link #stillValid} 返回 false，界面自动关掉。</p>
 */
public class CrateMenu extends AbstractContainerMenu {

    /** 左侧玩家区域宽度（和原版背包界面一致）。 */
    public static final int PLAYER_WIDTH = 176;
    public static final int WINDOW_HEIGHT = 166;
    /**
     * 右侧箱子面板。格子照原作容器窗口（{@code Inventory.UpdateSubInventory}）：
     * 列数 = {@code clamp(floor(sqrt(capacity)), 1, SlotsPerRow)}，行左对齐、
     * 整块以锚点为中心。所以 20 格是 **4 列 × 5 行**，12/11 格是 3 列。
     * 格子用原版 18px 小格（紧贴，无间隙）。
     */
    public static final int CRATE_PANEL_X = 184;
    public static final int CRATE_CELL = 18;
    public static final int CRATE_PITCH = CRATE_CELL;
    public static final int CRATE_TOP = 26;
    /** 原作 {@code SlotsPerRow} 默认值（列数上限）。 */
    public static final int SLOTS_PER_ROW = 5;

    /** 原作容器窗口的列数：sqrt(capacity) 取整，夹在 1..SlotsPerRow。 */
    public static int columnsFor(int capacity) {
        if (capacity <= 0) {
            return 1;
        }
        int columns = (int) Math.floor(Math.sqrt(capacity));
        return Math.max(1, Math.min(SLOTS_PER_ROW, columns));
    }

    /** 箱子面板宽度：列数决定，再留 8px 内边距。 */
    public static int panelWidthFor(int capacity) {
        return columnsFor(capacity) * CRATE_CELL + 16;
    }

    private final int hostMenuIndex;
    /** 打开界面时绑定的那件箱子；换箱子（哪怕容量相同）后界面必须关闭。 */
    private final ItemStack hostStack;
    private final int crateCapacity;
    private final int crateColumns;
    private final int bagCapacity;
    private final CrateContainer crate;
    /** 菜单槽位分区（quickMoveStack 用）。 */
    private final int crateStart = 0;
    private final int crateEnd;
    private final int equipmentStart;
    private final int equipmentEnd;
    private final int bagStart;
    private final int bagEnd;
    private final int handsIndex;
    private final int hotbarStart;
    private final int hotbarEnd;

    public CrateMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf == null ? -1 : buf.readVarInt());
    }

    public CrateMenu(int id, Inventory inventory, int hostMenuIndex) {
        super(ModMenus.CRATE.get(), id);
        Player player = inventory.player;
        this.hostMenuIndex = hostMenuIndex;
        ItemStack host = hostAt(player, hostMenuIndex);
        this.hostStack = host;
        this.crateCapacity = host.getItem() instanceof CrateItem item ? item.capacity() : 0;
        this.crateColumns = columnsFor(crateCapacity);
        this.crate = new CrateContainer(host, crateCapacity);

        // ---- 右：箱子内部（原作容器窗口：sqrt(容量) 列、行左对齐、整块居中）----
        int panelWidth = panelWidthFor(crateCapacity);
        int gridWidth = crateColumns * CRATE_CELL;
        int gridX = CRATE_PANEL_X + (panelWidth - gridWidth) / 2;
        for (int i = 0; i < crateCapacity; i++) {
            // Slot 的坐标是 16×16 物品图标的左上角；格子本身比图标大 1px。
            addSlot(new CrateSlot(crate, i,
                    gridX + (i % crateColumns) * CRATE_PITCH + (CRATE_CELL - 16) / 2,
                    CRATE_TOP + (i / crateColumns) * CRATE_PITCH + (CRATE_CELL - 16) / 2));
        }
        this.crateEnd = slots.size();
        this.equipmentStart = crateEnd;

        // ---- 左：装备槽（和 E 界面同一套坐标）----
        EquipmentInventory equipment = EquipmentData.getOrCreate(player);
        for (EquipmentSlotType type : EquipmentSlotType.values()) {
            addSlot(new EquipmentSlot(equipment, type.slotIndex(), type.x(), type.y(), type));
        }
        this.equipmentEnd = slots.size();

        // ---- 左：装备背包 + 双手格 + 热键栏 ----
        BagContainer bag = BagManager.container(player);
        this.bagCapacity = BagManager.capacity(player);
        BagLayout.Grid grid = BagLayout.forCapacityIn(bagCapacity, 8, 84, 160, 54);
        this.bagStart = slots.size();
        for (int i = 0; i < bagCapacity; i++) {
            addSlot(new BagSlot(bag, i, grid.slotX(i), grid.slotY(i),
                    () -> BagManager.capacity(player), bagCapacity));
        }
        this.bagEnd = slots.size();
        this.handsIndex = slots.size();
        // 双手格在箱子界面里只显示、不能点：箱子本身就是这格里那件物品，
        // 改它会把界面绑定的容器搬走。
        addSlot(new HandsSlot(inventory) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        this.hotbarStart = slots.size();
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(inventory, i, 8 + i * 18, 142));
        }
        this.hotbarEnd = slots.size();
    }

    /** 服务端：校验背包槽位上的物品确实是带容器的箱子，再开界面。 */
    public static void open(ServerPlayer player, int hostMenuIndex) {
        ItemStack host = hostAt(player, hostMenuIndex);
        if (!(host.getItem() instanceof CrateItem crate) || crate.capacity() <= 0) {
            return;
        }
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, p) -> new CrateMenu(id, inventory, hostMenuIndex),
                        host.getHoverName()),
                buf -> buf.writeVarInt(hostMenuIndex));
    }

    /**
     * 背包菜单里下标 {@code index} 的槽位上的物品。
     * 开界面时玩家当前菜单就是背包菜单（或从背包界面里点开），两边下标一致。
     */
    public static ItemStack hostAt(Player player, int index) {
        if (player == null || index < 0 || index >= player.inventoryMenu.slots.size()) {
            return ItemStack.EMPTY;
        }
        return player.inventoryMenu.slots.get(index).getItem();
    }

    public int windowWidth() {
        return CRATE_PANEL_X + panelWidthFor(crateCapacity) + 8;
    }

    /** 右侧箱子面板宽度（按列数算）。 */
    public int cratePanelWidth() {
        return panelWidthFor(crateCapacity);
    }

    public int crateColumns() {
        return crateColumns;
    }

    public int windowHeight() {
        return WINDOW_HEIGHT;
    }

    public int crateCapacity() {
        return crateCapacity;
    }

    /** 箱子里的东西还在不在（箱子被拿走/换掉后返回 false，界面自动关闭）。 */
    @Override
    public boolean stillValid(Player player) {
        ItemStack current = hostAt(player, hostMenuIndex);
        // 必须是打开界面时的同一份 ItemStack：换成另一个同容量箱子也要关。
        return current == hostStack
                && current.getItem() instanceof CrateItem crateItem
                && crateItem.capacity() == crateCapacity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index == handsIndex) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        boolean moved = false;
        if (index < crateEnd) {
            // 箱子 → 背包 → 热键栏
            moved = moveItemStackTo(stack, bagStart, bagEnd, false)
                    || moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
        } else {
            // 玩家物品 → 箱子（能装的话），否则在背包/热键栏之间挪
            moved = moveItemStackTo(stack, crateStart, crateEnd, false);
            if (!moved) {
                if (index >= bagStart && index < bagEnd) {
                    moved = moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
                } else if (index >= hotbarStart && index < hotbarEnd) {
                    moved = moveItemStackTo(stack, bagStart, bagEnd, false);
                } else {
                    // 装备槽/双手格
                    moved = moveItemStackTo(stack, bagStart, bagEnd, false)
                            || moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
                }
            }
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }

    /** 箱子格子：只收原作允许的"小/中件"；界面按原作的方槽样式画。 */
    public static final class CrateSlot extends Slot {

        CrateSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
