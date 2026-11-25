package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModMenus;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 声纳菜单绑定打开它的终端。物理船移动时保留同一船坞方块实体。 */
public class SonarMenu extends DeviceMenu {

    public static final int DATA_CONDITION = 0;
    public static final int DATA_COUNT = 1;

    private final BlockPos sonarPos;
    private final int range;
    private final ContainerData data;
    private final ShipCoreBlockEntity terminal;
    private final boolean physical;

    public SonarMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readVarInt());
    }

    public SonarMenu(int id, Inventory inventory, BlockPos sonarPos, int range) {
        super(ModMenus.SONAR.get(), id, inventory, sonarPos);
        this.sonarPos = sonarPos;
        this.range = range;
        ShipCoreBlockEntity core = null;
        if (!inventory.player.level().isClientSide
                && inventory.player.level() instanceof ServerLevel serverLevel
                && RepairActionPacket.find(serverLevel, sonarPos) instanceof ShipCoreBlockEntity found) {
            core = found;
        }
        this.terminal = core;
        this.physical = core != null && VsState.active() && VsPilot.activeLevel() == core.getLevel()
                && VsPilot.isYardBlock(core.getBlockPos());
        if (core != null) {
            ShipCoreBlockEntity target = core;
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return index == DATA_CONDITION
                            ? (int) Math.round(target.condition() * 10.0D) : 0;
                }

                @Override
                public void set(int index, int value) {
                    // 只读同步。
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

    public BlockPos sonarPos() {
        return sonarPos;
    }

    public int range() {
        return range;
    }

    public ShipCoreBlockEntity terminal() {
        return terminal;
    }

    public boolean physical() {
        return physical;
    }

    /** 服务端在 stillValid 后使用相同绑定读取或控制这艘船。 */
    public ShipState ship(ServerLevel level) {
        if (terminal == null) {
            return null;
        }
        if (physical) {
            return VsState.activeState();
        }
        return terminal.state() != null ? terminal.state() : terminal.rescan(level);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        if (terminal == null || terminal.isRemoved() || terminal.getLevel() != level
                || !level.hasChunkAt(terminal.getBlockPos())
                || level.getBlockEntity(terminal.getBlockPos()) != terminal) {
            return false;
        }
        if (physical) {
            if (!VsState.active() || VsPilot.activeLevel() != level || !VsPilot.isYardBlock(terminal.getBlockPos())) {
                return false;
            }
            Vec3 shipPos = VsPilot.shipPosition();
            return shipPos != null && player.distanceToSqr(shipPos) <= 48.0D * 48.0D;
        }
        return player.distanceToSqr(Vec3.atCenterOf(terminal.getBlockPos())) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
