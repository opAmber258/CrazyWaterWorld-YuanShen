package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.FabricatorBlockEntity;
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
 * 加工台菜单：5 个输入格 + 1 个输出格 + 玩家背包 + 工具带/背包格。
 *
 * <p>照原作 {@code Fabricator} 的界面分区：左侧分类 + 筛选 + 配方列表，右侧
 * 成品预览、所需材料、数量、Fabricate 按钮，底部输入/输出。配方选择是客户端
 * 界面状态；点“制作”时把配方在配方表里的下标作为 {@code clickMenuButton} 的 id
 * 发给服务端，服务端按同一份静态配方表校验材料、输出格、耐久和供电。</p>
 */
public class FabricatorMenu extends DeviceMenu {

    public static final int INPUT_SLOTS = FabricatorBlockEntity.INPUT_SLOTS;
    public static final int OUTPUT_SLOT = FabricatorBlockEntity.OUTPUT_SLOT;
    public static final int MACHINE_SLOTS = FabricatorBlockEntity.SLOTS;

    /** 进度 0..1000。 */
    public static final int DATA_PROGRESS = 0;
    /** 是否在制作中。 */
    public static final int DATA_WORKING = 1;
    /** 当前电压够不够（0/1）。 */
    public static final int DATA_POWER = 2;
    /** 耐久 × 10。 */
    public static final int DATA_CONDITION = 3;
    public static final int DATA_COUNT = 4;

    /** 玩家主物品栏 27 格的菜单位置（这 27 格会被换成装备背包 + 禁用占位）。 */
    public static final int INV_START = MACHINE_SLOTS;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int HOTBAR_END = HOTBAR_START + 9;
    /** 物品栏区域（GUI 局部坐标），和界面里画的槽位底纹同一块地方。 */
    public static final int INV_AREA_X = 46;
    public static final int INV_AREA_Y = 244;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    private final BlockPos pos;
    private final ContainerData data;
    private final FabricatorBlockEntity serverMachine;

    public FabricatorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public FabricatorMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.FABRICATOR.get(), id, inventory, pos);
        this.pos = pos;
        FabricatorBlockEntity machine = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos) instanceof FabricatorBlockEntity direct) {
            machine = direct;
        }
        this.serverMachine = machine;
        Container container = machine != null ? machine : new SimpleContainer(MACHINE_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            addSlot(new Slot(container, i, 46 + i * 20, 214));
        }
        addSlot(new Slot(container, OUTPUT_SLOT, 206, 214) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // 输出格只由机器写入。
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        INV_AREA_X + col * 18, INV_AREA_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_AREA_X + col * 18, INV_AREA_Y + 58));
        }
        // 主物品栏 27 格换成装备背包 + 禁用占位：和玩家物品栏同款，
        // 有工具带/背包就在这 3×9 区域里居中格子，没装备就是黑底提示。
        BagSlots.installAt(this, inventory.player, INV_START,
                INV_AREA_X, INV_AREA_Y, INV_AREA_W, INV_AREA_H);
        // 双手持栏位：放在装备背包区左侧空位（箱子只能抱在手上，容器界面里也要能放/拿）。
        addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                com.opamb.barotrauma.equipment.HandsSlot.containerY(INV_AREA_Y)));

        if (machine != null) {
            FabricatorBlockEntity be = machine;
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

    // 制作/取消走 FabricatorActionPacket：原版按钮编号是 writeByte，
    // 配方下标 + 数量 × 1000 会被截断，不能再用 clickMenuButton。

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
        if (!slot.hasItem()) {
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
        } else if (index == HOTBAR_END) {
            // 双手栏（箱子）→ 只回装备背包/快捷栏：输入格是默认 Slot，
            // mayPlace 恒 true，箱子不能被塞进加工台输入格。
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
