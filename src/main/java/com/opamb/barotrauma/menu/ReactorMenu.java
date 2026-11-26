package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.reactor.ReactorFuel;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
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
 * 反应堆面板菜单：4 个燃料棒槽 + 16 个数据位。
 *
 * <p>数据位每 tick 由服务端 {@link ContainerData} 同步；槽位用标准
 * {@link Slot}，服务端容器是反应堆方块实体，客户端是 {@link SimpleContainer}。</p>
 */
public class ReactorMenu extends DeviceMenu {

    public static final int FUEL_SLOTS = ReactorBlockEntity.FUEL_SLOTS;
    public static final int FUEL_X = 16;
    public static final int FUEL_Y = 84;
    public static final int FUEL_STEP = 25;

    /** 玩家主物品栏 27 格的菜单位置（这 27 格会被换成装备背包 + 禁用占位）。 */
    public static final int INV_START = FUEL_SLOTS;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int HOTBAR_END = HOTBAR_START + 9;
    /** 物品栏区域（GUI 局部坐标）。 */
    public static final int INV_AREA_X = 150;
    public static final int INV_AREA_Y = 212;
    public static final int INV_AREA_W = 160;
    public static final int INV_AREA_H = 54;

    public static final int DATA_POWER_ON = 0;
    public static final int DATA_AUTO = 1;
    public static final int DATA_TARGET_FISSION = 2;
    public static final int DATA_TARGET_TURBINE = 3;
    public static final int DATA_FISSION = 4;
    public static final int DATA_TURBINE = 5;
    public static final int DATA_TEMPERATURE = 6;
    public static final int DATA_FUEL = 7;
    public static final int DATA_LOAD = 8;
    public static final int DATA_OUTPUT = 9;
    public static final int DATA_MELTDOWN = 10;
    public static final int DATA_MELTED = 11;
    public static final int DATA_BOOST = 12;
    public static final int DATA_RATIO = 13;
    public static final int DATA_CAPACITY = 14;
    public static final int DATA_CORRECT_TURBINE = 15;
    public static final int DATA_CONDITION = 16;
    public static final int DATA_COUNT = 17;

    private final BlockPos reactorPos;
    private final ContainerData data;

    public ReactorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public ReactorMenu(int id, Inventory inventory, BlockPos reactorPos) {
        super(ModMenus.REACTOR.get(), id, inventory, reactorPos);
        this.reactorPos = reactorPos;

        ReactorBlockEntity reactor = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(reactorPos) instanceof ReactorBlockEntity direct) {
                reactor = direct;
            } else if (VsState.active()
                    && serverLevel.getBlockState(reactorPos).is(ModShipBlocks.REACTOR_PROXY.get())) {
                reactor = VsPilot.reactorBlockEntity(serverLevel);
            }
        }

        Container fuelContainer = reactor != null ? reactor : new SimpleContainer(FUEL_SLOTS);
        for (int i = 0; i < FUEL_SLOTS; i++) {
            addSlot(new Slot(fuelContainer, i, FUEL_X, FUEL_Y + i * FUEL_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ReactorFuel.isFuel(stack);
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
        com.opamb.barotrauma.equipment.BagSlots.installAt(this, inventory.player, INV_START,
                INV_AREA_X, INV_AREA_Y, INV_AREA_W, INV_AREA_H);
        // 双手持栏位：放在装备背包区左侧空位（箱子只能抱在手上，容器界面里也要能放/拿）。
        addSlot(new com.opamb.barotrauma.equipment.HandsSlot(inventory,
                com.opamb.barotrauma.equipment.HandsSlot.CONTAINER_X,
                com.opamb.barotrauma.equipment.HandsSlot.containerY(INV_AREA_Y)));

        if (reactor != null) {
            ReactorBlockEntity be = reactor;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_POWER_ON -> be.powerOn() ? 1 : 0;
                        case DATA_AUTO -> be.autoTemp() ? 1 : 0;
                        case DATA_TARGET_FISSION -> (int) Math.round(be.targetFission());
                        case DATA_TARGET_TURBINE -> (int) Math.round(be.targetTurbine());
                        case DATA_FISSION -> (int) Math.round(be.state().fission() * 10.0D);
                        case DATA_TURBINE -> (int) Math.round(be.state().turbine() * 10.0D);
                        case DATA_TEMPERATURE -> (int) Math.round(be.state().temperature() * 10.0D);
                        case DATA_FUEL -> (int) Math.round(be.availableFuel() * 10.0D);
                        case DATA_LOAD -> (int) Math.round(be.lastLoadKw());
                        case DATA_OUTPUT -> (int) Math.round(be.generatedKw());
                        case DATA_MELTDOWN -> (int) Math.round(be.state().meltdownTimer() * 10.0D);
                        case DATA_MELTED -> be.state().meltedDown() ? 1 : 0;
                        case DATA_BOOST -> (int) Math.round(be.state().temperatureBoost() * 10.0D);
                        case DATA_RATIO -> (int) Math.round(be.lastRatio() * 1000.0D);
                        case DATA_CAPACITY -> (int) Math.round(be.state().capacityKw());
                        case DATA_CORRECT_TURBINE -> (int) Math.round(be.state().correctTurbine());
                        case DATA_CONDITION -> (int) Math.round(be.condition() * 10.0D);
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 服务端值只由 ReactorActionPacket 改。
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

    public BlockPos reactorPos() {
        return reactorPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(Vec3.atCenterOf(reactorPos)) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();

        int bagStart = INV_START;
        int bagEnd = bagStart + com.opamb.barotrauma.equipment.BagManager.capacity(player);
        boolean moved;
        if (index < FUEL_SLOTS) {
            moved = moveItemStackTo(stack, bagStart, bagEnd, false)
                    || moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false);
        } else if (ReactorFuel.isFuel(stack)) {
            moved = moveItemStackTo(stack, 0, FUEL_SLOTS, false)
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
        return result;
    }
}
