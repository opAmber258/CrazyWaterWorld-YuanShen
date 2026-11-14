package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：商人商店状态（钱包、商人余额、全部有价商品）。
 *
 * <p>照原作三个标签页：{@code entries} 给"购买/出售"页，{@code subEntries}
 * 给"潜艇"页（从潜艇货柜里出售，按物品类型聚合）。库存/价格由服务端算，
 * 客户端只负责显示和发操作。</p>
 */
public record StoreStatePacket(int wallet, int merchantBalance, List<Entry> entries,
                               List<SubEntry> subEntries) {

    public record Entry(String itemId, boolean buyable, int stock, int buyPrice,
                        int sellPrice, String category) {
    }

    /** 潜艇里可出售的一类物品（聚合数量）。 */
    public record SubEntry(String itemId, int quantity, int sellPrice) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, wallet));
        buf.writeVarInt(Math.max(0, merchantBalance));
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.itemId(), 128);
            buf.writeBoolean(entry.buyable());
            buf.writeVarInt(Math.max(0, entry.stock()));
            buf.writeVarInt(Math.max(0, entry.buyPrice()));
            buf.writeVarInt(Math.max(0, entry.sellPrice()));
            buf.writeUtf(entry.category() == null ? "" : entry.category(), 32);
        }
        buf.writeVarInt(subEntries.size());
        for (SubEntry entry : subEntries) {
            buf.writeUtf(entry.itemId(), 128);
            buf.writeVarInt(Math.max(0, entry.quantity()));
            buf.writeVarInt(Math.max(0, entry.sellPrice()));
        }
    }

    public static StoreStatePacket decode(FriendlyByteBuf buf) {
        int wallet = buf.readVarInt();
        int balance = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 512) {
            throw new IllegalArgumentException("store entry count");
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(
                    buf.readUtf(128),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(32)));
        }
        int subCount = buf.readVarInt();
        if (subCount < 0 || subCount > 512) {
            throw new IllegalArgumentException("store sub entry count");
        }
        List<SubEntry> subEntries = new ArrayList<>(subCount);
        for (int i = 0; i < subCount; i++) {
            subEntries.add(new SubEntry(buf.readUtf(128), buf.readVarInt(), buf.readVarInt()));
        }
        return new StoreStatePacket(wallet, balance, List.copyOf(entries), List.copyOf(subEntries));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.StoreScreen.receive(this)));
        context.setPacketHandled(true);
    }
}
