package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.EngineBlockEntity;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.network.RepairActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Telemetry and local thrust control for the engine that opened this menu. */
public class EngineMenu extends DeviceMenu {

    public static final int DATA_FORCE = 0;
    public static final int DATA_SUPPLY = 1;
    public static final int DATA_DEMAND = 2;
    public static final int DATA_COUNT = 3;
    public static final int DATA_MAX_FORCE = 4;
    public static final int DATA_CONDITION = 5;
    public static final int DATA_TARGET_FORCE = 6;
    public static final int DATA_CONTROL = 7;
    public static final int DATA_SIZE = 8;

    private final BlockPos enginePos;
    private final ContainerData data;
    private final EngineBlockEntity engine;

    public EngineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public EngineMenu(int id, Inventory inventory, BlockPos enginePos) {
        super(ModMenus.ENGINE.get(), id, inventory, enginePos);
        this.enginePos = enginePos;

        EngineBlockEntity engine = null;
        if (inventory.player.level() instanceof ServerLevel level
                && RepairActionPacket.find(level, enginePos) instanceof EngineBlockEntity resolved) {
            engine = resolved;
        }
        this.engine = engine;

        if (engine != null) {
            EngineBlockEntity be = engine;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_CONTROL -> be.controlMode().ordinal();
                        case DATA_FORCE -> (int) Math.round(be.lastForce());
                        case DATA_TARGET_FORCE -> (int) Math.round(be.lastTargetForce());
                        case DATA_SUPPLY -> (int) Math.min(2000, Math.round(be.lastSupply() * 1000.0D));
                        case DATA_DEMAND -> (int) Math.round(be.lastDemandKw());
                        case DATA_COUNT -> be.lastEngineCount();
                        case DATA_MAX_FORCE -> (int) Math.round(be.lastMaxForce());
                        case DATA_CONDITION -> (int) Math.round(be.condition() * 10.0D);
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 只读面板。
                }

                @Override
                public int getCount() {
                    return DATA_SIZE;
                }
            };
        } else {
            SimpleContainerData fallback = new SimpleContainerData(DATA_SIZE);
            fallback.set(DATA_CONDITION, 1000);
            this.data = fallback;
        }
        addDataSlots(data);
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos enginePos() {
        return enginePos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return true;
        return engine != null && !engine.isRemoved() && level.hasChunkAt(enginePos)
                && RepairActionPacket.find(level, enginePos) == engine
                && player.distanceToSqr(RepairActionPacket.worldCenter(level, enginePos)) <= 64.0D;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id >= 0 && id <= 20 && player.containerMenu == this && stillValid(player)
                && engine != null && engine.setLocalForce(id * 10 - 100);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
