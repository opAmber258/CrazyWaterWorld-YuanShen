package com.opamb.barotrauma.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public abstract class DeviceMenu extends AbstractContainerMenu {

    private final RepairSession repair;

    protected DeviceMenu(MenuType<?> type, int id, Inventory inventory, BlockPos pos) {
        super(type, id);
        repair = new RepairSession(inventory.player, pos);
        addDataSlots(repair.data());
    }

    public final RepairSession repair() {
        return repair;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        repair.stop();
    }
}
