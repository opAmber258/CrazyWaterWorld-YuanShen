package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class JunctionBoxMenu extends DeviceMenu {

    private final BlockPos boxCell;
    private final Player owner;

    public JunctionBoxMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readBlockPos());
    }

    public JunctionBoxMenu(int id, Inventory inventory, BlockPos pos, BlockPos boxCell) {
        super(ModMenus.JUNCTION_BOX.get(), id, inventory, pos);
        this.boxCell = boxCell;
        this.owner = inventory.player;
    }

    public BlockPos boxCell() {
        return boxCell;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || repair().valid();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof net.minecraft.server.level.ServerPlayer player
                && player.tickCount % 20 == 0 && stillValid(player)) {
            com.opamb.barotrauma.network.PowerPanelState.refresh(player, boxCell);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
