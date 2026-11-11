package com.opamb.barotrauma.network;

import com.opamb.barotrauma.equipment.ItemSubSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 悬停子格（潜水服氧气瓶 / 焊接工具油箱）的取出/放入。
 *
 * <p>客户端只发"点了哪个菜单槽"；服务端校验该槽物品确实带子格、内容物类型匹配，
 * 再和光标物品交换。物品 NBT 变化由原版容器槽位同步回客户端。</p>
 */
public record ItemSubSlotPacket(int menuIndex) {

    public static void encode(ItemSubSlotPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.menuIndex);
    }

    public static ItemSubSlotPacket decode(FriendlyByteBuf buf) {
        return new ItemSubSlotPacket(buf.readVarInt());
    }

    public static void handle(ItemSubSlotPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            // 只允许玩家自己的背包菜单；别的容器菜单下标含义不同。
            if (menu == null || menu != player.inventoryMenu) {
                return;
            }
            if (message.menuIndex < 0 || message.menuIndex >= menu.slots.size()) {
                return;
            }
            Slot slot = menu.slots.get(message.menuIndex);
            ItemStack host = slot.getItem();
            if (!ItemSubSlot.hasSubSlot(host)) {
                return;
            }
            ItemStack contained = ItemSubSlot.get(host);
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) {
                if (!contained.isEmpty()) {
                    menu.setCarried(contained);
                    ItemSubSlot.set(host, ItemStack.EMPTY);
                }
            } else if (contained.isEmpty() && ItemSubSlot.accepts(host, carried)) {
                ItemStack placed = carried.copy();
                placed.setCount(1);
                ItemSubSlot.set(host, placed);
                carried.shrink(1);
                if (carried.isEmpty()) {
                    menu.setCarried(ItemStack.EMPTY);
                }
            } else {
                // 子格已有东西/类型不匹配：不做隐式交换，避免误操作。
                return;
            }
            slot.setChanged();
            // 宿主如果在装备背包里：内容变了要把背包 ItemStack 的 NBT 重新写一遍。
            com.opamb.barotrauma.equipment.BagManager.commit(player);
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
