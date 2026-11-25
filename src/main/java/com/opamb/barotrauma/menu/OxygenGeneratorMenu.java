package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.OxygenGeneratorBlockEntity;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 制氧机面板：五个氧气瓶槽 + 产氧量/电压/耐久读数。
 *
 * <p>照原作 {@code OxygenGenerator} 的面板：五格 {@code ItemContainer}（氧气瓶/焊接油箱
 * 会在这里被充气），没有开关——通电就工作。数值来自方块实体每 tick 上报。</p>
 */
public class OxygenGeneratorMenu extends DeviceMenu {

    /** 数据位 0：当前产氧量（单位/秒 × 10）。 */
    public static final int DATA_FLOW = 0;
    /** 数据位 1：当前电压（× 100）。 */
    public static final int DATA_VOLTAGE = 1;
    /** 数据位 2：耐久（× 10）。 */
    public static final int DATA_CONDITION = 2;
    /** 数据位 3：是否绑定接线盒（0/1）。 */
    public static final int DATA_BOUND = 3;
    public static final int DATA_COUNT = 4;

    /** 玩家主物品栏 27 格的菜单位置（会被换成装备背包 + 禁用占位）。 */
    public static final int INV_START = OxygenGeneratorBlockEntity.TANK_SLOTS;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int HOTBAR_END = HOTBAR_START + 9;

    public static final int INV_AREA_X = 12;
    public static final int INV_AREA_Y = 206;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    /** 五个瓶槽：横向一排，整块居中。 */
    public static final int TANK_ROW_X = 59;
    public static final int TANK_ROW_Y = 44;
    public static final int TANK_PITCH = 22;

    private final BlockPos pos;
    private final ContainerData data;
    /** 服务端解析到的方块实体（客户端为 null）；用来确认菜单还绑着同一台机器。 */
    private final OxygenGeneratorBlockEntity machine;

    public OxygenGeneratorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf == null ? BlockPos.ZERO : buf.readBlockPos());
    }

    public OxygenGeneratorMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.OXYGEN_GENERATOR.get(), id, inventory, pos);
        this.pos = pos;
        OxygenGeneratorBlockEntity resolved = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof OxygenGeneratorBlockEntity direct) {
            resolved = direct;
        }
        this.machine = resolved;
        if (resolved != null) {
            OxygenGeneratorBlockEntity generator = resolved;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_FLOW -> (int) Math.round(generator.flow() * 10.0D);
                        case DATA_VOLTAGE -> (int) Math.round(generator.voltage() * 100.0D);
                        case DATA_CONDITION -> (int) Math.round(generator.condition() * 10.0D);
                        case DATA_BOUND -> generator.powerBox() == null ? 0 : 1;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 只读数据：槽位变化由原版 setItem/broadcastChanges 同步。
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

        net.minecraft.world.Container tankContainer = resolved != null
                ? resolved : new SimpleContainer(OxygenGeneratorBlockEntity.TANK_SLOTS);
        for (int i = 0; i < OxygenGeneratorBlockEntity.TANK_SLOTS; i++) {
            addSlot(new Slot(tankContainer, i, TANK_ROW_X + i * TANK_PITCH, TANK_ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 原作容器只收 oxygen source / 焊接油箱；两端用同一份静态判定，
                    // 客户端在服务端实例还没到手时也能正确拒绝别的物品。
                    return isRefillable(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
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
        // 玩家主物品栏 27 格换成装备背包 + 禁用占位（和玩家物品栏同款）。
        com.opamb.barotrauma.equipment.BagSlots.installAt(this, inventory.player, INV_START,
                INV_AREA_X, INV_AREA_Y, INV_AREA_W, INV_AREA_H);
        // 双手持栏位：和快捷栏平行、面板左侧外面。
        addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                com.opamb.barotrauma.equipment.HandsSlot.containerY(INV_AREA_Y)));
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos pos() {
        return pos;
    }

    /** 能放进瓶槽的物品（原作 {@code refillableoxygensource}）：氧气瓶、焊接油箱。 */
    public static boolean isRefillable(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(com.opamb.barotrauma.registry.ModItems.OXYGEN_TANK.get())
                || stack.is(com.opamb.barotrauma.registry.ModItems.WELDING_FUEL_TANK.get()));
    }

    @Override
    public boolean stillValid(Player player) {
        if (pos.equals(BlockPos.ZERO)) {
            return false;
        }
        Vec3 center = RepairActionPacket.worldCenter(player.level(), pos);
        if (center == null || player.distanceToSqr(center) > 64.0D) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // 服务端：原机器实例必须还在且同一台，否则菜单要关闭。
        return machine != null && !machine.isRemoved()
                && level.getBlockEntity(pos) == machine;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int tankSlots = OxygenGeneratorBlockEntity.TANK_SLOTS;
        int bagStart = INV_START;
        int bagEnd = bagStart + com.opamb.barotrauma.equipment.BagManager.capacity(player);
        boolean moved;
        if (index < tankSlots) {
            moved = moveItemStackTo(stack, bagStart, bagEnd, false)
                    || moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
        } else if (isRefillable(stack)) {
            moved = moveItemStackTo(stack, 0, tankSlots, false)
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
