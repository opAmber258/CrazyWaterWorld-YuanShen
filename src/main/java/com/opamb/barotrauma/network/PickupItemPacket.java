package com.opamb.barotrauma.network;

import com.opamb.barotrauma.event.ItemPickupEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端右键点到地上的掉落物 → 请求服务端拾取。
 *
 * <p>不能只靠服务端的 {@code RightClickBlock}：物理船上 VS2 把方块收进船坞，
 * 客户端命中的方块坐标是船坞坐标（离玩家几百万格），服务端
 * {@code handleUseItemOn} 的距离检查会直接丢掉这个包；空手右键空气（MISS）
 * 时服务端也会丢掉 use-item 包。所以右键拾取改成客户端报实体 id，服务端
 * 再校验距离/存活后按正常规则塞进背包。</p>
 */
public record PickupItemPacket(int entityId) {

    /** 服务端接受的距离（格）：略大于客户端 2.5 格的准星距离，留出延迟余量。 */
    private static final double MAX_DISTANCE = 4.0D;

    public static void encode(PickupItemPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
    }

    public static PickupItemPacket decode(FriendlyByteBuf buf) {
        return new PickupItemPacket(buf.readVarInt());
    }

    public static void handle(PickupItemPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            Entity entity = player.level().getEntity(message.entityId);
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || !item.isAlive()) {
                return;
            }
            if (player.distanceToSqr(item) > MAX_DISTANCE * MAX_DISTANCE) {
                return;
            }
            ItemPickupEvents.pickup(player, item);
        });
        context.setPacketHandled(true);
    }
}
