package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 电池面板：电量、充电速率、耐久、三个电池 cell 充电槽。
 *
 * <p>照原作 {@code PowerContainer} 的面板：一条电量进度条、一个充电速率滑条。
 * 滑条通过 {@link #clickMenuButton(Player, int)} 回服务端，0..100 对应
 * 0..{@link BatteryBlockEntity#MAX_RECHARGE_KW}。台面三个槽对应原作的
 * {@code batterycellrecharger}，只收电池 cell。</p>
 */
public class BatteryMenu extends DeviceMenu {

    /** 数据位 0：电量（kW·min × 10，避免 int 精度不够）。 */
    public static final int DATA_CHARGE = 0;
    /** 数据位 1：容量（kW·min × 10）。 */
    public static final int DATA_CAPACITY = 1;
    /** 数据位 2：充电速率（kW）。 */
    public static final int DATA_RECHARGE = 2;
    /** 数据位 3：耐久 × 10。 */
    public static final int DATA_CONDITION = 3;
    /** 数据位 4：是否绑定接线盒（0/1）。 */
    public static final int DATA_BOUND = 4;

    public static final int DATA_MAX_RECHARGE = 5;
    public static final int DATA_GRID_CONNECTED = 6;
    public static final int DATA_GRID_FLOW = 7;
    public static final int DATA_COUNT = 8;

    /** 玩家主物品栏 27 格的菜单位置（这 27 格会被换成装备背包 + 禁用占位）。 */
    public static final int INV_START = BatteryBlockEntity.CELL_SLOTS;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int HOTBAR_END = HOTBAR_START + 9;
    /** 物品栏区域（GUI 局部坐标）。 */
    public static final int INV_AREA_X = 42;
    public static final int INV_AREA_Y = 164;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    private final BlockPos batteryPos;
    private final ContainerData data;
    /** 构造时解析到的电池实例（仅服务端非 null）；用于校验菜单还绑着同一台电池。 */
    private final BatteryBlockEntity battery;

    public BatteryMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf == null ? BlockPos.ZERO : buf.readBlockPos());
    }

    public BatteryMenu(int id, Inventory inventory, BlockPos batteryPos) {
        super(ModMenus.BATTERY.get(), id, inventory, batteryPos);
        this.batteryPos = batteryPos;
        BatteryBlockEntity resolved = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel) {
            resolved = RepairActionPacket.battery(serverLevel, batteryPos);
        }
        this.battery = resolved;
        if (resolved != null) {
            BatteryBlockEntity battery = resolved;
            this.data = new ContainerData() {                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_CHARGE -> (int) Math.round(battery.storedKwm() * 10.0D);
                        case DATA_CAPACITY -> (int) Math.round(battery.capacityKwm() * 10.0D);
                        case DATA_RECHARGE -> (int) Math.round(battery.rechargeKw());
                        case DATA_CONDITION -> (int) Math.round(battery.condition() * 10.0D);
                        case DATA_MAX_RECHARGE -> (int) Math.round(battery.maxRechargeKw());
                        case DATA_BOUND -> battery.storageBox() == null ? 0 : 1;
                        case DATA_GRID_CONNECTED -> battery.gridConnected() ? 1 : 0;
                        case DATA_GRID_FLOW -> (int) Math.round(battery.gridFlowKw());
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 服务端数据只由滑条按钮改，原版同步包不写这里。
                }

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            // 客户端在服务端数据到位前先给一组默认值，避免滑条/电量条闪 0。
            SimpleContainerData fallback = new SimpleContainerData(DATA_COUNT);
            fallback.set(DATA_MAX_RECHARGE, (int) BatteryBlockEntity.MAX_RECHARGE_KW);
            fallback.set(DATA_CAPACITY, (int) Math.round(BatteryBlockEntity.CAPACITY_KWM * 10.0D));
            fallback.set(DATA_RECHARGE, (int) Math.round(BatteryBlockEntity.MAX_RECHARGE_KW));
            fallback.set(DATA_CONDITION, 1000);
            this.data = fallback;
        }
        addDataSlots(data);

        net.minecraft.world.Container cellContainer = resolved != null
                ? resolved : new net.minecraft.world.SimpleContainer(BatteryBlockEntity.CELL_SLOTS);
        for (int i = 0; i < BatteryBlockEntity.CELL_SLOTS; i++) {
            addSlot(new net.minecraft.world.inventory.Slot(cellContainer, i, 12 + i * 22, 134) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof com.opamb.barotrauma.item.BatteryCellItem;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new net.minecraft.world.inventory.Slot(inventory,
                        col + row * 9 + 9,
                        INV_AREA_X + col * 18, INV_AREA_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, col,
                    INV_AREA_X + col * 18, INV_AREA_Y + 58));
        }
        // 主物品栏 27 格换成装备背包 + 禁用占位（和玩家物品栏同款）。
        com.opamb.barotrauma.equipment.BagSlots.installAt(this, inventory.player, INV_START,
                INV_AREA_X, INV_AREA_Y, INV_AREA_W, INV_AREA_H);
        // 双手持栏位：放在装备背包区左侧空位（箱子只能抱在手上，容器界面里也要能放/拿）。
        addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                com.opamb.barotrauma.equipment.HandsSlot.containerY(INV_AREA_Y)));
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos batteryPos() {
        return batteryPos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id > 100 || player.level().isClientSide) {
            return false;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            // 优先用构造时校验过的实例，避免同位置换电池后改到新电池上。
            BatteryBlockEntity target = battery != null ? battery
                    : RepairActionPacket.battery(serverLevel, batteryPos);
            if (target != null) {
                target.setRechargeKw(target.maxRechargeKw() * id / 100.0D);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (batteryPos.equals(BlockPos.ZERO)) {
            return false;
        }
        Vec3 center = RepairActionPacket.worldCenter(player.level(), batteryPos);
        if (center == null || player.distanceToSqr(center) > 64.0D) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // 服务端：原电池实例必须还在，且当前位置仍解析为同一实例；
        // 同位置换了一块新电池时旧菜单必须关闭，否则槽位写进已移除的实体。
        return battery != null && !battery.isRemoved()
                && RepairActionPacket.battery(level, batteryPos) == battery;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        net.minecraft.world.inventory.Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int cellSlots = BatteryBlockEntity.CELL_SLOTS;
        int bagStart = INV_START;
        int bagEnd = bagStart + com.opamb.barotrauma.equipment.BagManager.capacity(player);
        boolean moved;
        if (index < cellSlots) {
            // 充电槽 → 装备背包，再进快捷栏。
            moved = moveItemStackTo(stack, bagStart, bagEnd, false)
                    || moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
        } else if (stack.getItem() instanceof com.opamb.barotrauma.item.BatteryCellItem) {
            // 电池 cell：先进充电槽。
            moved = moveItemStackTo(stack, 0, cellSlots, false)
                    || moveItemStackTo(stack, index >= HOTBAR_START ? bagStart : HOTBAR_START,
                    index >= HOTBAR_START ? bagEnd : HOTBAR_END, false);
        } else if (index >= HOTBAR_START) {
            moved = moveItemStackTo(stack, bagStart, bagEnd, false);
        } else {
            moved = moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
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
