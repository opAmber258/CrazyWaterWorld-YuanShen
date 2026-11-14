package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：提交购物车/出售车。
 *
 * <p>购买行用 {@code itemId + quantity}；背包出售行用 {@code slot + quantity}；
 * 潜艇出售行（{@link #SELL_SUB}）用 {@code itemId + quantity}，由服务端在船上
 * 货柜里按物品类型扣件。服务端整单校验库存/余额/耐久，成功后再逐件入包或移除。</p>
 *
 * <p>{@code deliverImmediately} 对应原作购买确认的两个选项：{@code true}=现在拿
 * （进角色背包），{@code false}=送到潜艇（放进船上钢柜/货架）。</p>
 */
public record StoreActionPacket(int action, List<Line> lines, boolean deliverImmediately) {

    public static final int BUY = 1;
    public static final int SELL = 2;
    /** 照原作 {@code StoreTab.SellSub}：从潜艇货柜出售。 */
    public static final int SELL_SUB = 3;

    public record Line(String itemId, int slot, int quantity) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(lines.size());
        for (Line line : lines) {
            buf.writeUtf(line.itemId() == null ? "" : line.itemId(), 128);
            buf.writeVarInt(Math.max(0, line.slot()));
            buf.writeVarInt(Math.max(0, line.quantity()));
        }
        buf.writeBoolean(deliverImmediately);
    }

    public static StoreActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("store cart size");
        }
        List<Line> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(new Line(buf.readUtf(128), buf.readVarInt(), buf.readVarInt()));
        }
        return new StoreActionPacket(action, List.copyOf(lines), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.opamb.barotrauma.station.StoreService.handle(player, this);
            }
        });
        context.setPacketHandled(true);
    }
}
