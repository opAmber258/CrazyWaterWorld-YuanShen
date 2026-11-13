package com.opamb.barotrauma.network;

import com.opamb.barotrauma.equipment.BagManager;
import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.item.CrateItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 拿在手上的箱子（双手格）悬停弹窗里的取放。
 *
 * <p>客户端只发"双手格 + 箱子第几格 + 左右键"；服务端校验该槽确实是双手格、
 * 上面是带容器的箱子，再和光标物品交换。左键整堆、右键 1 个（取出时右键拆一半）。</p>
 */
public record CrateSlotPacket(int menuIndex, int crateSlot, int button) {

    public static void encode(CrateSlotPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.menuIndex);
        buf.writeVarInt(message.crateSlot);
        buf.writeByte(message.button);
    }

    public static CrateSlotPacket decode(FriendlyByteBuf buf) {
        return new CrateSlotPacket(buf.readVarInt(), buf.readVarInt(), buf.readByte());
    }

    public static void handle(CrateSlotPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null || menu != player.inventoryMenu) {
                return;
            }
            if (message.menuIndex < 0 || message.menuIndex >= menu.slots.size()) {
                return;
            }
            Slot slot = menu.slots.get(message.menuIndex);
            // 只有拿在手上的箱子会弹格子（放在背包/容器里的箱子看不到内容）。
            if (!(slot instanceof HandsSlot)) {
                return;
            }
            ItemStack host = slot.getItem();
            if (!(host.getItem() instanceof CrateItem crateItem) || crateItem.capacity() <= 0) {
                return;
            }
            int capacity = crateItem.capacity();
            if (message.crateSlot < 0 || message.crateSlot >= capacity) {
                return;
            }
            CrateContainer crate = new CrateContainer(host, capacity);
            ItemStack carried = menu.getCarried();
            ItemStack current = crate.getItem(message.crateSlot);
            boolean one = message.button() == 1;

            if (carried.isEmpty()) {
                if (current.isEmpty()) {
                    return;
                }
                int amount = one ? Math.max(1, (current.getCount() + 1) / 2) : current.getCount();
                menu.setCarried(crate.removeItem(message.crateSlot, amount));
            } else {
                if (!crate.canPlaceItem(message.crateSlot, carried)) {
                    return;
                }
                if (current.isEmpty()) {
                    int amount = one ? 1 : carried.getCount();
                    crate.setItem(message.crateSlot, carried.copyWithCount(amount));
                    carried.shrink(amount);
                } else {
                    if (!ItemStack.isSameItemSameTags(current, carried)) {
                        return;
                    }
                    int limit = Math.min(current.getMaxStackSize(), crate.getMaxStackSize());
                    int room = limit - current.getCount();
                    if (room <= 0) {
                        return;
                    }
                    int amount = one ? Math.min(1, room) : Math.min(room, carried.getCount());
                    current.grow(amount);
                    carried.shrink(amount);
                    crate.setItem(message.crateSlot, current);
                }
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            // 箱子物品的 NBT 变了：宿主槽标脏，背包里的宿主写回背包 NBT。
            slot.setChanged();
            BagManager.commit(player);
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
