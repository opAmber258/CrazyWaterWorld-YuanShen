package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.EngineBlockEntity;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 引擎只读面板：没有物品槽；显示目标推力、供电比例、耗电和船上引擎数量。
 *
 * <p>控制仍然在声纳台：这里不提供滑条，只反映引擎收到的油门。</p>
 */
public class EngineMenu extends AbstractContainerMenu {

    public static final int DATA_FORCE = 0;
    public static final int DATA_SUPPLY = 1;
    public static final int DATA_DEMAND = 2;
    public static final int DATA_COUNT = 3;
    public static final int DATA_MAX_FORCE = 4;
    public static final int DATA_SIZE = 5;

    private final BlockPos enginePos;
    private final ContainerData data;

    public EngineMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public EngineMenu(int id, Inventory inventory, BlockPos enginePos) {
        super(ModMenus.ENGINE.get(), id);
        this.enginePos = enginePos;

        EngineBlockEntity engine = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(enginePos) instanceof EngineBlockEntity direct) {
                engine = direct;
            } else if (VsState.active()
                    && serverLevel.getBlockState(enginePos).is(ModShipBlocks.ENGINE_PROXY.get())) {
                engine = VsPilot.engineBlockEntity(serverLevel);
            }
        }

        if (engine != null) {
            EngineBlockEntity be = engine;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_FORCE -> (int) Math.round(be.lastForce());
                        case DATA_SUPPLY -> (int) Math.round(be.lastSupply() * 1000.0D);
                        case DATA_DEMAND -> (int) Math.round(be.lastDemandKw());
                        case DATA_COUNT -> be.lastEngineCount();
                        case DATA_MAX_FORCE -> (int) Math.round(be.lastMaxForce());
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
            this.data = new SimpleContainerData(DATA_SIZE);
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
        return player.distanceToSqr(Vec3.atCenterOf(enginePos)) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
