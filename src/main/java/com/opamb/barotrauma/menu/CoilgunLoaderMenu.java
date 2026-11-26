package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.CoilgunLoaderBlockEntity;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.item.CoilgunAmmoBoxItem;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CoilgunLoaderMenu extends DeviceMenu {
    public static final int INVENTORY_X = 9;
    public static final int INVENTORY_Y = 68;
    private final BlockPos pos;

    public CoilgunLoaderMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos());
    }

    public CoilgunLoaderMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.COILGUN_LOADER.get(), id, inventory, pos);
        this.pos = pos;
        SimpleContainer ammo = inventory.player.level().getBlockEntity(pos) instanceof CoilgunLoaderBlockEntity loader
                ? loader.inventory() : new SimpleContainer(1);
        addSlot(new Slot(ammo, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CoilgunAmmoBoxItem;
            }
        });
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(inventory, i + 9, INVENTORY_X + i % 9 * 18, INVENTORY_Y + i / 9 * 18));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(inventory, i, INVENTORY_X + i * 18, INVENTORY_Y + 58));
        }
        BagSlots.installAt(this, inventory.player, 1, INVENTORY_X, INVENTORY_Y, 160, 54);
        addSlot(new HandsSlot(inventory, HandsSlot.CONTAINER_X, HandsSlot.containerY(INVENTORY_Y)));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || (player.level().hasChunkAt(pos)
                && player.level().getBlockEntity(pos) instanceof CoilgunLoaderBlockEntity
                && player.distanceToSqr(RepairActionPacket.worldCenter(player.level(), pos)) <= 64);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true)
                : moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
