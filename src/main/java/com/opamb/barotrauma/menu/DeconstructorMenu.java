package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.DeconstructorBlockEntity;
import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 解构仪菜单：8 个输入格（4×2）+ 5 个输出格 + 玩家背包 + 工具带/背包格。
 *
 * <p>点“拆解”时服务端读第一个非空输入格，按原作的 {@code <Deconstruct>} 表拆成材料，
 * 成品进输出格，装不下就掉在机器前面。</p>
 */
public class DeconstructorMenu extends DeviceMenu {

    public static final int INPUT_SLOTS = DeconstructorBlockEntity.INPUT_SLOTS;
    public static final int OUTPUT_SLOTS = DeconstructorBlockEntity.OUTPUT_SLOTS;
    public static final int MACHINE_SLOTS = DeconstructorBlockEntity.SLOTS;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_WORKING = 1;
    public static final int DATA_POWER = 2;
    public static final int DATA_CONDITION = 3;
    public static final int DATA_COUNT = 4;

    public static final int BUTTON_START = 0;
    public static final int BUTTON_CANCEL = 1;

    /** 玩家主物品栏 27 格的菜单位置（这 27 格会被换成装备背包 + 禁用占位）。 */
    public static final int INV_START = MACHINE_SLOTS;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int HOTBAR_END = HOTBAR_START + 9;
    /** 物品栏区域（GUI 局部坐标）。 */
    public static final int INV_AREA_X = 16;
    public static final int INV_AREA_Y = 132;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    /** 输入格：4 列 × 2 行。 */
    private static final int INPUT_COLUMNS = 4;
    private static final int INPUT_X = 16;
    private static final int INPUT_Y = 30;
    private static final int SLOT_PITCH = 20;

    private final BlockPos pos;
    private final ContainerData data;
    private final DeconstructorBlockEntity serverMachine;

    public DeconstructorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public DeconstructorMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.DECONSTRUCTOR.get(), id, inventory, pos);
        this.pos = pos;
        DeconstructorBlockEntity machine = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof DeconstructorBlockEntity direct) {
            machine = direct;
        }
        this.serverMachine = machine;
        Container container = machine != null ? machine : new SimpleContainer(MACHINE_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            final int slotIndex = i;
            addSlot(new Slot(container, i,
                    INPUT_X + (i % INPUT_COLUMNS) * SLOT_PITCH,
                    INPUT_Y + (i / INPUT_COLUMNS) * SLOT_PITCH) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !locked(slotIndex);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !locked(slotIndex);
                }
            });
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            addSlot(new Slot(container, INPUT_SLOTS + i, INPUT_X + i * SLOT_PITCH, 86) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        INV_AREA_X + col * 18, INV_AREA_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_AREA_X + col * 18, INV_AREA_Y + 58));
        }
        // 主物品栏 27 格换成装备背包 + 禁用占位（和玩家物品栏同款）。
        BagSlots.installAt(this, inventory.player, INV_START,
                INV_AREA_X, INV_AREA_Y, INV_AREA_W, INV_AREA_H);
        // 双手持栏位：放在装备背包区左侧空位（箱子只能抱在手上，容器界面里也要能放/拿）。
        addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                com.opamb.barotrauma.equipment.HandsSlot.containerY(INV_AREA_Y)));

        if (machine != null) {
            DeconstructorBlockEntity be = machine;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_PROGRESS -> (int) Math.round(be.progressFraction() * 1000.0D);
                        case DATA_WORKING -> be.isWorking() ? 1 : 0;
                        case DATA_POWER -> be.powerAvailable() ? 1 : 0;
                        case DATA_CONDITION -> (int) Math.round(be.condition() * 10.0D);
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 服务端值只由机器自己或 clickMenuButton 改。
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            SimpleContainerData fallback = new SimpleContainerData(DATA_COUNT);
            fallback.set(DATA_CONDITION, 1000);
            this.data = fallback;
        }
        addDataSlots(data);
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (serverMachine == null) {
            return false;
        }
        if (id == BUTTON_START) {
            return serverMachine.start((ServerLevel) player.level(), pos,
                    player.level().getBlockState(pos));
        }
        if (id == BUTTON_CANCEL) {
            serverMachine.cancel((ServerLevel) player.level(), pos,
                    player.level().getBlockState(pos));
            return true;
        }
        return false;
    }

    /** 工作期间锁住活动输入槽（和机器 setItem/removeItem 的守卫一致）。 */
    private boolean locked(int slot) {
        return serverMachine != null && serverMachine.working()
                && serverMachine.workingSlot() == slot;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        // 服务端：机器被拆掉或同位置换了一台时关闭菜单，
        // 否则槽位继续引用已移除的实体，放进去的物品关窗后会丢。
        return serverMachine != null && !serverMachine.isRemoved()
                && player.level().getBlockEntity(pos) == serverMachine;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        // Shift 点击也要尊重槽位锁（工作中活动输入槽 mayPickup=false）。
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int bagStart = INV_START;
        int bagEnd = bagStart + BagManager.capacity(player);
        boolean moved;
        if (index < MACHINE_SLOTS) {
            // 机器 → 装备背包，再进快捷栏。
            moved = moveItemStackTo(stack, bagStart, bagEnd, false)
                    || moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
        } else if (index >= HOTBAR_START) {
            // 快捷栏 → 输入格，再进装备背包。
            moved = moveItemStackTo(stack, 0, INPUT_SLOTS, false)
                    || moveItemStackTo(stack, bagStart, bagEnd, false);
        } else {
            // 装备背包 → 输入格，再进快捷栏。
            moved = moveItemStackTo(stack, 0, INPUT_SLOTS, false)
                    || moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
