package com.opamb.barotrauma.network;

import com.opamb.barotrauma.menu.CrateMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 背包界面里右键箱子物品 → 打开箱子容器界面。
 *
 * <p>只发背包菜单的槽位下标；服务端校验那个槽位上确实是带容器的箱子，
 * 再按同一个下标开 {@link CrateMenu}（客户端也从 {@code inventoryMenu}
 * 的同一下标取箱子，两边共用同一份 NBT）。</p>
 */
public record OpenCratePacket(int menuIndex) {

    public static void encode(OpenCratePacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.menuIndex);
    }

    public static OpenCratePacket decode(FriendlyByteBuf buf) {
        return new OpenCratePacket(buf.readVarInt());
    }

    public static void handle(OpenCratePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            // 只在玩家自己背包界面里生效；别的容器菜单下标含义不同。
            if (menu == null || menu != player.inventoryMenu) {
                return;
            }
            CrateMenu.open(player, message.menuIndex);
        });
        context.setPacketHandled(true);
    }
}
