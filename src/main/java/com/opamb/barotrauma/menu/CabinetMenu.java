package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.ship.vs.VsDistance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 钢柜 / 货架 / 潜水服柜 / 氧气瓶架的统一容器菜单。
 *
 * <p>槽位布局按 {@link CabinetBlock.Kind} 决定：中型柜 5×4 主格 + 3 个移动容器格，
 * 货架 1×4，潜水服柜 1 格，氧气瓶架 3 格；玩家背包位置固定。</p>
 */
public class CabinetMenu extends AbstractContainerMenu {

    /** 3×9 装备背包区在 GUI 里的 x / 宽 / 高；y 随柜子高度变（见 {@link #playerAreaY()}）。 */
    public static final int INV_AREA_X = 8;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    private final BlockPos pos;
    private final CabinetBlock.Kind kind;
    /** 玩家 3×9 物品栏区域的 y（快捷栏在它下面 58px）。 */
    private int playerAreaY = 140;

    public CabinetMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf == null ? BlockPos.ZERO : buf.readBlockPos(),
                buf == null || buf.readableBytes() <= 0 ? null : readKind(buf));
    }

    public CabinetMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, null);
    }

    private CabinetMenu(int id, Inventory inventory, BlockPos pos, @Nullable CabinetBlock.Kind requestedKind) {
        super(ModMenus.CABINET.get(), id);
        this.pos = pos;
        // 客户端由服务端下发种类（物理船上船坞区块可能未同步，自己猜会槽位错位）；
        // 服务端 createMenu 路径没有种类，按方块实体/方块状态解析。
        // 注意：后面一律读 this.kind——三参构造器传进来的 requestedKind 是 null。
        this.kind = requestedKind != null ? requestedKind : resolveKind(inventory, pos);

        CabinetBlockEntity cabinet = null;
        if (inventory.player.level().getBlockEntity(pos) instanceof CabinetBlockEntity be) {
            cabinet = be;
        }
        Container container = cabinet != null ? cabinet : new SimpleContainer(kind.slots());

        if (kind.tall()) {
            int playerY = 140;
            int hotbarY = 198;
            if (kind == CabinetBlock.Kind.DIVING_SUIT_STATION) {
                // 上：潜水服 1 格；下：氧气瓶 3 格
                addSlot(containerSlot(container, 0, 80, 32, 1));
                for (int i = 0; i < 3; i++) {
                    addSlot(containerSlot(container, 1 + i, 61 + i * 18, 66, 1));
                }
                playerY = 104;
                hotbarY = 162;
            } else if (kind == CabinetBlock.Kind.CRATE_SHELF) {
                for (int row = 0; row < 4; row++) {
                    addSlot(containerSlot(container, row, 80, 18 + row * 18, 1));
                }
            } else {
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 5; col++) {
                        addSlot(containerSlot(container, row * 5 + col,
                                43 + col * 18, 18 + row * 18, 64));
                    }
                }
                for (int i = 0; i < 3; i++) {
                    int index = 20 + i;
                    addSlot(containerSlot(container, index, 61 + i * 18, 106, 1));
                }
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(inventory, col + row * 9 + 9,
                            INV_AREA_X + col * 18, playerY + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col, INV_AREA_X + col * 18, hotbarY));
            }
            // 原版 27 格主背包换成装备背包区 + 禁用占位（和加工台/解构仪同款）。
            this.playerAreaY = playerY;
            BagSlots.installAt(this, inventory.player, kind.slots(), INV_AREA_X, playerY,
                    INV_AREA_W, INV_AREA_H);
            // 双手持栏位：放在装备背包区左侧空位（箱子只能抱在手上，柜子界面里也要能放/拿）。
            addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                    com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                    com.opamb.barotrauma.equipment.HandsSlot.containerY(playerY)));
        } else {
            if (kind == CabinetBlock.Kind.DIVING_SUIT_LOCKER) {
                addSlot(containerSlot(container, 0, 80, 28, 1));
            } else {
                for (int i = 0; i < 3; i++) {
                    addSlot(containerSlot(container, i, 61 + i * 18, 28, 1));
                }
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(inventory, col + row * 9 + 9,
                            INV_AREA_X + col * 18, 84 + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col, INV_AREA_X + col * 18, 142));
            }
            // 矮柜同样是 3×9 装备背包区 + 快捷栏。
            this.playerAreaY = 84;
            BagSlots.installAt(this, inventory.player, kind.slots(), INV_AREA_X, 84,
                    INV_AREA_W, INV_AREA_H);
            // 双手持栏位：放在装备背包区左侧空位（箱子只能抱在手上，柜子界面里也要能放/拿）。
            addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                    com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                    com.opamb.barotrauma.equipment.HandsSlot.containerY(84)));
        }
    }

    private Slot containerSlot(Container container, int index, int x, int y, int maxStack) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(index, stack);
            }

            @Override
            public int getMaxStackSize() {
                return maxStack;
            }
        };
    }

    /** 读服务端下发的容器种类；越界/旧包返回 null，交给 {@link #resolveKind} 兜底。 */
    @Nullable
    private static CabinetBlock.Kind readKind(FriendlyByteBuf buf) {
        int ordinal = buf.readByte() & 0xFF;
        CabinetBlock.Kind[] kinds = CabinetBlock.Kind.values();
        return ordinal < kinds.length ? kinds[ordinal] : null;
    }

    private static CabinetBlock.Kind resolveKind(Inventory inventory, BlockPos pos) {
        BlockEntity be = inventory.player.level().getBlockEntity(pos);
        if (be instanceof CabinetBlockEntity cabinet) {
            return cabinet.kind();
        }
        if (inventory.player.level().getBlockState(pos).getBlock() instanceof CabinetBlock cabinet) {
            return cabinet.kind();
        }
        return CabinetBlock.Kind.MEDIUM_STEEL_CABINET;
    }

    public BlockPos pos() {
        return pos;
    }

    public CabinetBlock.Kind kind() {
        return kind;
    }

    /** 玩家 3×9 装备背包区的 y（快捷栏在 {@code +58}）。 */
    public int playerAreaY() {
        return playerAreaY;
    }

    /** 菜单里玩家 27 格主物品栏的起始下标（= 容器格数）。 */
    public int invStart() {
        return kind.slots();
    }

    @Override
    public boolean stillValid(Player player) {
        // 服务端：方块/方块实体没了就关界面（原版箱子的行为），否则玩家还能
        // 往一个已经移除的容器里塞东西，关界面时这些物品就没了。
        if (!player.level().isClientSide
                && !(player.level().getBlockEntity(pos) instanceof CabinetBlockEntity)) {
            return false;
        }
        // 物理船的方块坐标在船坞里，直接算世界距离会让服务端下一 tick 就关掉界面；
        // 用船感知距离（没装 VS2 时自动退回普通距离）。
        return VsDistance.squared(player.level(),
                player.getX(), player.getY(), player.getZ(),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int containerSlots = kind.slots();
        int playerMainStart = containerSlots;
        int playerMainEnd = containerSlots + 27;
        int playerEnd = slots.size();

        if (index < containerSlots) {
            if (!moveItemStackTo(stack, playerMainStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // moveItemStackTo 自己会按槽位的 mayPlace 过滤，直接扫整个容器范围：
            // 组合柜的氧气瓶不会再被 0 号槽（只收潜水服）挡住。
            boolean moved = moveItemStackTo(stack, 0, containerSlots, false);
            if (!moved) {
                if (index < playerMainEnd) {
                    if (!moveItemStackTo(stack, playerMainEnd, playerEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, playerMainStart, playerMainEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }
}
