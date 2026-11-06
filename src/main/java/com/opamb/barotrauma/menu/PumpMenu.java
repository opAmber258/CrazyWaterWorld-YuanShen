package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipState;
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
 * 排水泵菜单：无物品槽，数据位通过 {@link ContainerData} 自动同步。
 *
 * <p>数据位对齐原版水泵面板：能源灯、自动控制、泵出速度（-100..100%）。
 * 服务端实例直接代理方块实体的值；客户端实例用 {@link SimpleContainerData}，
 * 由原版 {@code ClientboundContainerSetDataPacket} 每 tick 刷新。</p>
 */
public class PumpMenu extends AbstractContainerMenu {

    /** 数据位 0：自动控制（0/1）。 */
    public static final int DATA_AUTO = 0;
    /** 数据位 1：实际生效的流量百分比（-100..100）。 */
    public static final int DATA_FLOW = 1;
    /** 数据位 2：能源（0/1，船上电力是否供得上）。 */
    public static final int DATA_POWER = 2;

    public static final int DATA_COUNT = 3;

    private final BlockPos pumpPos;
    private final ContainerData data;

    public PumpMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public PumpMenu(int id, Inventory inventory, BlockPos pumpPos) {
        super(ModMenus.PUMP.get(), id);
        this.pumpPos = pumpPos;
        DrainPumpBlockEntity resolved = null;
        ShipState ship = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(pumpPos) instanceof DrainPumpBlockEntity direct) {
                resolved = direct;
            } else if (VsState.active()
                    && serverLevel.getBlockState(pumpPos).is(ModShipBlocks.PUMP_PROXY.get())) {
                // 物理船：泵方块实体在船坞里，菜单用代理位置做距离校验，数据仍读真实泵。
                resolved = VsPilot.pumpBlockEntity(serverLevel);
            }
            ship = DrainPumpBlockEntity.resolveShip(serverLevel, pumpPos);
        }
        if (resolved != null) {
            DrainPumpBlockEntity pump = resolved;
            ShipState shipState = ship;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_AUTO -> pump.auto() ? 1 : 0;
                        case DATA_FLOW -> pump.effectiveFlow();
                        case DATA_POWER -> powered(shipState) ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                    // 服务端的值只由 PumpActionPacket 改，原版同步包不会写到这里
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

    /** 船上电力够不够（对标原版水泵面板左上角的能源灯）。 */
    private static boolean powered(ShipState ship) {
        return ship != null && ship.simulation().report().powerRatio() > 0.02D;
    }

    public ContainerData data() {
        return data;
    }

    public BlockPos pumpPos() {
        return pumpPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(Vec3.atCenterOf(pumpPos)) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
