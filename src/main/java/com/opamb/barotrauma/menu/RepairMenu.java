package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Doors and hatches have no separate operating panel. */
public class RepairMenu extends DeviceMenu {

    public RepairMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.REPAIR.get(), id, inventory, pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || repair().valid();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
