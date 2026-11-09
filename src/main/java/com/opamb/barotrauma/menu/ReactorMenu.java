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
import net.minecraft.world.inventory.AbstractContainerMenu;
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
public class ReactorMenu extends AbstractContainerMenu {

    public static final int FUEL_SLOTS = ReactorBlockEntity.FUEL_SLOTS;

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
    public static final int DATA_COUNT = 16;

    private final BlockPos reactorPos;
    private final ContainerData data;

    public ReactorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public ReactorMenu(int id, Inventory inventory, BlockPos reactorPos) {
        super(ModMenus.REACTOR.get(), id);
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
            addSlot(new Slot(fuelContainer, i, 12, 32 + i * 24) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ReactorFuel.isFuel(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 119 + col * 18, 214 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 119 + col * 18, 272));
        }

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
                        case DATA_OUTPUT -> (int) Math.round(be.state().outputKw());
                        case DATA_MELTDOWN -> (int) Math.round(be.state().meltdownTimer() * 10.0D);
                        case DATA_MELTED -> be.state().meltedDown() ? 1 : 0;
                        case DATA_BOOST -> (int) Math.round(be.state().temperatureBoost() * 10.0D);
                        case DATA_RATIO -> (int) Math.round(be.lastRatio() * 1000.0D);
                        case DATA_CAPACITY -> (int) Math.round(be.state().capacityKw());
                        case DATA_CORRECT_TURBINE -> (int) Math.round(be.state().correctTurbine());
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
            this.data = new SimpleContainerData(DATA_COUNT);
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

        int inventoryStart = FUEL_SLOTS;
        int inventoryEnd = slots.size();
        if (index < FUEL_SLOTS) {
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ReactorFuel.isFuel(stack)) {
            if (!moveItemStackTo(stack, 0, FUEL_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < inventoryStart + 27) {
            if (!moveItemStackTo(stack, inventoryStart + 27, inventoryEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, inventoryStart, inventoryStart + 27, false)) {
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
